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
import io.github.cowwoc.cat.hooks.tool.post.DetectValidationWithoutEvidence;
import io.github.cowwoc.cat.hooks.tool.post.RemindRestartAfterSkillModification;
import io.github.cowwoc.cat.hooks.tool.post.SetPendingAgentResult;
import tools.jackson.databind.JsonNode;

import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.io.PrintStream;
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
  private final ClaudeHook jvmScope;

  /**
   * Creates a new PostToolUseHook instance.
   *
   * @param scope the hook scope providing configuration paths
   * @throws NullPointerException if {@code scope} is null
   */
  public PostToolUseHook(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.jvmScope = scope;
  }

  /**
   * Entry point for the general post-tool output hook.
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    try (ClaudeHook scope = new MainClaudeHook())
    {
      run(scope, args, System.in, System.out);
    }
    catch (RuntimeException | AssertionError e)
    {
      LoggerFactory.getLogger(PostToolUseHook.class).error("Failed to create JVM scope", e);
      System.err.println("Hook failed: " + e.getMessage());
    }
  }

  /**
   * Testable entry point with injectable I/O.
   *
   * @param scope the hook scope
   * @param args command line arguments (unused)
   * @param in input stream (unused)
   * @param out output stream for writing JSON response
   * @throws NullPointerException if any argument is null
   */
  public static void run(ClaudeHook scope, String[] args, InputStream in, PrintStream out)
  {
    HookRunner.execute(PostToolUseHook::new, scope, out);
  }

  /**
   * Processes hook data and returns the result with any warnings.
   *
   * @param scope the hook scope providing input data and output building
   * @return the hook result containing JSON output and warnings
   */
  @Override
  public HookResult run(ClaudeHook scope)
  {
    String toolName = scope.getToolName();
    if (toolName.isEmpty())
      return HookResult.withoutWarnings(scope.empty());

    JsonNode toolResult = scope.getToolResult();
    String sessionId = scope.getSessionId();
    requireThat(sessionId, "sessionId").isNotBlank();

    // Create handlers using sessionId from the hook scope
    Path sessionDirectory = jvmScope.getCatSessionPath(sessionId);
    List<PostToolHandler> handlers = List.of(
      new SetPendingAgentResult(jvmScope),
      new ResetFailureCounter(sessionDirectory),
      new AutoLearnMistakes(jvmScope),
      new DetectAssistantGivingUp(jvmScope),
      new DetectValidationWithoutEvidence(jvmScope),
      new DetectTokenThreshold(jvmScope),
      new RemindRestartAfterSkillModification());

    List<String> warnings = new ArrayList<>();
    List<String> additionalContexts = new ArrayList<>();
    List<String> errorWarnings = new ArrayList<>();

    // Run all general post-tool handlers
    for (PostToolHandler handler : handlers)
    {
      try
      {
        PostToolHandler.Result result = handler.check(toolName, toolResult, sessionId, scope.getRaw());
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
      jsonOutput = scope.additionalContext("PostToolUse", combined);
    }
    else
    {
      jsonOutput = scope.empty();
    }

    return new HookResult(jsonOutput, allWarnings);
  }
}
