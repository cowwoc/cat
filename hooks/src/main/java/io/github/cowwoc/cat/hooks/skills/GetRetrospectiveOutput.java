/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import io.github.cowwoc.cat.hooks.util.SkillOutput;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Output generator for /cat:run-retrospective skill.
 * <p>
 * Analyzes accumulated mistakes and determines whether a retrospective should be triggered.
 * Supports three trigger conditions:
 * <ul>
 *   <li>Time-based: days since last retrospective exceeds threshold (default 7)</li>
 *   <li>Count-based: accumulated mistakes exceed threshold (default 10)</li>
 *   <li>First retrospective: no previous retrospective and mistakes exist</li>
 * </ul>
 * When triggered, outputs analysis including category breakdown, action item effectiveness,
 * pattern status, and open action items.
 */
public final class GetRetrospectiveOutput implements SkillOutput
{
  private static final int DEFAULT_TRIGGER_DAYS = 7;
  private static final int DEFAULT_MISTAKE_THRESHOLD = 10;
  private final JvmScope scope;

  /**
   * Creates a GetRetrospectiveOutput instance.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if scope is null
   */
  public GetRetrospectiveOutput(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Generates the retrospective output.
   *
   * @return the retrospective analysis or status message
   * @throws IOException if an I/O error occurs
   */
  @Override
  public String getOutput() throws IOException
  {
    Path projectDir = scope.getClaudeProjectDir();
    Path retroDir = projectDir.resolve(".claude/cat/retrospectives");

    if (!Files.isDirectory(retroDir))
    {
      return """
        SCRIPT OUTPUT RETROSPECTIVE ERROR:
        Retrospectives directory not found: %s
        """.formatted(retroDir);
    }

    Path indexFile = retroDir.resolve("index.json");
    if (!Files.exists(indexFile))
    {
      return """
        SCRIPT OUTPUT RETROSPECTIVE ERROR:
        Index file not found: %s
        """.formatted(indexFile);
    }

    JsonMapper mapper = scope.getJsonMapper();
    String content = Files.readString(indexFile);
    JsonNode root = mapper.readTree(content);

    int triggerDays = DEFAULT_TRIGGER_DAYS;
    int mistakeThreshold = DEFAULT_MISTAKE_THRESHOLD;
    String lastRetro = "";
    int mistakeCount = 0;

    JsonNode config = root.get("config");
    if (config != null)
    {
      JsonNode triggerNode = config.get("trigger_interval_days");
      if (triggerNode != null && triggerNode.isNumber())
        triggerDays = triggerNode.asInt();
      JsonNode thresholdNode = config.get("mistake_count_threshold");
      if (thresholdNode != null && thresholdNode.isNumber())
        mistakeThreshold = thresholdNode.asInt();
    }

    JsonNode lastRetroNode = root.get("last_retrospective");
    if (lastRetroNode != null && lastRetroNode.isString())
      lastRetro = lastRetroNode.asString();

    JsonNode mistakeCountNode = root.get("mistake_count_since_last");
    if (mistakeCountNode != null && mistakeCountNode.isNumber())
      mistakeCount = mistakeCountNode.asInt();

    String triggerReason = checkTrigger(lastRetro, mistakeCount, triggerDays, mistakeThreshold,
      retroDir, mapper);

    if (triggerReason.isEmpty())
    {
      long daysSince;
      if (lastRetro.isEmpty())
        daysSince = 0;
      else
        daysSince = daysSinceDate(lastRetro);
      return """
        SCRIPT OUTPUT RETROSPECTIVE STATUS:
        Retrospective not triggered.
        Days since last retrospective: %d/%d
        Mistakes accumulated: %d/%d
        """.formatted(daysSince, triggerDays, mistakeCount, mistakeThreshold);
    }

    return generateAnalysis(retroDir, root, lastRetro, triggerReason, mapper);
  }

  /**
   * Checks whether a retrospective should be triggered.
   *
   * @param lastRetro the last retrospective timestamp, or empty string if none
   * @param mistakeCount the number of mistakes since last retrospective
   * @param triggerDays the number of days threshold for triggering
   * @param mistakeThreshold the mistake count threshold for triggering
   * @param retroDir the retrospectives directory
   * @param mapper the JSON mapper
   * @return the trigger reason, or empty string if not triggered
   * @throws IOException if an I/O error occurs
   */
  private String checkTrigger(String lastRetro, int mistakeCount, int triggerDays, int mistakeThreshold,
    Path retroDir, JsonMapper mapper) throws IOException
  {
    boolean hasLastRetro = !lastRetro.isEmpty() && !lastRetro.equals("null");
    if (!hasLastRetro)
    {
      int totalMistakes = countMistakesFromFiles(retroDir, mapper);
      if (totalMistakes > 0)
        return "First retrospective with " + totalMistakes + " logged mistakes";
    }
    if (hasLastRetro)
    {
      long daysSince = daysSinceDate(lastRetro);
      if (daysSince >= triggerDays)
        return daysSince + " days since last retrospective (threshold: " + triggerDays + ")";
    }

    if (mistakeCount >= mistakeThreshold)
      return mistakeCount + " mistakes accumulated (threshold: " + mistakeThreshold + ")";

    return "";
  }

  /**
   * Generates the full retrospective analysis output.
   *
   * @param retroDir the retrospectives directory
   * @param index the index.json root node
   * @param lastRetro the last retrospective timestamp
   * @param triggerReason the reason for triggering
   * @param mapper the JSON mapper
   * @return the analysis output
   * @throws IOException if an I/O error occurs
   */
  private String generateAnalysis(Path retroDir, JsonNode index, String lastRetro, String triggerReason,
    JsonMapper mapper) throws IOException
  {
    Instant lastRetroTime;
    if (lastRetro.isEmpty() || lastRetro.equals("null"))
      lastRetroTime = Instant.EPOCH;
    else
      lastRetroTime = Instant.parse(lastRetro);

    Instant now = Instant.now();
    String periodAnalyzed;
    if (lastRetroTime.equals(Instant.EPOCH))
      periodAnalyzed = "Beginning to " + now;
    else
      periodAnalyzed = lastRetro + " to " + now;

    List<JsonNode> mistakes = loadMistakesSince(retroDir, index, lastRetroTime, mapper);
    int mistakesAnalyzed = mistakes.size();

    return "SCRIPT OUTPUT RETROSPECTIVE ANALYSIS:\n\n" +
      "Trigger: " + triggerReason + "\n" +
      "Period analyzed: " + periodAnalyzed + "\n" +
      "Mistakes analyzed: " + mistakesAnalyzed + "\n\n" +
      "Category breakdown:\n" + generateCategoryBreakdown(mistakes) + "\n" +
      "Action item effectiveness:\n" + generateEffectivenessReport(index) + "\n" +
      "Pattern status:\n" + generatePatternStatus(index) + "\n" +
      "Open action items:\n" + generateOpenActionItems(index);
  }

  /**
   * Loads all mistakes since the last retrospective.
   *
   * @param retroDir the retrospectives directory
   * @param index the index.json root node
   * @param since the timestamp to filter from
   * @param mapper the JSON mapper
   * @return the list of mistake nodes
   * @throws IOException if an I/O error occurs
   */
  private List<JsonNode> loadMistakesSince(Path retroDir, JsonNode index, Instant since, JsonMapper mapper)
    throws IOException
  {
    List<JsonNode> result = new ArrayList<>();
    JsonNode filesNode = index.get("files");
    if (filesNode == null)
      return result;

    JsonNode mistakeFilesNode = filesNode.get("mistakes");
    if (mistakeFilesNode == null || !mistakeFilesNode.isArray())
      return result;

    for (JsonNode fileNode : mistakeFilesNode)
    {
      if (!fileNode.isString())
        continue;

      String fileName = fileNode.asString();
      Path mistakeFile = retroDir.resolve(fileName);
      if (!Files.exists(mistakeFile))
      {
        throw new IOException("Mistakes file listed in index.json not found: " + mistakeFile);
      }

      String content = Files.readString(mistakeFile);
      JsonNode mistakeRoot = mapper.readTree(content);
      JsonNode mistakesArray = mistakeRoot.get("mistakes");
      if (mistakesArray == null || !mistakesArray.isArray())
        continue;

      for (JsonNode mistake : mistakesArray)
      {
        JsonNode timestampNode = mistake.get("timestamp");
        if (timestampNode == null || !timestampNode.isString())
          continue;

        String timestamp = timestampNode.asString();
        Instant mistakeTime;
        try
        {
          mistakeTime = Instant.parse(timestamp);
        }
        catch (DateTimeParseException _)
        {
          continue;
        }
        if (mistakeTime.isAfter(since))
          result.add(mistake);
      }
    }

    return result;
  }

  /**
   * Generates category breakdown from mistakes.
   *
   * @param mistakes the list of mistake nodes
   * @return the category breakdown string
   */
  private String generateCategoryBreakdown(List<JsonNode> mistakes)
  {
    if (mistakes.isEmpty())
      return "(no mistakes in period)\n";

    Map<String, Integer> categoryCount = new HashMap<>();
    for (JsonNode mistake : mistakes)
    {
      JsonNode categoryNode = mistake.get("category");
      if (categoryNode != null && categoryNode.isString())
      {
        String category = categoryNode.asString();
        categoryCount.put(category, categoryCount.getOrDefault(category, 0) + 1);
      }
    }

    List<String> categories = new ArrayList<>(categoryCount.keySet());
    categories.sort(String::compareTo);
    StringBuilder sb = new StringBuilder();
    for (String category : categories)
      sb.append("%s: %d\n".formatted(category, categoryCount.get(category)));

    return sb.toString();
  }

  /**
   * Generates action item effectiveness report.
   *
   * @param index the index.json root node
   * @return the effectiveness report string
   */
  private String generateEffectivenessReport(JsonNode index)
  {
    JsonNode actionItems = index.get("action_items");
    if (actionItems == null || !actionItems.isArray() || actionItems.isEmpty())
      return "(no action items)\n";

    StringBuilder sb = new StringBuilder();
    for (JsonNode item : actionItems)
    {
      JsonNode idNode = item.get("id");
      if (idNode == null || !idNode.isString())
        continue;

      String id = idNode.asString();
      JsonNode effectivenessCheck = item.get("effectiveness_check");
      if (effectivenessCheck == null)
        continue;

      JsonNode verdictNode = effectivenessCheck.get("verdict");
      if (verdictNode != null && verdictNode.isString())
      {
        String verdict = verdictNode.asString();
        sb.append("%s: %s\n".formatted(id, verdict));
      }
    }

    if (sb.length() == 0)
      return "(no effectiveness checks)\n";
    return sb.toString();
  }

  /**
   * Generates pattern status summary.
   *
   * @param index the index.json root node
   * @return the pattern status string
   */
  private String generatePatternStatus(JsonNode index)
  {
    JsonNode patterns = index.get("patterns");
    if (patterns == null || !patterns.isArray() || patterns.isEmpty())
      return "(no patterns)\n";

    StringBuilder sb = new StringBuilder(64);
    for (JsonNode pattern : patterns)
    {
      JsonNode statusNode = pattern.get("status");
      if (statusNode == null || !statusNode.isString())
        continue;

      String status = statusNode.asString();
      if (status.equals("addressed"))
        continue;

      JsonNode idNode = pattern.get("pattern_id");
      JsonNode occurrencesTotalNode = pattern.get("occurrences_total");
      JsonNode occurrencesAfterNode = pattern.get("occurrences_after_fix");

      if (idNode != null && idNode.isString())
      {
        String id = idNode.asString();
        int total;
        if (occurrencesTotalNode != null && occurrencesTotalNode.isNumber())
          total = occurrencesTotalNode.asInt();
        else
          total = 0;
        int after;
        if (occurrencesAfterNode != null && occurrencesAfterNode.isNumber())
          after = occurrencesAfterNode.asInt();
        else
          after = 0;

        sb.append("%s: %s (occurrences: %d/%d)\n".formatted(id, status, total, after));
      }
    }

    if (sb.length() == 0)
      return "(all patterns addressed)\n";
    return sb.toString();
  }

  /**
   * Generates open action items list.
   *
   * @param index the index.json root node
   * @return the open action items string
   */
  private String generateOpenActionItems(JsonNode index)
  {
    JsonNode actionItems = index.get("action_items");
    if (actionItems == null || !actionItems.isArray() || actionItems.isEmpty())
      return "(no action items)\n";

    List<ActionItemSummary> openItems = new ArrayList<>();
    for (JsonNode item : actionItems)
    {
      JsonNode statusNode = item.get("status");
      if (statusNode == null || !statusNode.isString())
        continue;

      String status = statusNode.asString();
      if (!status.equals("open") && !status.equals("escalated"))
        continue;

      ActionItemSummary summary = parseActionItem(item);
      if (summary != null)
        openItems.add(summary);
    }

    if (openItems.isEmpty())
      return "(no open action items)\n";

    openItems.sort((a, b) ->
    {
      int priorityCompare = b.priority().getValue() - a.priority().getValue();
      if (priorityCompare != 0)
        return priorityCompare;
      return a.id().compareTo(b.id());
    });

    StringBuilder sb = new StringBuilder(128);
    for (ActionItemSummary item : openItems)
    {
      sb.append("%s (%s): %s\n".formatted(item.id(),
        item.priority().name().toLowerCase(Locale.ROOT), item.description()));
    }

    return sb.toString();
  }

  /**
   * Parses a JSON action item node into an ActionItemSummary.
   *
   * @param item the JSON node representing an action item
   * @return the parsed summary, or {@code null} if the item has no valid ID
   */
  private ActionItemSummary parseActionItem(JsonNode item)
  {
    JsonNode idNode = item.get("id");
    if (idNode == null || !idNode.isString())
      return null;

    String id = idNode.asString();
    JsonNode priorityNode = item.get("priority");
    String priorityText;
    if (priorityNode != null && priorityNode.isString())
      priorityText = priorityNode.asString();
    else
      priorityText = "medium";
    Priority priority;
    try
    {
      priority = Priority.fromString(priorityText);
    }
    catch (IllegalArgumentException _)
    {
      priority = Priority.MEDIUM;
    }

    JsonNode descNode = item.get("description");
    String description;
    if (descNode != null && descNode.isString())
      description = descNode.asString();
    else
      description = "";

    return new ActionItemSummary(id, priority, description);
  }

  /**
   * Counts total mistakes from mistakes-*.json files.
   *
   * @param retroDir the retrospectives directory
   * @param mapper the JSON mapper
   * @return the total number of mistakes
   * @throws IOException if an I/O error occurs
   */
  private int countMistakesFromFiles(Path retroDir, JsonMapper mapper) throws IOException
  {
    int total = 0;
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(retroDir, "mistakes-*.json"))
    {
      for (Path file : stream)
      {
        JsonNode root = mapper.readTree(Files.readString(file));
        JsonNode mistakes = root.get("mistakes");
        if (mistakes != null && mistakes.isArray())
          total += mistakes.size();
      }
    }
    return total;
  }

  /**
   * Calculates the number of days since a given ISO date string.
   *
   * @param dateString the date string to parse
   * @return the number of days since the date
   */
  private long daysSinceDate(String dateString)
  {
    Instant lastDate = Instant.parse(dateString);
    Instant now = Instant.now();
    return ChronoUnit.DAYS.between(lastDate, now);
  }

  /**
   * Main entry point.
   *
   * @param args command line arguments (unused)
   */
  public static void main(String[] args)
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetRetrospectiveOutput generator = new GetRetrospectiveOutput(scope);
      String output = generator.getOutput();
      System.out.print(output);
    }
    catch (IOException e)
    {
      System.err.println("Error generating retrospective output: " + e.getMessage());
      System.exit(1);
    }
  }
}
