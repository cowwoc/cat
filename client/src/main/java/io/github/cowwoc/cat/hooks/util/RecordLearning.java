/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.cat.hooks.util.GitCommands.runGit;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.ClaudeEnv;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
  private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
  private static final int DEFAULT_THRESHOLD = 10;
  private static final int DEFAULT_INTERVAL_DAYS = 7;

  private final JvmScope scope;
  private final Path projectDir;
  private final Clock clock;

  /**
   * Creates a new RecordLearning instance.
   *
   * @param scope the JVM scope providing JSON mapper
   * @param projectDir the project root directory (CLAUDE_PROJECT_DIR)
   * @param clock the clock to use for timestamp generation
   * @throws NullPointerException if any parameter is null
   */
  public RecordLearning(JvmScope scope, Path projectDir, Clock clock)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(projectDir, "projectDir").isNotNull();
    requireThat(clock, "clock").isNotNull();
    this.scope = scope;
    this.projectDir = projectDir;
    this.clock = clock;
  }

  /**
   * Executes the record learning operation.
   *
   * @param phase3Input the Phase 3 JSON output containing learning metadata
   * @param cwdForCommit the current working directory — used to determine if we are in a worktree
   * @return JSON result with {@code learning_id}, {@code counter_status}, {@code retrospective_trigger},
   *   and {@code commit_hash}
   * @throws NullPointerException if any parameter is null
   * @throws IOException if file operations or git commands fail
   */
  public String execute(ObjectNode phase3Input, Path cwdForCommit) throws IOException
  {
    requireThat(phase3Input, "phase3Input").isNotNull();
    requireThat(cwdForCommit, "cwdForCommit").isNotNull();

    // Determine commit location first — retrospective files are written into commitDir
    Path commitDir = determineCommitLocation(cwdForCommit);
    Path retroDir = commitDir.resolve(".claude").resolve("cat").resolve("retrospectives");
    Files.createDirectories(retroDir);

    Path indexFile = retroDir.resolve("index.json");
    ObjectNode index = loadOrCreateIndex(indexFile);

    // Determine current month file
    String yearMonth = ZonedDateTime.now(clock).format(YEAR_MONTH_FORMAT);
    Path mistakesFile = retroDir.resolve("mistakes-" + yearMonth + ".json");
    ObjectNode mistakesData = loadOrCreateMistakesFile(mistakesFile, yearMonth, index);

    // Generate next ID
    String nextId = generateNextId(retroDir);

    // Build the entry
    String timestamp = ZonedDateTime.now(clock).format(ISO_FORMAT);
    ObjectNode entry = buildEntry(nextId, timestamp, phase3Input);

    // Append to mistakes file
    ArrayNode mistakes = (ArrayNode) mistakesData.get("mistakes");
    mistakes.add(entry);
    writePrettyJson(mistakesFile, mistakesData);

    // Verify the entry was written
    JsonNode verifyData = scope.getJsonMapper().readTree(Files.readString(mistakesFile));
    boolean found = false;
    for (JsonNode m : verifyData.get("mistakes"))
    {
      if (nextId.equals(m.get("id").asString()))
      {
        found = true;
        break;
      }
    }
    if (!found)
      throw new IOException("Failed to write mistake entry " + nextId + " to " + mistakesFile);

    // Validate and increment counter
    int newCount = validateAndIncrementCounter(index, retroDir, indexFile);

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
      truncate(description, 60);

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
    if (Files.exists(indexFile))
      return (ObjectNode) scope.getJsonMapper().readTree(Files.readString(indexFile));

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
    if (Files.exists(mistakesFile))
      return (ObjectNode) scope.getJsonMapper().readTree(Files.readString(mistakesFile));

    ObjectNode data = scope.getJsonMapper().createObjectNode();
    data.put("period", yearMonth);
    data.set("mistakes", scope.getJsonMapper().createArrayNode());
    writePrettyJson(mistakesFile, data);

    // Add to index files list
    String filename = "mistakes-" + yearMonth + ".json";
    ArrayNode mistakeFiles = (ArrayNode) index.path("files").path("mistakes");
    boolean alreadyListed = false;
    for (JsonNode f : mistakeFiles)
    {
      if (filename.equals(f.asString()))
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
   * Generates the next mistake ID by scanning all existing mistake files.
   *
   * @param retroDir the retrospectives directory
   * @return the next ID string (e.g., "M001")
   * @throws IOException if reading files fails
   */
  private String generateNextId(Path retroDir) throws IOException
  {
    int maxNum = 0;
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
          JsonNode data = scope.getJsonMapper().readTree(Files.readString(f));
          JsonNode mistakesArr = data.path("mistakes");
          if (mistakesArr.isArray())
          {
            for (JsonNode m : mistakesArr)
            {
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
            }
          }
        }
      }
    }
    return String.format("M%03d", maxNum + 1);
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
    entry.put("rca_method", getStringField(phase3Input, "rca_method", ""));
    entry.put("rca_method_name", getStringField(phase3Input, "rca_method_name", ""));
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
   *
   * @param index the index ObjectNode (modified in place)
   * @param retroDir the retrospectives directory
   * @param indexFile path to index.json (written after update)
   * @return the new counter value after incrementing
   * @throws IOException if reading or writing fails
   */
  private int validateAndIncrementCounter(ObjectNode index, Path retroDir, Path indexFile) throws IOException
  {
    String lastRetro = null;
    JsonNode lastRetroNode = index.path("last_retrospective");
    if (!lastRetroNode.isNull() && !lastRetroNode.isMissingNode())
      lastRetro = lastRetroNode.asString();

    // Count actual mistakes since last retrospective
    int actualCount = countMistakesSinceLastRetro(retroDir, lastRetro);
    int counter = index.path("mistake_count_since_last").asInt(0);

    int newCount;
    // The entry was already appended, so actualCount includes the new mistake.
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
   * Counts mistakes recorded after the last retrospective across all split files.
   *
   * @param retroDir the retrospectives directory
   * @param lastRetroTimestamp the ISO timestamp of the last retrospective, or null if none
   * @return the count of mistakes since last retrospective
   * @throws IOException if reading files fails
   */
  private int countMistakesSinceLastRetro(Path retroDir, String lastRetroTimestamp) throws IOException
  {
    if (!Files.isDirectory(retroDir))
      return 0;

    int count = 0;
    try (Stream<Path> files = Files.list(retroDir))
    {
      List<Path> mistakeFiles = files.
        filter(p -> p.getFileName().toString().startsWith("mistakes-") &&
          p.getFileName().toString().endsWith(".json")).
        toList();

      for (Path f : mistakeFiles)
      {
        JsonNode data = scope.getJsonMapper().readTree(Files.readString(f));
        JsonNode mistakesArr = data.path("mistakes");
        if (mistakesArr.isArray())
        {
          for (JsonNode m : mistakesArr)
          {
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
    return count;
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
    catch (Exception e)
    {
      throw new IllegalArgumentException("Corrupted last_retrospective timestamp '" + lastRetroStr +
        "' in index.json. Expected ISO-8601 format (e.g., 2026-03-05T10:00:00Z).", e);
    }
    Instant now = clock.instant();
    long daysSince = (now.toEpochMilli() - lastRetroInstant.toEpochMilli()) / 86_400_000L;
    return daysSince >= intervalDays;
  }

  /**
   * Determines where to commit: in a worktree if the current directory is a CAT worktree,
   * otherwise the project directory.
   *
   * @param cwd the current working directory
   * @return the path to commit from
   * @throws IOException if git commands fail
   */
  private Path determineCommitLocation(Path cwd) throws IOException
  {
    // Check if cwd is a git worktree with a cat-branch-point marker
    try
    {
      String gitCommonDirStr = runGit(cwd, "rev-parse", "--git-common-dir").strip();
      Path gitCommonDir = cwd.resolve(gitCommonDirStr).normalize();
      String worktreeName = cwd.getFileName().toString();
      Path catBranchPoint = gitCommonDir.resolve("worktrees").resolve(worktreeName).
        resolve("cat-branch-point");
      if (Files.exists(catBranchPoint))
        return cwd;
    }
    catch (IOException _)
    {
      // Not in a git repo or command failed — fall through
    }
    return projectDir;
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
   * Truncates a string to at most {@code maxLen} characters, appending "..." if truncated.
   *
   * @param str the string to truncate
   * @param maxLen the maximum length
   * @return the truncated string
   */
  private static String truncate(String str, int maxLen)
  {
    if (str.length() <= maxLen)
      return str;
    return str.substring(0, maxLen - 3) + "...";
  }

  /**
   * Main method for command-line execution.
   * <p>
   * Reads Phase 3 JSON from stdin and writes the JSON result to stdout.
   *
   * @param args command-line arguments (none required)
   * @throws IOException if the operation fails
   */
  public static void main(String[] args) throws IOException
  {
    String projectDirStr;
    try
    {
      projectDirStr = new ClaudeEnv().getClaudeProjectDir().toString();
    }
    catch (AssertionError _)
    {
      // CLAUDE_PROJECT_DIR not set — fall back to git root detection
      try
      {
        projectDirStr = runGit("rev-parse", "--show-toplevel");
      }
      catch (IOException _)
      {
        System.err.println("""
          {
            "status": "error",
            "message": "CLAUDE_PROJECT_DIR is not set and git rev-parse --show-toplevel failed"
          }""");
        System.exit(1);
        return;
      }
    }

    String stdinJson;
    try (BufferedReader reader = new BufferedReader(
      new InputStreamReader(System.in, StandardCharsets.UTF_8)))
    {
      StringBuilder sb = new StringBuilder();
      String line = reader.readLine();
      while (line != null)
      {
        sb.append(line).append('\n');
        line = reader.readLine();
      }
      stdinJson = sb.toString().strip();
    }

    if (stdinJson.isEmpty())
    {
      System.err.println("""
        {
          "status": "error",
          "message": "No input provided on stdin. Expected Phase 3 JSON."
        }""");
      System.exit(1);
    }

    try (JvmScope scope = new MainJvmScope())
    {
      ObjectNode phase3Input;
      try
      {
        phase3Input = (ObjectNode) scope.getJsonMapper().readTree(stdinJson);
      }
      catch (Exception e)
      {
        System.err.println("""
          {
            "status": "error",
            "message": "Failed to parse stdin as JSON: %s"
          }""".formatted(e.getMessage().replace("\"", "\\\"")));
        System.exit(1);
        return;
      }

      Path projectDir = Path.of(projectDirStr);
      Path cwd = Path.of(System.getProperty("user.dir"));
      RecordLearning cmd = new RecordLearning(scope, projectDir, Clock.systemUTC());

      try
      {
        String result = cmd.execute(phase3Input, cwd);
        System.out.println(result);
      }
      catch (IOException e)
      {
        System.err.println("""
          {
            "status": "error",
            "message": "%s"
          }""".formatted(e.getMessage().replace("\"", "\\\"")));
        System.exit(1);
      }
      catch (RuntimeException | AssertionError e)
      {
        Logger log = LoggerFactory.getLogger(RecordLearning.class);
        log.error("Unexpected error", e);
        throw e;
      }
    }
  }
}
