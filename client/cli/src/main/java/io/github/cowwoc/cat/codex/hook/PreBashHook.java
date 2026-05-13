/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.codex.hook;

import io.github.cowwoc.cat.hook.bash.GitUserConfigGuard;
import tools.jackson.databind.JsonNode;

import java.io.InputStream;
import java.io.PrintStream;

/**
 * Codex entrypoint for Bash pre-tool validation.
 */
public final class PreBashHook
{
  /**
   * Prevents construction.
   */
  private PreBashHook()
  {
  }

  /**
   * Entry point for the Codex hook.
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    run(args, System.in, System.out);
  }

  /**
   * Testable entry point.
   *
   * @param args command line arguments
   * @param in standard input
   * @param out standard output
   */
  public static void run(String[] args, InputStream in, PrintStream out)
  {
    CodexHookInput.requireNoArgs(args);
    JsonNode nativeInput = CodexHookInput.read(in);
    String command = CodexHookInput.command(nativeInput);
    String reason = GitUserConfigGuard.getBlockReason(command);
    if (reason.isEmpty())
    {
      CodexHookInput.empty(out);
      return;
    }
    CodexHookInput.block(out, reason);
  }
}
