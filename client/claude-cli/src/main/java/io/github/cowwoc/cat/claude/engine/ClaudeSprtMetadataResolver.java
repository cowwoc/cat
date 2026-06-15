/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.engine;

import io.github.cowwoc.cat.tool.skills.ModelIdResolver;
import io.github.cowwoc.cat.tool.skills.SprtMetadataResolver;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Resolves SPRT model/effort metadata for Claude.
 */
public final class ClaudeSprtMetadataResolver implements SprtMetadataResolver
{
  private static final String DEFAULT_TEST_MODEL = "haiku";
  private static final String DEFAULT_TEST_EFFORT = "low";
  private final String claudeCodeVersion;

  /**
   * Creates a new resolver.
   *
   * @param claudeCodeVersion Claude Code version used for model id resolution
   */
  public ClaudeSprtMetadataResolver(String claudeCodeVersion)
  {
    requireThat(claudeCodeVersion, "claudeCodeVersion").isNotBlank();
    this.claudeCodeVersion = claudeCodeVersion;
  }

  @Override
  public ResolvedConfig resolve(Path instructionPath, JsonNode frontmatter) throws IOException
  {
    requireThat(instructionPath, "instructionPath").isNotNull();
    requireThat(frontmatter, "frontmatter").isNotNull();
    String model = extractStringField(frontmatter, "model");
    String effort = extractStringField(frontmatter, "effort");
    String source = CONFIG_SOURCE_FRONTMATTER;
    if (model.isBlank() && effort.isBlank())
      source = CONFIG_SOURCE_DEFAULT;
    if (model.isBlank())
      model = DEFAULT_TEST_MODEL;
    if (effort.isBlank())
      effort = DEFAULT_TEST_EFFORT;
    return new ResolvedConfig(ModelIdResolver.resolve(claudeCodeVersion, model), effort, source);
  }

  /**
   * Extracts a string field from parsed YAML frontmatter.
   *
   * @param frontmatter parsed YAML frontmatter
   * @param fieldName the field to extract
   * @return the field value, or blank if absent
   */
  private static String extractStringField(JsonNode frontmatter, String fieldName)
  {
    JsonNode node = frontmatter.get(fieldName);
    if (node == null || node.isNull() || node.isMissingNode())
      return "";
    return node.asString("");
  }
}
