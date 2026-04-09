/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook;

import static io.github.cowwoc.cat.claude.hook.Strings.equalsIgnoreCase;
import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.bash.BlockGitUserConfigChange;
import io.github.cowwoc.cat.claude.hook.bash.BlockLockManipulation;
import io.github.cowwoc.cat.claude.hook.bash.BlockMainRebase;
import io.github.cowwoc.cat.claude.hook.bash.BlockMergeCommits;
import io.github.cowwoc.cat.claude.hook.bash.BlockReflogDestruction;
import io.github.cowwoc.cat.claude.hook.bash.BlockUnauthorizedMergeCleanup;
import io.github.cowwoc.cat.claude.hook.bash.BlockUnsafeRemoval;
import io.github.cowwoc.cat.claude.hook.bash.BlockWorktreeIsolationViolation;
import io.github.cowwoc.cat.claude.hook.bash.BlockWrongBranchCommit;
import io.github.cowwoc.cat.claude.hook.bash.ComputeBoxLines;
import io.github.cowwoc.cat.claude.hook.bash.RemindGitSquash;
import io.github.cowwoc.cat.claude.hook.bash.RequireSkillForCommand;
import io.github.cowwoc.cat.claude.hook.bash.ValidateCommitType;
import io.github.cowwoc.cat.claude.hook.bash.ValidateGitFilterBranch;
import io.github.cowwoc.cat.claude.hook.bash.ValidateGitOperations;
import io.github.cowwoc.cat.claude.hook.bash.VerifyStateInCommit;
import io.github.cowwoc.cat.claude.hook.bash.WarnFileExtraction;
import io.github.cowwoc.cat.claude.hook.bash.WarnMainWorkspaceCommit;
import io.github.cowwoc.cat.claude.hook.bash.WarnPipedWithoutTee;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified PreToolUse hook for Bash commands.
 * <p>
 * TRIGGER: PreToolUse (matcher: Bash)
 * <p>
 * Consolidates all Bash command validation hooks into a single Java dispatcher. Includes
 * {@link BlockUnauthorizedMergeCleanup} to prevent agents from invoking the merge-and-cleanup binary
 * directly via Bash — a bypass route that circumvents the Task-tool-level approval enforcement.
 * <p>
 * Handlers can:
 * <ul>
 *   <li>Block commands (return decision=block with reason)</li>
 *   <li>Warn about commands (return warning)</li>
 *   <li>Allow commands (return allow)</li>
 * </ul>
 */
public final class PreToolUseHook implements HookHandler
{
  private final Logger log = LoggerFactory.getLogger(PreToolUseHook.class);
  private final List<BashHandler> handlers;

  /**
   * Creates a new PreToolUseHook instance with the specified JVM scope.
   *
   * @param scope the JVM scope providing access to shared resources
   * @throws NullPointerException if {@code scope} is null
   */
  public PreToolUseHook(ClaudeHook scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.handlers = List.of(
      new BlockGitUserConfigChange(),
      new BlockLockManipulation(),
      new BlockMainRebase(scope),
      new BlockMergeCommits(),
      new BlockReflogDestruction(),
      new BlockUnsafeRemoval(scope),
      new BlockUnauthorizedMergeCleanup(scope),
      new BlockWorktreeIsolationViolation(scope),
      new BlockWrongBranchCommit(),
      new ComputeBoxLines(scope),
      new RemindGitSquash(),
      new ValidateCommitType(),
      new ValidateGitFilterBranch(),
      new ValidateGitOperations(),
      new VerifyStateInCommit(),
      new WarnFileExtraction(),
      new WarnMainWorkspaceCommit(),
      new WarnPipedWithoutTee(),
      new RequireSkillForCommand(scope));
  }

  /**
   * Entry point for the Bash pretool output hook.
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
      LoggerFactory.getLogger(PreToolUseHook.class).error("Failed to create JVM scope", e);
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
    HookRunner.execute(PreToolUseHook::new, scope, out);
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
    if (!equalsIgnoreCase(toolName, "Bash"))
      return HookResult.withoutWarnings(scope.empty());

    if (scope.getCommand().isEmpty())
      return HookResult.withoutWarnings(scope.empty());

    List<String> warnings = new ArrayList<>();

    // Run all bash pretool handlers
    for (BashHandler handler : handlers)
    {
      try
      {
        BashHandler.Result result = handler.check(scope);
        if (result.blocked())
        {
          String jsonOutput;
          if (result.additionalContext().isEmpty())
            jsonOutput = scope.block(result.reason());
          else
            jsonOutput = scope.block(result.reason(), result.additionalContext());
          return new HookResult(jsonOutput, warnings);
        }
        if (!result.reason().isEmpty())
          warnings.add(result.reason());
      }
      catch (RuntimeException e)
      {
        log.error("get-bash-pretool-output: handler error", e);
      }
    }

    // Allow the command
    return new HookResult(scope.empty(), warnings);
  }
}
