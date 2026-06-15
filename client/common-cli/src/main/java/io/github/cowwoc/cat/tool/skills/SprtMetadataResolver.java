/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.skills;

import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Resolves engine-specific model/effort metadata for SPRT instruction tests.
 */
@FunctionalInterface
public interface SprtMetadataResolver
{
  /**
   * Indicates the resolved metadata came from engine defaults.
   */
  String CONFIG_SOURCE_DEFAULT = "default";
  /**
   * Indicates the resolved metadata came from an owning agent.
   */
  String CONFIG_SOURCE_OWNER = "owner";
  /**
   * Indicates the resolved metadata came from instruction frontmatter.
   */
  String CONFIG_SOURCE_FRONTMATTER = "frontmatter";

  /**
   * Resolves the model/effort configuration for one instruction path.
   *
   * @param instructionPath the instruction file path
   * @param frontmatter parsed YAML frontmatter from {@code instructionPath}
   * @return resolved model/effort metadata
   * @throws IOException if engine-specific metadata cannot be read
   */
  ResolvedConfig resolve(Path instructionPath, JsonNode frontmatter) throws IOException;

  /**
   * Returns a resolver for legacy callers that never execute metadata extraction commands.
   *
   * @return an unconfigured resolver
   */
  static SprtMetadataResolver unconfigured()
  {
    return (_, _) ->
    {
      throw new IllegalStateException(
        "SPRT metadata resolver is not configured. Use the engine-specific sprt-runner entrypoint.");
    };
  }

  /**
   * One model/effort pair.
   *
   * @param modelId the model id
   * @param effort the effort value
   */
  record ModelEffort(String modelId, String effort)
  {
    /**
     * Creates a new model/effort pair.
     *
     * @param modelId the model id
     * @param effort the effort value
     */
    public ModelEffort
    {
      requireThat(modelId, "modelId").isNotBlank();
      requireThat(effort, "effort").isNotBlank();
    }
  }

  /**
   * Resolved SPRT metadata.
   *
   * @param modelId the model id
   * @param effort the effort value
   * @param source {@code default}, {@code owner}, or {@code frontmatter}
   */
  record ResolvedConfig(String modelId, String effort, String source)
  {
    /**
     * Creates a new resolved config.
     *
     * @param modelId the model id
     * @param effort the effort value
     * @param source metadata source
     */
    public ResolvedConfig
    {
      requireThat(modelId, "modelId").isNotBlank();
      requireThat(effort, "effort").isNotBlank();
      requireThat(source, "source").isNotBlank();
    }
  }
}
