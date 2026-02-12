package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.cat.hooks.skills.JsonHelper.getStringOrDefault;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.MainJvmScope;
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
      JsonNode result = migrator.migrate(Path.of(projectDir), dryRun);
      System.out.println(mapper.writeValueAsString(result));
    }
  }


  /**
   * Migrates retrospective files to time-based split format.
   * <p>
   * Returns a JSON object with migration stats. If dry run is enabled, shows what would be done
   * without making changes.
   *
   * @param projectDir path to project root directory
   * @param dryRun if true, show what would be done without making changes
   * @return JSON object containing migration statistics
   * @throws NullPointerException if projectDir is null
   * @throws IOException if file operations fail
   */
  public JsonNode migrate(Path projectDir, boolean dryRun) throws IOException
  {
    requireThat(projectDir, "projectDir").isNotNull();

    Path retroDir = projectDir.resolve(".claude").resolve("cat").resolve("retrospectives");

    if (!Files.exists(retroDir))
    {
      ObjectNode result = mapper.createObjectNode();
      result.put("status", "skipped");
      result.put("reason", "directory not found");
      System.out.println("No retrospectives directory found at " + retroDir);
      return result;
    }

    Path mistakesFile = retroDir.resolve("mistakes.json");
    Path retroFile = retroDir.resolve("retrospectives.json");
    Path indexFile = retroDir.resolve("index.json");

    if (Files.exists(indexFile))
    {
      ObjectNode result = mapper.createObjectNode();
      result.put("status", "skipped");
      result.put("reason", "already migrated");
      System.out.println("Migration already complete: " + indexFile + " exists");
      return result;
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

    System.out.println();
    System.out.println(prefix + "Migration Summary:");
    System.out.println("  Mistakes: " + mistakesTotal + " total");
    List<String> sortedMistakePeriods = new ArrayList<>(mistakesByPeriod.keySet());
    sortedMistakePeriods.sort(String::compareTo);
    for (String period : sortedMistakePeriods)
      System.out.println("    " + period + ": " + mistakesByPeriod.get(period).size() + " mistakes");

    System.out.println("  Retrospectives: " + retrospectivesTotal + " total");
    List<String> sortedRetroPeriods = new ArrayList<>(retrosByPeriod.keySet());
    sortedRetroPeriods.sort(String::compareTo);
    for (String period : sortedRetroPeriods)
      System.out.println("    " + period + ": " + retrosByPeriod.get(period).size() + " retrospectives");

    if (dryRun)
    {
      System.out.println();
      System.out.println("Dry run complete. No files were modified.");
      return stats;
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
      System.out.println("  Created: " + splitFile.getFileName());
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
      System.out.println("  Created: " + splitFile.getFileName());
    }

    Files.writeString(indexFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(indexData));
    filesCreated.add(indexFile.toString());
    System.out.println("  Created: " + indexFile.getFileName());

    System.out.println();
    System.out.println("Verifying migration...");

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
      System.out.println("  ERROR: Mistake count mismatch!");
      System.out.println("    Original: " + mistakesTotal);
      System.out.println("    In splits: " + totalInSplits);
      ObjectNode errorResult = mapper.createObjectNode();
      errorResult.put("status", "error");
      errorResult.put("reason", "count mismatch");
      errorResult.setAll(stats);
      return errorResult;
    }

    System.out.println("  Verified: " + totalInSplits + " mistakes preserved correctly");

    if (Files.exists(mistakesFile))
    {
      Path backupFile = retroDir.resolve("mistakes.json.backup");
      Files.move(mistakesFile, backupFile);
      System.out.println("  Backed up: mistakes.json -> mistakes.json.backup");
    }

    if (Files.exists(retroFile))
    {
      Path backupFile = retroDir.resolve("retrospectives.json.backup");
      Files.move(retroFile, backupFile);
      System.out.println("  Backed up: retrospectives.json -> retrospectives.json.backup");
    }

    System.out.println();
    System.out.println("Migration complete!");

    stats.set("files_created", filesCreated);
    stats.put("status", "success");

    return stats;
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
}
