/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.bash.BlockWorktreeIsolationViolation;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link BlockWorktreeIsolationViolation}.
 * <p>
 * Tests verify that the handler blocks Bash file-write commands targeting paths inside
 * the project directory but outside the active worktree, while allowing writes to paths
 * inside the worktree, outside the project directory, or when no session lock exists.
 * <p>
 * Each test is self-contained with its own temporary directory structure.
 */
public final class BlockWorktreeIsolationViolationTest
{
  private static final String SESSION_ID = "12345678-1234-1234-1234-123456789012";
  private static final String ISSUE_ID = "2.1-test-task";

  /**
   * Creates a lock file associating {@code sessionId} with {@code issueId} under {@code projectDir}.
   *
   * @param projectDir the project root directory
   * @param issueId the issue identifier (becomes the lock filename stem)
   * @param sessionId the session ID to embed in the lock content
   * @throws IOException if the lock file cannot be written
   */
  private static void writeLockFile(Path projectDir, String issueId, String sessionId) throws IOException
  {
    Path lockDir = projectDir.resolve(".claude").resolve("cat").resolve("locks");
    Files.createDirectories(lockDir);
    String content = """
      {"session_id": "%s", "worktrees": {}, "created_at": 1000000, "created_iso": "2026-01-01T00:00:00Z"}
      """.formatted(sessionId);
    Files.writeString(lockDir.resolve(issueId + ".lock"), content);
  }

  /**
   * Creates the worktree directory for the given issue ID under {@code projectDir}.
   *
   * @param projectDir the project root directory
   * @param issueId the issue identifier
   * @return the created worktree directory path
   * @throws IOException if the directory cannot be created
   */
  private static Path createWorktreeDir(Path projectDir, String issueId) throws IOException
  {
    Path worktreeDir = projectDir.resolve(".claude").resolve("cat").resolve("worktrees").resolve(issueId);
    Files.createDirectories(worktreeDir);
    return worktreeDir;
  }

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
    Path projectDir = Files.createTempDirectory("bwiv-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      String outsidePath = projectDir.resolve("plugin/file.txt").toString();
      String command = "echo \"text\" > " + outsidePath;

      BashHandler.Result result = handler.check(command, projectDir.toString(), null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = Files.createTempDirectory("bwiv-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      String outsidePath = projectDir.resolve("plugin/file.txt").toString();
      String command = "echo \"text\" > " + outsidePath;

      BashHandler.Result result = handler.check(command, projectDir.toString(), null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = Files.createTempDirectory("bwiv-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      Path worktreeDir = createWorktreeDir(projectDir, ISSUE_ID);
      String insidePath = worktreeDir.resolve("plugin/file.txt").toString();
      String command = "echo \"text\" > " + insidePath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(command, projectDir.toString(), null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = Files.createTempDirectory("bwiv-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      Path worktreeDir = createWorktreeDir(projectDir, ISSUE_ID);
      Path outsidePath = projectDir.resolve("plugin/file.txt");
      String command = "echo \"text\" > " + outsidePath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(command, projectDir.toString(), null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Worktree isolation violation");
      requireThat(result.reason(), "reason").contains(worktreeDir.toAbsolutePath().normalize().toString());
      requireThat(result.reason(), "reason").contains(outsidePath.toAbsolutePath().normalize().toString());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = Files.createTempDirectory("bwiv-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      createWorktreeDir(projectDir, ISSUE_ID);
      Path outsidePath = projectDir.resolve("plugin/file.txt");
      String command = "echo \"text\" >> " + outsidePath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(command, projectDir.toString(), null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Worktree isolation violation");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = Files.createTempDirectory("bwiv-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      createWorktreeDir(projectDir, ISSUE_ID);
      Path outsidePath = projectDir.resolve("plugin/file.txt");
      String command = "cat source.txt | tee " + outsidePath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(command, projectDir.toString(), null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Worktree isolation violation");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = Files.createTempDirectory("bwiv-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      createWorktreeDir(projectDir, ISSUE_ID);
      Path outsidePath = projectDir.resolve("plugin/file.txt");
      String command = "echo \"text\" | tee -a " + outsidePath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(command, projectDir.toString(), null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Worktree isolation violation");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = Files.createTempDirectory("bwiv-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      Path worktreeDir = createWorktreeDir(projectDir, ISSUE_ID);
      String insidePath = worktreeDir.resolve("plugin/file.txt").toString();
      String command = "cat source.txt | tee " + insidePath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(command, projectDir.toString(), null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = Files.createTempDirectory("bwiv-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      createWorktreeDir(projectDir, ISSUE_ID);
      Path sourcePath = Path.of("/tmp/source.txt");
      Path destPath = projectDir.resolve("plugin/file.txt");
      String command = "cp " + sourcePath + " " + destPath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(command, projectDir.toString(), null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Worktree isolation violation");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = Files.createTempDirectory("bwiv-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      Path worktreeDir = createWorktreeDir(projectDir, ISSUE_ID);
      Path sourcePath = Path.of("/tmp/source.txt");
      Path destPath = worktreeDir.resolve("plugin/file.txt");
      String command = "cp " + sourcePath + " " + destPath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(command, projectDir.toString(), null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = Files.createTempDirectory("bwiv-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      createWorktreeDir(projectDir, ISSUE_ID);
      Path sourcePath = Path.of("/tmp/source.txt");
      Path destPath = projectDir.resolve("plugin/file.txt");
      String command = "mv " + sourcePath + " " + destPath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(command, projectDir.toString(), null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Worktree isolation violation");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = Files.createTempDirectory("bwiv-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      Path worktreeDir = createWorktreeDir(projectDir, ISSUE_ID);
      Path sourcePath = Path.of("/tmp/source.txt");
      Path destPath = worktreeDir.resolve("plugin/file.txt");
      String command = "mv " + sourcePath + " " + destPath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(command, projectDir.toString(), null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = Files.createTempDirectory("bwiv-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      createWorktreeDir(projectDir, ISSUE_ID);
      String command = "echo \"text\" > /tmp/output.txt";

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(command, projectDir.toString(), null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = Files.createTempDirectory("bwiv-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      createWorktreeDir(projectDir, ISSUE_ID);
      // Working directory is projectDir; relative path resolves to projectDir/plugin/file.txt
      String command = "echo \"text\" > plugin/file.txt";

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(command, projectDir.toString(), null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Worktree isolation violation");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = Files.createTempDirectory("bwiv-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      createWorktreeDir(projectDir, ISSUE_ID);
      Path outsidePath = projectDir.resolve("plugin/file.txt");
      String command = "echo \"text\" | tee --append " + outsidePath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(command, projectDir.toString(), null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Worktree isolation violation");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = Files.createTempDirectory("bwiv-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      Path worktreeDir = createWorktreeDir(projectDir, ISSUE_ID);
      String insidePath = worktreeDir.resolve("plugin/file1.txt").toString();
      Path outsidePath = projectDir.resolve("plugin/file2.txt");
      String command = "echo x | tee " + insidePath + " " + outsidePath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(command, projectDir.toString(), null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Worktree isolation violation");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = Files.createTempDirectory("bwiv-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      Path worktreeDir = createWorktreeDir(projectDir, ISSUE_ID);
      String insidePath1 = worktreeDir.resolve("plugin/file1.txt").toString();
      String insidePath2 = worktreeDir.resolve("plugin/file2.txt").toString();
      String command = "echo x | tee " + insidePath1 + " " + insidePath2;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(command, projectDir.toString(), null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that a redirect targeting a variable-expanded path using {@code $} is blocked.
   * <p>
   * Variable-expanded paths cannot be verified statically, so they are conservatively blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void variableExpansionDollarSignIsBlocked() throws IOException
  {
    Path projectDir = Files.createTempDirectory("bwiv-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      createWorktreeDir(projectDir, ISSUE_ID);
      String command = "echo \"text\" > $CLAUDE_PROJECT_DIR/plugin/file.txt";

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      Path worktreeDir = projectDir.resolve(".claude").resolve("cat").resolve("worktrees").resolve(ISSUE_ID);
      BashHandler.Result result = handler.check(command, projectDir.toString(), null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("variable-expanded");
      requireThat(result.reason(), "reason").contains(
        worktreeDir.toAbsolutePath().normalize().toString());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that a redirect targeting a backtick-expanded path is blocked.
   * <p>
   * Backtick-expanded paths cannot be verified statically, so they are conservatively blocked.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void backtickExpansionIsBlocked() throws IOException
  {
    Path projectDir = Files.createTempDirectory("bwiv-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      createWorktreeDir(projectDir, ISSUE_ID);
      String command = "echo \"text\" > `echo /some/path`";

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      Path worktreeDir = projectDir.resolve(".claude").resolve("cat").resolve("worktrees").resolve(ISSUE_ID);
      BashHandler.Result result = handler.check(command, projectDir.toString(), null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("variable-expanded");
      requireThat(result.reason(), "reason").contains(
        worktreeDir.toAbsolutePath().normalize().toString());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = Files.createTempDirectory("bwiv-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      createWorktreeDir(projectDir, ISSUE_ID);
      Path outsidePath = projectDir.resolve("plugin/path with spaces/file.txt");
      String command = "echo \"text\" > \"" + outsidePath + "\"";

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(command, projectDir.toString(), null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Worktree isolation violation");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = Files.createTempDirectory("bwiv-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      createWorktreeDir(projectDir, ISSUE_ID);
      String command = "cat " + projectDir.resolve("plugin/file.txt") + " | grep pattern";

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(command, projectDir.toString(), null, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that the error message contains the corrected worktree path.
   * <p>
   * When blocking a write to {@code projectDir/plugin/file.txt}, the corrected path should be
   * {@code worktreeDir/plugin/file.txt}.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void blockedMessageContainsCorrectedPath() throws IOException
  {
    Path projectDir = Files.createTempDirectory("bwiv-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      Path worktreeDir = createWorktreeDir(projectDir, ISSUE_ID);
      Path outsidePath = projectDir.resolve("plugin/file.txt");
      String command = "echo \"text\" > " + outsidePath;

      BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope);
      BashHandler.Result result = handler.check(command, projectDir.toString(), null, null, SESSION_ID);

      Path expectedCorrected = worktreeDir.resolve("plugin/file.txt").toAbsolutePath().normalize();
      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains(expectedCorrected.toString());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }
}
