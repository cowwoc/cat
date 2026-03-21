/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.failure;

import io.github.cowwoc.cat.hooks.PostToolHandler;
import tools.jackson.databind.JsonNode;

/**
 * Detects skill preprocessor command failures and suggests filing a bug report.
 * <p>
 * When a skill's {@code !`} preprocessor command fails, the hook input contains an error field with
 * the text {@code Bash command failed for pattern "!`"}. This handler injects an additionalContext
 * message instructing the agent to tell the user to run {@code /cat:feedback}.
 */
public final class DetectPreprocessorFailure implements PostToolHandler
{
  private static final String PREPROCESSOR_FAILURE_PATTERN = "Bash command failed for pattern \"!`\"";
  private static final String FEEDBACK_MESSAGE =
    "A skill preprocessor command failed. This is a plugin bug, not a user error.\n" +
    "\n" +
    "Tell the user: \"A skill preprocessor command failed unexpectedly. Please run /cat:feedback " +
    "to report this issue so it can be investigated and fixed.\"\n" +
    "\n" +
    "Do not retry the skill invocation — the preprocessor will fail again until the underlying " +
    "plugin issue is resolved.";

  /**
   * Creates a new DetectPreprocessorFailure handler.
   */
  public DetectPreprocessorFailure()
  {
  }

  @Override
  public Result check(String toolName, JsonNode toolResult, String sessionId, JsonNode hookData)
  {
    JsonNode errorNode = hookData.get("error");
    if (errorNode == null)
      return Result.allow();
    if (!errorNode.isString())
      return Result.allow();
    String error = errorNode.asString();
    if (!error.contains(PREPROCESSOR_FAILURE_PATTERN))
      return Result.allow();
    return Result.context(FEEDBACK_MESSAGE);
  }
}
