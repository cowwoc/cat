/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import java.io.PrintStream;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.ClaudeTool;
import io.github.cowwoc.cat.hooks.MainClaudeTool;
import io.github.cowwoc.cat.hooks.Strings;
import io.github.cowwoc.cat.hooks.util.SkillOutput;

import static io.github.cowwoc.cat.hooks.Strings.block;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
  private static final int MAX_CATEGORY_ROWS = 8;
  private static final DateTimeFormatter PERIOD_FORMATTER =
    DateTimeFormatter.ofPattern("MMM d h:mm a", Locale.ENGLISH).withZone(ZoneOffset.UTC);
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
    Path retroDir = scope.getCatDir().resolve("retrospectives");

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

    return generateAnalysis(retroDir, indexFile, root, lastRetro, triggerReason, mapper);
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
        return daysSince + " days since last retrospective (threshold: " + triggerDays + " days)";
    }

    if (mistakeCount >= mistakeThreshold)
      return mistakeCount + " mistakes since last retrospective (threshold: " + mistakeThreshold + ")";

    return "";
  }

  /**
   * Generates the full retrospective analysis output as a display box.
   *
   * @param retroDir the retrospectives directory
   * @param indexFile the path to index.json
   * @param index the index.json root node
   * @param lastRetro the last retrospective timestamp
   * @param triggerReason the reason for triggering
   * @param mapper the JSON mapper
   * @return the analysis output rendered as a display box
   * @throws IOException if an I/O error occurs
   */
  private String generateAnalysis(Path retroDir, Path indexFile, JsonNode index, String lastRetro,
    String triggerReason, JsonMapper mapper) throws IOException
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

    // Executive Summary section (appears before trigger/period)
    for (String line : generateExecutiveSummaryLines(mistakes, index))
      contentLines.add(line);

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
    for (String line : generateOpenActionItemLines(index, lastRetroTime))
      contentLines.add("  " + line);

    // Action Item Details section
    contentLines.add("");
    contentLines.add("Action Item Details:");
    for (String line : generateActionItemDetailsLines(index))
      contentLines.add("  " + line);

    String boxOutput = display.buildHeaderBox("RETROSPECTIVE ANALYSIS", contentLines);
    if (!(index instanceof ObjectNode objectNode))
    {
      throw new IOException("Expected index.json root to be a JSON object but got: " +
        index.getClass().getSimpleName());
    }
    resetRetrospectiveCounter(retroDir, indexFile, objectNode, mapper);
    return boxOutput;
  }

  /**
   * Resets the retrospective counter in index.json after a successful analysis output.
   * <p>
   * Sets {@code last_retrospective} to the current UTC timestamp and {@code mistake_count_since_last}
   * to {@code 0}. Writes atomically via a temp file to prevent corruption on failed writes.
   *
   * @param retroDir the retrospectives directory
   * @param indexFile the path to index.json
   * @param root the parsed index.json root node
   * @param mapper the JSON mapper
   * @throws IOException if writing index.json fails
   */
  private void resetRetrospectiveCounter(Path retroDir, Path indexFile, ObjectNode root, JsonMapper mapper)
    throws IOException
  {
    root.put("last_retrospective", Instant.now().toString());
    root.put("mistake_count_since_last", 0);
    String updated = mapper.writeValueAsString(root);
    Path tempFile = Files.createTempFile(retroDir, "index", ".tmp");
    try
    {
      Files.writeString(tempFile, updated);
      try
      {
        Files.move(tempFile, indexFile, StandardCopyOption.ATOMIC_MOVE);
      }
      catch (AtomicMoveNotSupportedException _)
      {
        Files.move(tempFile, indexFile, StandardCopyOption.REPLACE_EXISTING);
      }
    }
    catch (IOException e)
    {
      Files.deleteIfExists(tempFile);
      throw e;
    }
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
   * Generates category breakdown lines from mistakes, sorted by count descending.
   * Each line includes the count and percentage of total mistakes.
   * If there are more than {@value #MAX_CATEGORY_ROWS} categories, only the top
   * {@value #MAX_CATEGORY_ROWS} are shown and the remainder are summarized as
   * {@code "[N more categories]"}.
   *
   * @param mistakes the list of mistake nodes
   * @return the category breakdown lines
   */
  private List<String> generateCategoryBreakdownLines(List<JsonNode> mistakes)
  {
    if (mistakes.isEmpty())
      return List.of("(no mistakes in period)");

    Map<String, Integer> categoryCount = new HashMap<>();
    int total = 0;
    for (JsonNode mistake : mistakes)
    {
      String category = extractString(mistake, "category", "");
      if (!category.isEmpty())
      {
        categoryCount.put(category, categoryCount.getOrDefault(category, 0) + 1);
        total += 1;
      }
    }

    // Sort by count descending, then alphabetically for stable ordering
    List<String> categories = new ArrayList<>(categoryCount.keySet());
    final int finalTotal = total;
    categories.sort((a, b) ->
    {
      int countCompare = categoryCount.get(b) - categoryCount.get(a);
      if (countCompare != 0)
        return countCompare;
      return a.compareTo(b);
    });

    List<String> lines = new ArrayList<>();
    int shown = Math.min(categories.size(), MAX_CATEGORY_ROWS);
    for (int i = 0; i < shown; i += 1)
    {
      String category = categories.get(i);
      int count = categoryCount.get(category);
      int pct = count * 100 / finalTotal;
      lines.add("%s: %d (%d%%)".formatted(category, count, pct));
    }
    if (categories.size() > MAX_CATEGORY_ROWS)
      lines.add("[" + (categories.size() - MAX_CATEGORY_ROWS) + " more categories]");

    return lines;
  }

  /**
   * Generates the executive summary lines shown before the trigger and period information.
   * <p>
   * The summary includes:
   * <ol>
   *   <li>Top mistake category with count and percentage</li>
   *   <li>Count of WORSENING patterns (omitted when zero)</li>
   *   <li>Count of high-priority open action items</li>
   * </ol>
   *
   * @param mistakes the list of mistake nodes for the current period
   * @param index    the index.json root node
   * @return the executive summary lines, each starting with the bullet character
   */
  private List<String> generateExecutiveSummaryLines(List<JsonNode> mistakes, JsonNode index)
  {
    List<String> lines = new ArrayList<>();

    // Bullet 1: top mistake category
    if (!mistakes.isEmpty())
    {
      Map<String, Integer> categoryCount = new HashMap<>();
      for (JsonNode mistake : mistakes)
      {
        String category = extractString(mistake, "category", "");
        if (!category.isEmpty())
          categoryCount.put(category, categoryCount.getOrDefault(category, 0) + 1);
      }
      if (!categoryCount.isEmpty())
      {
        String topCategory = categoryCount.entrySet().stream().
          max(Map.Entry.comparingByValue()).
          get().
          getKey();
        int topCount = categoryCount.get(topCategory);
        int pct = topCount * 100 / mistakes.size();
        lines.add("• Top category: " + topCategory + " (" + topCount + " mistakes, " + pct + "%)");
      }
    }

    // Bullet 2: worsening patterns count (omit if zero)
    int worseningCount = countWorseningPatterns(index);
    if (worseningCount > 0)
      lines.add("• " + worseningCount + " pattern(s) WORSENING — fixes not effective");

    // Bullet 3: high-priority open items
    int highPriorityCount = countHighPriorityOpenItems(index);
    lines.add("• " + highPriorityCount + " high-priority action item(s) require attention");

    // Blank line separator after summary before trigger line
    lines.add("");

    return lines;
  }

  /**
   * Counts patterns with WORSENING semantic status (occurrences_after_fix greater than occurrences_total).
   *
   * @param index the index.json root node
   * @return the number of worsening patterns
   */
  private static int countWorseningPatterns(JsonNode index)
  {
    JsonNode patterns = index.get("patterns");
    if (patterns == null || !patterns.isArray())
      return 0;
    int count = 0;
    for (JsonNode pattern : patterns)
    {
      String status = extractString(pattern, "status", "");
      if (status.isBlank() || status.equals("addressed"))
        continue;
      int total = extractInt(pattern, "occurrences_total", 0);
      int after = extractInt(pattern, "occurrences_after_fix", 0);
      if (after > total)
        count += 1;
    }
    return count;
  }

  /**
   * Counts open action items (status "open" or "escalated") with HIGH priority.
   *
   * @param index the index.json root node
   * @return the number of high-priority open action items
   */
  private static int countHighPriorityOpenItems(JsonNode index)
  {
    JsonNode actionItems = index.get("action_items");
    if (actionItems == null || !actionItems.isArray())
      return 0;
    int count = 0;
    for (JsonNode item : actionItems)
    {
      String status = extractString(item, "status", "");
      if (!status.equals("open") && !status.equals("escalated"))
        continue;
      String priority = extractString(item, "priority", "medium");
      if (priority.equalsIgnoreCase("high"))
        count += 1;
    }
    return count;
  }

  /**
   * Generates action item effectiveness report lines, including verdict-specific guidance.
   * Descriptions longer than {@value Strings#DESCRIPTION_MAX_LENGTH} characters are truncated with
   * {@code "... (see details)"} to direct readers to the Action Item Details section.
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
        String truncated = truncateWithDetails(description);
        if (truncated.isBlank())
          lines.add("%s: %s".formatted(id, verdict));
        else
          lines.add("%s: %s - %s".formatted(id, verdict, truncated));
        lines.add("     → " + verdictGuidance(verdict));
      }
    }

    if (lines.isEmpty())
      return List.of("(no effectiveness checks)");
    return lines;
  }

  /**
   * Returns the verdict-specific next-step guidance for an effectiveness verdict.
   *
   * @param verdict the effectiveness verdict string
   * @return the guidance text
   */
  private static String verdictGuidance(String verdict)
  {
    return switch (verdict)
    {
      case "effective" -> "Continue monitoring";
      case "ineffective" -> "Escalate: root cause may be misdiagnosed";
      case "partially_effective" -> "Refine approach based on remaining occurrences";
      case "pending" -> "Awaiting data to evaluate";
      default -> "Review and update action item";
    };
  }

  /**
   * Truncates a description to at most {@value Strings#DESCRIPTION_MAX_LENGTH} characters.
   * If truncated, appends {@code "... (see details)"} to direct readers to the full text.
   *
   * @param description the description to truncate
   * @return the original description if short enough, or a truncated version ending with
   *   {@code "... (see details)"}
   */
  private static String truncateWithDetails(String description)
  {
    if (description.length() <= Strings.DESCRIPTION_MAX_LENGTH)
      return description;
    return description.substring(0, Strings.DESCRIPTION_MAX_LENGTH) + "... (see details)";
  }

  /**
   * Severity ordering for pattern status labels.
   * Lower value = higher severity (displayed first).
   *
   * @param semanticStatus the semantic status label
   * @return the sort order value, where 0 is highest severity
   */
  private static int patternSeverityOrder(String semanticStatus)
  {
    return switch (semanticStatus)
    {
      case "📈 WORSENING" -> 0;
      case "⛔ NO IMPROVEMENT" -> 1;
      case "📉 IMPROVING" -> 2;
      case "✅ RESOLVED" -> 3;
      default -> 4;
    };
  }

  /**
   * Derives a semantic status label from pattern occurrence counts.
   *
   * @param total the total occurrences
   * @param after the occurrences after the fix was applied
   * @return the semantic status label
   */
  private static String derivePatternSemanticStatus(int total, int after)
  {
    if (after > total)
      return "📈 WORSENING";
    if (after == total)
      return "⛔ NO IMPROVEMENT";
    if (after > 0)
      return "📉 IMPROVING";
    return "✅ RESOLVED";
  }

  /**
   * Generates pattern status summary lines using semantic labels (WORSENING, NO IMPROVEMENT, IMPROVING,
   * RESOLVED). Patterns with status "addressed" are excluded. Results are sorted by severity
   * (WORSENING first, then NO IMPROVEMENT, then IMPROVING, then RESOLVED).
   *
   * @param index the index.json root node
   * @return the pattern status lines
   */
  private List<String> generatePatternStatusLines(JsonNode index)
  {
    JsonNode patterns = index.get("patterns");
    if (patterns == null || !patterns.isArray() || patterns.isEmpty())
      return List.of("(no patterns)");

    record PatternEntry(String id, String semanticStatus, int total, int after, String patternName)
    {
    }

    List<PatternEntry> entries = new ArrayList<>();
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
      String semanticStatus = derivePatternSemanticStatus(total, after);
      entries.add(new PatternEntry(id, semanticStatus, total, after, patternName));
    }

    if (entries.isEmpty())
      return List.of("(all patterns addressed)");

    entries.sort(Comparator.comparingInt((PatternEntry e) -> patternSeverityOrder(e.semanticStatus())).
      thenComparing(PatternEntry::id));

    List<String> lines = new ArrayList<>();
    for (PatternEntry entry : entries)
    {
      String occurrencePart = switch (entry.semanticStatus())
      {
        case "📈 WORSENING" -> "%d total, %d after fix".formatted(entry.total(), entry.after());
        case "⛔ NO IMPROVEMENT" -> "%d detected, %d after fix".formatted(entry.total(), entry.after());
        case "📉 IMPROVING" -> "%d total, %d remaining".formatted(entry.total(), entry.after());
        case "✅ RESOLVED" -> "%d total".formatted(entry.total());
        default -> "%d total, %d after fix".formatted(entry.total(), entry.after());
      };

      String line;
      if (entry.patternName().isBlank())
        line = "%s: %s — %s".formatted(entry.id(), entry.semanticStatus(), occurrencePart);
      else
        line = "%s: %s — %s - %s".formatted(entry.id(), entry.semanticStatus(), occurrencePart,
          Strings.truncate(entry.patternName(), Strings.DESCRIPTION_MAX_LENGTH));
      lines.add(line);
    }
    return lines;
  }

  /**
   * Generates open action item lines grouped by priority (HIGH, MEDIUM, LOW) with section headers.
   * Each item is prefixed with {@code [NEW]} if created during the current retrospective period, or
   * {@code [recurring xN]} if it has appeared in N previous retrospectives.
   * Descriptions longer than {@value Strings#DESCRIPTION_MAX_LENGTH} characters are truncated with
   * {@code "... (see details)"}.
   *
   * @param index         the index.json root node
   * @param lastRetroTime the start of the current retrospective period
   * @return the open action item lines
   */
  private List<String> generateOpenActionItemLines(JsonNode index, Instant lastRetroTime)
  {
    JsonNode actionItems = index.get("action_items");
    if (actionItems == null || !actionItems.isArray() || actionItems.isEmpty())
      return List.of("(no open action items)");

    int pastRetroCount = countPastRetrospectives(index);

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

    // Group by priority
    Map<Priority, List<ActionItemSummary>> byPriority = new LinkedHashMap<>();
    for (Priority p : new Priority[]{Priority.HIGH, Priority.MEDIUM, Priority.LOW})
      byPriority.put(p, new ArrayList<>());
    for (ActionItemSummary item : openItems)
      byPriority.computeIfAbsent(item.priority(), _ -> new ArrayList<>()).add(item);

    List<String> lines = new ArrayList<>();
    for (Map.Entry<Priority, List<ActionItemSummary>> entry : byPriority.entrySet())
    {
      List<ActionItemSummary> group = entry.getValue();
      if (group.isEmpty())
        continue;

      String priorityLabel = entry.getKey().name() + " PRIORITY (" + group.size() + " items):";
      lines.add(priorityLabel);
      for (ActionItemSummary item : group)
      {
        String indicator = computeRecurrenceIndicator(item.createdDate(), lastRetroTime, pastRetroCount);
        String truncatedDesc = truncateWithDetails(item.description());
        lines.add("  " + item.id() + ": " + indicator + " " + truncatedDesc);
      }
    }

    return lines;
  }

  /**
   * Counts the number of past retrospectives listed in the index.
   *
   * @param index the index.json root node
   * @return the count of past retrospective files
   */
  private static int countPastRetrospectives(JsonNode index)
  {
    JsonNode filesNode = index.get("files");
    if (filesNode == null)
      return 0;
    JsonNode retrosNode = filesNode.get("retrospectives");
    if (retrosNode == null || !retrosNode.isArray())
      return 0;
    return retrosNode.size();
  }

  /**
   * Computes the recurrence indicator for an action item.
   *
   * @param createdDate    the ISO-8601 creation date of the action item, or empty string if absent
   * @param lastRetroTime  the start of the current retrospective period
   * @param pastRetroCount the number of past retrospective files
   * @return {@code "[NEW]"} if the item was created in the current period, or
   *         {@code "[recurring xN]"} where N is the number of past retrospectives
   */
  private static String computeRecurrenceIndicator(String createdDate, Instant lastRetroTime,
    int pastRetroCount)
  {
    if (!createdDate.isEmpty())
    {
      try
      {
        Instant created = Instant.parse(createdDate);
        if (created.isAfter(lastRetroTime))
          return "[NEW]";
      }
      catch (DateTimeParseException _)
      {
        // fall through to recurring
      }
    }
    return "[recurring × " + pastRetroCount + "]";
  }

  /**
   * Generates the action item details section showing full (untruncated) descriptions.
   * This section follows the open action items section to provide the complete text
   * referenced by truncated summary lines.
   *
   * @param index the index.json root node
   * @return the action item detail lines, one per action item with an id
   */
  private List<String> generateActionItemDetailsLines(JsonNode index)
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
      String status = extractString(item, "status", "");
      if (!status.equals("open") && !status.equals("escalated"))
        continue;
      String description = extractString(item, "description", "");
      lines.add(id + ": " + description);
    }

    if (lines.isEmpty())
      return List.of("(no action items)");
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
    String createdDate = extractString(item, "created_date", "");
    return new ActionItemSummary(id, priority, description, createdDate);
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
   * <p>
   * Format: {@code "MMM d h:mm a — MMM d h:mm a (N days)"} or
   * {@code "MMM d h:mm a — MMM d h:mm a (N days, M hours)"} when hours are non-zero.
   * When there is no previous retrospective, returns {@code "Beginning — MMM d h:mm a"}.
   *
   * @param lastRetroTime the time of the last retrospective, or {@link Instant#EPOCH} if none
   * @return the formatted period string
   */
  private static String formatPeriod(Instant lastRetroTime)
  {
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    if (lastRetroTime.equals(Instant.EPOCH))
      return "Beginning — " + PERIOD_FORMATTER.format(now);

    Duration duration = Duration.between(lastRetroTime, now);
    long days = duration.toDays();
    long hours = duration.toHoursPart();

    String durationStr;
    if (hours == 0)
      durationStr = days + " days";
    else
      durationStr = days + " days, " + hours + " hours";

    return PERIOD_FORMATTER.format(lastRetroTime) + " — " +
      PERIOD_FORMATTER.format(now) + " (" + durationStr + ")";
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
  public static void main(String[] args) throws IOException
  {
    try (ClaudeTool scope = new MainClaudeTool())
    {
      try
      {
        run(scope, args, System.out);
      }
      catch (RuntimeException | AssertionError e)
      {
        Logger log = LoggerFactory.getLogger(GetRetrospectiveOutput.class);
        log.error("Unexpected error", e);
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }

  /**
   * Executes the retrospective output logic with a caller-provided output stream.
   *
   * @param scope the JVM scope
   * @param args  command line arguments
   * @param out   the output stream to write to
   * @throws NullPointerException if {@code scope}, {@code args} or {@code out} are null
   * @throws IOException          if an I/O error occurs
   */
  public static void run(JvmScope scope, String[] args, PrintStream out) throws IOException
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();
    GetRetrospectiveOutput generator = new GetRetrospectiveOutput(scope);
    String output = generator.getOutput(args);
    out.print(output);
  }
}
