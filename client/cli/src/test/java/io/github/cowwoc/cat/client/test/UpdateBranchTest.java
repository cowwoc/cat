/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.util.UpdateBranch;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link UpdateBranch}.
 */
public final class UpdateBranchTest
{
  /**
   * Verifies that a fast-forward update succeeds and updates the branch tip to the requested commit.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void fastForwardUpdateSucceeds() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try
    {
      String oldHead = git(repoDir, "rev-parse", "HEAD");
      writeAndCommit(repoDir, "forward.txt", "forward", "forward commit");
      String newHead = git(repoDir, "rev-parse", "HEAD");

      git(repoDir, "branch", "task-branch", oldHead);

      RunResult result = runUpdateBranch(repoDir, "task-branch", newHead);

      requireThat(result.exitCode(), "exitCode").isEqualTo(0);
      String updatedHead = git(repoDir, "rev-parse", "refs/heads/task-branch");
      requireThat(updatedHead, "updatedHead").isEqualTo(newHead);
      TestUtils.assertPlainText(result.stdout(), result.stderr());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
    }
  }

  /**
   * Verifies that a non-fast-forward update is rejected when {@code --force} is not provided.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void nonFastForwardRejectedWithoutForce() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try
    {
      writeAndCommit(repoDir, "first.txt", "first", "first commit");
      String newerHead = git(repoDir, "rev-parse", "HEAD");
      String olderHead = git(repoDir, "rev-parse", "HEAD~1");

      git(repoDir, "branch", "task-branch", newerHead);

      RunResult result = runUpdateBranch(repoDir, "task-branch", olderHead);

      requireThat(result.exitCode(), "exitCode").isNotEqualTo(0);
      String unchangedHead = git(repoDir, "rev-parse", "refs/heads/task-branch");
      requireThat(unchangedHead, "unchangedHead").isEqualTo(newerHead);
      requireThat(result.stderr(), "errorOutput").contains("fast-forward");
      TestUtils.assertPlainText(result.stdout(), result.stderr());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
    }
  }

  /**
   * Verifies that {@code --force} allows a non-fast-forward branch-tip update.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void forceAllowsNonFastForwardUpdate() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try
    {
      writeAndCommit(repoDir, "first.txt", "first", "first commit");
      String newerHead = git(repoDir, "rev-parse", "HEAD");
      String olderHead = git(repoDir, "rev-parse", "HEAD~1");

      git(repoDir, "branch", "task-branch", newerHead);

      RunResult result = runUpdateBranch(repoDir, "--force", "task-branch", olderHead);

      requireThat(result.exitCode(), "exitCode").isEqualTo(0);
      String updatedHead = git(repoDir, "rev-parse", "refs/heads/task-branch");
      requireThat(updatedHead, "updatedHead").isEqualTo(olderHead);
      TestUtils.assertPlainText(result.stdout(), result.stderr());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
    }
  }

  /**
   * Verifies that a missing local branch is created directly without requiring a fast-forward check.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void missingBranchIsCreated() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try
    {
      String targetHead = git(repoDir, "rev-parse", "HEAD");

      RunResult result = runUpdateBranch(repoDir, "new-branch", targetHead);

      requireThat(result.exitCode(), "exitCode").isEqualTo(0);
      String createdHead = git(repoDir, "rev-parse", "refs/heads/new-branch");
      requireThat(createdHead, "createdHead").isEqualTo(targetHead);
      TestUtils.assertPlainText(result.stdout(), result.stderr());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
    }
  }

  /**
   * Verifies that invalid arguments fail and emit usage text.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void invalidArgumentsFailWithUsage() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try
    {
      RunResult missingArgs = runUpdateBranch(repoDir, "only-branch");
      requireThat(missingArgs.exitCode(), "missingArgsExitCode").isNotEqualTo(0);
      requireThat(missingArgs.stderr(), "missingArgsError").contains("Usage:");
      TestUtils.assertPlainText(missingArgs.stdout(), missingArgs.stderr());

      RunResult unknownFlag = runUpdateBranch(repoDir, "--bogus", "branch", "hash");
      requireThat(unknownFlag.exitCode(), "unknownFlagExitCode").isNotEqualTo(0);
      requireThat(unknownFlag.stderr(), "unknownFlagError").contains("Usage:");
      TestUtils.assertPlainText(unknownFlag.stdout(), unknownFlag.stderr());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
    }
  }

  /**
   * Verifies that invalid inputs fail without mutating an existing branch.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void invalidInputsDoNotMutateBranch() throws IOException
  {
    Path repoDir = TestUtils.createTempGitRepo("main");
    try
    {
      String originalHead = git(repoDir, "rev-parse", "HEAD");
      git(repoDir, "branch", "task-branch", originalHead);

      RunResult duplicateForce = runUpdateBranch(repoDir, "--force", "--force", "task-branch",
        originalHead);
      requireThat(duplicateForce.exitCode(), "duplicateForceExitCode").isNotEqualTo(0);
      requireThat(duplicateForce.stderr(), "duplicateForceError").contains("Duplicate flag");
      TestUtils.assertPlainText(duplicateForce.stdout(), duplicateForce.stderr());

      RunResult bogusTarget = runUpdateBranch(repoDir, "task-branch", "not-a-commit");
      requireThat(bogusTarget.exitCode(), "bogusTargetExitCode").isNotEqualTo(0);
      requireThat(bogusTarget.stderr(), "bogusTargetError").contains("Invalid target commit");
      TestUtils.assertPlainText(bogusTarget.stdout(), bogusTarget.stderr());

      String unchangedHead = git(repoDir, "rev-parse", "refs/heads/task-branch");
      requireThat(unchangedHead, "unchangedHead").isEqualTo(originalHead);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(repoDir);
    }
  }

  private static void writeAndCommit(Path repoDir, String fileName, String content, String message)
    throws IOException
  {
    Files.writeString(repoDir.resolve(fileName), content, StandardCharsets.UTF_8);
    TestUtils.runGit(repoDir, "add", fileName);
    TestUtils.runGit(repoDir, "commit", "-m", message);
  }

  private static String git(Path repoDir, String... args) throws IOException
  {
    return TestUtils.runGitCommandWithOutput(repoDir, args).strip();
  }

  private static RunResult runUpdateBranch(Path repoDir, String... args)
  {
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8);
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
    PrintStream err = new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8);

    int exitCode = UpdateBranch.run(args, out, err, repoDir);
    return new RunResult(exitCode,
      stdoutBuffer.toString(StandardCharsets.UTF_8).strip(),
      stderrBuffer.toString(StandardCharsets.UTF_8).strip());
  }

  private record RunResult(int exitCode, String stdout, String stderr)
  {
  }
}
