/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.codex.hook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * SubagentStart hook for Codex.
 * <p>
 * Codex 0.134.0 exposes subagent-scoped lifecycle hooks with {@code agent_type}. CAT uses this
 * hook to inject agent-targeted rules without running SessionStart-only migration work.
 */
public final class SubagentStartHook extends AbstractCodexContextHook
{
  /**
   * Creates a SubagentStart hook.
   */
  public SubagentStartHook()
  {
  }

  /**
   * Entry point for the Codex SubagentStart hook.
   *
   * @param args command line arguments (unused)
   */
  public static void main(String[] args)
  {
    try
    {
      SessionStartHook.HookResult result = run(args);
      for (String warning : result.warnings())
        System.err.println(warning);
      System.out.println(result.output());
    }
    catch (RuntimeException | AssertionError e)
    {
      Logger log = LoggerFactory.getLogger(SubagentStartHook.class);
      log.error("Codex SubagentStart hook failed", e);
      System.err.println("Hook failed: " + e.getMessage());
      System.out.println("{}");
    }
  }

  /**
   * Runs the Codex SubagentStart hook without writing to process streams.
   *
   * @param args command line arguments
   * @return the hook output and warnings
   */
  public static SessionStartHook.HookResult run(String[] args)
  {
    return new SubagentStartHook().runFromSystem(args);
  }

  /**
   * Runs the Codex SubagentStart hook from process streams and environment.
   *
   * @param args command line arguments
   * @return the hook output and warnings
   */
  public SessionStartHook.HookResult runFromSystem(String[] args)
  {
    CodexHookInput.requireNoArgs(args);
    try (CodexHookScope scope = createScope(System.in, System.getenv(),
      Path.of(System.getProperty("user.dir"))))
    {
      return run(scope);
    }
  }

  /**
   * Runs the Codex SubagentStart hook against an initialized scope.
   *
   * @param scope the Codex hook scope
   * @return the hook output and warnings
   */
  public SessionStartHook.HookResult run(CodexHookScope scope)
  {
    return runContextHook(scope, "SubagentStart", false);
  }
}
