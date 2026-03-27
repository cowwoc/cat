/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import tools.jackson.databind.JsonNode;

import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Unified PostToolUse hook for Read/Glob/Grep/WebFetch/WebSearch
 *
 * TRIGGER: PostToolUse (matcher: Read|Glob|Grep|WebFetch|WebSearch)
 *
 * Consolidates read operation validation hooks into a single Java dispatcher.
 *
 * Handlers can:
 * - Warn about patterns (return warning)
 * - Allow silently (return null)
 */
public final class PostReadHook implements HookHandler
{
  private static final Set<String> SUPPORTED_TOOLS = Set.of(
      "Read", "Glob", "Grep", "WebFetch", "WebSearch");

  private final List<ReadHandler> handlers;

  /**
   * Creates a new PostReadHook instance.
   *
   * @param scope the JVM scope providing singleton handlers
   * @throws NullPointerException if scope is null
   */
  public PostReadHook(ClaudeHook scope)
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
    try (ClaudeHook scope = new MainClaudeHook())
    {
      run(scope, args, System.in, System.out);
    }
    catch (RuntimeException | AssertionError e)
    {
      LoggerFactory.getLogger(PostReadHook.class).error("Failed to create JVM scope", e);
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
    HookRunner.execute(PostReadHook::new, scope, out);
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
    if (!SUPPORTED_TOOLS.contains(toolName))
      return HookResult.withoutWarnings(scope.empty());

    JsonNode toolInput = scope.getToolInput();
    JsonNode toolResult = scope.getToolResult();
    String sessionId = scope.getSessionId();
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
    return new HookResult(scope.empty(), allWarnings);
  }
}
