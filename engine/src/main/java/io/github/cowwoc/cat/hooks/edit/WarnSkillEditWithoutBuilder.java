/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.edit;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.EditHandler;
import tools.jackson.databind.JsonNode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Warn when editing skill SKILL.md without skill-builder (A019/M213).
 * <p>
 * Skills should be updated using /cat:skill-builder for proper backward reasoning
 * and step extraction. This handler warns when editing SKILL.md files directly.
 */
public final class WarnSkillEditWithoutBuilder implements EditHandler
{
  private static final Pattern SKILL_MD_PATTERN = Pattern.compile("/skills/([^/]+)/SKILL\\.md$");

  /**
   * Creates a new WarnSkillEditWithoutBuilder handler.
   */
  public WarnSkillEditWithoutBuilder()
  {
  }

  @Override
  public Result check(JsonNode toolInput, String sessionId)
  {
    requireThat(toolInput, "toolInput").isNotNull();
    requireThat(sessionId, "sessionId").isNotBlank();

    JsonNode filePathNode = toolInput.get("file_path");
    String filePath;
    if (filePathNode != null)
      filePath = filePathNode.asString();
    else
      filePath = "";

    Matcher matcher = SKILL_MD_PATTERN.matcher(filePath);
    if (!matcher.find())
      return Result.allow();

    String skillName = matcher.group(1);
    String warning = "📝 SKILL EDIT DETECTED (A019/M213)\n" +
                     "\n" +
                     "Editing skill: " + skillName + "\n" +
                     "\n" +
                     "Before modifying skill documentation, consider using /cat:skill-builder:\n" +
                     "- Decomposes goal into forward steps via backward reasoning\n" +
                     "- Identifies computation candidates for hook extraction\n" +
                     "- Ensures consistent skill structure\n" +
                     "\n" +
                     "If you've already used skill-builder, or this is a minor fix (typo, formatting), proceed.\n" +
                     "\n" +
                     "Proceeding with edit (warning only, not blocked).";

    return Result.warn(warning);
  }
}
