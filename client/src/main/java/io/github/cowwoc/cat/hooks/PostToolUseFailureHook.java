/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import io.github.cowwoc.cat.hooks.failure.DetectPreprocessorFailure;
import io.github.cowwoc.cat.hooks.failure.DetectRepeatedFailures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Unified PostToolUseFailure hook for all tools.
 * <p>
 * TRIGGER: PostToolUseFailure (no matcher - runs for all tools)
 * <p>
 * Consolidates all PostToolUseFailure hooks into a single Java dispatcher.
 * <p>
 * Handlers can:
 * <ul>
 *   <li>Warn about repeated failures (return warning string)</li>
 *   <li>Allow silently (return allow)</li>
 * </ul>
 */
public final class PostToolUseFailureHook implements HookHandler
{
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final ClaudeHook jvmScope;

  /**
   * Creates a new PostToolUseFailureHook instance.
   *
   * @param scope the hook scope providing configuration paths
   * @throws NullPointerException if {@code scope} is null
   */
  public PostToolUseFailureHook(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.jvmScope = scope;
  }

  /**
   * Entry point for the PostToolUseFailure hook.
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    HookRunner.execute(PostToolUseFailureHook::new, args);
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
    String sessionId = scope.getSessionId();
    // Create handlers using sessionId from the hook scope
    Path sessionDirectory = this.jvmScope.getCatSessionPath(sessionId);
    List<PostToolHandler> handlers = List.of(
      new DetectRepeatedFailures(Clock.systemUTC(), sessionDirectory),
      new DetectPreprocessorFailure());

    String toolName = scope.getToolName();
    JsonNode toolResult = scope.getToolResult();
    List<String> warnings = new ArrayList<>();
    List<String> additionalContexts = new ArrayList<>();

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
      catch (Exception e)
      {
        log.error("post-tool-use-failure: handler error", e);
      }
    }

    String jsonOutput;
    if (!additionalContexts.isEmpty())
    {
      String combined = String.join("\n\n", additionalContexts);
      jsonOutput = scope.additionalContext("PostToolUseFailureHook", combined);
    }
    else
      jsonOutput = scope.empty();

    return new HookResult(jsonOutput, warnings);
  }
}
