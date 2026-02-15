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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * enforce-status-output - Stop hook to enforce verbatim status box output.
 * <p>
 * HOOK: Stop
 * <p>
 * TRIGGER: When /cat:status was invoked in this turn
 * <p>
 * M402: After 3+ documentation-level failures (M341, M395, M401), this hook
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
    try (JvmScope scope = new MainJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookOutput hookOutput = new HookOutput(mapper);
      String output;
      try
      {
        HookInput input = HookInput.readFromStdin(mapper);

        String transcriptPath = input.getString("transcript_path");

        boolean stopHookActive = input.getBoolean("stop_hook_active", false);
        if (stopHookActive)
        {
          output = hookOutput.empty();
          System.out.println(output);
          return;
        }

        CheckResult result = checkTranscriptForStatusSkill(mapper, transcriptPath);

        if (result.statusInvoked && !result.hasBoxOutput)
        {
          String reason =
            "M402 ENFORCEMENT: /cat:status was invoked but you did NOT output the status box. " +
            "The skill's MANDATORY OUTPUT REQUIREMENT states: Copy-paste ALL content between " +
            "the START and END markers. You summarized instead of copy-pasting. " +
            "OUTPUT THE COMPLETE STATUS BOX NOW - including the ╭── border, all issue lines, " +
            "the NEXT STEPS table, and the Legend. Do NOT summarize or interpret.";
          output = hookOutput.block(reason);
        }
        else
        {
          output = hookOutput.empty();
        }
      }
      catch (Exception e)
      {
        String errorMessage =
          "❌ Hook error: " + e.getMessage() + "\n" +
          "\n" +
          "Blocking as fail-safe. Please verify your working environment.";
        output = hookOutput.block(errorMessage);
      }
      System.out.println(output);
    }
    catch (RuntimeException | Error e)
    {
      Logger log = LoggerFactory.getLogger(EnforceStatusOutput.class);
      log.error("Unexpected error", e);
      throw e;
    }
  }

  /**
   * Check the transcript to see if /cat:status was invoked and if output was correct.
   *
   * @param mapper the JSON mapper to use for parsing
   * @param transcriptPath path to the transcript file
   * @return result indicating whether status was invoked and whether response had box output
   */
  private static CheckResult checkTranscriptForStatusSkill(JsonMapper mapper, String transcriptPath) throws IOException
  {
    if (transcriptPath.isEmpty())
      return new CheckResult(false, true);

    Path path = Paths.get(transcriptPath);
    if (!Files.exists(path))
      return new CheckResult(false, true);

    List<String> lines = Files.readAllLines(path);

    List<String> recentLines = getRecentLines(lines, 10);

    boolean statusInvoked = false;
    boolean hasBoxOutput = false;

    for (String line : recentLines)
    {
      String trimmed = line.trim();
      if (trimmed.isEmpty())
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
