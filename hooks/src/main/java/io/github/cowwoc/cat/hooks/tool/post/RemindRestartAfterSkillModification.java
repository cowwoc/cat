/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.tool.post;

import static io.github.cowwoc.cat.hooks.Strings.equalsIgnoreCase;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.PostToolHandler;
import tools.jackson.databind.JsonNode;

import java.util.regex.Pattern;

/**
 * Reminds user to restart Claude Code after skill or settings modifications.
 * <p>
 * Detects modifications to skill definitions, settings.json, or hook scripts via Write or Edit tools
 * and outputs a restart reminder banner to stderr.
 */
public final class RemindRestartAfterSkillModification implements PostToolHandler
{
  private static final Pattern SKILL_PATTERN = Pattern.compile("\\.claude/skills/[^/]+/SKILL\\.md$");
  private static final Pattern SETTINGS_PATTERN = Pattern.compile("\\.claude/settings\\.json$");
  private static final Pattern HOOK_PATTERN = Pattern.compile("\\.claude/hooks/[^/]+\\.sh$");

  /**
   * Creates a new remind-restart-after-skill-modification handler.
   */
  public RemindRestartAfterSkillModification()
  {
  }

  @Override
  public Result check(String toolName, JsonNode toolResult, String sessionId, JsonNode hookData)
  {
    requireThat(toolName, "toolName").isNotNull();
    requireThat(toolResult, "toolResult").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();
    requireThat(hookData, "hookData").isNotNull();

    if (!equalsIgnoreCase(toolName, "Write") && !equalsIgnoreCase(toolName, "Edit"))
      return Result.allow();

    JsonNode toolInput = hookData.get("tool_input");
    if (toolInput == null)
      return Result.allow();

    JsonNode filePathNode = toolInput.get("file_path");
    if (filePathNode == null || !filePathNode.isString())
      return Result.allow();

    String filePath = filePathNode.asString();
    if (filePath == null || filePath.isEmpty())
      return Result.allow();

    String fileType = determineFileType(filePath);
    if (fileType.isEmpty())
      return Result.allow();

    String basename = getBasename(filePath);
    String warning = """

      🔴 RESTART REQUIRED - ASK USER NOW 🔴
      ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
      You modified: %s (%s)

      ⚠️  ACTION REQUIRED: Ask the user to restart Claude Code NOW.

      Do NOT continue with other tasks until you have:
        1. TOLD the user: "Please restart Claude Code for changes to take effect"
        2. WAITED for confirmation they will restart

      Changes to %s files require a restart to take effect.
      ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

      """.formatted(basename, fileType, fileType);

    return Result.warn(warning);
  }

  /**
   * Determines the file type based on path.
   *
   * @param filePath the file path to check
   * @return the file type description or empty string if not matched
   */
  private String determineFileType(String filePath)
  {
    if (SKILL_PATTERN.matcher(filePath).find())
      return "skill definition";
    if (SETTINGS_PATTERN.matcher(filePath).find())
      return "settings";
    if (HOOK_PATTERN.matcher(filePath).find())
      return "hook script";
    return "";
  }

  /**
   * Gets the basename of a file path.
   *
   * @param filePath the file path
   * @return the basename (filename without directory)
   */
  private String getBasename(String filePath)
  {
    int lastSlash = filePath.lastIndexOf('/');
    if (lastSlash >= 0 && lastSlash < filePath.length() - 1)
      return filePath.substring(lastSlash + 1);
    return filePath;
  }
}
