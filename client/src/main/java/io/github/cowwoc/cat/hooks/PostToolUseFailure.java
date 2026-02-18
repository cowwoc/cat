/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import io.github.cowwoc.cat.hooks.failure.DetectRepeatedFailures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
public final class PostToolUseFailure implements HookHandler
{
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final List<PostToolHandler> handlers;

  /**
   * Creates a new PostToolUseFailure with specified handlers.
   *
   * @param handlers the handlers to use
   * @throws NullPointerException if {@code handlers} is null
   */
  PostToolUseFailure(List<PostToolHandler> handlers)
  {
    requireThat(handlers, "handlers").isNotNull();
    this.handlers = handlers;
  }

  /**
   * Creates a new PostToolUseFailure instance.
   */
  public PostToolUseFailure()
  {
    this.handlers = List.of(new DetectRepeatedFailures());
  }

  /**
   * Entry point for the PostToolUseFailure hook.
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    try (JvmScope scope = new MainJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      HookInput input = HookInput.readFromStdin(mapper);
      HookOutput output = new HookOutput(scope);
      HookResult result = new PostToolUseFailure().run(input, output);

      for (String warning : result.warnings())
        System.err.println(warning);
      System.out.println(result.output());
    }
    catch (RuntimeException | AssertionError e)
    {
      Logger log = LoggerFactory.getLogger(PostToolUseFailure.class);
      log.error("Unexpected error", e);
      throw e;
    }
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

    String sessionId = input.getSessionId();
    if (sessionId.isEmpty())
      return HookResult.withoutWarnings(output.empty());

    String toolName = input.getToolName();
    JsonNode toolResult = input.getToolResult();
    List<String> warnings = new ArrayList<>();
    List<String> additionalContexts = new ArrayList<>();

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
      catch (Exception e)
      {
        log.error("post-tool-use-failure: handler error", e);
      }
    }

    String jsonOutput;
    if (!additionalContexts.isEmpty())
    {
      String combined = String.join("\n\n", additionalContexts);
      jsonOutput = output.additionalContext("PostToolUseFailure", combined);
    }
    else
      jsonOutput = output.empty();

    return new HookResult(jsonOutput, warnings);
  }
}
