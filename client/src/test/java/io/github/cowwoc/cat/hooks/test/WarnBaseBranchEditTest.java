/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.FileWriteHandler;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.write.WarnBaseBranchEdit;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link WarnBaseBranchEdit}.
 * <p>
 * Tests verify that the handler warns when editing source files on a base branch, and uses
 * {@link io.github.cowwoc.cat.hooks.WorktreeContext#forSession} to detect worktree path bypass.
 * <p>
 * Lock and worktree files are created via {@link JvmScope#getCatWorkPath()} to match
 * the external CAT storage location used by the production code.
 * <p>
 * Each test is self-contained with its own temporary directory structure.
 */
public final class WarnBaseBranchEditTest
{
  private static final String SESSION_ID = "12345678-1234-1234-1234-123456789012";
  private static final String ISSUE_ID = "2.1-test-task";

  /**
   * Creates a lock file associating {@code sessionId} with {@code issueId}.
   *
   * @param scope the JVM scope providing the lock directory path
   * @param issueId the issue identifier (becomes the lock filename stem)
   * @param sessionId the session ID to embed in the lock content
   * @throws IOException if the lock file cannot be written
   */
  private static void writeLockFile(JvmScope scope, String issueId, String sessionId) throws IOException
  {
    Path lockDir = scope.getCatWorkPath().resolve("locks");
    Files.createDirectories(lockDir);
    String content = """
      {"session_id": "%s", "worktrees": {}, "created_at": 1000000, "created_iso": "2026-01-01T00:00:00Z"}
      """.formatted(sessionId);
    Files.writeString(lockDir.resolve(issueId + ".lock"), content);
  }

  /**
   * Creates the worktree directory for the given issue ID.
   *
   * @param scope the JVM scope providing the worktree base path
   * @param issueId the issue identifier
   * @return the created worktree directory path
   * @throws IOException if the directory cannot be created
   */
  private static Path createWorktreeDir(JvmScope scope, String issueId) throws IOException
  {
    Path worktreeDir = scope.getCatWorkPath().resolve("worktrees").resolve(issueId);
    Files.createDirectories(worktreeDir);
    return worktreeDir;
  }

  /**
   * Verifies that edits to files matching allowed patterns are permitted without warning.
   * <p>
   * STATE.md, PLAN.md, and similar orchestration files must always be allowed even when
   * editing on a base branch.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void allowedPatternIsNotWarned() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("wbbe-test-");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      WarnBaseBranchEdit handler = new WarnBaseBranchEdit(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", projectPath.resolve(".cat/issues/my-issue/STATE.md").toString());

      FileWriteHandler.Result result = handler.check(input, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that editing source files on a base branch produces a warning.
   * <p>
   * When the current git branch is "main" (a base branch) and the file is not in the allowed
   * patterns, the handler must warn about the base branch edit.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void editOnBaseBranchProducesWarning() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("wbbe-test-");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      // No lock file — session has no active worktree (main context)
      WarnBaseBranchEdit handler = new WarnBaseBranchEdit(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      // A source file that would typically be blocked
      input.put("file_path", projectPath.resolve("SomeClass.java").toString());

      FileWriteHandler.Result result = handler.check(input, SESSION_ID);

      // Must warn (not block) about the base branch edit
      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").contains("BASE BRANCH EDIT DETECTED");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that using an absolute project path while in a worktree produces a worktree bypass warning.
   * <p>
   * The worktree bypass check runs only when the current branch is NOT a base branch. The test
   * uses a non-base branch name so the base branch check passes and the bypass check runs.
   * When the session has an active worktree lock and the file path targets the project root
   * (not the worktree), the handler must warn about the worktree path bypass.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void absoluteProjectPathInWorktreeProducesWarning() throws IOException
  {
    // Create the main repo on a non-base branch so the base branch check doesn't fire first
    Path mainRepo = TestUtils.createTempGitRepo("feature-dev");
    Path pluginRoot = Files.createTempDirectory("wbbe-test-");
    try (JvmScope scope = new TestJvmScope(mainRepo, pluginRoot))
    {
      // Create worktree and lock so the handler sees an active worktree context
      Path worktreeDir = createWorktreeDir(scope, ISSUE_ID);
      writeLockFile(scope, ISSUE_ID, SESSION_ID);

      WarnBaseBranchEdit handler = new WarnBaseBranchEdit(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      // Absolute path to main repo — this bypasses worktree isolation
      // findExistingAncestor will find mainRepo (on "feature-dev", a non-base branch)
      // so the base branch check passes, then the worktree bypass check fires
      input.put("file_path", mainRepo.resolve("plugin/SomeClass.java").toString());

      FileWriteHandler.Result result = handler.check(input, SESSION_ID);

      // Must warn about worktree path bypass
      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").contains("WORKTREE PATH BYPASS DETECTED");
      requireThat(result.reason(), "reason").contains(worktreeDir.toAbsolutePath().normalize().toString());
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that editing a file inside the worktree (on a non-base branch) produces a
   * subagent-delegation warning, not a base branch warning.
   * <p>
   * When the session has an active worktree lock and the file is inside the worktree directory,
   * the handler still warns about main-agent source edits.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void editInsideWorktreeProducesSubagentDelegationWarning() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("wbbe-test-");
    try (JvmScope scope = new TestJvmScope(mainRepo, pluginRoot))
    {
      // Create a real git worktree so branch detection works
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);
      Path worktree = TestUtils.createWorktree(mainRepo, worktreesDir, ISSUE_ID);
      writeLockFile(scope, ISSUE_ID, SESSION_ID);

      WarnBaseBranchEdit handler = new WarnBaseBranchEdit(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      // File inside the worktree — proper isolation, but main agent should delegate
      input.put("file_path", worktree.resolve("SomeClass.java").toString());

      FileWriteHandler.Result result = handler.check(input, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
      // Must contain the delegation warning, not the base branch warning
      requireThat(result.reason(), "reason").contains("MAIN AGENT SOURCE EDIT DETECTED");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that a missing file_path field is always allowed.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void missingFilePathIsAllowed() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("wbbe-test-");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      WarnBaseBranchEdit handler = new WarnBaseBranchEdit(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      // No file_path field

      FileWriteHandler.Result result = handler.check(input, SESSION_ID);

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }
}
