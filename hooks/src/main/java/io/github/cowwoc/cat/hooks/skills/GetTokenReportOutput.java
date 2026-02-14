/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import io.github.cowwoc.cat.hooks.JvmScope;

import static io.github.cowwoc.cat.hooks.skills.JsonHelper.getStringOrDefault;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Output generator for /cat:token-report skill.
 *
 * Reads session JSONL directly and computes token table in Java,
 * eliminating the Python subprocess call.
 */
public final class GetTokenReportOutput
{
  /**
   * Column widths (fixed).
   */
  private static final int TYPE_WIDTH = 17;
  private static final int DESCRIPTION_WIDTH = 30;
  private static final int TOKENS_WIDTH = 8;
  private static final int CONTEXT_WIDTH = 16;
  private static final int DURATION_WIDTH = 10;

  /**
   * Context limit for percentage calculation.
   */
  private static final int CONTEXT_LIMIT = 200_000;

  /**
   * The JVM scope for accessing shared services.
   */
  private final JvmScope scope;

  /**
   * Creates a GetTokenReportOutput instance.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if scope is null
   */
  public GetTokenReportOutput(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Run token table computation using session ID from environment.
   *
   * This method supports direct preprocessing pattern - it collects all
   * necessary data from the environment without requiring LLM-provided arguments.
   *
   * @return the formatted output, or null if session not found or CLAUDE_SESSION_ID not set
   */
  public String getOutput()
  {
    String sessionId = System.getenv("CLAUDE_SESSION_ID");
    if (sessionId == null || sessionId.isBlank())
      return null;
    return getOutput(sessionId);
  }

  /**
   * Run token table computation and return result.
   *
   * @param sessionId the session ID to analyze
   * @return the formatted output, or null if session file not found
   * @throws NullPointerException if sessionId is null
   * @throws IllegalArgumentException if sessionId is blank
   */
  public String getOutput(String sessionId)
  {
    requireThat(sessionId, "sessionId").isNotBlank();

    // Build session file path
    Path sessionFile = Path.of(System.getProperty("user.home"),
                               ".config", "claude", "projects", "-workspace", sessionId + ".jsonl");

    if (!Files.exists(sessionFile))
      return null;

    // Extract subagent data directly from session file
    SubagentData data = extractSubagentData(sessionFile);
    if (data == null)
      return null;

    if (data.subagents.isEmpty())
      return "No subagent data found in session.";

    // Build table lines
    List<String> lines = buildTableLines(data);

    String tableLines = String.join("\n", lines);

    return tableLines + "\n" +
           "\n" +
           "Summary: " + data.subagents.size() + " subagents, " + data.totalTokens + " total tokens\n" +
           "\n" +
           "Legend:\n" +
           "- Percentages show context utilization per subagent\n" +
           "- Warning emoji inside Context column indicates high (>=40%) usage\n" +
           "- Critical emoji inside Context column indicates exceeded (>=80%) limit";
  }

  /**
   * Extracts subagent data from session JSONL file.
   *
   * @param sessionFile the session file path
   * @return the extracted data, or null on error
   */
  private SubagentData extractSubagentData(Path sessionFile)
  {
    List<SubagentEntry> subagents = new ArrayList<>();
    int totalTokens = 0;
    int totalDurationMs = 0;

    try (BufferedReader reader = Files.newBufferedReader(sessionFile))
    {
      JsonMapper mapper = scope.getJsonMapper();
      List<JsonNode> entries = new ArrayList<>();

      // Read all JSONL entries
      for (String line = reader.readLine(); line != null; line = reader.readLine())
      {
        if (!line.isBlank())
        {
          JsonNode entry = mapper.readTree(line);
          entries.add(entry);
        }
      }

      // Find Task tool_use entries and their results
      for (int i = 0; i < entries.size(); ++i)
      {
        JsonNode entry = entries.get(i);
        if (!isAssistantEntry(entry))
          continue;

        // Look for Task tool_use in message content
        JsonNode message = entry.get("message");
        if (message == null)
          continue;

        JsonNode content = message.get("content");
        if (content == null || !content.isArray())
          continue;

        for (JsonNode item : content)
        {
          if (!isTaskToolUse(item))
            continue;

          String toolUseId = getStringOrDefault(item, "id", "");
          if (toolUseId.isEmpty())
            continue;

          // Extract task info
          JsonNode input = item.get("input");
          String taskType = "";
          String description = "Subagent task";

          if (input != null)
          {
            String prompt = getStringOrDefault(input, "prompt", "");
            if (!prompt.isEmpty())
            {
              // Get first line, remove "## " prefix
              String firstLine = prompt.split("\n")[0];
              if (firstLine.startsWith("## "))
                firstLine = firstLine.substring(3);
              taskType = truncate(firstLine, 25);
            }
            description = truncate(getStringOrDefault(input, "description", "Subagent task"), 28);
          }

          // Find the result for this tool_use
          SubagentResult result = findToolUseResult(entries, toolUseId, i);

          if (result.tokens > 0)
          {
            SubagentEntry subagent = new SubagentEntry();
            subagent.type = taskType;
            subagent.description = description;
            subagent.tokens = result.tokens;
            subagent.durationMs = result.durationMs;
            subagents.add(subagent);

            totalTokens += result.tokens;
            totalDurationMs += result.durationMs;
          }
        }
      }
    }
    catch (IOException _)
    {
      return null;
    }

    SubagentData data = new SubagentData();
    data.subagents = subagents;
    data.totalTokens = totalTokens;
    data.totalDurationMs = totalDurationMs;
    return data;
  }

  /**
   * Checks if entry is an assistant message.
   *
   * @param entry the JSON entry
   * @return true if it's an assistant message
   */
  private boolean isAssistantEntry(JsonNode entry)
  {
    JsonNode type = entry.get("type");
    return type != null && type.isString() && type.asString().equals("assistant");
  }

  /**
   * Checks if content item is a Task tool_use.
   *
   * @param item the content item
   * @return true if it's a Task tool_use
   */
  private boolean isTaskToolUse(JsonNode item)
  {
    JsonNode type = item.get("type");
    JsonNode name = item.get("name");
    return type != null && type.isString() && type.asString().equals("tool_use") &&
           name != null && name.isString() && name.asString().equals("Task");
  }

  /**
   * Finds the tool_use result for a given tool_use ID.
   *
   * @param entries all session entries
   * @param toolUseId the tool_use ID to find
   * @param startIndex the index to start searching from
   * @return the result with tokens and duration
   */
  private SubagentResult findToolUseResult(List<JsonNode> entries, String toolUseId, int startIndex)
  {
    SubagentResult result = new SubagentResult();

    for (int i = startIndex + 1; i < entries.size(); ++i)
    {
      JsonNode entry = entries.get(i);
      JsonNode type = entry.get("type");
      if (type == null || !type.isString() || !type.asString().equals("user"))
        continue;

      JsonNode toolResult = entry.get("toolUseResult");
      if (toolResult == null)
        continue;

      JsonNode resultToolId = toolResult.get("tool_use_id");
      if (resultToolId == null || !resultToolId.isString())
        continue;

      if (resultToolId.asString().equals(toolUseId))
      {
        JsonNode tokens = toolResult.get("totalTokens");
        JsonNode duration = toolResult.get("durationMs");

        if (tokens != null && tokens.isNumber())
          result.tokens = tokens.asInt();
        if (duration != null && duration.isNumber())
          result.durationMs = duration.asInt();
        break;
      }
    }

    return result;
  }

  /**
   * Builds all table lines with exact formatting.
   *
   * @param data the subagent data
   * @return the list of table lines
   */
  private List<String> buildTableLines(SubagentData data)
  {
    List<String> lines = new ArrayList<>();

    // Top border
    String top = "╭" + "─".repeat(TYPE_WIDTH + 2) + "┬" +
                 "─".repeat(DESCRIPTION_WIDTH + 2) + "┬" +
                 "─".repeat(TOKENS_WIDTH + 2) + "┬" +
                 "─".repeat(CONTEXT_WIDTH + 2) + "┬" +
                 "─".repeat(DURATION_WIDTH + 2) + "╮";
    lines.add(top);

    // Header row
    String header = "│ " + padCell("Type", TYPE_WIDTH) + " │ " +
                    padCell("Description", DESCRIPTION_WIDTH) + " │ " +
                    padCell("Tokens", TOKENS_WIDTH) + " │ " +
                    padCell("Context", CONTEXT_WIDTH) + " │ " +
                    padCell("Duration", DURATION_WIDTH) + " │";
    lines.add(header);

    // Header divider
    String divider = "├" + "─".repeat(TYPE_WIDTH + 2) + "┼" +
                     "─".repeat(DESCRIPTION_WIDTH + 2) + "┼" +
                     "─".repeat(TOKENS_WIDTH + 2) + "┼" +
                     "─".repeat(CONTEXT_WIDTH + 2) + "┼" +
                     "─".repeat(DURATION_WIDTH + 2) + "┤";
    lines.add(divider);

    // Data rows
    for (SubagentEntry subagent : data.subagents)
    {
      String typeVal = truncate(subagent.type, TYPE_WIDTH);
      String descVal = truncate(subagent.description, DESCRIPTION_WIDTH);
      String tokensVal = formatTokens(subagent.tokens);
      String durationVal = formatDuration(subagent.durationMs);

      // Context with emoji indicator INSIDE
      ContextStatus status = contextStatus(subagent.tokens);
      String contextContent;
      if (status.level.equals("critical"))
        contextContent = status.text + "🚨";  // Police car light emoji
      else if (status.level.equals("warning"))
        contextContent = status.text + "⚠️";  // Warning emoji
      else
        contextContent = status.text;

      String row = "│ " + padCell(typeVal, TYPE_WIDTH) + " │ " +
                   padCell(descVal, DESCRIPTION_WIDTH) + " │ " +
                   padCell(tokensVal, TOKENS_WIDTH) + " │ " +
                   padCell(contextContent, CONTEXT_WIDTH) + " │ " +
                   padCell(durationVal, DURATION_WIDTH) + " │";
      lines.add(row);
    }

    // Footer divider
    lines.add(divider);

    // Total row
    String totalTokensStr = formatTokens(data.totalTokens);
    String totalDurationStr = formatDuration(data.totalDurationMs);
    String totalRow = "│ " + padCell("", TYPE_WIDTH) + " │ " +
                      padCell("TOTAL", DESCRIPTION_WIDTH) + " │ " +
                      padCell(totalTokensStr, TOKENS_WIDTH) + " │ " +
                      padCell("-", CONTEXT_WIDTH) + " │ " +
                      padCell(totalDurationStr, DURATION_WIDTH) + " │";
    lines.add(totalRow);

    // Bottom border
    String bottom = "╰" + "─".repeat(TYPE_WIDTH + 2) + "┴" +
                    "─".repeat(DESCRIPTION_WIDTH + 2) + "┴" +
                    "─".repeat(TOKENS_WIDTH + 2) + "┴" +
                    "─".repeat(CONTEXT_WIDTH + 2) + "┴" +
                    "─".repeat(DURATION_WIDTH + 2) + "╯";
    lines.add(bottom);

    return lines;
  }

  /**
   * Formats token count (e.g., 68400 -> "68.4k", 1500000 -> "1.5M").
   *
   * @param count the token count
   * @return the formatted string
   */
  private String formatTokens(int count)
  {
    if (count >= 1_000_000)
      return String.format("%.1fM", count / 1_000_000.0);
    if (count >= 1000)
      return String.format("%.1fk", count / 1000.0);
    return String.valueOf(count);
  }

  /**
   * Formats duration (e.g., 67000ms -> "1m 7s").
   *
   * @param ms the duration in milliseconds
   * @return the formatted string
   */
  private String formatDuration(int ms)
  {
    int secs = ms / 1000;
    if (secs >= 60)
    {
      int mins = secs / 60;
      int remainingSecs = secs % 60;
      return mins + "m " + remainingSecs + "s";
    }
    return secs + "s";
  }

  /**
   * Computes context status with percentage and warning level.
   *
   * @param tokens the token count
   * @return the context status
   */
  private ContextStatus contextStatus(int tokens)
  {
    int pct = (tokens * 100) / CONTEXT_LIMIT;
    ContextStatus status = new ContextStatus();
    if (pct >= 80)
    {
      status.text = pct + "% ";
      status.level = "critical";
    }
    else if (pct >= 40)
    {
      status.text = pct + "% ";
      status.level = "warning";
    }
    else
    {
      status.text = pct + "%";
      status.level = "ok";
    }
    return status;
  }

  /**
   * Pads content to exact display width.
   *
   * @param content the content to pad
   * @param width the target width
   * @return the padded string
   */
  private String padCell(String content, int width)
  {
    DisplayUtils display = scope.getDisplayUtils();
    int contentWidth = display.displayWidth(content);
    int padding = width - contentWidth;
    if (padding < 0)
      padding = 0;
    return content + " ".repeat(padding);
  }

  /**
   * Truncates text to max display width.
   *
   * @param text the text to truncate
   * @param maxWidth the maximum width
   * @return the truncated text
   */
  private String truncate(String text, int maxWidth)
  {
    DisplayUtils display = scope.getDisplayUtils();
    int textWidth = display.displayWidth(text);
    if (textWidth <= maxWidth)
      return text;

    // Truncate character by character
    StringBuilder result = new StringBuilder();
    int targetWidth = maxWidth - 3;  // Reserve space for "..."
    int width = 0;

    for (int i = 0; i < text.length(); ++i)
    {
      char c = text.charAt(i);
      int charWidth = 1;
      if (width + charWidth > targetWidth)
        break;
      result.append(c);
      width += charWidth;
    }

    return result.toString() + "...";
  }

  /**
   * Holds aggregated subagent data.
   */
  private static final class SubagentData
  {
    List<SubagentEntry> subagents = new ArrayList<>();
    int totalTokens;
    int totalDurationMs;
  }

  /**
   * Holds data for a single subagent.
   */
  private static final class SubagentEntry
  {
    String type = "";
    String description = "";
    int tokens;
    int durationMs;
  }

  /**
   * Holds result from finding tool_use result.
   */
  private static final class SubagentResult
  {
    int tokens;
    int durationMs;
  }

  /**
   * Holds context status information.
   */
  private static final class ContextStatus
  {
    String text = "";
    String level = "ok";
  }
}
