/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import io.github.cowwoc.cat.hooks.Strings;
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
 * Output generator for /cat:retrospective skill.
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
   * @param args the arguments from the preprocessor directive (must be empty)
   * @return the retrospective analysis or status message
   * @throws NullPointerException if {@code args} is null
   * @throws IllegalArgumentException if {@code args} is not empty
   * @throws IOException if an I/O error occurs
   */
  @Override
  public String getOutput(String[] args) throws IOException
  {
    requireThat(args, "args").length().isEqualTo(0);
    Path projectDir = scope.getClaudeProjectDir();
    Path retroDir = projectDir.resolve(".claude/cat/retrospectives");

    if (!Files.isDirectory(retroDir))
    {
      return """
        ERROR:
        Retrospectives directory not found: %s
        """.formatted(retroDir);
    }

    Path indexFile = retroDir.resolve("index.json");
    if (!Files.exists(indexFile))
    {
      return """
        ERROR:
        Index file not found: %s
        """.formatted(indexFile);
    }

    JsonMapper mapper = scope.getJsonMapper();
    String content = Files.readString(indexFile);
    JsonNode root = mapper.readTree(content);

    int triggerDays = DEFAULT_TRIGGER_DAYS;
    int mistakeThreshold = DEFAULT_MISTAKE_THRESHOLD;

    JsonNode config = root.get("config");
    if (config != null)
    {
      triggerDays = extractInt(config, "trigger_interval_days", DEFAULT_TRIGGER_DAYS);
      mistakeThreshold = extractInt(config, "mistake_count_threshold", DEFAULT_MISTAKE_THRESHOLD);
    }

    String lastRetro = extractString(root, "last_retrospective", "");
    int mistakeCount = extractInt(root, "mistake_count_since_last", 0);

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
   * Generates the full retrospective analysis output as a display box.
   *
   * @param retroDir the retrospectives directory
   * @param index the index.json root node
   * @param lastRetro the last retrospective timestamp
   * @param triggerReason the reason for triggering
   * @param mapper the JSON mapper
   * @return the analysis output rendered as a display box
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

    String period = formatPeriod(lastRetroTime);

    List<JsonNode> mistakes = loadMistakesSince(retroDir, index, lastRetroTime, mapper);
    int mistakesAnalyzed = mistakes.size();

    DisplayUtils display = scope.getDisplayUtils();

    List<String> contentLines = new ArrayList<>();

    // Subtitle rows: trigger, period, mistakes analyzed
    contentLines.add("Trigger: " + triggerReason);
    contentLines.add("Period: " + period);
    contentLines.add("Mistakes analyzed: " + mistakesAnalyzed);

    // Category Breakdown section
    contentLines.add("");
    contentLines.add("Category Breakdown:");
    for (String line : generateCategoryBreakdownLines(mistakes))
      contentLines.add("  " + line);

    // Action Item Effectiveness section
    contentLines.add("");
    contentLines.add("Action Item Effectiveness:");
    for (String line : generateEffectivenessLines(index))
      contentLines.add("  " + line);

    // Pattern Status section
    contentLines.add("");
    contentLines.add("Pattern Status:");
    for (String line : generatePatternStatusLines(index))
      contentLines.add("  " + line);

    // Open Action Items section
    contentLines.add("");
    contentLines.add("Open Action Items:");
    for (String line : generateOpenActionItemLines(index))
      contentLines.add("  " + line);

    return display.buildHeaderBox("RETROSPECTIVE ANALYSIS", contentLines);
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
   * Generates category breakdown lines from mistakes.
   *
   * @param mistakes the list of mistake nodes
   * @return the category breakdown lines
   */
  private List<String> generateCategoryBreakdownLines(List<JsonNode> mistakes)
  {
    if (mistakes.isEmpty())
      return List.of("(no mistakes in period)");

    Map<String, Integer> categoryCount = new HashMap<>();
    for (JsonNode mistake : mistakes)
    {
      String category = extractString(mistake, "category", "");
      if (!category.isEmpty())
        categoryCount.put(category, categoryCount.getOrDefault(category, 0) + 1);
    }

    List<String> categories = new ArrayList<>(categoryCount.keySet());
    categories.sort(String::compareTo);
    List<String> lines = new ArrayList<>();
    for (String category : categories)
      lines.add("%s: %d".formatted(category, categoryCount.get(category)));

    return lines;
  }

  /**
   * Generates action item effectiveness report lines.
   *
   * @param index the index.json root node
   * @return the effectiveness report lines
   */
  private List<String> generateEffectivenessLines(JsonNode index)
  {
    JsonNode actionItems = index.get("action_items");
    if (actionItems == null || !actionItems.isArray() || actionItems.isEmpty())
      return List.of("(no action items)");

    List<String> lines = new ArrayList<>();
    for (JsonNode item : actionItems)
    {
      String id = extractString(item, "id", "");
      if (id.isBlank())
        continue;

      JsonNode effectivenessCheck = item.get("effectiveness_check");
      if (effectivenessCheck == null)
        continue;

      String verdict = extractString(effectivenessCheck, "verdict", "");
      if (!verdict.isBlank())
      {
        String description = extractString(item, "description", "");
        String truncated = Strings.truncate(description, Strings.DESCRIPTION_MAX_LENGTH);
        if (truncated.isBlank())
          lines.add("%s: %s".formatted(id, verdict));
        else
          lines.add("%s: %s - %s".formatted(id, verdict, truncated));
      }
    }

    if (lines.isEmpty())
      return List.of("(no effectiveness checks)");
    return lines;
  }

  /**
   * Generates pattern status summary lines.
   *
   * @param index the index.json root node
   * @return the pattern status lines
   */
  private List<String> generatePatternStatusLines(JsonNode index)
  {
    JsonNode patterns = index.get("patterns");
    if (patterns == null || !patterns.isArray() || patterns.isEmpty())
      return List.of("(no patterns)");

    List<String> lines = new ArrayList<>();
    for (JsonNode pattern : patterns)
    {
      String status = extractString(pattern, "status", "");
      if (status.isBlank() || status.equals("addressed"))
        continue;

      String id = extractString(pattern, "pattern_id", "");
      if (id.isBlank())
        continue;

      int total = extractInt(pattern, "occurrences_total", 0);
      int after = extractInt(pattern, "occurrences_after_fix", 0);
      String patternName = extractString(pattern, "pattern", "");
      if (patternName.isBlank())
        lines.add("%s: %s (%d total, %d after fix)".formatted(id, status, total, after));
      else
        lines.add("%s: %s (%d total, %d after fix) - %s".formatted(id, status, total, after,
          Strings.truncate(patternName, Strings.DESCRIPTION_MAX_LENGTH)));
    }

    if (lines.isEmpty())
      return List.of("(all patterns addressed)");
    return lines;
  }

  /**
   * Generates open action item lines.
   *
   * @param index the index.json root node
   * @return the open action item lines
   */
  private List<String> generateOpenActionItemLines(JsonNode index)
  {
    JsonNode actionItems = index.get("action_items");
    if (actionItems == null || !actionItems.isArray() || actionItems.isEmpty())
      return List.of("(no open action items)");

    List<ActionItemSummary> openItems = new ArrayList<>();
    for (JsonNode item : actionItems)
    {
      String status = extractString(item, "status", "");
      if (!status.equals("open") && !status.equals("escalated"))
        continue;

      ActionItemSummary summary = parseActionItem(item);
      if (summary != null)
        openItems.add(summary);
    }

    if (openItems.isEmpty())
      return List.of("(no open action items)");

    openItems.sort((a, b) ->
    {
      int priorityCompare = b.priority().getValue() - a.priority().getValue();
      if (priorityCompare != 0)
        return priorityCompare;
      return a.id().compareTo(b.id());
    });

    List<String> lines = new ArrayList<>();
    for (ActionItemSummary item : openItems)
      lines.add("%s (%s): %s".formatted(item.id(),
        item.priority().name().toLowerCase(Locale.ROOT), item.description()));

    return lines;
  }

  /**
   * Parses a JSON action item node into an ActionItemSummary.
   *
   * @param item the JSON node representing an action item
   * @return the parsed summary, or {@code null} if the item has no valid ID
   */
  private ActionItemSummary parseActionItem(JsonNode item)
  {
    String id = extractString(item, "id", "");
    if (id.isEmpty())
      return null;

    String priorityText = extractString(item, "priority", "medium");
    Priority priority;
    try
    {
      priority = Priority.fromString(priorityText);
    }
    catch (IllegalArgumentException _)
    {
      priority = Priority.MEDIUM;
    }

    String description = extractString(item, "description", "");
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
   * Formats the retrospective period as a human-readable string.
   *
   * @param lastRetroTime the time of the last retrospective, or {@link Instant#EPOCH} if none
   * @return the formatted period string
   */
  private static String formatPeriod(Instant lastRetroTime)
  {
    Instant now = Instant.now();
    if (lastRetroTime.equals(Instant.EPOCH))
      return "Beginning to " + now;
    return lastRetroTime + " to " + now;
  }

  /**
   * Extracts a string field from a JSON node, returning a default value if absent or not a string.
   *
   * @param node the JSON object node to read from
   * @param key the field name to extract
   * @param defaultValue the value to return when the field is absent or not a string
   * @return the string value, or {@code defaultValue}
   */
  private static String extractString(JsonNode node, String key, String defaultValue)
  {
    JsonNode child = node.get(key);
    if (child != null && child.isString())
      return child.asString();
    return defaultValue;
  }

  /**
   * Extracts an integer field from a JSON node, returning a default value if absent or not a number.
   *
   * @param node the JSON object node to read from
   * @param key the field name to extract
   * @param defaultValue the value to return when the field is absent or not a number
   * @return the integer value, or {@code defaultValue}
   */
  private static int extractInt(JsonNode node, String key, int defaultValue)
  {
    JsonNode child = node.get(key);
    if (child != null && child.isNumber())
      return child.asInt();
    return defaultValue;
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
      String output = generator.getOutput(args);
      System.out.print(output);
    }
    catch (IOException e)
    {
      System.err.println("Error generating retrospective output: " + e.getMessage());
      System.exit(1);
    }
  }
}
