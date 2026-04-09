/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.tool.post;

import io.github.cowwoc.cat.claude.hook.ClaudeHook;
import io.github.cowwoc.cat.claude.hook.PostToolHandler;
import io.github.cowwoc.cat.claude.hook.util.AgentIdPatterns;
import tools.jackson.databind.JsonNode;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.that;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Detects mistakes from tool results and suggests learn skill.
 * <p>
 * Monitors tool results for error patterns including build failures, test failures,
 * merge conflicts, and self-acknowledged mistakes.
 */
public final class AutoLearnMistakes implements PostToolHandler
{
  private static final int MAX_OUTPUT_LENGTH = 100_000;
  private static final int MAX_RECENT_LINES = 100;

  private static final Pattern BUILD_FAILURE_PATTERN =
    Pattern.compile("BUILD FAILURE|COMPILATION ERROR|compilation failure", Pattern.CASE_INSENSITIVE);

  private static final Pattern TEST_FAILURE_PATTERN =
    Pattern.compile(
      "Tests run:.*Failures: [1-9]|\\d+\\s+tests?\\s+failed|\\d+\\s+failures?\\b|" +
      "^\\s*\\S+\\s+\\.\\.\\.\\s+FAILED",
      Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

  private static final Pattern MERGE_CONFLICT_PATTERN =
    Pattern.compile("CONFLICT \\(|^<<<<<<<|^=======$|^>>>>>>>", Pattern.MULTILINE);

  private static final Pattern GIT_OPERATION_FAILURE_PATTERN =
    Pattern.compile("^fatal:|^error: ", Pattern.MULTILINE);

  private static final Pattern GIT_NOT_REPO_PATTERN =
    Pattern.compile("fatal: not a git repository|not a git repository \\(or any",
      Pattern.CASE_INSENSITIVE);

  private static final Pattern MISSING_POM_PATTERN =
    Pattern.compile("Could not (find|locate) (the )?pom\\.xml|No pom\\.xml found",
      Pattern.CASE_INSENSITIVE);

  private static final Pattern BASH_PATH_ERROR_PATTERN =
    Pattern.compile(
      "No such file or directory.*(/workspace|/tasks)|cannot access.*/workspace",
      Pattern.CASE_INSENSITIVE);

  private static final Pattern PARSE_ERROR_PATTERN =
    Pattern.compile(
      "parse error.*Invalid|jq: error|JSON.parse.*SyntaxError|malformed JSON",
      Pattern.CASE_INSENSITIVE);

  private static final Pattern BASH_PARSE_ERROR_PATTERN =
    Pattern.compile("\\(eval\\):[0-9]+:.*parse error");

  private static final Pattern EDIT_FAILURE_PATTERN =
    Pattern.compile("String to replace not found|old_string not found", Pattern.CASE_INSENSITIVE);

  private static final Pattern SKILL_STEP_FAILURE_PATTERN =
    Pattern.compile(
      "\\bERROR\\b|\\bFAILED\\b|failed to|step.*(failed|failure)|could not|unable to",
      Pattern.CASE_INSENSITIVE);

  private static final Pattern ASSISTANT_CRITICAL_PATTERN =
    Pattern.compile("I made a critical (error|mistake)|CRITICAL (DISASTER|MISTAKE|ERROR)",
      Pattern.CASE_INSENSITIVE);

  private static final Pattern ASSISTANT_SELF_ACK_PATTERN =
    Pattern.compile("My error|I (made|created) (a|an) (mistake|error)|I accidentally",
      Pattern.CASE_INSENSITIVE);

  private static final Pattern COMMIT_HASH_PATTERN = Pattern.compile("^[a-f0-9]{7,}");

  private final ClaudeHook scope;
  private final Map<String, Integer> sessionIdToLineCount = new HashMap<>();

  /**
   * Creates a new auto-learn-mistakes handler.
   *
   * @param scope the JVM scope providing session path configuration
   * @throws NullPointerException if {@code scope} is null
   */
  public AutoLearnMistakes(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  @Override
  public Result check(String toolName, JsonNode toolResult, String sessionId, JsonNode hookData)
  {
    requireThat(sessionId, "sessionId").isNotBlank();
    String stdout = getTextValue(toolResult, "stdout");
    String stderr = getTextValue(toolResult, "stderr");
    String toolOutput = stdout + stderr;
    if (toolOutput.length() > MAX_OUTPUT_LENGTH)
      toolOutput = toolOutput.substring(0, MAX_OUTPUT_LENGTH);

    int exitCode = 0;
    JsonNode exitCodeNode = toolResult.get("exit_code");
    if (exitCodeNode != null && exitCodeNode.isNumber())
      exitCode = exitCodeNode.asInt();

    String lastAssistantMessage = getRecentAssistantMessages(sessionId);

    MistakeDetection detection = detectMistake(toolName, toolOutput, exitCode, lastAssistantMessage);
    if (detection == null)
      return Result.allow();

    String taskSubject = "LFM: Investigate " + detection.type() + " from " + toolName;
    String taskActiveForm = "Investigating " + detection.type() + " mistake";

    String details = detection.details();
    if (details.length() > 500)
      details = details.substring(0, 500);

    return Result.context(
      "MISTAKE DETECTED: " + detection.type() + "\n\n" +
      "**MANDATORY**: Run `/cat:learn-agent` to record this mistake and prevent recurrence, then use " +
      "TaskCreate to track this investigation:\n" +
      "- subject: \"" + taskSubject + "\"\n" +
      "- description: \"Investigate " + detection.type() + " detected during " +
          toolName + " execution\"\n" +
      "- activeForm: \"" + taskActiveForm + "\"\n\n" +
      "**Context**: Detected " + detection.type() + " during " + toolName + " execution.\n" +
      "**Details**: " + details);
  }

  /**
   * Represents a detected mistake.
   *
   * @param type the mistake type
   * @param details contextual details about the mistake
   */
  private record MistakeDetection(String type, String details)
  {
    /**
     * Creates a mistake detection.
     *
     * @param type the mistake type
     * @param details contextual details about the mistake
     * @throws AssertionError if {@code type} or {@code details} are blank
     */
    MistakeDetection
    {
      assert that(type, "type").isNotBlank().elseThrow();
      assert that(details, "details").isNotBlank().elseThrow();
    }
  }

  /**
   * Gets a text value from a JSON node.
   *
   * @param node the JSON node
   * @param key the key to look up
   * @return the text value or empty string
   */
  private String getTextValue(JsonNode node, String key)
  {
    if (node == null)
      return "";
    JsonNode child = node.get(key);
    if (child == null || !child.isString())
      return "";
    return child.asString();
  }

  /**
   * Gets recent assistant messages from the conversation log.
   *
   * @param sessionId the session ID
   * @return concatenated recent assistant messages
   */
  private String getRecentAssistantMessages(String sessionId)
  {
    if (!AgentIdPatterns.SESSION_ID_PATTERN.matcher(sessionId).matches())
    {
      throw new IllegalArgumentException("Invalid sessionId format: '" + sessionId +
        "'. Expected UUID format (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx).");
    }
    Path convLog = scope.getClaudeSessionsPath().resolve(sessionId + ".jsonl");
    if (Files.notExists(convLog))
      return "";

    try
    {
      int lastChecked = sessionIdToLineCount.getOrDefault(sessionId, 0);

      // Stream the file line-by-line to avoid loading the entire (potentially multi-MB) session
      // file into memory. Lines before lastChecked are counted but discarded. Lines after
      // lastChecked are kept in a bounded deque of MAX_RECENT_LINES to bound peak memory.
      Deque<String> buffer = new ArrayDeque<>(MAX_RECENT_LINES + 1);
      int currentCount = 0;
      try (BufferedReader reader = Files.newBufferedReader(convLog))
      {
        String line = reader.readLine();
        while (line != null)
        {
          ++currentCount;
          if (currentCount > lastChecked)
          {
            buffer.addLast(line);
            if (buffer.size() > MAX_RECENT_LINES)
              buffer.removeFirst();
          }
          line = reader.readLine();
        }
      }

      if (currentCount <= lastChecked)
        return "";

      sessionIdToLineCount.put(sessionId, currentCount);
      StringBuilder result = new StringBuilder();
      int count = 0;
      for (String line : buffer)
      {
        if (count >= MAX_RECENT_LINES)
          break;
        if (line.contains("\"role\":\"assistant\""))
        {
          if (!result.isEmpty())
            result.append('\n');
          result.append(line);
          ++count;
        }
      }
      return result.toString();
    }
    catch (IOException _)
    {
      return "";
    }
  }

  /**
   * Detects mistake type from tool output.
   * <p>
   * Bash-failure patterns (Patterns 1, 2, 4, 7, 12, 13, 14, 15, 16) only trigger when the Bash tool
   * exits with a non-zero code. A successful Bash command (exit_code == 0) cannot represent a real
   * failure — any matching keywords in its output are false positives from displayed content
   * (source files, git history, etc.).
   *
   * @param toolName the tool name
   * @param output the tool output
   * @param exitCode the exit code
   * @param assistantMsg recent assistant messages
   * @return detection result or null if no mistake detected
   */
  private MistakeDetection detectMistake(String toolName, String output, int exitCode, String assistantMsg)
  {
    String filtered = output;

    if (toolName.equals("Bash") && exitCode != 0)
    {
      // Pattern 1: Build failures
      if (BUILD_FAILURE_PATTERN.matcher(filtered).find())
        return new MistakeDetection("build_failure", extractContext(filtered, "error|failure", 5));

      // Pattern 2: Test failures
      if (TEST_FAILURE_PATTERN.matcher(filtered).find())
        return new MistakeDetection("test_failure", extractContext(filtered, "fail|error", 5));

      // Pattern 4: Merge conflicts
      if (MERGE_CONFLICT_PATTERN.matcher(filtered).find())
        return new MistakeDetection("merge_conflict", extractContext(filtered, "CONFLICT \\(|<<<<<<<", 3));

      // Pattern 7: Git operation failures
      String gitFiltered = filterGitNoise(filtered);
      if (GIT_OPERATION_FAILURE_PATTERN.matcher(gitFiltered).find())
        return new MistakeDetection("git_operation_failure", extractContext(gitFiltered, "^fatal:|^error: ", 3));

      // Pattern 12: Wrong working directory (git repo)
      if (GIT_NOT_REPO_PATTERN.matcher(filtered).find())
      {
        return new MistakeDetection("wrong_working_directory",
            extractContext(filtered, "not a git repository", 3));
      }

      // Pattern 13: Wrong working directory (pom.xml)
      if (MISSING_POM_PATTERN.matcher(filtered).find())
      {
        return new MistakeDetection("wrong_working_directory",
            extractContext(filtered, "pom\\.xml", 3));
      }

      // Pattern 14: Path errors in Bash
      if (BASH_PATH_ERROR_PATTERN.matcher(filtered).find())
        return new MistakeDetection("wrong_working_directory",
          extractContext(filtered, "No such file or directory|cannot access", 3));

      // Pattern 15: Parse errors
      if (PARSE_ERROR_PATTERN.matcher(filtered).find())
        return new MistakeDetection("parse_error",
          extractContext(filtered, "parse error|jq: error|JSON|SyntaxError", 5));

      // Pattern 16: Bash parse errors
      if (BASH_PARSE_ERROR_PATTERN.matcher(filtered).find())
        return new MistakeDetection("bash_parse_error",
          extractContext(filtered, "\\(eval\\):[0-9]+:|parse error", 3));
    }

    // Pattern 5: Edit tool failures — only trigger for the Edit tool itself.
    // Bash commands that read files containing these phrases (e.g., task output files that include
    // subagent conversation JSON) must not trigger this pattern; the phrase is only meaningful when
    // the Edit tool reports it as its own failure.
    if (toolName.equals("Edit") && EDIT_FAILURE_PATTERN.matcher(filtered).find())
    {
      String editPattern = "string to replace not found|old_string not found";
      return new MistakeDetection("edit_failure", extractContext(filtered, editPattern, 2));
    }

    // Pattern 6: Skill step failures
    if (toolName.equals("Skill") && SKILL_STEP_FAILURE_PATTERN.matcher(filtered).find())
    {
      return new MistakeDetection("skill_step_failure",
          extractContext(filtered, "error|failed|could not|unable to", 5));
    }

    // Check assistant messages for self-acknowledged mistakes. These patterns detect agent
    // reasoning/dialogue and can only appear meaningfully in agent responses, not in tool output.
    if (!assistantMsg.isEmpty())
    {
      if (ASSISTANT_CRITICAL_PATTERN.matcher(assistantMsg).find())
        return new MistakeDetection("critical_self_acknowledgment", extractAssistantContext(assistantMsg));

      if (ASSISTANT_SELF_ACK_PATTERN.matcher(assistantMsg).find())
        return new MistakeDetection("self_acknowledged_mistake", extractAssistantContext(assistantMsg));
    }

    return null;
  }

  /**
   * Filters out git log output, JSON, and diff lines.
   *
   * @param output the raw output
   * @return filtered output
   */
  private String filterGitNoise(String output)
  {
    StringBuilder result = new StringBuilder();
    for (String line : output.split("\n"))
    {
      if (line.strip().startsWith("\""))
        continue;
      if (line.startsWith("+") || line.startsWith("-") || line.startsWith("@"))
        continue;
      if (COMMIT_HASH_PATTERN.matcher(line).find())
        continue;
      if (line.startsWith("commit ") || line.startsWith("Author:") ||
          line.startsWith("Date:") || line.startsWith("    "))
        continue;
      if (!result.isEmpty())
        result.append('\n');
      result.append(line);
    }
    return result.toString();
  }

  /**
   * Extracts context around matching pattern.
   *
   * @param output the output to search
   * @param patternStr the pattern to match
   * @param linesAfter number of lines to include after match
   * @return extracted context
   */
  private String extractContext(String output, String patternStr, int linesAfter)
  {
    String[] lines = output.split("\n");
    StringBuilder result = new StringBuilder();
    Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
    int resultLines = 0;
    for (int i = 0; i < lines.length && resultLines < 20; ++i)
    {
      if (pattern.matcher(lines[i]).find())
      {
        int start = Math.max(0, i - 2);
        int end = Math.min(lines.length, i + linesAfter + 1);
        for (int j = start; j < end && resultLines < 20; ++j)
        {
          if (!result.isEmpty())
            result.append('\n');
          result.append(lines[j]);
          ++resultLines;
        }
      }
    }
    return result.toString();
  }

  /**
   * Extracts context from assistant messages.
   *
   * @param assistantMsg the assistant messages
   * @return extracted context lines
   */
  private String extractAssistantContext(String assistantMsg)
  {
    String[] keywords = {"critical", "catastrophic", "devastating",
        "My error", "mistake", "error", "accidentally"};
    StringBuilder result = new StringBuilder();
    int count = 0;
    for (String line : assistantMsg.split("\n"))
    {
      if (count >= 3)
        break;
      String lower = line.toLowerCase(Locale.ROOT);
      for (String keyword : keywords)
      {
        if (lower.contains(keyword.toLowerCase(Locale.ROOT)))
        {
          if (!result.isEmpty())
            result.append('\n');
          result.append(line);
          ++count;
          break;
        }
      }
    }
    return result.toString();
  }
}
