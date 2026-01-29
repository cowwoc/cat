package io.github.cowwoc.cat.hooks.tool.post;

import io.github.cowwoc.cat.hooks.PosttoolHandler;
import tools.jackson.databind.JsonNode;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects mistakes from tool results and suggests learn-from-mistakes skill.
 *
 * <p>Monitors tool results for error patterns including build failures, test failures,
 * protocol violations, merge conflicts, and self-acknowledged mistakes.</p>
 */
public final class AutoLearnMistakes implements PosttoolHandler
{
  private final Map<String, Integer> sessionIdToLineCount = new HashMap<>();

  /**
   * Creates a new auto-learn-mistakes handler.
   */
  public AutoLearnMistakes()
  {
    // Handler class
  }

  @Override
  public Result check(String toolName, JsonNode toolResult, String sessionId, JsonNode hookData)
  {
    requireThat(sessionId, "sessionId").isNotBlank();
    String stdout = getTextValue(toolResult, "stdout");
    String stderr = getTextValue(toolResult, "stderr");
    String toolOutput = stdout + stderr;

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

    return Result.context(
      "MISTAKE DETECTED: " + detection.type() + "\n\n" +
      "**MANDATORY**: Use TaskCreate to track this investigation:\n" +
      "- subject: \"" + taskSubject + "\"\n" +
      "- description: \"Investigate " + detection.type() + " detected during " + toolName + " execution\"\n" +
      "- activeForm: \"" + taskActiveForm + "\"\n\n" +
      "**Context**: Detected " + detection.type() + " during " + toolName + " execution.\n" +
      "**Details**: " + (detection.details().length() > 500 ? detection.details().substring(0, 500) : detection.details())
    );
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
     */
    public MistakeDetection
    {
      // Record validation
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
    String value = child.asString();
    if (value != null)
      return value;
    return "";
  }

  /**
   * Gets recent assistant messages from the conversation log.
   *
   * @param sessionId the session ID
   * @return concatenated recent assistant messages
   */
  private String getRecentAssistantMessages(String sessionId)
  {
    Path convLog = Path.of(System.getProperty("user.home"), ".config", "claude", "projects",
      "-workspace", sessionId + ".jsonl");
    if (!Files.exists(convLog))
      return "";

    try
    {
      List<String> lines = Files.readAllLines(convLog);
      int currentCount = lines.size();
      int lastChecked = sessionIdToLineCount.getOrDefault(sessionId, 0);

      if (currentCount <= lastChecked)
        return "";

      sessionIdToLineCount.put(sessionId, currentCount);
      StringBuilder result = new StringBuilder();
      int count = 0;
      for (int i = lastChecked; i < currentCount && count < 5; ++i)
      {
        String line = lines.get(i);
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
   *
   * @param toolName the tool name
   * @param output the tool output
   * @param exitCode the exit code
   * @param assistantMsg recent assistant messages
   * @return detection result or null if no mistake detected
   */
  private MistakeDetection detectMistake(String toolName, String output, int exitCode, String assistantMsg)
  {
    String filtered = filterJsonContent(output);

    // Pattern 1: Build failures
    if (Pattern.compile("BUILD FAILURE|COMPILATION ERROR|compilation failure", Pattern.CASE_INSENSITIVE)
        .matcher(filtered).find())
      return new MistakeDetection("build_failure", extractContext(filtered, "error|failure", 5));

    // Pattern 2: Test failures
    if (Pattern.compile(
        "Tests run:.*Failures: [1-9]|\\d+\\s+tests?\\s+failed|\\d+\\s+failures?\\b|^(FAIL:|FAILED\\s)|^\\s*\\S+\\s+\\.\\.\\.\\s+FAILED",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(filtered).find())
      return new MistakeDetection("test_failure", extractContext(filtered, "fail|error", 5));

    // Pattern 3: Protocol violations
    if (Pattern.compile("PROTOCOL VIOLATION|VIOLATION").matcher(filtered).find())
      return new MistakeDetection("protocol_violation", extractContext(filtered, "violation", 5));

    // Pattern 4: Merge conflicts
    if (Pattern.compile("CONFLICT \\(|^<<<<<<<|^=======$|^>>>>>>>", Pattern.MULTILINE).matcher(filtered).find())
      return new MistakeDetection("merge_conflict", extractContext(filtered, "CONFLICT \\(|<<<<<<<", 3));

    // Pattern 5: Edit tool failures
    if (Pattern.compile("String to replace not found|old_string not found", Pattern.CASE_INSENSITIVE)
        .matcher(filtered).find())
      return new MistakeDetection("edit_failure", extractContext(filtered, "string to replace not found|old_string not found", 2));

    // Pattern 6: Skill step failures
    if (toolName.equals("Skill") && Pattern.compile(
        "\\bERROR\\b|\\bFAILED\\b|failed to|step.*(failed|failure)|could not|unable to",
        Pattern.CASE_INSENSITIVE).matcher(filtered).find())
      return new MistakeDetection("skill_step_failure", extractContext(filtered, "error|failed|could not|unable to", 5));

    // Pattern 7: Git operation failures
    String gitFiltered = filterGitNoise(filtered);
    if (Pattern.compile("^fatal:|^error: ", Pattern.MULTILINE).matcher(gitFiltered).find())
      return new MistakeDetection("git_operation_failure", extractContext(gitFiltered, "^fatal:|^error: ", 3));

    // Pattern 8: Missing cleanup
    if (Pattern.compile("why didn't you (remove|delete|clean|cleanup)", Pattern.CASE_INSENSITIVE)
        .matcher(filtered).find())
      return new MistakeDetection("missing_cleanup", extractContext(filtered, "why didn't you|didn't you", 2));

    // Pattern 9: Self-acknowledged mistakes
    if (Pattern.compile(
        "(you're|you are) (right|correct|absolutely right).*(should have|should've)|I should have.*instead",
        Pattern.CASE_INSENSITIVE).matcher(filtered).find())
      return new MistakeDetection("self_acknowledged_mistake", extractContext(filtered, "you're right|should have", 5));

    // Pattern 10: Restore from backup
    if (Pattern.compile(
        "(let me|I'll|I will|going to) (restore|reset).*(from|using|to).{0,10}backup",
        Pattern.CASE_INSENSITIVE).matcher(filtered).find())
      return new MistakeDetection("restore_from_backup", extractContext(filtered, "restore|reset|backup", 3));

    // Pattern 11: Critical self-acknowledgments
    if (Pattern.compile("CRITICAL (DISASTER|MISTAKE|ERROR|FAILURE|BUG|PROBLEM|ISSUE)|catastrophic|devastating",
        Pattern.CASE_INSENSITIVE).matcher(gitFiltered).find())
      return new MistakeDetection("critical_self_acknowledgment",
        extractContext(gitFiltered, "CRITICAL|catastrophic|devastating", 5));

    // Pattern 12: Wrong working directory
    if (Pattern.compile("fatal: not a git repository|not a git repository \\(or any", Pattern.CASE_INSENSITIVE)
        .matcher(filtered).find())
      return new MistakeDetection("wrong_working_directory", extractContext(filtered, "not a git repository", 3));

    // Pattern 12b: Missing pom.xml
    if (Pattern.compile("Could not (find|locate) (the )?pom\\.xml|No pom\\.xml found", Pattern.CASE_INSENSITIVE)
        .matcher(filtered).find())
      return new MistakeDetection("wrong_working_directory", extractContext(filtered, "pom\\.xml", 3));

    // Pattern 12c: Path errors in Bash
    if (toolName.equals("Bash") && Pattern.compile(
        "No such file or directory.*(/workspace|/tasks)|cannot access.*/workspace",
        Pattern.CASE_INSENSITIVE).matcher(filtered).find())
      return new MistakeDetection("wrong_working_directory",
        extractContext(filtered, "No such file or directory|cannot access", 3));

    // Pattern 13: Parse errors
    if (exitCode != 0 && Pattern.compile(
        "parse error.*Invalid|jq: error|JSON.parse.*SyntaxError|malformed JSON",
        Pattern.CASE_INSENSITIVE).matcher(filtered).find())
      return new MistakeDetection("parse_error", extractContext(filtered, "parse error|jq: error|JSON|SyntaxError", 5));

    // Pattern 14: Bash parse errors
    if (toolName.equals("Bash") && Pattern.compile("\\(eval\\):[0-9]+:.*parse error").matcher(filtered).find())
      return new MistakeDetection("bash_parse_error", extractContext(filtered, "\\(eval\\):[0-9]+:|parse error", 3));

    // Check assistant messages
    if (!assistantMsg.isEmpty())
    {
      if (Pattern.compile("I made a critical (error|mistake)|CRITICAL (DISASTER|MISTAKE|ERROR)",
          Pattern.CASE_INSENSITIVE).matcher(assistantMsg).find())
        return new MistakeDetection("critical_self_acknowledgment", extractAssistantContext(assistantMsg));

      if (Pattern.compile("My error|I (made|created) (a|an) (mistake|error)|I accidentally",
          Pattern.CASE_INSENSITIVE).matcher(assistantMsg).find())
        return new MistakeDetection("self_acknowledged_mistake", extractAssistantContext(assistantMsg));
    }

    return null;
  }

  /**
   * Filters out JSON/JSONL content to avoid false positives.
   *
   * @param output the raw output
   * @return filtered output
   */
  private String filterJsonContent(String output)
  {
    StringBuilder result = new StringBuilder();
    for (String line : output.split("\n"))
    {
      String stripped = line.trim();
      if (stripped.startsWith("{") && (stripped.contains("\"type\":") ||
          stripped.contains("\"parentUuid\":") || stripped.contains("\"sessionId\":")))
        continue;
      if ((stripped.startsWith("[{") || stripped.startsWith("{\"")) &&
          (stripped.contains("\"type\":") || stripped.contains("\"message\":")))
        continue;
      if (!result.isEmpty())
        result.append('\n');
      result.append(line);
    }
    return result.toString();
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
    Pattern commitPattern = Pattern.compile("^[a-f0-9]{7,}");
    for (String line : output.split("\n"))
    {
      if (line.trim().startsWith("\""))
        continue;
      if (line.startsWith("+") || line.startsWith("-") || line.startsWith("@"))
        continue;
      if (commitPattern.matcher(line).find())
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
    StringBuilder result = new StringBuilder();
    Pattern pattern = Pattern.compile(".*(?:critical|CRITICAL|catastrophic|devastating|My error|mistake|error|accidentally).*");
    Matcher matcher = pattern.matcher(assistantMsg);
    int count = 0;
    while (matcher.find() && count < 3)
    {
      if (!result.isEmpty())
        result.append('\n');
      result.append(matcher.group());
      ++count;
    }
    return result.toString();
  }
}
