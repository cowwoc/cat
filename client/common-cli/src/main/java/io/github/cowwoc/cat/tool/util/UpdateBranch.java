/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.tool.util;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.agent.ProcessRunner;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Safely updates a local branch tip with a default fast-forward guard.
 * <p>
 * Usage: {@code update-branch [--force] <branch> <new-tip-hash>}
 */
public final class UpdateBranch
{
  private static final String USAGE = "Usage: update-branch [--force] <branch> <new-tip-hash>";
  private static final String MISSING_REF = "0000000000000000000000000000000000000000";

  private UpdateBranch()
  {
  }

  /**
   * Main entry point for command-line execution.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args)
  {
    int exitCode;
    try
    {
      exitCode = run(args, System.out, System.err, Path.of(""));
    }
    catch (RuntimeException | AssertionError e)
    {
      System.err.println("Unexpected error: " + Objects.toString(e.getMessage(),
        e.getClass().getSimpleName()));
      exitCode = 1;
    }
    if (exitCode != 0)
      System.exit(exitCode);
  }

  /**
   * Executes the update-branch command.
   * <p>
   * This method is separated from {@link #main(String[])} for testability.
   *
   * @param args             command-line arguments
   * @param out              stream for successful messages
   * @param err              stream for error and usage messages
   * @param workingDirectory working directory for git commands
   * @return exit code ({@code 0} on success, non-zero on failure)
   */
  public static int run(String[] args, PrintStream out, PrintStream err, Path workingDirectory)
  {
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();
    requireThat(err, "err").isNotNull();
    requireThat(workingDirectory, "workingDirectory").isNotNull();

    ParsedArgs parsed = parseArguments(args, err);
    if (parsed == null)
      return 2;

    String branch = parsed.branch();
    String targetHash = parsed.targetHash();
    String branchRef = "refs/heads/" + branch;

    ProcessRunner.Result newTipCheck = runGit(workingDirectory,
      "rev-parse", "--verify", targetHash + "^{commit}");
    if (newTipCheck.exitCode() != 0)
    {
      err.println("Invalid target commit: " + targetHash);
      return 1;
    }
    String resolvedTargetHash = newTipCheck.stdout().strip();

    ProcessRunner.Result currentTipResult = runGit(workingDirectory,
      "rev-parse", "--verify", branchRef);
    boolean branchExists = currentTipResult.exitCode() == 0;
    String expectedOldTip = MISSING_REF;
    if (branchExists && !parsed.force())
    {
      String currentTip = currentTipResult.stdout().strip();
      ProcessRunner.Result fastForwardCheck = runGit(workingDirectory,
        "merge-base", "--is-ancestor", currentTip, resolvedTargetHash);
      if (fastForwardCheck.exitCode() != 0)
      {
        err.println("Rejected non-fast-forward update for branch '" + branch +
          "'. Re-run with --force to override.");
        return 1;
      }
      expectedOldTip = currentTip;
    }

    ProcessRunner.Result updateResult;
    if (parsed.force())
      updateResult = runGit(workingDirectory, "update-ref", branchRef, resolvedTargetHash);
    else
      updateResult = runGit(workingDirectory, "update-ref", branchRef, resolvedTargetHash, expectedOldTip);
    if (updateResult.exitCode() != 0)
    {
      String details = updateResult.stdout().strip();
      if (details.isEmpty())
        err.println("Failed to update branch '" + branch + "'.");
      else
        err.println("Failed to update branch '" + branch + "': " + details);
      return 1;
    }

    if (branchExists)
      out.println("Updated branch '" + branch + "' to " + resolvedTargetHash + ".");
    else
      out.println("Created branch '" + branch + "' at " + resolvedTargetHash + ".");
    return 0;
  }

  private static ParsedArgs parseArguments(String[] args, PrintStream err)
  {
    boolean force = false;
    List<String> positional = new ArrayList<>(2);
    for (String arg : args)
    {
      if (arg.equals("--force"))
      {
        if (force)
        {
          err.println("Duplicate flag: --force");
          err.println(USAGE);
          return null;
        }
        force = true;
        continue;
      }
      if (arg.startsWith("--"))
      {
        err.println("Unknown flag: " + arg);
        err.println(USAGE);
        return null;
      }
      positional.add(arg);
    }

    if (positional.size() != 2)
    {
      err.println(USAGE);
      return null;
    }

    String branch = positional.get(0).strip();
    String targetHash = positional.get(1).strip();
    if (branch.isEmpty() || targetHash.isEmpty())
    {
      err.println(USAGE);
      return null;
    }
    return new ParsedArgs(force, branch, targetHash);
  }

  private static ProcessRunner.Result runGit(Path workingDirectory, String... args)
  {
    String[] command = new String[args.length + 1];
    command[0] = "git";
    System.arraycopy(args, 0, command, 1, args.length);
    return ProcessRunner.run(workingDirectory, command);
  }

  private record ParsedArgs(boolean force, String branch, String targetHash)
  {
  }
}
