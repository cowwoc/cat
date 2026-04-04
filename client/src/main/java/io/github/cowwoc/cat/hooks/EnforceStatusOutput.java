/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.cat.hooks.Strings.block;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
      try
      {
        run(scope, args, System.in, System.out);
      }
      catch (IllegalArgumentException e)
      {
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
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
   * Executes the status output enforcement check.
   *
   * @param scope the hook scope providing access to hook input fields and session paths
   * @param args  command line arguments (unused)
   * @param in    the input stream (unused in current implementation)
   * @param out   the output stream to write the hook decision to
   * @throws NullPointerException if any of {@code scope}, {@code args}, {@code in}, or {@code out} are null
   */
  public static void run(ClaudeHook scope, String[] args, InputStream in, PrintStream out)
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(args, "args").isNotNull();
    requireThat(in, "in").isNotNull();
    requireThat(out, "out").isNotNull();
    if (args.length > 0)
      throw new IllegalArgumentException("Unexpected arguments: " + String.join(" ", args));
    JsonMapper mapper = scope.getJsonMapper();
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
    out.println(output);
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
    // Read only the last 10 lines via a bounded deque, avoiding loading the entire
    // (potentially multi-MB) transcript file into the 96MB heap.
    Deque<String> buffer = new ArrayDeque<>(11);
    try (BufferedReader reader = Files.newBufferedReader(path))
    {
      String line = reader.readLine();
      while (line != null)
      {
        buffer.addLast(line);
        if (buffer.size() > 10)
          buffer.removeFirst();
        line = reader.readLine();
      }
    }

    List<String> recentLines = new ArrayList<>(buffer);

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
   * Result of checking transcript for status skill invocation.
   *
   * @param statusInvoked whether status skill was invoked
   * @param hasBoxOutput whether response contained box output
   */
  private record CheckResult(boolean statusInvoked, boolean hasBoxOutput)
  {
  }
}
