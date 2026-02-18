/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.FileWriteHandler;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.write.EnforceWorktreePathIsolation;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for EnforceWorktreePathIsolation hook.
 * <p>
 * Tests verify that the handler uses session ID-based lock file lookup to determine whether an
 * edit is permitted. When no lock file matches the session, all edits are allowed. When a lock
 * file matches and the worktree directory exists, only edits within the worktree are allowed.
 * <p>
 * Tests create temp directories mimicking the lock/worktree structure:
 * {@code tempDir/.claude/cat/locks/{issue_id}.lock} and
 * {@code tempDir/.claude/cat/worktrees/{issue_id}/}.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class EnforceWorktreePathIsolationTest
{
  private static final String SESSION_ID = "12345678-1234-1234-1234-123456789012";
  private static final String ISSUE_ID = "2.1-test-task";

  /**
   * Writes a lock file for the given session and issue ID under {@code projectDir}.
   *
   * @param projectDir the project root
   * @param issueId the issue identifier (becomes the lock filename stem)
   * @param sessionId the session ID to embed in the lock content
   * @throws IOException if the lock file cannot be written
   */
  private static void writeLockFile(Path projectDir, String issueId, String sessionId) throws IOException
  {
    Path lockDir = projectDir.resolve(".claude").resolve("cat").resolve("locks");
    Files.createDirectories(lockDir);
    String content = """
      {"session_id": "%s", "created_at": 1000000, "worktree": "", "created_iso": "2026-01-01T00:00:00Z"}
      """.formatted(sessionId);
    Files.writeString(lockDir.resolve(issueId + ".lock"), content);
  }

  /**
   * Creates the worktree directory for the given issue ID under {@code projectDir}.
   *
   * @param projectDir the project root
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
   * Verifies that edits are allowed when no lock file matches the session ID.
   * <p>
   * No lock files exist, so the handler has no worktree context and must allow all edits.
   */
  @Test
  public void noLockFileForSession() throws IOException
  {
    Path projectDir = Files.createTempDirectory("ewpi-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      EnforceWorktreePathIsolation handler = new EnforceWorktreePathIsolation(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", projectDir.resolve("plugin/test.py").toString());

      FileWriteHandler.Result result = handler.check(input, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that edits are allowed when a lock file exists but the worktree directory does not.
   * <p>
   * The lock was acquired before the worktree was set up, so there is no constraint yet.
   */
  @Test
  public void lockExistsButWorktreeNotCreated() throws IOException
  {
    Path projectDir = Files.createTempDirectory("ewpi-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);

      EnforceWorktreePathIsolation handler = new EnforceWorktreePathIsolation(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", projectDir.resolve("plugin/test.py").toString());

      FileWriteHandler.Result result = handler.check(input, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that editing a file inside the worktree is allowed when a lock and worktree exist.
   * <p>
   * The lock maps the session to the issue, and the file path falls within the worktree directory.
   */
  @Test
  public void fileInsideWorktreeIsAllowed() throws IOException
  {
    Path projectDir = Files.createTempDirectory("ewpi-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      Path worktreeDir = createWorktreeDir(projectDir, ISSUE_ID);

      EnforceWorktreePathIsolation handler = new EnforceWorktreePathIsolation(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", worktreeDir.resolve("plugin/test.py").toString());

      FileWriteHandler.Result result = handler.check(input, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that editing a file outside the worktree is blocked when a lock and worktree exist.
   * <p>
   * The file targets the project root instead of the worktree directory. The error message must
   * include the worktree path and the offending file path.
   */
  @Test
  public void fileOutsideWorktreeIsBlocked() throws IOException
  {
    Path projectDir = Files.createTempDirectory("ewpi-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      Path worktreeDir = createWorktreeDir(projectDir, ISSUE_ID);
      Path offendingFile = projectDir.resolve("plugin/test.py");

      EnforceWorktreePathIsolation handler = new EnforceWorktreePathIsolation(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", offendingFile.toString());

      FileWriteHandler.Result result = handler.check(input, SESSION_ID);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Worktree isolation violation");
      requireThat(result.reason(), "reason").contains(worktreeDir.toAbsolutePath().normalize().toString());
      requireThat(result.reason(), "reason").contains(offendingFile.toAbsolutePath().normalize().toString());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that a missing file_path field is allowed regardless of lock state.
   * <p>
   * When the tool input has no file_path, there is nothing to check.
   */
  @Test
  public void missingFilePathIsAllowed() throws IOException
  {
    Path projectDir = Files.createTempDirectory("ewpi-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      createWorktreeDir(projectDir, ISSUE_ID);

      EnforceWorktreePathIsolation handler = new EnforceWorktreePathIsolation(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      // No file_path field

      FileWriteHandler.Result result = handler.check(input, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that an empty file_path field is allowed regardless of lock state.
   * <p>
   * An empty path cannot be validated against the worktree boundary.
   */
  @Test
  public void emptyFilePathIsAllowed() throws IOException
  {
    Path projectDir = Files.createTempDirectory("ewpi-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      createWorktreeDir(projectDir, ISSUE_ID);

      EnforceWorktreePathIsolation handler = new EnforceWorktreePathIsolation(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", "");

      FileWriteHandler.Result result = handler.check(input, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that a file targeting the project root (main workspace) is blocked when a worktree
   * context is active.
   * <p>
   * Agents assigned to a worktree must not edit files in the main project root. This simulates
   * an agent using an absolute project-root path while working in a worktree.
   */
  @Test
  public void fileTargetingMainWorkspaceIsBlocked() throws IOException
  {
    Path projectDir = Files.createTempDirectory("ewpi-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(projectDir, ISSUE_ID, SESSION_ID);
      Path worktreeDir = createWorktreeDir(projectDir, ISSUE_ID);
      Path mainWorkspaceFile = projectDir.resolve("plugin/important.py");

      EnforceWorktreePathIsolation handler = new EnforceWorktreePathIsolation(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", mainWorkspaceFile.toString());

      FileWriteHandler.Result result = handler.check(input, SESSION_ID);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Worktree isolation violation");
      requireThat(result.reason(), "reason").contains(worktreeDir.toAbsolutePath().normalize().toString());
      requireThat(result.reason(), "reason").contains(mainWorkspaceFile.toAbsolutePath().normalize().toString());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }
}
