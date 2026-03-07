/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.FileWriteHandler;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.ReadHandler;
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
 * Tests create temp directories mimicking the external CAT storage structure:
 * {@code {claudeConfigDir}/projects/{encodedProjectDir}/cat/locks/{issue_id}.lock} and
 * {@code {claudeConfigDir}/projects/{encodedProjectDir}/cat/worktrees/{issue_id}/}.
 * <p>
 * The {@code TestJvmScope(projectDir, projectDir)} constructor sets {@code configDir = projectDir},
 * so external paths resolve relative to {@code projectDir}. Lock and worktree files are created
 * via {@link JvmScope#getProjectCatDir()} to stay consistent with what the production code looks up.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class EnforceWorktreePathIsolationTest
{
  private static final String SESSION_ID = "12345678-1234-1234-1234-123456789012";
  private static final String ISSUE_ID = "2.1-test-task";

  /**
   * Writes a lock file for the given session and issue ID using the scope's project CAT directory.
   *
   * @param scope the JVM scope providing the lock directory path
   * @param issueId the issue identifier (becomes the lock filename stem)
   * @param sessionId the session ID to embed in the lock content
   * @throws IOException if the lock file cannot be written
   */
  private static void writeLockFile(JvmScope scope, String issueId, String sessionId) throws IOException
  {
    Path lockDir = scope.getProjectCatDir().resolve("locks");
    Files.createDirectories(lockDir);
    String content = """
      {"session_id": "%s", "worktrees": {}, "created_at": 1000000, "created_iso": "2026-01-01T00:00:00Z"}
      """.formatted(sessionId);
    Files.writeString(lockDir.resolve(issueId + ".lock"), content);
  }

  /**
   * Creates the worktree directory for the given issue ID using the scope's project CAT directory.
   *
   * @param scope the JVM scope providing the worktree base path
   * @param issueId the issue identifier
   * @return the created worktree directory path
   * @throws IOException if the directory cannot be created
   */
  private static Path createWorktreeDir(JvmScope scope, String issueId) throws IOException
  {
    Path worktreeDir = scope.getProjectCatDir().resolve("worktrees").resolve(issueId);
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
      writeLockFile(scope, ISSUE_ID, SESSION_ID);

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
      writeLockFile(scope, ISSUE_ID, SESSION_ID);
      Path worktreeDir = createWorktreeDir(scope, ISSUE_ID);

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
      writeLockFile(scope, ISSUE_ID, SESSION_ID);
      Path worktreeDir = createWorktreeDir(scope, ISSUE_ID);
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
      writeLockFile(scope, ISSUE_ID, SESSION_ID);
      createWorktreeDir(scope, ISSUE_ID);

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
      writeLockFile(scope, ISSUE_ID, SESSION_ID);
      createWorktreeDir(scope, ISSUE_ID);

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
      writeLockFile(scope, ISSUE_ID, SESSION_ID);
      Path worktreeDir = createWorktreeDir(scope, ISSUE_ID);
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

  /**
   * Verifies that writing to a path outside both the worktree and the main workspace (e.g., /tmp/)
   * is allowed when a worktree context is active.
   * <p>
   * Agents may legitimately write to temporary files outside the project. The hook should only
   * block writes to the main workspace, not all writes outside the worktree.
   */
  @Test
  public void fileOutsideWorkspaceIsAllowed() throws IOException
  {
    Path projectDir = Files.createTempDirectory("ewpi-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(scope, ISSUE_ID, SESSION_ID);
      createWorktreeDir(scope, ISSUE_ID);
      Path tmpFile = Files.createTempDirectory("test-outside-").resolve("file.txt");

      // Verify the test path is actually outside the project root (precondition check)
      Path normalizedProject = projectDir.toAbsolutePath().normalize();
      Path normalizedTmp = tmpFile.toAbsolutePath().normalize();
      requireThat(normalizedTmp.startsWith(normalizedProject), "tmpFileInsideProjectDir").isFalse();

      EnforceWorktreePathIsolation handler = new EnforceWorktreePathIsolation(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", tmpFile.toString());

      FileWriteHandler.Result result = handler.check(input, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that the error message for a blocked workspace file includes the corrected
   * worktree path suggestion.
   * <p>
   * When a write targets the main workspace, the error must include a helpful suggestion
   * for the corrected path inside the worktree.
   */
  @Test
  public void fileInsideWorkspaceShowsCorrectedPathSuggestion() throws IOException
  {
    Path projectDir = Files.createTempDirectory("ewpi-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(scope, ISSUE_ID, SESSION_ID);
      Path worktreeDir = createWorktreeDir(scope, ISSUE_ID);
      Path mainWorkspaceFile = projectDir.resolve("plugin/important.py");

      EnforceWorktreePathIsolation handler = new EnforceWorktreePathIsolation(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", mainWorkspaceFile.toString());

      FileWriteHandler.Result result = handler.check(input, SESSION_ID);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Use the corrected worktree path");
      // The corrected path should be relative to the worktree and resolve to a valid path
      requireThat(result.reason(), "reason").contains(worktreeDir.toAbsolutePath().normalize().toString());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that paths with ".." components are normalized before checking against the
   * workspace boundary, and writes outside the workspace are allowed.
   * <p>
   * A path like {@code projectDir + "/../tmp/file"} normalizes to outside the project,
   * so the write should be allowed even when a worktree is active.
   */
  @Test
  public void pathWithDotsNormalizedOutsideWorkspaceIsAllowed() throws IOException
  {
    Path projectDir = Files.createTempDirectory("ewpi-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(scope, ISSUE_ID, SESSION_ID);
      createWorktreeDir(scope, ISSUE_ID);
      // Create a path that looks like it goes outside: projectDir/../outside-tmp/file.txt
      Path outsidePath = projectDir.resolve("..").resolve("outside-tmp-" + System.nanoTime()).resolve("file.txt");

      EnforceWorktreePathIsolation handler = new EnforceWorktreePathIsolation(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", outsidePath.toString());

      FileWriteHandler.Result result = handler.check(input, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  // ============================================================================
  // Read interception tests
  // ============================================================================

  /**
   * Verifies that reading a file inside the worktree is allowed when a lock and worktree exist.
   * <p>
   * The Read tool should be permitted to access files within the active worktree.
   */
  @Test
  public void readInsideWorktreeIsAllowed() throws IOException
  {
    Path projectDir = Files.createTempDirectory("ewpi-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(scope, ISSUE_ID, SESSION_ID);
      Path worktreeDir = createWorktreeDir(scope, ISSUE_ID);

      EnforceWorktreePathIsolation handler = new EnforceWorktreePathIsolation(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", worktreeDir.resolve("plugin/test.py").toString());

      ReadHandler.Result result = handler.check("Read", input, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that reading a file outside the worktree (in the main workspace) is blocked
   * when a lock and worktree exist.
   * <p>
   * Agents in a worktree must not read stale project-root files — they must read through
   * the worktree path to get the correct version of the file.
   */
  @Test
  public void readOutsideWorktreeIsBlocked() throws IOException
  {
    Path projectDir = Files.createTempDirectory("ewpi-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(scope, ISSUE_ID, SESSION_ID);
      createWorktreeDir(scope, ISSUE_ID);
      Path mainWorkspaceFile = projectDir.resolve("plugin/important.py");

      EnforceWorktreePathIsolation handler = new EnforceWorktreePathIsolation(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", mainWorkspaceFile.toString());

      ReadHandler.Result result = handler.check("Read", input, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Worktree isolation violation");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that Glob tool calls are NOT blocked by Read interception.
   * <p>
   * Glob uses a {@code pattern} field (not {@code file_path}) and must not be subject to
   * the isolation check even when its pattern string happens to match a path inside the project.
   * The check must be skipped because the Glob tool has different input semantics.
   */
  @Test
  public void globToolIsNotBlocked() throws IOException
  {
    Path projectDir = Files.createTempDirectory("ewpi-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(scope, ISSUE_ID, SESSION_ID);
      createWorktreeDir(scope, ISSUE_ID);

      EnforceWorktreePathIsolation handler = new EnforceWorktreePathIsolation(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      // Glob uses 'pattern', not 'file_path'; a path that would be blocked for Read
      // must be allowed for Glob because Glob is excluded from the isolation check
      input.put("pattern", projectDir.resolve("plugin/**/*.py").toString());

      ReadHandler.Result result = handler.check("Glob", input, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that Grep tool calls are NOT blocked by Read interception.
   * <p>
   * Grep uses a {@code pattern} field and a {@code path} field, not {@code file_path}.
   * The check must be skipped because Grep is excluded from the isolation check.
   */
  @Test
  public void grepToolIsNotBlocked() throws IOException
  {
    Path projectDir = Files.createTempDirectory("ewpi-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(scope, ISSUE_ID, SESSION_ID);
      createWorktreeDir(scope, ISSUE_ID);

      EnforceWorktreePathIsolation handler = new EnforceWorktreePathIsolation(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      // Grep uses 'pattern' and 'path', not 'file_path'
      input.put("pattern", "someFunction");
      input.put("path", projectDir.resolve("plugin").toString());

      ReadHandler.Result result = handler.check("Grep", input, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that reading with no lock file is always allowed.
   * <p>
   * No active worktree means no isolation constraint for reads.
   */
  @Test
  public void readWithNoLockIsAllowed() throws IOException
  {
    Path projectDir = Files.createTempDirectory("ewpi-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      // No lock file created
      EnforceWorktreePathIsolation handler = new EnforceWorktreePathIsolation(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", projectDir.resolve("plugin/test.py").toString());

      ReadHandler.Result result = handler.check("Read", input, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that the Read error message includes the corrected worktree path suggestion.
   * <p>
   * When a read targets the main workspace, the error must include the corrected path
   * inside the worktree.
   */
  @Test
  public void readBlockedMessageShowsCorrectedPath() throws IOException
  {
    Path projectDir = Files.createTempDirectory("ewpi-test-");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      writeLockFile(scope, ISSUE_ID, SESSION_ID);
      Path worktreeDir = createWorktreeDir(scope, ISSUE_ID);
      Path mainWorkspaceFile = projectDir.resolve("plugin/important.py");

      EnforceWorktreePathIsolation handler = new EnforceWorktreePathIsolation(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", mainWorkspaceFile.toString());

      ReadHandler.Result result = handler.check("Read", input, null, SESSION_ID);

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Use the corrected worktree path");
      requireThat(result.reason(), "reason").contains(worktreeDir.toAbsolutePath().normalize().toString());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }
}
