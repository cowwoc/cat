/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.BashHandler;

import io.github.cowwoc.cat.claude.hook.bash.BlockWorktreeIsolationViolation;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link BlockWorktreeIsolationViolation}.
 * <p>
 * Tests verify that the handler blocks Bash file-write commands targeting paths inside
 * the project directory but outside the active worktree, while allowing writes to paths
 * inside the worktree, outside the project directory, or when no session lock exists.
 * <p>
 * Lock and worktree files are created via {@link JvmScope#getCatWorkPath()} to match
 * the external CAT storage location used by the production code.
 * <p>
 * Each test is self-contained with its own temporary directory structure.
 */
public final class BlockWorktreeIsolationViolationTest
{
  private static final String SESSION_ID = "12345678-1234-1234-1234-123456789012";
  private static final String ISSUE_ID = "2.1-test-task";

  /**
   * Verifies that all commands are allowed when no lock file exists for the session.
   * <p>
   * Without a lock, the handler has no worktree context and must allow all writes.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void noLockFileForSession() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      String outsidePath = projectPath.resolve("plugin/file.txt").toString();
      String command = "echo \"text\" > " + outsidePath;

      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that commands are allowed when a lock exists but the worktree directory does not yet exist.
   * <p>
   * The lock was acquired before the worktree was set up; no isolation boundary can be enforced.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void lockExistsButWorktreeNotCreated() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      String outsidePath = projectPath.resolve("plugin/file.txt").toString();
      String command = "echo \"text\" > " + outsidePath;

      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a shell redirect ({@code >}) targeting a path inside the worktree is allowed.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void shellRedirectInsideWorktreeIsAllowed() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
      String insidePath = worktreeDir.resolve("plugin/file.txt").toString();
      String command = "echo \"text\" > " + insidePath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a shell redirect ({@code >}) targeting a path outside the worktree is blocked.
   * <p>
   * The target is inside the project directory but not the worktree. The error message must
   * contain the worktree path and the offending target path.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void shellRedirectOutsideWorktreeIsBlocked() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
      Path outsidePath = projectPath.resolve("plugin/file.txt");
      String command = "echo \"text\" > " + outsidePath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Worktree isolation violation");
      requireThat(result.reason(), "reason").contains(worktreeDir.toAbsolutePath().normalize().toString());
      requireThat(result.reason(), "reason").contains(outsidePath.toAbsolutePath().normalize().toString());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that an append redirect ({@code >>}) targeting a path outside the worktree is blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void appendRedirectOutsideWorktreeIsBlocked() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      TestUtils.createWorktreeDir(scope, ISSUE_ID);
      Path outsidePath = projectPath.resolve("plugin/file.txt");
      String command = "echo \"text\" >> " + outsidePath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Worktree isolation violation");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that {@code tee /path} targeting a path outside the worktree is blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void teeOutsideWorktreeIsBlocked() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      TestUtils.createWorktreeDir(scope, ISSUE_ID);
      Path outsidePath = projectPath.resolve("plugin/file.txt");
      String command = "cat source.txt | tee " + outsidePath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Worktree isolation violation");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that {@code tee -a /path} (append mode) targeting a path outside the worktree is blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void teeAppendOutsideWorktreeIsBlocked() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      TestUtils.createWorktreeDir(scope, ISSUE_ID);
      Path outsidePath = projectPath.resolve("plugin/file.txt");
      String command = "echo \"text\" | tee -a " + outsidePath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Worktree isolation violation");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that {@code tee /path} targeting a path inside the worktree is allowed.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void teeInsideWorktreeIsAllowed() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
      String insidePath = worktreeDir.resolve("plugin/file.txt").toString();
      String command = "cat source.txt | tee " + insidePath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that {@code cp source dest} where dest is outside the worktree is blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void cpDestinationOutsideWorktreeIsBlocked() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      TestUtils.createWorktreeDir(scope, ISSUE_ID);
      Path sourcePath = Path.of("/tmp/source.txt");
      Path destPath = projectPath.resolve("plugin/file.txt");
      String command = "cp " + sourcePath + " " + destPath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Worktree isolation violation");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that {@code cp source dest} where dest is inside the worktree is allowed.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void cpDestinationInsideWorktreeIsAllowed() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
      Path sourcePath = Path.of("/tmp/source.txt");
      Path destPath = worktreeDir.resolve("plugin/file.txt");
      String command = "cp " + sourcePath + " " + destPath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that {@code mv source dest} where dest is outside the worktree is blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void mvDestinationOutsideWorktreeIsBlocked() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      TestUtils.createWorktreeDir(scope, ISSUE_ID);
      Path sourcePath = Path.of("/tmp/source.txt");
      Path destPath = projectPath.resolve("plugin/file.txt");
      String command = "mv " + sourcePath + " " + destPath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Worktree isolation violation");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that {@code mv source dest} where dest is inside the worktree is allowed.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void mvDestinationInsideWorktreeIsAllowed() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
      Path sourcePath = Path.of("/tmp/source.txt");
      Path destPath = worktreeDir.resolve("plugin/file.txt");
      String command = "mv " + sourcePath + " " + destPath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that writing to a path outside the project directory entirely is allowed.
   * <p>
   * Writes to {@code /tmp} or other non-project paths must not be blocked, as the worktree
   * isolation check only applies to paths under the project directory.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void writeOutsideProjectDirectoryIsAllowed() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      TestUtils.createWorktreeDir(scope, ISSUE_ID);
      String command = "echo \"text\" > /tmp/output.txt";

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a redirect to a relative path outside the worktree is blocked when resolved
   * against the working directory.
   * <p>
   * A relative path like {@code plugin/file.txt} resolved against the project root lands outside
   * the worktree and should be blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void relativeRedirectOutsideWorktreeIsBlocked() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      TestUtils.createWorktreeDir(scope, ISSUE_ID);
      // Working directory is projectPath; relative path resolves to projectPath/plugin/file.txt
      String command = "echo \"text\" > plugin/file.txt";

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Worktree isolation violation");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that {@code tee --append /path} (long form) targeting a path outside the worktree is blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void teeAppendLongFormOutsideWorktreeIsBlocked() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      TestUtils.createWorktreeDir(scope, ISSUE_ID);
      Path outsidePath = projectPath.resolve("plugin/file.txt");
      String command = "echo \"text\" | tee --append " + outsidePath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Worktree isolation violation");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that {@code tee file1 file2} where the second target is outside the worktree is blocked.
   * <p>
   * Multi-target tee must check all file arguments, not just the first one.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void teeMultipleTargetsSecondTargetOutsideWorktreeIsBlocked() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
      String insidePath = worktreeDir.resolve("plugin/file1.txt").toString();
      Path outsidePath = projectPath.resolve("plugin/file2.txt");
      String command = "echo x | tee " + insidePath + " " + outsidePath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Worktree isolation violation");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that {@code tee file1 file2} where both targets are inside the worktree is allowed.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void teeMultipleTargetsBothInsideWorktreeIsAllowed() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
      String insidePath1 = worktreeDir.resolve("plugin/file1.txt").toString();
      String insidePath2 = worktreeDir.resolve("plugin/file2.txt").toString();
      String command = "echo x | tee " + insidePath1 + " " + insidePath2;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a redirect targeting a variable-expanded path with an unset variable is blocked.
   * <p>
   * When a variable in the redirect path is unset in the hook process environment, the path cannot
   * be expanded and must be conservatively blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void variableExpansionDollarSignIsBlocked() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
      String command = "echo \"text\" > ${BWIV_TEST_UNDEFINED_ENV_VAR}/plugin/file.txt";

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Undefined variable(s): BWIV_TEST_UNDEFINED_ENV_VAR");
      requireThat(result.reason(), "reason").contains(
        worktreeDir.toAbsolutePath().normalize().toString());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a redirect targeting a backtick-expanded path is blocked.
   * <p>
   * Backtick expressions cannot be evaluated statically, so they are conservatively blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void backtickExpansionIsBlocked() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
      String command = "echo \"text\" > `echo /some/path`";

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("backtick");
      requireThat(result.reason(), "reason").contains(
        worktreeDir.toAbsolutePath().normalize().toString());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a redirect to a double-quoted path with spaces that is outside the worktree is blocked.
   * <p>
   * Quoted paths with embedded spaces must be parsed correctly and subject to the same isolation check.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void quotedPathWithSpacesOutsideWorktreeIsBlocked() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      TestUtils.createWorktreeDir(scope, ISSUE_ID);
      Path outsidePath = projectPath.resolve("plugin/path with spaces/file.txt");
      String command = "echo \"text\" > \"" + outsidePath + "\"";

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Worktree isolation violation");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a command with no file-write patterns is allowed.
   * <p>
   * Read-only commands like {@code cat} or {@code grep} must not be blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void readOnlyCommandIsAllowed() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      TestUtils.createWorktreeDir(scope, ISSUE_ID);
      String command = "cat " + projectPath.resolve("plugin/file.txt") + " | grep pattern";

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a redirect is allowed when an environment variable expands to a path inside the
   * worktree.
   * <p>
   * When the variable reference in the redirect path resolves to the worktree directory, the write
   * is within the isolation boundary and must be permitted.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void allowsRedirectWhenEnvVarExpandsToWorktreePath() throws IOException
  {
    Path fakeHome = Files.createTempDirectory("fake-home-");
    try
    {
      Path projectPath = Files.createTempDirectory(fakeHome, "bwiv-test-");
      try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
      {
        TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
        Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
        String relativePath = fakeHome.relativize(worktreeDir.resolve("file.txt")).toString();
        String command = "echo foo > ${HOME}/" + relativePath;
        Map<String, String> env = Map.of("HOME", fakeHome.toString());

        BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope, env);
        BashHandler.Result result = handler.check(
          TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

        requireThat(result.blocked(), "blocked").isFalse();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(fakeHome);
    }
  }

  /**
   * Verifies that a redirect is allowed when a bare (no-braces) environment variable expands to a
   * path inside the worktree.
   * <p>
   * Both {@code $VAR} and {@code ${VAR}} syntax must be handled; this test exercises the bare form.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void allowsRedirectWhenBareEnvVarExpandsToWorktreePath() throws IOException
  {
    Path fakeHome = Files.createTempDirectory("fake-home-");
    try
    {
      Path projectPath = Files.createTempDirectory(fakeHome, "bwiv-test-");
      try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
      {
        TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
        Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
        String relativePath = fakeHome.relativize(worktreeDir.resolve("file.txt")).toString();
        String command = "echo foo > $HOME/" + relativePath;
        Map<String, String> env = Map.of("HOME", fakeHome.toString());

        BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope, env);
        BashHandler.Result result = handler.check(
          TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

        requireThat(result.blocked(), "blocked").isFalse();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(fakeHome);
    }
  }

  /**
   * Verifies that a redirect is blocked when an environment variable expands to a path outside the
   * worktree but inside the project directory.
   * <p>
   * Even when the variable expands successfully, the expanded path must still satisfy the worktree
   * isolation check.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void blocksRedirectWhenEnvVarExpandsOutsideWorktree() throws IOException
  {
    Path fakeHome = Files.createTempDirectory("fake-home-");
    try
    {
      Path projectPath = Files.createTempDirectory(fakeHome, "bwiv-test-");
      try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
      {
        TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
        TestUtils.createWorktreeDir(scope, ISSUE_ID);
        String relativePath = fakeHome.relativize(projectPath.resolve("plugin/file.txt")).toString();
        String command = "echo foo > ${HOME}/" + relativePath;
        Map<String, String> env = Map.of("HOME", fakeHome.toString());

        BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope, env);
        BashHandler.Result result = handler.check(
          TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

        requireThat(result.blocked(), "blocked").isTrue();
        requireThat(result.reason(), "reason").contains("isolation violation");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(fakeHome);
    }
  }

  /**
   * Verifies that a redirect to a backtick-expanded path is blocked.
   * <p>
   * Backtick expressions cannot be evaluated statically and must be conservatively blocked
   * regardless of their apparent target.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void blocksRedirectToBacktickExpression() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
      String command = "echo foo > `pwd`/file.txt";

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("backtick");
      requireThat(result.reason(), "reason").contains(worktreeDir.toString());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that the error message contains the corrected worktree path.
   * <p>
   * When blocking a write to {@code projectPath/plugin/file.txt}, the corrected path should be
   * {@code worktreeDir/plugin/file.txt}.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void blockedMessageContainsCorrectedPath() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
      Path outsidePath = projectPath.resolve("plugin/file.txt");
      String command = "echo \"text\" > " + outsidePath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      Path expectedCorrected = worktreeDir.resolve("plugin/file.txt").toAbsolutePath().normalize();
      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains(expectedCorrected.toString());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a redirect is allowed when the variable is defined as a literal path earlier
   * in the same script block and that path is inside the active worktree.
   * <p>
   * The hook must scan the script for {@code VAR="/path"} assignments and use them to resolve
   * the redirect target without relying on the process environment.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void redirectAllowedWhenVariableDefinedLiterallyInScript() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
      String insidePath = worktreeDir.resolve("plugin/file.txt").toString();
      // Embed the literal assignment in the same script block as the redirect
      String command = "OUT=\"" + insidePath + "\"\nsome-command > \"${OUT}\"";
      Map<String, String> env = Map.of();  // variable not in env — must come from script

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope, env);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a redirect is blocked when the variable is defined as a literal path earlier
   * in the same script but that path is outside the active worktree (inside the project directory).
   * <p>
   * Resolving the variable successfully does not grant permission — the resolved path must still
   * pass the worktree-isolation check.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void redirectBlockedWhenLiteralVariableResolvesOutsideWorktree() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      TestUtils.createWorktreeDir(scope, ISSUE_ID);
      Path outsidePath = projectPath.resolve("plugin/file.txt");
      String command = "OUT=\"" + outsidePath + "\"\nsome-command > \"${OUT}\"";
      Map<String, String> env = Map.of();

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope, env);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Worktree isolation violation");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a redirect remains blocked when the variable is assigned via command
   * substitution ({@code VAR=$(mktemp)}) rather than a literal value.
   * <p>
   * Command substitutions cannot be evaluated statically, so the hook must conservatively
   * block the redirect even if the variable name appears in the script.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void redirectRemainsBlockedWhenVariableAssignedViaCommandSubstitution() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      TestUtils.createWorktreeDir(scope, ISSUE_ID);
      // Command substitution: $(mktemp) cannot be statically resolved
      String command = "OUT=$(mktemp)\nsome-command > \"${OUT}\"";
      Map<String, String> env = Map.of();

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope, env);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Undefined variable(s): OUT");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a redirect to a variable assigned via {@code $(mktemp -p .cat/work/tmp)} is allowed
   * when the relative mktemp directory resolves inside the active worktree.
   * <p>
   * The working directory is set to the worktree root, so {@code .cat/work/tmp} resolves to a path
   * inside the worktree. Because mktemp will create a file inside the worktree directory, the write
   * must be permitted.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void mktempWithRelativeWorktreePathIsAllowed() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
      // Working directory is the worktree root, so .cat/work/tmp resolves inside it
      String command = "tmp=$(mktemp -p .cat/work/tmp) && echo foo > \"$tmp\"";
      Map<String, String> env = Map.of();

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope, env);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, worktreeDir.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a redirect to a variable assigned via {@code $(mktemp -p <absolutePath>)} is allowed
   * when the absolute mktemp directory is inside the active worktree.
   * <p>
   * An explicit absolute path pointing into the worktree must be recognized as safe, regardless of
   * the working directory.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void mktempWithAbsoluteWorktreePathIsAllowed() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
      String absoluteTmpDir = worktreeDir.resolve(".cat/work/tmp").toAbsolutePath().toString();
      String command = "tmp=$(mktemp -p " + absoluteTmpDir + ") && echo foo > \"$tmp\"";
      Map<String, String> env = Map.of();

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope, env);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a redirect to a variable assigned via plain {@code $(mktemp)} (no {@code -p} flag)
   * is blocked conservatively.
   * <p>
   * Without a {@code -p} directory argument, the mktemp path cannot be statically resolved, so the
   * hook cannot determine whether the resulting file will be inside the worktree. The write must be
   * blocked to prevent undetected isolation violations.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void mktempWithoutPathFlagIsBlocked() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      TestUtils.createWorktreeDir(scope, ISSUE_ID);
      // No -p flag: path is unresolvable, must be conservatively blocked
      String command = "tmp=$(mktemp) && echo foo > \"$tmp\"";
      Map<String, String> env = Map.of();

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope, env);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a redirect to a variable assigned via {@code $(mktemp -p /tmp)} is allowed when
   * the mktemp directory is outside the project directory.
   * <p>
   * The {@code /tmp} directory is not under the project root, so any file created there is outside
   * the project. Writes to paths outside the project are always permitted by this hook.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void mktempWithSystemTmpPathIsAllowed() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      TestUtils.createWorktreeDir(scope, ISSUE_ID);
      // /tmp is outside the project directory — always allowed
      String command = "tmp=$(mktemp -p /tmp) && echo foo > \"$tmp\"";
      Map<String, String> env = Map.of();

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope, env);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a redirect to a variable assigned via {@code $(mktemp -p <path>)} is blocked when
   * the mktemp directory is inside the project but outside the active worktree.
   * <p>
   * Even though the mktemp path is statically resolvable, if it falls within the project directory
   * but outside the worktree boundary, the write must still be blocked to enforce isolation.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void mktempWithProjectPathOutsideWorktreeIsBlocked() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      TestUtils.createWorktreeDir(scope, ISSUE_ID);
      // Path is inside the project but outside the worktree — must be blocked
      String absolutePluginDir = projectPath.resolve("plugin").toAbsolutePath().toString();
      String command = "tmp=$(mktemp -p " + absolutePluginDir + ") && echo foo > \"$tmp\"";
      Map<String, String> env = Map.of();

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope, env);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("isolation violation");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a redirect is allowed when the redirect target is computed via a chained
   * variable assignment — where the first variable holds a literal path and the second
   * variable appends a suffix using the first — and the resolved path is inside the worktree.
   * <p>
   * The hook must expand the chain statically (without executing the script) so that the
   * redirect target resolves to a concrete path inside the worktree.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void shellRedirectViaChainedVariablesInsideWorktreeIsAllowed() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
      String command = "WORKTREE_PATH=\"" + worktreeDir + "\"\n" +
                       "OUTPUT_FILE=\"${WORKTREE_PATH}/.cat/work/output.log\"\n" +
                       "some_command > \"${OUTPUT_FILE}\" 2>&1";
      Map<String, String> env = Map.of();  // variable not in env — must come from script

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope, env);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a redirect is blocked when the redirect target is computed via a chained
   * variable assignment — where the first variable holds a literal path OUTSIDE the worktree
   * and the second variable appends a suffix using the first.
   * <p>
   * The hook must expand the chain statically (without executing the script) so that the
   * redirect target resolves to a concrete path outside the worktree, triggering a block.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void shellRedirectViaChainedVariablesOutsideWorktreeIsBlocked() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      TestUtils.createWorktreeDir(scope, ISSUE_ID);
      // OUTSIDE_PATH is inside the project directory but outside the worktree
      String outsidePath = projectPath.resolve("plugin").toAbsolutePath().toString();
      String command = "OUTSIDE_PATH=\"" + outsidePath + "\"\n" +
                       "OUTPUT_FILE=\"${OUTSIDE_PATH}/output.log\"\n" +
                       "some_command > \"${OUTPUT_FILE}\" 2>&1";
      Map<String, String> env = Map.of();  // variable not in env — must come from script

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope, env);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("isolation violation");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that {@code tee "${WORKTREE_PATH}/..."} is allowed when {@code WORKTREE_PATH} is
   * assigned as an unquoted literal in the same script and the path is inside the active worktree.
   * <p>
   * The hook must capture unquoted assignments (no quotes around the value) and use them to
   * resolve variable references in the tee target, without raising a false-positive warning.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void teeViaUnquotedWorktreeVarIsAllowed() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
      // WORKTREE_PATH is set without quotes — must be captured by the parser
      String command = "WORKTREE_PATH=" + worktreeDir + "\n" +
                       "cmd 2>&1 | tee \"${WORKTREE_PATH}/.cat/work/output.log\"";
      Map<String, String> env = Map.of();  // variable not in env — must come from script

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope, env);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that {@code tee "${WORKTREE_PATH}/..."} is blocked when {@code WORKTREE_PATH} is
   * assigned as an unquoted literal in the same script but the path is OUTSIDE the active worktree.
   * <p>
   * Unquoted variable capture must not disable the isolation check — paths outside the worktree
   * must still be blocked even when the variable is set without quotes.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void teeViaUnquotedWorktreeVarIsBlocked() throws IOException
  {
    Path projectPath = Files.createTempDirectory("bwiv-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
    {
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
      TestUtils.createWorktreeDir(scope, ISSUE_ID);
      // outsidePath is inside the project directory but outside the worktree
      String outsidePath = projectPath.resolve("outside").toAbsolutePath().toString();
      // WORKTREE_PATH is set without quotes, pointing outside the worktree
      String command = "WORKTREE_PATH=" + outsidePath + "\n" +
                       "cmd 2>&1 | tee \"${WORKTREE_PATH}/.cat/work/output.log\"";
      Map<String, String> env = Map.of();  // variable not in env — must come from script

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope, env);
      BashHandler.Result result = handler.check(
        TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("isolation violation");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }
}
