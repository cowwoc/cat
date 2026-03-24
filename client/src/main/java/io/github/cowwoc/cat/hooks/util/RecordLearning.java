/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.cat.hooks.util.GitCommands.runGit;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import static io.github.cowwoc.cat.hooks.Strings.block;

import io.github.cowwoc.cat.hooks.Config;
import io.github.cowwoc.cat.hooks.ClaudeTool;
import io.github.cowwoc.cat.hooks.MainClaudeTool;
import io.github.cowwoc.cat.hooks.Strings;
import io.github.cowwoc.cat.hooks.WorktreeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Stream;

/**
 * Records a learning entry from Phase 3 JSON into the retrospectives system.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Generate next mistake ID (e.g., M001, M002, ...)</li>
 *   <li>Append entry to {@code mistakes-YYYY-MM.json} for the current month</li>
 *   <li>Validate and increment the counter in {@code index.json}</li>
 *   <li>Detect retrospective threshold</li>
 *   <li>Determine commit location (worktree vs main workspace)</li>
 *   <li>Stage and commit the new learning entry</li>
 * </ul>
 */
public final class RecordLearning
{
  private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
  private static final DateTimeFormatter ISO_FORMAT =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);
  private static final int DEFAULT_THRESHOLD = 10;
  private static final int DEFAULT_INTERVAL_DAYS = 7;
  private static final int MAX_STDIN_BYTES = 10 * 1024 * 1024;

  private final ClaudeTool scope;
  private final Path projectPath;
  private final Clock clock;

  /**
   * Creates a new RecordLearning instance.
   *
   * @param scope the JVM scope providing JSON mapper
   * @param projectPath the project root directory (CLAUDE_PROJECT_DIR)
   * @param clock the clock to use for timestamp generation
   * @throws NullPointerException if any parameter is null
   */
  public RecordLearning(ClaudeTool scope, Path projectPath, Clock clock)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(projectPath, "projectPath").isNotNull();
    requireThat(clock, "clock").isNotNull();
    this.scope = scope;
    this.projectPath = projectPath;
    this.clock = clock;
  }

  /**
   * Executes the record learning operation.
   * <p>
   * Uses the session ID to look up the active worktree lock and determine the commit location.
   * When the session holds an active worktree lock, files are committed inside that worktree.
   * When no lock is found, files are committed in the project directory.
   *
   * @param phase3Input the Phase 3 JSON output containing learning metadata
   * @param sessionId the session ID used to locate the active worktree lock
   * @return JSON result with {@code learning_id}, {@code counter_status}, {@code retrospective_trigger},
   *   and {@code commit_hash}
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if {@code sessionId} is blank
   * @throws IOException if file operations or git commands fail
   */
  public String execute(ObjectNode phase3Input, String sessionId) throws IOException
  {
    requireThat(phase3Input, "phase3Input").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();

    Path commitDir = determineCommitLocation(sessionId);
    return executeWithCommitDir(phase3Input, commitDir);
  }

  /**
   * Executes the core record learning logic against a specific commit directory.
   *
   * @param phase3Input the Phase 3 JSON output containing learning metadata
   * @param commitDir the directory from which to make the git commit
   * @return JSON result with {@code learning_id}, {@code counter_status}, {@code retrospective_trigger},
   *   and {@code commit_hash}
   * @throws IOException if file operations or git commands fail
   */
  private String executeWithCommitDir(ObjectNode phase3Input, Path commitDir) throws IOException
  {
    Path retroDir = commitDir.resolve(Config.CAT_DIR_NAME).resolve("retrospectives");
    Files.createDirectories(retroDir);

    Path indexFile = retroDir.resolve("index.json");
    ObjectNode index = loadOrCreateIndex(indexFile);

    // Determine current month file
    String yearMonth = ZonedDateTime.now(clock).format(YEAR_MONTH_FORMAT);
    Path mistakesFile = retroDir.resolve("mistakes-" + yearMonth + ".json");
    ObjectNode mistakesData = loadOrCreateMistakesFile(mistakesFile, yearMonth, index);

    // Determine last retrospective timestamp for counting (needed by scan)
    String lastRetro = null;
    JsonNode lastRetroNode = index.path("last_retrospective");
    if (!lastRetroNode.isNull() && !lastRetroNode.isMissingNode())
      lastRetro = lastRetroNode.asString();

    // Single scan: compute next ID and count of mistakes since last retrospective together
    ScanResult scan = scanMistakesFiles(retroDir, lastRetro);
    String nextId = String.format("M%03d", scan.maxId() + 1);

    // Build the entry
    String timestamp = ZonedDateTime.now(clock).format(ISO_FORMAT);
    ObjectNode entry = buildEntry(nextId, timestamp, phase3Input);

    // Append to mistakes file
    JsonNode mistakesNode = mistakesData.get("mistakes");
    if (mistakesNode == null || !mistakesNode.isArray())
      throw new IOException("Corrupted mistakes file at '" + mistakesFile + "': 'mistakes' field is missing or " +
        "not an array. Inspect and fix the file, then retry.");
    ArrayNode mistakes = (ArrayNode) mistakesNode;
    mistakes.add(entry);
    writePrettyJson(mistakesFile, mistakesData);

    // Validate and increment counter using the pre-computed count from the single scan pass.
    // The entry was already appended to the in-memory array and written to disk, so
    // scan.countSinceLastRetro() does not yet include it; pass it directly for increment logic.
    int newCount = validateAndIncrementCounter(index, scan.countSinceLastRetro(), indexFile);

    // Get threshold and trigger status
    int threshold = index.path("config").path("mistake_count_threshold").asInt(DEFAULT_THRESHOLD);
    int intervalDays = index.path("config").path("trigger_interval_days").asInt(DEFAULT_INTERVAL_DAYS);
    boolean retrospectiveTrigger = checkRetrospectiveTrigger(index, newCount, threshold, intervalDays);

    // Stage and commit — paths relative to the git top-level of commitDir
    String gitTopLevel = runGit(commitDir, "rev-parse", "--show-toplevel").strip();
    Path repoRoot = Path.of(gitTopLevel);
    String mistakesRelative = repoRoot.relativize(mistakesFile.toAbsolutePath()).toString();
    String indexRelative = repoRoot.relativize(indexFile.toAbsolutePath()).toString();
    String description = getStringField(phase3Input, "description", "learning entry");
    String commitMessage = "config: record learning " + nextId + " - " +
      Strings.truncate(description, Strings.DESCRIPTION_MAX_LENGTH);

    runGit(commitDir, "add", "--", mistakesRelative, indexRelative);
    runGit(commitDir, "commit", "-m", commitMessage);

    String commitHash = runGit(commitDir, "rev-parse", "--short", "HEAD").strip();

    // Build output
    ObjectNode result = scope.getJsonMapper().createObjectNode();
    result.put("learning_id", nextId);
    ObjectNode counterStatus = scope.getJsonMapper().createObjectNode();
    counterStatus.put("count", newCount);
    counterStatus.put("threshold", threshold);
    result.set("counter_status", counterStatus);
    result.put("retrospective_trigger", retrospectiveTrigger);
    result.put("commit_hash", commitHash);

    return scope.getJsonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result);
  }

  /**
   * Loads the index.json file, or creates a default one if it does not exist.
   *
   * @param indexFile path to index.json
   * @return the index as an ObjectNode
   * @throws IOException if reading fails
   */
  private ObjectNode loadOrCreateIndex(Path indexFile) throws IOException
  {
    try
    {
      JsonNode parsed = scope.getJsonMapper().readTree(Files.readString(indexFile));
      if (!parsed.isObject())
        throw new IOException("Corrupted index file at '" + indexFile + "': expected a JSON object but got " +
          parsed.getNodeType() + ". Inspect and fix the file, then retry.");
      return (ObjectNode) parsed;
    }
    catch (NoSuchFileException _)
    {
      // File does not exist yet — fall through to create the default index
    }
    catch (JacksonException e)
    {
      throw wrapJacksonException(indexFile.toString(), e);
    }

    ObjectNode index = scope.getJsonMapper().createObjectNode();
    index.put("version", "2.0");
    ObjectNode config = scope.getJsonMapper().createObjectNode();
    config.put("mistake_count_threshold", DEFAULT_THRESHOLD);
    config.put("trigger_interval_days", DEFAULT_INTERVAL_DAYS);
    index.set("config", config);
    index.putNull("last_retrospective");
    index.put("mistake_count_since_last", 0);
    ObjectNode files = scope.getJsonMapper().createObjectNode();
    files.set("mistakes", scope.getJsonMapper().createArrayNode());
    files.set("retrospectives", scope.getJsonMapper().createArrayNode());
    index.set("files", files);
    writePrettyJson(indexFile, index);
    return index;
  }

  /**
   * Loads a mistakes file for the given period, or creates an empty one if it does not exist.
   * <p>
   * If created, the filename is added to the index's {@code files.mistakes} list.
   *
   * @param mistakesFile path to the mistakes file
   * @param yearMonth the period string (e.g. "2026-03")
   * @param index the index ObjectNode (modified in place if file is created)
   * @return the mistakes data as an ObjectNode
   * @throws IOException if reading or writing fails
   */
  private ObjectNode loadOrCreateMistakesFile(Path mistakesFile, String yearMonth, ObjectNode index)
    throws IOException
  {
    try
    {
      JsonNode parsed = scope.getJsonMapper().readTree(Files.readString(mistakesFile));
      if (!parsed.isObject())
        throw new IOException("Corrupted mistakes file at '" + mistakesFile + "': expected a JSON object but got " +
          parsed.getNodeType() + ". Inspect and fix the file, then retry.");
      return (ObjectNode) parsed;
    }
    catch (NoSuchFileException _)
    {
      // File does not exist yet — fall through to create a new empty mistakes file
    }
    catch (JacksonException e)
    {
      throw wrapJacksonException(mistakesFile.toString(), e);
    }

    ObjectNode data = scope.getJsonMapper().createObjectNode();
    data.put("period", yearMonth);
    data.set("mistakes", scope.getJsonMapper().createArrayNode());
    writePrettyJson(mistakesFile, data);

    // Add to index files list
    String filename = "mistakes-" + yearMonth + ".json";
    JsonNode mistakeFilesNode = index.path("files").path("mistakes");
    if (!mistakeFilesNode.isArray())
      throw new IOException("Corrupted index.json: 'files.mistakes' field is missing or not an array. " +
        "Inspect and fix index.json in the retrospectives directory, then retry.");
    ArrayNode mistakeFiles = (ArrayNode) mistakeFilesNode;
    boolean alreadyListed = false;
    for (JsonNode fileNode : mistakeFiles)
    {
      if (filename.equals(fileNode.asString()))
      {
        alreadyListed = true;
        break;
      }
    }
    if (!alreadyListed)
      mistakeFiles.add(filename);

    return data;
  }

  /**
   * Result of a single scan pass over all mistakes files.
   *
   * @param maxId the highest numeric suffix seen in any {@code M<n>} ID (0 when no entries exist)
   * @param countSinceLastRetro the number of entries whose timestamp is after the last retrospective
   *   (all entries when no retrospective has been recorded)
   */
  private record ScanResult(int maxId, int countSinceLastRetro)
  {
  }

  /**
   * Scans all mistakes files in one pass, computing both the highest existing ID number and the count
   * of mistakes since the last retrospective.
   * <p>
   * Combining these two traversals avoids reading each file twice when recording a new mistake.
   *
   * @param retroDir the retrospectives directory
   * @param lastRetroTimestamp the ISO timestamp of the last retrospective, or null if none
   * @return a {@link ScanResult} with the max ID and count-since-last-retro
   * @throws IOException if reading files fails
   */
  private ScanResult scanMistakesFiles(Path retroDir, String lastRetroTimestamp) throws IOException
  {
    int maxNum = 0;
    int count = 0;
    if (Files.isDirectory(retroDir))
    {
      try (Stream<Path> files = Files.list(retroDir))
      {
        List<Path> mistakeFiles = files.
          filter(p -> p.getFileName().toString().startsWith("mistakes-") &&
            p.getFileName().toString().endsWith(".json")).
          toList();
        for (Path f : mistakeFiles)
        {
          JsonNode data;
          try
          {
            data = scope.getJsonMapper().readTree(Files.readString(f));
          }
          catch (JacksonException e)
          {
            throw wrapJacksonException(f.toString(), e);
          }
          JsonNode mistakesArr = data.path("mistakes");
          if (mistakesArr.isArray())
          {
            for (JsonNode m : mistakesArr)
            {
              // Track max ID
              String id = getStringField(m, "id", "");
              if (id.startsWith("M") && id.length() > 1)
              {
                int num;
                try
                {
                  num = Integer.parseInt(id.substring(1));
                }
                catch (NumberFormatException e)
                {
                  throw new IOException("Corrupted mistake ID '" + id + "' in " + f +
                    ". Expected format: M followed by digits (e.g., M001).", e);
                }
                if (num > maxNum)
                  maxNum = num;
              }

              // Count entries since last retrospective
              if (lastRetroTimestamp == null)
                ++count;
              else
              {
                String ts = getStringField(m, "timestamp", "");
                if (!ts.isEmpty() && ts.compareTo(lastRetroTimestamp) > 0)
                  ++count;
              }
            }
          }
        }
      }
    }
    return new ScanResult(maxNum, count);
  }

  /**
   * Builds a mistake entry ObjectNode from Phase 3 input.
   *
   * @param id the generated mistake ID
   * @param timestamp the ISO timestamp
   * @param phase3Input the Phase 3 input JSON
   * @return the entry as an ObjectNode
   */
  private ObjectNode buildEntry(String id, String timestamp, ObjectNode phase3Input)
  {
    ObjectNode entry = scope.getJsonMapper().createObjectNode();
    entry.put("id", id);
    entry.put("timestamp", timestamp);
    entry.put("category", getStringField(phase3Input, "category", ""));
    entry.put("description", getStringField(phase3Input, "description", ""));
    entry.put("root_cause", getStringField(phase3Input, "root_cause", ""));
    String causeSignature = getStringField(phase3Input, "cause_signature", null);
    if (causeSignature == null)
      entry.putNull("cause_signature");
    else
      entry.put("cause_signature", causeSignature);
    entry.put("prevention_type", getStringField(phase3Input, "prevention_type", ""));
    entry.put("prevention_path", getStringField(phase3Input, "prevention_path", ""));

    JsonNode keywords = phase3Input.path("pattern_keywords");
    if (keywords.isArray())
      entry.set("pattern_keywords", keywords);
    else
      entry.set("pattern_keywords", scope.getJsonMapper().createArrayNode());

    entry.put("prevention_implemented", phase3Input.path("prevention_implemented").asBoolean(false));
    entry.put("prevention_verified", phase3Input.path("prevention_verified").asBoolean(false));

    JsonNode recurrenceOf = phase3Input.path("recurrence_of");
    if (recurrenceOf.isNull() || recurrenceOf.isMissingNode())
      entry.putNull("recurrence_of");
    else
      entry.put("recurrence_of", recurrenceOf.asString());

    JsonNode quality = phase3Input.path("prevention_quality");
    if (quality.isObject())
      entry.set("prevention_quality", quality);

    entry.put("correct_behavior", getStringField(phase3Input, "correct_behavior", ""));
    return entry;
  }

  /**
   * Validates the counter against actual mistake count, corrects if mismatched, then increments by 1.
   * <p>
   * The counter in index.json reflects mistakes since the last retrospective. If the counter does not
   * match the actual count in the mistake files, it is corrected before incrementing.
   * <p>
   * The caller must pass the count derived from the same scan that generated the next ID, to avoid
   * a redundant file scan.
   *
   * @param index the index ObjectNode (modified in place)
   * @param actualCountBeforeNewEntry the count of mistakes since last retrospective, NOT including
   *   the entry that was just appended (derived from the scan done before appending)
   * @param indexFile path to index.json (written after update)
   * @return the new counter value after incrementing
   * @throws IOException if writing fails
   */
  private int validateAndIncrementCounter(ObjectNode index, int actualCountBeforeNewEntry, Path indexFile)
    throws IOException
  {
    // The new entry has already been appended, so the true current count is actualCountBeforeNewEntry + 1.
    int actualCount = actualCountBeforeNewEntry + 1;
    int counter = index.path("mistake_count_since_last").asInt(0);

    int newCount;
    // counter should be (actualCount - 1) before we increment, meaning counter
    // was behind by exactly 1. If it differs by more, fix it.
    if (counter != actualCount - 1 && counter != actualCount)
    {
      // Mismatch: set to actual count (which includes the newly appended entry)
      newCount = actualCount;
    }
    else
    {
      newCount = counter + 1;
    }

    index.put("mistake_count_since_last", newCount);
    writePrettyJson(indexFile, index);
    return newCount;
  }

  /**
   * Checks whether a retrospective should be triggered.
   *
   * @param index the index ObjectNode
   * @param count the current mistake count
   * @param threshold the mistake count threshold
   * @param intervalDays the interval threshold in days
   * @return true if retrospective should be triggered
   */
  private boolean checkRetrospectiveTrigger(ObjectNode index, int count, int threshold, int intervalDays)
  {
    if (count >= threshold)
      return true;

    JsonNode lastRetroNode = index.path("last_retrospective");
    if (lastRetroNode.isNull() || lastRetroNode.isMissingNode())
      return false;

    String lastRetroStr = lastRetroNode.asString();
    Instant lastRetroInstant;
    try
    {
      lastRetroInstant = Instant.parse(lastRetroStr);
    }
    catch (DateTimeParseException e)
    {
      throw new IllegalArgumentException("Corrupted last_retrospective timestamp '" + lastRetroStr +
        "' in index.json. Expected ISO-8601 format (e.g., 2026-03-05T10:00:00Z).", e);
    }
    Instant now = clock.instant();
    long daysSince = (now.toEpochMilli() - lastRetroInstant.toEpochMilli()) / 86_400_000L;
    return daysSince >= intervalDays;
  }

  /**
   * Executes the record learning operation in a specific commit directory.
   * <p>
   * This method is for testing and for fallback execution when no session ID is available.
   * Production code should prefer {@link #execute(ObjectNode, String)}.
   *
   * @param phase3Input the Phase 3 JSON output containing learning metadata
   * @param commitDir the directory to commit from
   * @return JSON result with {@code learning_id}, {@code counter_status}, {@code retrospective_trigger},
   *   and {@code commit_hash}
   * @throws NullPointerException if any parameter is null
   * @throws IOException if file operations or git commands fail
   */
  public String executeInDir(ObjectNode phase3Input, Path commitDir) throws IOException
  {
    requireThat(phase3Input, "phase3Input").isNotNull();
    requireThat(commitDir, "commitDir").isNotNull();
    return executeWithCommitDir(phase3Input, commitDir.toAbsolutePath().normalize());
  }

  /**
   * Determines where to commit by looking up the active worktree lock for the session.
   * <p>
   * When the session holds an active worktree lock and the worktree directory exists,
   * the commit is made inside the worktree. Otherwise the commit is made in the project directory.
   *
   * @param sessionId the session ID used to look up the active worktree lock
   * @return the path to commit from
   */
  private Path determineCommitLocation(String sessionId)
  {
    return WorktreeContext.forSession(
        scope.getCatWorkPath(), projectPath, scope.getJsonMapper(), sessionId).
      map(WorktreeContext::absoluteWorktreePath).
      orElse(projectPath);
  }

  /**
   * Wraps a Jackson JSON parse failure as an IOException with a user-actionable message.
   * <p>
   * Jackson 3.x parse exceptions extend {@link RuntimeException} via {@link JacksonException}.
   * This helper converts a {@link JacksonException} into an {@link IOException} with the file path
   * and repair instructions included.
   *
   * @param filePath the path of the file that failed to parse
   * @param cause the original Jackson parse exception
   * @return an IOException with a message naming the file and instructing the user to inspect it
   */
  private static IOException wrapJacksonException(String filePath, JacksonException cause)
  {
    return new IOException(
      "Failed to parse file at '" + filePath + "': " + cause.getMessage() +
        ". The file contains malformed JSON. Inspect and fix the file, then retry.", cause);
  }

  /**
   * Writes a JSON node to a file using pretty printing.
   *
   * @param path the file path
   * @param node the JSON node to write
   * @throws IOException if writing fails
   */
  private void writePrettyJson(Path path, JsonNode node) throws IOException
  {
    Files.writeString(path, scope.getJsonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(node));
  }

  /**
   * Gets a string field from a JSON node, returning a default if missing or not a string.
   *
   * @param node the JSON node
   * @param field the field name
   * @param defaultValue the default value
   * @return the field value or default
   */
  private static String getStringField(JsonNode node, String field, String defaultValue)
  {
    JsonNode v = node.path(field);
    if (v.isNull() || v.isMissingNode())
      return defaultValue;
    return v.asString(defaultValue);
  }

  /**
   * Main method for command-line execution.
   * <p>
   * Reads Phase 3 JSON from stdin and writes the JSON result to stdout.
   *
   * @param args command-line arguments (none required)
   */
  public static void main(String[] args)
  {
    try (ClaudeTool scope = new MainClaudeTool())
    {
      try
      {
        run(scope, System.in, System.out, () -> scope.getProjectPath().toString());
      }
      catch (RuntimeException | AssertionError e)
      {
        Logger log = LoggerFactory.getLogger(RecordLearning.class);
        log.error("Unexpected error", e);
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }

  /**
   * Executes the record-learning logic with caller-provided streams.
   * <p>
   * Separated from {@link #main(String[])} to allow unit testing without JVM exit.
   * IOException is converted to a block response on {@code out}.
   *
   * @param scope the JVM scope
   * @param in    the input stream to read Phase 3 JSON from
   * @param out   the output stream to write JSON to
   * @param projectPathProvider supplier that provides CLAUDE_PROJECT_DIR
   * @throws NullPointerException if {@code in}, {@code out}, or {@code projectPathProvider} are null
   */
  public static void run(ClaudeTool scope, InputStream in, PrintStream out,
    Supplier<String> projectPathProvider)
  {
    requireThat(in, "in").isNotNull();
    requireThat(out, "out").isNotNull();
    requireThat(projectPathProvider, "projectPathProvider").isNotNull();

    Path projectPath;
    try
    {
      String projectPathStr = projectPathProvider.get();
      projectPath = Path.of(projectPathStr);
    }
    catch (AssertionError _)
    {
      out.println(block(scope,
        "CLAUDE_PROJECT_DIR environment variable is not set. " +
          "The record-learning hook requires this variable to locate the project root directory."));
      return;
    }

    String stdinJson;
    try (BufferedReader reader = new BufferedReader(
      new InputStreamReader(in, StandardCharsets.UTF_8)))
    {
      StringBuilder sb = new StringBuilder();
      String line = reader.readLine();
      while (line != null)
      {
        sb.append(line).append('\n');
        if (sb.length() > MAX_STDIN_BYTES)
        {
          out.println(block(scope, "Input exceeds maximum allowed size of 10 MB"));
          return;
        }
        line = reader.readLine();
      }
      stdinJson = sb.toString().strip();
    }
    catch (IOException e)
    {
      out.println(block(scope,
        "Failed to read stdin: " + Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      return;
    }

    if (stdinJson.isEmpty())
    {
      out.println(block(scope, "No input provided on stdin. Expected Phase 3 JSON."));
      return;
    }

    ObjectNode phase3Input;
    try
    {
      JsonNode parsed = scope.getJsonMapper().readTree(stdinJson);
      if (!parsed.isObject())
      {
        out.println(block(scope, "Stdin JSON is not an object. Expected Phase 3 JSON object."));
        return;
      }
      phase3Input = (ObjectNode) parsed;
    }
    catch (Exception e)
    {
      out.println(block(scope,
        "Failed to parse stdin as JSON: " + Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      return;
    }

    String sessionId = scope.getSessionId();
    RecordLearning cmd = new RecordLearning(scope, projectPath, Clock.systemUTC());

    try
    {
      String result = cmd.execute(phase3Input, sessionId);
      out.println(result);
    }
    catch (IOException e)
    {
      out.println(block(scope,
        Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
    }
  }
}
