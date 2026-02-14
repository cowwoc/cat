package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.cat.hooks.skills.JsonHelper.getStringOrDefault;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.MainJvmScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Migration script for retrospective files.
 * <p>
 * Splits existing mistakes.json and retrospectives.json into time-based files:
 * <ul>
 *   <li>mistakes-YYYY-MM.json for mistakes by month</li>
 *   <li>retrospectives-YYYY-MM.json for retrospectives by month</li>
 *   <li>index.json for centralized config and file tracking</li>
 * </ul>
 */
public final class RetrospectiveMigrator
{
  private static final DateTimeFormatter[] TIMESTAMP_FORMATS = {
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
  };

  private final JsonMapper mapper;

  /**
   * Creates a new retrospective migrator.
   *
   * @param mapper the JSON mapper for parsing and serialization
   * @throws NullPointerException if {@code mapper} is null
   */
  public RetrospectiveMigrator(JsonMapper mapper)
  {
    requireThat(mapper, "mapper").isNotNull();
    this.mapper = mapper;
  }

  /**
   * Main method for command-line execution.
   * <p>
   * Migrates retrospective files to time-based split format and prints the JSON result to stdout.
   *
   * @param args command-line arguments: {@code [--dry-run] [project-dir]}
   * @throws IOException if the operation fails
   */
  public static void main(String[] args) throws IOException
  {
    boolean dryRun = false;
    String projectDir = ".";

    for (String arg : args)
    {
      if (arg.equals("--dry-run"))
        dryRun = true;
      else
        projectDir = arg;
    }

    try (MainJvmScope scope = new MainJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      RetrospectiveMigrator migrator = new RetrospectiveMigrator(mapper);
      MigrationResult result = migrator.migrate(Path.of(projectDir), dryRun);
      for (String message : result.messages())
        System.out.println(message);
      System.out.println(mapper.writeValueAsString(result.stats()));
    }
    catch (RuntimeException | Error e)
    {
      Logger log = LoggerFactory.getLogger(RetrospectiveMigrator.class);
      log.error("Unexpected error", e);
      throw e;
    }
  }


  /**
   * Migrates retrospective files to time-based split format.
   * <p>
   * Returns a result with output messages and migration stats. If dry run is enabled, shows what would be done
   * without making changes.
   *
   * @param projectDir path to project root directory
   * @param dryRun if true, show what would be done without making changes
   * @return migration result containing output messages and JSON statistics
   * @throws NullPointerException if projectDir is null
   * @throws IOException if file operations fail
   */
  public MigrationResult migrate(Path projectDir, boolean dryRun) throws IOException
  {
    requireThat(projectDir, "projectDir").isNotNull();

    List<String> messages = new ArrayList<>();
    Path retroDir = projectDir.resolve(".claude").resolve("cat").resolve("retrospectives");

    if (!Files.exists(retroDir))
    {
      ObjectNode result = mapper.createObjectNode();
      result.put("status", "skipped");
      result.put("reason", "directory not found");
      messages.add("No retrospectives directory found at " + retroDir);
      return new MigrationResult(messages, result);
    }

    Path mistakesFile = retroDir.resolve("mistakes.json");
    Path retroFile = retroDir.resolve("retrospectives.json");
    Path indexFile = retroDir.resolve("index.json");

    if (Files.exists(indexFile))
    {
      ObjectNode result = mapper.createObjectNode();
      result.put("status", "skipped");
      result.put("reason", "already migrated");
      messages.add("Migration already complete: " + indexFile + " exists");
      return new MigrationResult(messages, result);
    }

    ObjectNode mistakesData = mapper.createObjectNode();
    mistakesData.set("mistakes", mapper.createArrayNode());

    ObjectNode retroData = mapper.createObjectNode();
    retroData.putNull("last_retrospective");
    retroData.put("mistake_count_since_last", 0);
    ObjectNode config = mapper.createObjectNode();
    config.put("mistake_count_threshold", 5);
    config.put("trigger_interval_days", 7);
    retroData.set("config", config);

    if (Files.exists(mistakesFile))
      mistakesData = (ObjectNode) mapper.readTree(Files.readString(mistakesFile));

    if (Files.exists(retroFile))
      retroData = (ObjectNode) mapper.readTree(Files.readString(retroFile));

    Map<String, List<JsonNode>> mistakesByPeriod = new HashMap<>();
    JsonNode mistakesNode = mistakesData.path("mistakes");
    if (mistakesNode.isArray())
    {
      for (JsonNode mistake : mistakesNode)
      {
        String timestamp = getStringOrDefault(mistake, "timestamp", "");
        if (!timestamp.isEmpty())
        {
          String period = getYearMonth(timestamp);
          mistakesByPeriod.computeIfAbsent(period, k -> new ArrayList<>()).add(mistake);
        }
      }
    }

    Map<String, List<JsonNode>> retrosByPeriod = new HashMap<>();
    JsonNode retrospectivesNode = retroData.path("retrospectives");
    if (retrospectivesNode.isArray())
    {
      for (JsonNode retro : retrospectivesNode)
      {
        String timestamp = getStringOrDefault(retro, "timestamp", "");
        if (!timestamp.isEmpty())
        {
          String period = getYearMonth(timestamp);
          retrosByPeriod.computeIfAbsent(period, k -> new ArrayList<>()).add(retro);
        }
      }
    }

    ObjectNode indexData = mapper.createObjectNode();
    indexData.put("version", "2.0");
    indexData.set("config", retroData.path("config"));
    indexData.set("last_retrospective", retroData.path("last_retrospective"));
    indexData.put("mistake_count_since_last", retroData.path("mistake_count_since_last").asInt(0));

    ArrayNode mistakeFiles = mapper.createArrayNode();
    List<String> mistakePeriods = new ArrayList<>(mistakesByPeriod.keySet());
    mistakePeriods.sort(String::compareTo);
    for (String period : mistakePeriods)
      mistakeFiles.add("mistakes-" + period + ".json");

    ArrayNode retroFiles = mapper.createArrayNode();
    List<String> retroPeriods = new ArrayList<>(retrosByPeriod.keySet());
    retroPeriods.sort(String::compareTo);
    for (String period : retroPeriods)
      retroFiles.add("retrospectives-" + period + ".json");

    ObjectNode filesNode = mapper.createObjectNode();
    filesNode.set("mistakes", mistakeFiles);
    filesNode.set("retrospectives", retroFiles);
    indexData.set("files", filesNode);

    ObjectNode stats = mapper.createObjectNode();
    int mistakesTotal = 0;
    if (mistakesNode.isArray())
      mistakesTotal = mistakesNode.size();
    stats.put("mistakes_total", mistakesTotal);
    ObjectNode mistakesByPeriodNode = mapper.createObjectNode();
    for (Map.Entry<String, List<JsonNode>> entry : mistakesByPeriod.entrySet())
      mistakesByPeriodNode.put(entry.getKey(), entry.getValue().size());
    stats.set("mistakes_by_period", mistakesByPeriodNode);

    int retrospectivesTotal = 0;
    if (retrospectivesNode.isArray())
      retrospectivesTotal = retrospectivesNode.size();
    stats.put("retrospectives_total", retrospectivesTotal);
    ObjectNode retrosByPeriodNode = mapper.createObjectNode();
    for (Map.Entry<String, List<JsonNode>> entry : retrosByPeriod.entrySet())
      retrosByPeriodNode.put(entry.getKey(), entry.getValue().size());
    stats.set("retrospectives_by_period", retrosByPeriodNode);

    String prefix;
    if (dryRun)
      prefix = "DRY RUN - ";
    else
      prefix = "";

    messages.add("");
    messages.add(prefix + "Migration Summary:");
    messages.add("  Mistakes: " + mistakesTotal + " total");
    List<String> sortedMistakePeriods = new ArrayList<>(mistakesByPeriod.keySet());
    sortedMistakePeriods.sort(String::compareTo);
    for (String period : sortedMistakePeriods)
      messages.add("    " + period + ": " + mistakesByPeriod.get(period).size() + " mistakes");

    messages.add("  Retrospectives: " + retrospectivesTotal + " total");
    List<String> sortedRetroPeriods = new ArrayList<>(retrosByPeriod.keySet());
    sortedRetroPeriods.sort(String::compareTo);
    for (String period : sortedRetroPeriods)
      messages.add("    " + period + ": " + retrosByPeriod.get(period).size() + " retrospectives");

    if (dryRun)
    {
      messages.add("");
      messages.add("Dry run complete. No files were modified.");
      return new MigrationResult(messages, stats);
    }

    ArrayNode filesCreated = mapper.createArrayNode();

    for (String period : mistakePeriods)
    {
      Path splitFile = retroDir.resolve("mistakes-" + period + ".json");
      ObjectNode splitData = mapper.createObjectNode();
      splitData.put("period", period);

      List<JsonNode> periodMistakes = new ArrayList<>(mistakesByPeriod.get(period));
      periodMistakes.sort((a, b) ->
        getStringOrDefault(a, "timestamp", "").compareTo(
          getStringOrDefault(b, "timestamp", "")));

      ArrayNode mistakesArray = mapper.createArrayNode();
      for (JsonNode mistake : periodMistakes)
        mistakesArray.add(mistake);
      splitData.set("mistakes", mistakesArray);

      Files.writeString(splitFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(splitData));
      filesCreated.add(splitFile.toString());
      messages.add("  Created: " + splitFile.getFileName());
    }

    for (String period : retroPeriods)
    {
      Path splitFile = retroDir.resolve("retrospectives-" + period + ".json");
      ObjectNode splitData = mapper.createObjectNode();
      splitData.put("period", period);

      List<JsonNode> periodRetros = new ArrayList<>(retrosByPeriod.get(period));
      periodRetros.sort((a, b) ->
        getStringOrDefault(a, "timestamp", "").compareTo(
          getStringOrDefault(b, "timestamp", "")));

      ArrayNode retrosArray = mapper.createArrayNode();
      for (JsonNode retro : periodRetros)
        retrosArray.add(retro);
      splitData.set("retrospectives", retrosArray);

      Files.writeString(splitFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(splitData));
      filesCreated.add(splitFile.toString());
      messages.add("  Created: " + splitFile.getFileName());
    }

    Files.writeString(indexFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(indexData));
    filesCreated.add(indexFile.toString());
    messages.add("  Created: " + indexFile.getFileName());

    messages.add("");
    messages.add("Verifying migration...");

    int totalInSplits = 0;
    for (Path mistakesSplit : Files.list(retroDir).
      filter(p -> p.getFileName().toString().startsWith("mistakes-") &&
        p.getFileName().toString().endsWith(".json")).
      toList())
    {
      JsonNode splitData = mapper.readTree(Files.readString(mistakesSplit));
      totalInSplits += splitData.path("mistakes").size();
    }

    if (totalInSplits != mistakesTotal)
    {
      messages.add("  ERROR: Mistake count mismatch!");
      messages.add("    Original: " + mistakesTotal);
      messages.add("    In splits: " + totalInSplits);
      ObjectNode errorResult = mapper.createObjectNode();
      errorResult.put("status", "error");
      errorResult.put("reason", "count mismatch");
      errorResult.setAll(stats);
      return new MigrationResult(messages, errorResult);
    }

    messages.add("  Verified: " + totalInSplits + " mistakes preserved correctly");

    if (Files.exists(mistakesFile))
    {
      Path backupFile = retroDir.resolve("mistakes.json.backup");
      Files.move(mistakesFile, backupFile);
      messages.add("  Backed up: mistakes.json -> mistakes.json.backup");
    }

    if (Files.exists(retroFile))
    {
      Path backupFile = retroDir.resolve("retrospectives.json.backup");
      Files.move(retroFile, backupFile);
      messages.add("  Backed up: retrospectives.json -> retrospectives.json.backup");
    }

    messages.add("");
    messages.add("Migration complete!");

    stats.set("files_created", filesCreated);
    stats.put("status", "success");

    return new MigrationResult(messages, stats);
  }

  /**
   * Extracts YYYY-MM from ISO timestamp.
   *
   * @param timestamp ISO format timestamp string
   * @return year-month string in YYYY-MM format
   * @throws NullPointerException if timestamp is null
   * @throws IllegalArgumentException if timestamp cannot be parsed
   */
  private String getYearMonth(String timestamp)
  {
    requireThat(timestamp, "timestamp").isNotNull();

    String normalizedTimestamp = timestamp.replace("+00:00", "Z");

    for (DateTimeFormatter formatter : TIMESTAMP_FORMATS)
    {
      try
      {
        ZonedDateTime dt = ZonedDateTime.parse(normalizedTimestamp, formatter);
        return dt.format(DateTimeFormatter.ofPattern("yyyy-MM"));
      }
      catch (DateTimeParseException _)
      {
      }
    }

    try
    {
      String datePart = timestamp.substring(0, 10);
      ZonedDateTime dt = ZonedDateTime.parse(datePart + "T00:00:00Z", TIMESTAMP_FORMATS[0]);
      return dt.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }
    catch (DateTimeParseException | StringIndexOutOfBoundsException _)
    {
    }

    throw new IllegalArgumentException("Cannot parse timestamp: " + timestamp);
  }

  /**
   * Result of a migration operation.
   *
   * @param messages output messages to be printed
   * @param stats JSON statistics object
   */
  public record MigrationResult(List<String> messages, JsonNode stats)
  {
    /**
     * Creates a new migration result.
     *
     * @param messages output messages to be printed
     * @param stats JSON statistics object
     * @throws NullPointerException if {@code messages} or {@code stats} are null
     */
    public MigrationResult
    {
      requireThat(messages, "messages").isNotNull();
      requireThat(stats, "stats").isNotNull();
    }
  }
}
