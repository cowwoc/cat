/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.github.cowwoc.cat.hooks.Strings.block;

/**
 * enforce-status-output - Stop hook to enforce verbatim status box output.
 * <p>
 * HOOK: Stop
 * <p>
 * TRIGGER: When /cat:status was invoked in this turn
 * <p>
 * After 3+ documentation-level failures, this hook
 * escalates enforcement to programmatic level.
 * <p>
 * Detects when:
 * <ol>
 *   <li>User invoked /cat:status in the current turn</li>
 *   <li>Agent's response did NOT contain the status box (╭── characters)</li>
 * </ol>
 * <p>
 * Returns decision=block to force Claude to output the status box verbatim.
 */
public final class EnforceStatusOutput
{
  /**
   * Maximum transcript file size to read, in bytes (100 MB).
   */
  private static final long MAX_TRANSCRIPT_SIZE = 100L * 1024 * 1024;

  private EnforceStatusOutput()
  {
    // Utility class
  }

  /**
   * Entry point for the status output enforcement hook.
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    try (ClaudeHook scope = new MainClaudeHook())
    {
      JsonMapper mapper = scope.getJsonMapper();
      try
      {
        String output;
        try
        {
          String transcriptPath = scope.getString("transcript_path");
          boolean stopHookActive = scope.getBoolean("stop_hook_active", false);
          String sessionId = scope.getSessionId();
          Path sessionBasePath = scope.getClaudeSessionsPath();
          output = check(mapper, transcriptPath, stopHookActive, scope, sessionId, sessionBasePath);
        }
        catch (Exception e)
        {
          String errorMessage =
            "❌ Hook error: " + e.getMessage() + "\n" +
            "\n" +
            "Blocking as fail-safe. Please verify your working environment.";
          output = block(scope, errorMessage);
        }
        System.out.println(output);
      }
      catch (RuntimeException | AssertionError e)
      {
        Logger log = LoggerFactory.getLogger(EnforceStatusOutput.class);
        log.error("Unexpected error", e);
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }

  /**
   * Checks the transcript and returns the hook decision, with pending-agent-result enforcement.
   * <p>
   * Before checking the transcript for the status box, this method checks whether a pending-agent-result
   * flag file exists for the session. If it does, the session cannot end until {@code collect-results-agent}
   * is invoked.
   * <p>
   * When {@code stopHookActive} is {@code true} (second attempt after a prior block), the hook
   * re-checks the transcript: if the status box is now present it allows the response through;
   * if the box is still missing it blocks with a fail-fast error telling the user to retry
   * {@code /cat:status} manually.
   * <p>
   * When {@code stopHookActive} is {@code false} (first attempt), the hook applies the standard
   * enforcement: block if status was invoked but the box is absent.
   *
   * @param mapper the JSON mapper to use for parsing
   * @param transcriptPath path to the transcript JSONL file, or empty string if unavailable
   * @param stopHookActive whether Claude Code set {@code stop_hook_active=true} on this invocation
   * @param scope the JVM scope for building hook responses
   * @param sessionId the session ID, or empty string if unavailable
   * @param sessionBasePath the base path for session directories, or null if unavailable
   * @return JSON string representing the hook decision
   * @throws IOException if the transcript file cannot be read
   */
  public static String check(JsonMapper mapper, String transcriptPath, boolean stopHookActive,
    JvmScope scope, String sessionId, Path sessionBasePath) throws IOException
  {
    // Check for pending-agent-result flag before any other enforcement
    if (!sessionId.isEmpty() && sessionBasePath != null)
    {
      Path flagPath = sessionBasePath.resolve(sessionId).resolve("pending-agent-result");
      if (Files.exists(flagPath))
      {
        String reason = """
          BLOCKED: Agent tool result has not been processed.

          The previous Agent tool invocation completed but collect-results-agent was not called.
          You cannot end your turn until you have collected the subagent result.

          Required next step: Invoke collect-results-agent:
            Skill tool: skill="cat:collect-results-agent"
            Arguments: "<catAgentId> <issuePath> <subagentCommitsJson>"

          See plugin/skills/collect-results-agent/SKILL.md for argument details.""";
        return block(scope, reason);
      }
    }

    CheckResult result = checkTranscriptForStatusSkill(mapper, transcriptPath);

    if (result.statusInvoked && !result.hasBoxOutput)
    {
      String reason;
      if (stopHookActive)
      {
        reason =
          "ENFORCEMENT FAILED: /cat:status was invoked but the status box was not output on " +
          "two consecutive attempts. The model has been blocked twice and is still not complying. " +
          "Please retry /cat:status manually to get the status box.";
      }
      else
      {
        reason =
          "M402 ENFORCEMENT: /cat:status was invoked but you did NOT output the status box. " +
          "The skill's MANDATORY OUTPUT REQUIREMENT states: Copy-paste ALL content between " +
          "the START and END markers. You summarized instead of copy-pasting. " +
          "OUTPUT THE COMPLETE STATUS BOX NOW - including the ╭── border, all issue lines, " +
          "the NEXT STEPS table, and the Legend. Do NOT summarize or interpret.";
      }
      return block(scope, reason);
    }
    return Strings.empty();
  }

  /**
   * Check the transcript to see if /cat:status was invoked and if output was correct.
   *
   * @param mapper the JSON mapper to use for parsing
   * @param transcriptPath path to the transcript file
   * @return result indicating whether status was invoked and whether response had box output
   * @throws IOException if the transcript file cannot be read
   */
  private static CheckResult checkTranscriptForStatusSkill(JsonMapper mapper, String transcriptPath) throws IOException
  {
    if (transcriptPath.isBlank())
      return new CheckResult(false, true);

    Path path = Paths.get(transcriptPath);
    List<String> lines;
    try
    {
      long fileSize = Files.size(path);
      if (fileSize > MAX_TRANSCRIPT_SIZE)
        return new CheckResult(false, true);
      lines = Files.readAllLines(path);
    }
    catch (NoSuchFileException _)
    {
      return new CheckResult(false, true);
    }

    List<String> recentLines = getRecentLines(lines, 10);

    boolean statusInvoked = false;
    boolean hasBoxOutput = false;

    for (String line : recentLines)
    {
      String trimmed = line.strip();
      if (trimmed.isBlank())
        continue;

      JsonNode entry;
      try
      {
        entry = mapper.readTree(trimmed);
      }
      catch (Exception _)
      {
        continue;
      }

      JsonNode typeNode = entry.get("type");
      if (typeNode == null)
        continue;
      String type = typeNode.asString();

      if (type.equals("user") && checkUserMessageForStatus(entry))
        statusInvoked = true;

      if (type.equals("assistant") && checkAssistantMessageForBox(entry))
        hasBoxOutput = true;
    }

    return new CheckResult(statusInvoked, hasBoxOutput);
  }

  /**
   * Check if user message contains status skill invocation.
   *
   * @param entry the user message entry
   * @return true if status skill detected
   */
  private static boolean checkUserMessageForStatus(JsonNode entry)
  {
    JsonNode messageNode = entry.get("message");
    if (messageNode == null)
      return false;

    JsonNode contentNode = messageNode.get("content");
    if (contentNode == null || !contentNode.isArray())
      return false;

    for (JsonNode block : contentNode)
    {
      if (!block.isObject())
        continue;

      JsonNode typeNode = block.get("type");
      if (typeNode == null || !typeNode.asString().equals("text"))
        continue;

      JsonNode textNode = block.get("text");
      if (textNode == null)
        continue;

      String text = textNode.asString();
      if (text.contains("# CAT Status Display") ||
          text.contains("<!-- START COPY HERE -->") ||
          text.contains("<command-name>cat:status</command-name>") ||
          text.contains("<command-name>/cat:status</command-name>"))
      {
        return true;
      }
    }

    return false;
  }

  /**
   * Check if assistant message contains box output.
   *
   * @param entry the assistant message entry
   * @return true if box characters detected
   */
  private static boolean checkAssistantMessageForBox(JsonNode entry)
  {
    JsonNode messageNode = entry.get("message");
    if (messageNode == null)
      return false;

    JsonNode contentNode = messageNode.get("content");
    if (contentNode == null || !contentNode.isArray())
      return false;

    for (JsonNode block : contentNode)
    {
      if (!block.isObject())
        continue;

      JsonNode typeNode = block.get("type");
      if (typeNode == null || !typeNode.asString().equals("text"))
        continue;

      JsonNode textNode = block.get("text");
      if (textNode == null)
        continue;

      String text = textNode.asString();
      if (text.contains("╭─") && text.contains("│") && text.contains("╰─"))
        return true;
    }

    return false;
  }

  /**
   * Get the last N lines from a list.
   *
   * @param lines all lines
   * @param count number of lines to return
   * @return the last count lines, or all lines if fewer than count
   */
  private static List<String> getRecentLines(List<String> lines, int count)
  {
    if (lines.size() <= count)
      return lines;

    return new ArrayList<>(lines.subList(lines.size() - count, lines.size()));
  }

  /**
   * Result of checking transcript for status skill invocation.
   *
   * @param statusInvoked whether status skill was invoked
   * @param hasBoxOutput whether response contained box output
   */
  private record CheckResult(boolean statusInvoked, boolean hasBoxOutput)
  {
  }
}
