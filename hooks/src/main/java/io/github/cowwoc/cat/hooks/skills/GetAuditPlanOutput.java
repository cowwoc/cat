package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Output generator for /cat:audit-plan skill.
 * <p>
 * Renders verification reports for PLAN.md acceptance criteria and file changes.
 * Reports show whether each planned criterion was implemented (DONE/PARTIAL/MISSING)
 * with supporting evidence from the codebase.
 */
public final class GetAuditPlanOutput
{
  private final JsonMapper jsonMapper;
  private final DisplayUtils display;

  /**
   * Creates a GetAuditPlanOutput instance.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if scope is null
   */
  public GetAuditPlanOutput(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.jsonMapper = scope.getJsonMapper();
    this.display = scope.getDisplayUtils();
  }

  /**
   * Main entry point for CLI invocation.
   *
   * @param args command line arguments (unused)
   */
  public static void main(String[] args)
  {
    try (JvmScope scope = new MainJvmScope())
    {
      String json = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
      GetAuditPlanOutput generator = new GetAuditPlanOutput(scope);
      String output = generator.getOutput(json);
      System.out.print(output);
    }
    catch (IOException e)
    {
      System.err.println("Error generating audit report: " + e.getMessage());
      System.exit(1);
    }
  }

  /**
   * Generates the audit report from JSON verification results.
   *
   * @param json the JSON verification results with structure:
   *             {issue_id, plan_path, criteria_results[], file_results[]}
   * @return the formatted audit report
   * @throws NullPointerException if {@code json} is null
   * @throws IllegalArgumentException if {@code json} is blank
   * @throws IOException if JSON parsing fails
   */
  public String getOutput(String json) throws IOException
  {
    requireThat(json, "json").isNotBlank();

    JsonNode root = jsonMapper.readTree(json);

    String issueId = getStringValue(root, "issue_id");
    String planPath = getStringValue(root, "plan_path");
    JsonNode criteriaResults = root.get("criteria_results");
    JsonNode fileResults = root.get("file_results");

    if (criteriaResults == null || !criteriaResults.isArray())
      return "Error: criteria_results must be an array";
    if (fileResults == null || !fileResults.isArray())
      return "Error: file_results must be an array";

    List<CriterionResult> criteria = parseCriteriaResults(criteriaResults);
    List<FileResult> files = parseFileResults(fileResults);

    return generateReport(issueId, planPath, criteria, files);
  }

  /**
   * Gets a string value from a JsonNode, returning empty string if missing.
   *
   * @param node the parent node
   * @param field the field name
   * @return the string value or empty string
   */
  private String getStringValue(JsonNode node, String field)
  {
    JsonNode fieldNode = node.get(field);
    if (fieldNode == null || !fieldNode.isString())
      return "";
    return fieldNode.asString();
  }

  /**
   * Parses criteria results from JSON array.
   *
   * @param array the criteria results array
   * @return the list of criterion results
   */
  private List<CriterionResult> parseCriteriaResults(JsonNode array)
  {
    List<CriterionResult> results = new ArrayList<>();
    for (JsonNode item : array)
    {
      CriterionResult result = new CriterionResult();
      result.criterion = getStringValue(item, "criterion");
      result.status = getStringValue(item, "status");
      result.evidence = getStringValue(item, "evidence");
      result.issues = parseStringArray(item.get("issues"));
      results.add(result);
    }
    return results;
  }

  /**
   * Parses file results from JSON array.
   *
   * @param array the file results array
   * @return the list of file results
   */
  private List<FileResult> parseFileResults(JsonNode array)
  {
    List<FileResult> results = new ArrayList<>();
    for (JsonNode item : array)
    {
      FileResult result = new FileResult();
      result.file = getStringValue(item, "file");
      result.status = getStringValue(item, "status");
      result.evidence = getStringValue(item, "evidence");
      result.issues = parseStringArray(item.get("issues"));
      results.add(result);
    }
    return results;
  }

  /**
   * Parses a JSON array into a list of strings.
   *
   * @param node the JSON array node
   * @return the list of strings
   */
  private List<String> parseStringArray(JsonNode node)
  {
    List<String> items = new ArrayList<>();
    if (node != null && node.isArray())
    {
      for (JsonNode item : node)
      {
        if (item.isString())
          items.add(item.asString());
      }
    }
    return items;
  }

  /**
   * Generates the complete audit report.
   *
   * @param issueId the issue ID
   * @param planPath the path to PLAN.md
   * @param criteria the criterion results
   * @param files the file results
   * @return the formatted report
   */
  private String generateReport(String issueId, String planPath, List<CriterionResult> criteria,
    List<FileResult> files)
  {
    List<String> contentItems = new ArrayList<>();

    contentItems.add("📋 Audit Report: " + issueId);
    contentItems.add("📄 Plan: " + planPath);
    contentItems.add("");

    int[] counts = countStatuses(criteria, files);
    int doneCount = counts[0];
    int partialCount = counts[1];
    int missingCount = counts[2];

    int totalChecks = criteria.size() + files.size();
    String overallStatus = getOverallStatus(doneCount, partialCount, missingCount, totalChecks);

    contentItems.add("📊 Summary: " + overallStatus);
    contentItems.add("   ✅ Done: " + doneCount + " | ⚠️ Partial: " + partialCount + " | ❌ Missing: " +
      missingCount);
    contentItems.add("");

    contentItems.addAll(renderCriteriaSection(criteria));
    contentItems.addAll(renderFilesSection(files));
    contentItems.addAll(renderActionsSection(partialCount, missingCount));

    int maxContentWidth = 0;
    for (String item : contentItems)
    {
      int width = display.displayWidth(item);
      if (width > maxContentWidth)
        maxContentWidth = width;
    }

    StringBuilder result = new StringBuilder();
    result.append(display.buildTopBorder(maxContentWidth)).append('\n');
    for (String item : contentItems)
      result.append(display.buildLine(item, maxContentWidth)).append('\n');
    result.append(display.buildBottomBorder(maxContentWidth));

    return result.toString();
  }

  /**
   * Counts status occurrences across criteria and files.
   *
   * @param criteria the criterion results
   * @param files the file results
   * @return array of [doneCount, partialCount, missingCount]
   */
  private int[] countStatuses(List<CriterionResult> criteria, List<FileResult> files)
  {
    int doneCount = 0;
    int partialCount = 0;
    int missingCount = 0;

    for (CriterionResult result : criteria)
    {
      switch (result.status)
      {
        case "DONE" ->
        {
          ++doneCount;
        }
        case "PARTIAL" ->
        {
          ++partialCount;
        }
        case "MISSING" ->
        {
          ++missingCount;
        }
        default ->
        {
        }
      }
    }

    for (FileResult result : files)
    {
      switch (result.status)
      {
        case "DONE" ->
        {
          ++doneCount;
        }
        case "PARTIAL" ->
        {
          ++partialCount;
        }
        case "MISSING" ->
        {
          ++missingCount;
        }
        default ->
        {
        }
      }
    }

    return new int[]{doneCount, partialCount, missingCount};
  }

  /**
   * Determines overall status from counts.
   *
   * @param doneCount number of completed items
   * @param partialCount number of partially completed items
   * @param missingCount number of missing items
   * @param totalChecks total number of checks
   * @return the overall status string
   */
  private String getOverallStatus(int doneCount, int partialCount, int missingCount, int totalChecks)
  {
    if (totalChecks == 0)
      return "NO CHECKS";
    if (missingCount == 0 && partialCount == 0)
      return "✅ COMPLETE";
    if (doneCount > 0)
      return "⚠️ PARTIAL";
    return "❌ INCOMPLETE";
  }

  /**
   * Renders the acceptance criteria section.
   *
   * @param criteria the criterion results
   * @return the formatted section lines
   */
  private List<String> renderCriteriaSection(List<CriterionResult> criteria)
  {
    List<String> lines = new ArrayList<>();
    if (criteria.isEmpty())
      return lines;

    lines.add("## Acceptance Criteria");
    lines.add("");

    for (CriterionResult result : criteria)
    {
      String icon = getStatusIcon(result.status);
      lines.add(icon + " " + result.criterion);
      if (!result.evidence.isEmpty())
        lines.add("   Evidence: " + result.evidence);
      if (!result.issues.isEmpty())
      {
        lines.add("   Issues:");
        for (String issue : result.issues)
          lines.add("      - " + issue);
      }
      lines.add("");
    }

    return lines;
  }

  /**
   * Renders the file changes section.
   *
   * @param files the file results
   * @return the formatted section lines
   */
  private List<String> renderFilesSection(List<FileResult> files)
  {
    List<String> lines = new ArrayList<>();
    if (files.isEmpty())
      return lines;

    lines.add("## File Changes");
    lines.add("");

    for (FileResult result : files)
    {
      String icon = getStatusIcon(result.status);
      lines.add(icon + " " + result.file);
      if (!result.evidence.isEmpty())
        lines.add("   Evidence: " + result.evidence);
      if (!result.issues.isEmpty())
      {
        lines.add("   Issues:");
        for (String issue : result.issues)
          lines.add("      - " + issue);
      }
      lines.add("");
    }

    return lines;
  }

  /**
   * Renders the actions required section.
   *
   * @param partialCount number of partial items
   * @param missingCount number of missing items
   * @return the formatted section lines
   */
  private List<String> renderActionsSection(int partialCount, int missingCount)
  {
    List<String> lines = new ArrayList<>();
    if (missingCount == 0 && partialCount == 0)
      return lines;

    lines.add("## Actions Required");
    lines.add("");
    if (missingCount > 0)
      lines.add("⚠️ " + missingCount + " check(s) missing - implementation may be incomplete");
    if (partialCount > 0)
      lines.add("⚠️ " + partialCount + " check(s) partially complete - review and address issues");
    lines.add("");

    return lines;
  }

  /**
   * Maps status to display icon.
   *
   * @param status the status string (DONE, PARTIAL, MISSING)
   * @return the corresponding icon
   */
  private String getStatusIcon(String status)
  {
    switch (status)
    {
      case "DONE" ->
      {
        return "✅";
      }
      case "PARTIAL" ->
      {
        return "⚠️";
      }
      case "MISSING" ->
      {
        return "❌";
      }
      default ->
      {
        return "❓";
      }
    }
  }

  /**
   * Holds a criterion verification result.
   */
  private static final class CriterionResult
  {
    String criterion = "";
    String status = "";
    String evidence = "";
    List<String> issues = new ArrayList<>();
  }

  /**
   * Holds a file verification result.
   */
  private static final class FileResult
  {
    String file = "";
    String status = "";
    String evidence = "";
    List<String> issues = new ArrayList<>();
  }
}
