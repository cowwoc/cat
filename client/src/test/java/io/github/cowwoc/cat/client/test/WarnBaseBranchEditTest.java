/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.FileWriteHandler;
import io.github.cowwoc.cat.claude.hook.write.WarnBaseBranchEdit;
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
 * {@link io.github.cowwoc.cat.claude.hook.WorktreeContext#forSession} to detect worktree path bypass.
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
   * Verifies that edits to files matching allowed patterns are permitted without warning.
   * <p>
   * index.json, plan.md, and similar orchestration files must always be allowed even when
   * editing on a base branch.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void allowedPatternIsNotWarned() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("wbbe-test-");
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, pluginRoot, projectPath))
    {
      WarnBaseBranchEdit handler = new WarnBaseBranchEdit(scope);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", projectPath.resolve(".cat/issues/my-issue/index.json").toString());

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
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, pluginRoot, projectPath))
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
    try (TestClaudeHook scope = new TestClaudeHook(mainRepo, pluginRoot, mainRepo))
    {
      // Create worktree and lock so the handler sees an active worktree context
      Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);

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
    try (TestClaudeHook scope = new TestClaudeHook(mainRepo, pluginRoot, mainRepo))
    {
      // Create a real git worktree so branch detection works
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);
      Path worktree = TestUtils.createWorktree(mainRepo, worktreesDir, ISSUE_ID);
      TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);

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
    try (TestClaudeHook scope = new TestClaudeHook(projectPath, pluginRoot, projectPath))
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
