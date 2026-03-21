/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.TaskHandler;
import io.github.cowwoc.cat.hooks.task.EnforceCommitBeforeSubagentSpawn;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Tests for {@link EnforceCommitBeforeSubagentSpawn}.
 * <p>
 * Tests verify that spawning a cat:work-execute subagent is blocked when the session's worktree
 * has uncommitted changes, and allowed when the worktree is clean or no worktree is active.
 */
public final class EnforceCommitBeforeSubagentSpawnTest
{
  /**
   * Creates a tool input JSON node for spawning a cat:work-execute subagent.
   *
   * @param mapper the JSON mapper
   * @return the tool input JSON node
   * @throws IOException if the JSON cannot be parsed
   */
  private static JsonNode createWorkExecuteToolInput(JsonMapper mapper) throws IOException
  {
    return mapper.readTree("""
      {"subagent_type": "cat:work-execute"}""");
  }

  /**
   * Creates a tool input JSON node for spawning a non-execute subagent (e.g., cat:work-merge).
   *
   * @param mapper the JSON mapper
   * @return the tool input JSON node
   * @throws IOException if the JSON cannot be parsed
   */
  private static JsonNode createOtherToolInput(JsonMapper mapper) throws IOException
  {
    return mapper.readTree("""
      {"subagent_type": "cat:work-merge"}""");
  }

  /**
   * Creates a worktree directory linked to the main git repo.
   *
   * @param mainRepo the main repository
   * @param scope the JVM scope
   * @param issueId the issue ID
   * @return the path to the worktree
   * @throws IOException if worktree creation fails
   */
  private static Path createWorktree(Path mainRepo, JvmScope scope, String issueId) throws IOException
  {
    Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
    Files.createDirectories(worktreesDir);
    return TestUtils.createWorktree(mainRepo, worktreesDir, issueId);
  }

  /**
   * Verifies that non-work-execute subagent types are allowed without checking git status.
   * <p>
   * The hook must allow spawning of subagents other than cat:work-execute unconditionally.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void nonWorkExecuteSubagentIsAllowed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("enforce-commit-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      EnforceCommitBeforeSubagentSpawn handler = new EnforceCommitBeforeSubagentSpawn(scope);
      JsonNode toolInput = createOtherToolInput(mapper);

      TaskHandler.Result result = handler.check(toolInput, UUID.randomUUID().toString(), "");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that spawning cat:work-execute is allowed when no session lock file exists.
   * <p>
   * Without an active worktree lock, there is no worktree to check — the hook must allow it.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void allowedWhenNoActiveLock() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      JsonMapper mapper = scope.getJsonMapper();
      EnforceCommitBeforeSubagentSpawn handler = new EnforceCommitBeforeSubagentSpawn(scope);
      JsonNode toolInput = createWorkExecuteToolInput(mapper);

      // No lock file created — no active worktree
      TaskHandler.Result result = handler.check(toolInput, UUID.randomUUID().toString(), "");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that spawning cat:work-execute is allowed when the worktree is clean.
   * <p>
   * A clean worktree (no uncommitted changes) must not block subagent spawn.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void allowedWhenWorktreeIsClean() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    Path worktreePath = null;
    String sessionId = UUID.randomUUID().toString();
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String issueId = "2.1-clean-issue";

      TestUtils.writeLockFile(scope, issueId, sessionId);
      worktreePath = createWorktree(mainRepo, scope, issueId);

      EnforceCommitBeforeSubagentSpawn handler = new EnforceCommitBeforeSubagentSpawn(scope);
      JsonNode toolInput = createWorkExecuteToolInput(mapper);

      // Worktree is clean — no uncommitted changes
      TaskHandler.Result result = handler.check(toolInput, sessionId, "");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      if (worktreePath != null)
        TestUtils.runGit(mainRepo, "worktree", "remove", "--force", worktreePath.toString());
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that spawning cat:work-execute is blocked when the worktree has uncommitted changes.
   * <p>
   * Uncommitted changes in the worktree must block subagent spawn, since the subagent's worktree
   * branches from HEAD and would not see those changes.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void blockedWhenWorktreeHasUncommittedChanges() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    Path worktreePath = null;
    String sessionId = UUID.randomUUID().toString();
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String issueId = "2.1-dirty-issue";

      TestUtils.writeLockFile(scope, issueId, sessionId);
      worktreePath = createWorktree(mainRepo, scope, issueId);

      // Create an uncommitted file in the worktree
      Files.writeString(worktreePath.resolve("uncommitted.txt"), "dirty change");

      EnforceCommitBeforeSubagentSpawn handler = new EnforceCommitBeforeSubagentSpawn(scope);
      JsonNode toolInput = createWorkExecuteToolInput(mapper);

      TaskHandler.Result result = handler.check(toolInput, sessionId, "");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("uncommitted");
    }
    finally
    {
      if (worktreePath != null)
        TestUtils.runGit(mainRepo, "worktree", "remove", "--force", worktreePath.toString());
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }

  /**
   * Verifies that spawning cat:work-execute is blocked when the worktree has staged but uncommitted changes.
   * <p>
   * Staged changes that are not yet committed must also block subagent spawn.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void blockedWhenWorktreeHasStagedChanges() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("v2.1");
    Path worktreePath = null;
    String sessionId = UUID.randomUUID().toString();
    try (JvmScope scope = new TestJvmScope(mainRepo, mainRepo))
    {
      JsonMapper mapper = scope.getJsonMapper();
      String issueId = "2.1-staged-issue";

      TestUtils.writeLockFile(scope, issueId, sessionId);
      worktreePath = createWorktree(mainRepo, scope, issueId);

      // Create a staged (but not committed) file in the worktree
      Files.writeString(worktreePath.resolve("staged.txt"), "staged change");
      TestUtils.runGit(worktreePath, "add", "staged.txt");

      EnforceCommitBeforeSubagentSpawn handler = new EnforceCommitBeforeSubagentSpawn(scope);
      JsonNode toolInput = createWorkExecuteToolInput(mapper);

      TaskHandler.Result result = handler.check(toolInput, sessionId, "");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("uncommitted");
    }
    finally
    {
      if (worktreePath != null)
        TestUtils.runGit(mainRepo, "worktree", "remove", "--force", worktreePath.toString());
      TestUtils.deleteDirectoryRecursively(mainRepo);
    }
  }
}
