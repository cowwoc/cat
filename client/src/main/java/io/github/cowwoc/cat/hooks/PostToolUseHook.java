/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.failure.ResetFailureCounter;
import io.github.cowwoc.cat.hooks.tool.post.AutoLearnMistakes;
import io.github.cowwoc.cat.hooks.tool.post.DetectAssistantGivingUp;
import io.github.cowwoc.cat.hooks.tool.post.DetectTokenThreshold;
import io.github.cowwoc.cat.hooks.tool.post.RemindRestartAfterSkillModification;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified PostToolUse hook for all tools
 *
 * TRIGGER: PostToolUse (no matcher - runs for all tools)
 *
 * Consolidates general PostToolUse hooks into a single Java dispatcher.
 * For Bash-specific PostToolUse hooks, see PostBashHook.
 *
 * Handlers can:
 * - Warn about tool results (return warning string)
 * - Inject additional context (return additionalContext)
 * - Allow silently (return empty result)
 */
public final class PostToolUseHook implements HookHandler
{
  private final JvmScope scope;

  /**
   * Creates a new PostToolUseHook instance.
   *
   * @param scope the JVM scope providing configuration paths
   * @throws NullPointerException if {@code scope} is null
   */
  public PostToolUseHook(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Entry point for the general post-tool output hook.
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    HookRunner.execute(PostToolUseHook::new, args);
  }

  /**
   * Processes hook input and returns the result with any warnings.
   *
   * @param input the hook input to process
   * @param output the hook output builder for creating responses
   * @return the hook result containing JSON output and warnings
   * @throws NullPointerException if {@code input} or {@code output} are null
   */
  @Override
  public HookResult run(HookInput input, HookOutput output)
  {
    requireThat(input, "input").isNotNull();
    requireThat(output, "output").isNotNull();

    String toolName = input.getToolName();
    if (toolName.isEmpty())
      return HookResult.withoutWarnings(output.empty());

    JsonNode toolResult = input.getToolResult();
    String sessionId = input.getSessionId();
    requireThat(sessionId, "sessionId").isNotBlank();

    // Create handlers using sessionId from HookInput
    Path claudeConfigDir = scope.getClaudeConfigDir();
    Path sessionDirectory = scope.getSessionBasePath().resolve(sessionId);
    List<PostToolHandler> handlers = List.of(
      new ResetFailureCounter(sessionDirectory),
      new AutoLearnMistakes(),
      new DetectAssistantGivingUp(scope),
      new DetectTokenThreshold(claudeConfigDir),
      new RemindRestartAfterSkillModification());

    List<String> warnings = new ArrayList<>();
    List<String> additionalContexts = new ArrayList<>();
    List<String> errorWarnings = new ArrayList<>();

    // Run all general post-tool handlers
    for (PostToolHandler handler : handlers)
    {
      try
      {
        PostToolHandler.Result result = handler.check(toolName, toolResult, sessionId, input.getRaw());
        if (!result.warning().isEmpty())
          warnings.add(result.warning());
        if (!result.additionalContext().isEmpty())
          additionalContexts.add(result.additionalContext());
      }
      catch (RuntimeException | AssertionError e)
      {
        errorWarnings.add("post-tool-use: handler error: " + e.getMessage());
      }
    }

    // Combine all warnings
    List<String> allWarnings = new ArrayList<>();
    allWarnings.addAll(warnings);
    allWarnings.addAll(errorWarnings);

    // Build response with additionalContext if present
    String jsonOutput;
    if (!additionalContexts.isEmpty())
    {
      String combined = String.join("\n\n", additionalContexts);
      jsonOutput = output.additionalContext("PostToolUse", combined);
    }
    else
    {
      jsonOutput = output.empty();
    }

    return new HookResult(jsonOutput, allWarnings);
  }
}
