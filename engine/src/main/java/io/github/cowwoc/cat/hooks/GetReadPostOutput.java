/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * get-read-posttool-output - Unified PostToolUse hook for Read/Glob/Grep/WebFetch/WebSearch
 *
 * TRIGGER: PostToolUse (matcher: Read|Glob|Grep|WebFetch|WebSearch)
 *
 * Consolidates read operation validation hooks into a single Java dispatcher.
 *
 * Handlers can:
 * - Warn about patterns (return warning)
 * - Allow silently (return null)
 */
public final class GetReadPostOutput implements HookHandler
{
  private static final Set<String> SUPPORTED_TOOLS = Set.of(
      "Read", "Glob", "Grep", "WebFetch", "WebSearch");

  private final List<ReadHandler> handlers;

  /**
   * Creates a new GetReadPostOutput instance.
   *
   * @param scope the JVM scope providing singleton handlers
   * @throws NullPointerException if scope is null
   */
  public GetReadPostOutput(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.handlers = List.of(scope.getDetectSequentialTools());
  }

  /**
   * Entry point for the Read posttool output hook.
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    try (JvmScope scope = new MainJvmScope())
    {
      HookInput input = HookInput.readFromStdin(scope.getJsonMapper());
      HookOutput output = new HookOutput(scope.getJsonMapper());
      new GetReadPostOutput(scope).run(input, output);
    }
    catch (RuntimeException | Error e)
    {
      Logger log = LoggerFactory.getLogger(GetReadPostOutput.class);
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

    String toolName = input.getToolName();
    if (!SUPPORTED_TOOLS.contains(toolName))
      return HookResult.withoutWarnings(output.empty());

    JsonNode toolInput = input.getToolInput();
    JsonNode toolResult = input.getToolResult();
    String sessionId = input.getSessionId();
    requireThat(sessionId, "sessionId").isNotBlank();
    List<String> warnings = new ArrayList<>();
    List<String> errorWarnings = new ArrayList<>();

    // Run all read posttool handlers
    for (ReadHandler handler : handlers)
    {
      try
      {
        ReadHandler.Result result = handler.check(toolName, toolInput, toolResult, sessionId);
        // PostToolUse cannot block, only warn
        if (!result.reason().isEmpty())
          warnings.add(result.reason());
      }
      catch (Exception e)
      {
        errorWarnings.add("get-read-posttool-output: handler error: " + e.getMessage());
      }
    }

    // Combine all warnings
    List<String> allWarnings = new ArrayList<>();
    allWarnings.addAll(warnings);
    allWarnings.addAll(errorWarnings);

    // Always allow (PostToolUse cannot block, only warn)
    return new HookResult(output.empty(), allWarnings);
  }
}
