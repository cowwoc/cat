/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.tool.ClaudeTool;
import io.github.cowwoc.cat.claude.hook.util.GitCommands;
import io.github.cowwoc.cat.claude.hook.util.TrustLevel;
import io.github.cowwoc.cat.claude.hook.util.WorkPrepare;
import io.github.cowwoc.cat.claude.hook.util.WorkPrepare.PrepareInput;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for WorkPrepare.
 * <p>
 * Tests verify JSON output contracts for all status codes: READY, NO_ISSUES, LOCKED, OVERSIZED, and ERROR.
 * Each test is self-contained with temporary git repositories to support parallel execution.
 */
public class WorkPrepareTest
{
  /**
   * Verifies that execute returns ERROR when the project has no .cat/config.json.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsErrorWhenNoCatStructure() throws IOException
  {
    Path projectPath = Files.createTempDirectory("work-prepare-test");
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("ERROR");
      requireThat(node.path("message").asString(), "message").
        contains("config.json");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that execute returns NO_ISSUES when there are no executable issues.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsNoIssuesWhenNoExecutableIssues() throws IOException
  {
    Path projectPath = createTempCatProject();
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // Create a closed issue only
      createIssue(projectPath, "2", "1", "done-feature", "closed");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("NO_ISSUES");
      requireThat(node.path("message").asString(), "message").isNotBlank();
      requireThat(node.path("closed_count").asInt(), "closedCount").isEqualTo(1);
      requireThat(node.path("total_count").asInt(), "totalCount").isEqualTo(1);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that execute returns OVERSIZED when the plan.md estimates too many tokens.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsOversizedWhenTokensExceedLimit() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "huge-feature", "open");
      createOversizedPlan(projectPath, "2", "1", "huge-feature");

      // Commit the issue so it's visible in the git repo
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add oversized issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("OVERSIZED");
      requireThat(node.path("issue_id").asString(), "issueId").isEqualTo("2.1-huge-feature");
      requireThat(node.path("estimated_tokens").asInt(), "estimatedTokens").isGreaterThan(160_000);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that execute returns CORRUPT when an issue directory has index.json but no plan.md.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsCorruptWhenIndexJsonExistsButNoPlanMd() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // Create issue directory with only index.json (no plan.md) — simulates a corrupt directory
      Path issueDir = projectPath.resolve(".cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("corrupt-issue");
      Files.createDirectories(issueDir);

      Files.writeString(issueDir.resolve("index.json"), "{\"status\":\"open\"}");
      // Deliberately no plan.md — this is the corrupt condition

      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add corrupt issue directory");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("CORRUPT");
      requireThat(node.path("issue_id").asString(), "issueId").isEqualTo("2.1-corrupt-issue");
      requireThat(node.path("issue_path").asString(), "issuePath").isNotBlank();
      requireThat(node.path("message").asString(), "message").contains("plan.md");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that no lock file exists after execute returns OVERSIZED.
   * <p>
   * The OVERSIZED check happens before lock acquisition (which occurs inside executeWhileLocked()),
   * so no lock is ever acquired and no lock file is created.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReleasesLockOnOversizedReturn() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "huge-feature", "open");
      createOversizedPlan(projectPath, "2", "1", "huge-feature");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add oversized issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("OVERSIZED");

      // The lock file must not remain after an OVERSIZED early return
      Path lockFile = scope.getCatWorkPath().resolve("locks").resolve("2.1-huge-feature.lock");
      requireThat(Files.notExists(lockFile), "lockFileAbsent").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that no lock file exists after execute returns CORRUPT.
   * <p>
   * The CORRUPT check happens before lock acquisition (which occurs inside executeWhileLocked()),
   * so no lock is ever acquired and no lock file is created.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReleasesLockOnCorruptReturn() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // Create issue directory with only index.json (no plan.md) — simulates a corrupt directory
      Path issueDir = projectPath.resolve(".cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("corrupt-issue");
      Files.createDirectories(issueDir);

      Files.writeString(issueDir.resolve("index.json"), "{\"status\":\"open\"}");
      // Deliberately no plan.md — this is the corrupt condition

      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add corrupt issue directory");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("CORRUPT");

      // The lock file must not remain after a CORRUPT early return
      Path lockFile = scope.getCatWorkPath().resolve("locks").resolve("2.1-corrupt-issue.lock");
      requireThat(Files.notExists(lockFile), "lockFileAbsent").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a second call to execute after OVERSIZED return does not throw an exception.
   * <p>
   * After a first call returns OVERSIZED and releases the lock, a second call on the same issue
   * must succeed without throwing IOException due to "empty worktrees map" or other lock-related errors.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeDoesNotThrowWhenCalledTwiceOnOversizedIssue() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "huge-feature", "open");
      createOversizedPlan(projectPath, "2", "1", "huge-feature");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add oversized issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM, false);

      // First call should return OVERSIZED
      String json1 = prepare.execute(input);
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node1 = mapper.readTree(json1);
      requireThat(node1.path("status").asString(), "firstStatus").isEqualTo("OVERSIZED");

      // Second call with same sessionId should also return OVERSIZED (not throw)
      String json2 = prepare.execute(input);
      JsonNode node2 = mapper.readTree(json2);
      requireThat(node2.path("status").asString(), "secondStatus").isEqualTo("OVERSIZED");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a second call to execute after CORRUPT return does not throw an exception.
   * <p>
   * After a first call returns CORRUPT and releases the lock, a second call on the same issue
   * must succeed without throwing IOException due to "empty worktrees map" or other lock-related errors.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeDoesNotThrowWhenCalledTwiceOnCorruptIssue() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // Create issue directory with only index.json (no plan.md) — simulates a corrupt directory
      Path issueDir = projectPath.resolve(".cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("corrupt-issue");
      Files.createDirectories(issueDir);

      Files.writeString(issueDir.resolve("index.json"), "{\"status\":\"open\"}");
      // Deliberately no plan.md — this is the corrupt condition

      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add corrupt issue directory");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM, false);

      // First call should return CORRUPT
      String json1 = prepare.execute(input);
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node1 = mapper.readTree(json1);
      requireThat(node1.path("status").asString(), "firstStatus").isEqualTo("CORRUPT");

      // Second call with same sessionId should also return CORRUPT (not throw)
      String json2 = prepare.execute(input);
      JsonNode node2 = mapper.readTree(json2);
      requireThat(node2.path("status").asString(), "secondStatus").isEqualTo("CORRUPT");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that execute returns READY with a valid worktree for an open issue in a git repo.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsReadyAndCreatesWorktree() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "my-feature", "open");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);

      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      requireThat(node.path("issue_id").asString(), "issueId").isEqualTo("2.1-my-feature");
      requireThat(node.path("major").asString(), "major").isEqualTo("2");
      requireThat(node.path("minor").asString(), "minor").isEqualTo("1");
      requireThat(node.path("issue_name").asString(), "issueName").isEqualTo("my-feature");
      requireThat(node.path("issue_branch").asString(), "issueBranch").isEqualTo("2.1-my-feature");
      requireThat(node.path("target_branch").asString(), "targetBranch").isEqualTo("v2.1");
      requireThat(node.path("lock_acquired").asBoolean(), "lockAcquired").isTrue();
      requireThat(node.path("estimated_tokens").asInt(), "estimatedTokens").isGreaterThan(0);
      requireThat(node.path("worktree_path").isMissingNode(), "hasWorktreePath").isFalse();
      requireThat(node.path("approach_selected").isMissingNode(), "hasApproachSelected").isFalse();

      // Verify worktree was actually created
      worktreePath = Path.of(node.path("worktree_path").asString());
      requireThat(Files.isDirectory(worktreePath), "worktreeExists").isTrue();
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that execute sets index.json to in-progress after creating a worktree.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeUpdatesIndexJsonToInProgress() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "my-feature", "open");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");

      worktreePath = Path.of(node.path("worktree_path").asString());

      // Read the index.json from the worktree
      Path stateFile = worktreePath.resolve(".cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("my-feature").resolve("index.json");
      String stateContent = Files.readString(stateFile);

      requireThat(stateContent, "stateContent").contains("in-progress");
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that execute uses specific issue ID when provided.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeUsesSpecificIssueIdWhenProvided() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "first-feature", "open");
      createIssue(projectPath, "2", "1", "second-feature", "open");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add issues");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      // Request specific issue that would not be selected by priority
      PrepareInput input = new PrepareInput(sessionId, "", "2.1-second-feature", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      requireThat(node.path("issue_id").asString(), "issueId").isEqualTo("2.1-second-feature");

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that execute resolves bare issue names (e.g., "fix-bug") to the correct qualified ID.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeResolvesBareBugNameToQualifiedIssueId() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "fix-bug", "open");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add fix-bug issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "fix-bug", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      requireThat(node.path("issue_id").asString(), "issueId").isEqualTo("2.1-fix-bug");

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that execute applies exclude pattern and returns NO_ISSUES when all issues are excluded.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsNoIssuesWhenAllIssuesExcluded() throws IOException
  {
    Path projectPath = createTempCatProject();
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "compress-feature", "open");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*compress*", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("NO_ISSUES");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that execute includes estimatedTokens and percentOfThreshold in READY result.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeIncludesEstimatedTokensInReadyResult() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "my-feature", "open");
      createSimplePlan(projectPath, "2", "1", "my-feature");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add issue with plan");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      requireThat(node.path("estimated_tokens").asInt(), "estimatedTokens").isGreaterThan(0);
      requireThat(node.path("percent_of_threshold").isMissingNode(), "hasPercentOfThreshold").
        isFalse();

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that execute includes goal in READY result when plan.md has a Goal section.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeIncludesGoalFromPlanMd() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "my-feature", "open");
      createPlanWithGoal(projectPath, "2", "1", "my-feature", "Implement the best feature ever");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add issue with goal");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      requireThat(node.path("goal").asString(), "goal").
        contains("Implement the best feature ever");

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that execute returns LOCKED when IssueDiscovery returns NotExecutable with "locked" reason.
   * <p>
   * Creates a lock file for the issue before calling execute to trigger the LOCKED path.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsLockedWhenReasonContainsLocked() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "locked-feature", "open");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add issue");

      // Create a lock file owned by a different session with recent timestamp (non-stale)
      Path locksDir = scope.getCatWorkPath().resolve("locks");
      Files.createDirectories(locksDir);
      String otherSession = UUID.randomUUID().toString();
      long recentTimestamp = Instant.now().getEpochSecond();
      String lockContent = """
        {
          "session_id": "%s",
          "worktrees": {"/some/worktree": "%s"},
          "created_at": %d,
          "created_iso": "2026-03-01T23:00:00Z"
        }""".formatted(otherSession, otherSession, recentTimestamp);
      Files.writeString(locksDir.resolve("2.1-locked-feature.lock"), lockContent);

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "2.1-locked-feature", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("LOCKED");
      requireThat(node.path("message").asString(), "message").contains("locked");
      requireThat(node.path("issue_id").asString(), "issueId").isEqualTo("2.1-locked-feature");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that execute returns ERROR with locked_by and lock_age_seconds when the issue is
   * locked by a foreign session AND an existing worktree directory is present for that issue.
   * <p>
   * This allows the skill to show a confirmation dialog with lock-holder info so the user can
   * decide whether to resume work across sessions, rather than silently skipping the issue.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsErrorWithLockedByWhenForeignSessionLockAndWorktreeExists()
    throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "locked-with-wt", "open");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add issue");

      // Create a lock file owned by a different session with a recent timestamp (non-stale)
      Path locksDir = scope.getCatWorkPath().resolve("locks");
      Files.createDirectories(locksDir);
      String otherSession = UUID.randomUUID().toString();
      long recentTimestamp = Instant.now().getEpochSecond();
      String lockContent = """
        {
          "session_id": "%s",
          "worktrees": {"/some/worktree": "%s"},
          "created_at": %d,
          "created_iso": "2026-03-01T23:00:00Z"
        }""".formatted(otherSession, otherSession, recentTimestamp);
      Files.writeString(locksDir.resolve("2.1-locked-with-wt.lock"), lockContent);

      // Create an actual git worktree for the issue branch so the worktree directory exists
      worktreePath = scope.getCatWorkPath().resolve("worktrees").resolve("2.1-locked-with-wt");
      Files.createDirectories(worktreePath.getParent());
      GitCommands.runGit(projectPath, "worktree", "add", "-b", "2.1-locked-with-wt",
        worktreePath.toString(), "HEAD");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "2.1-locked-with-wt", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      // ERROR (not LOCKED) so the skill can present a confirmation dialog
      requireThat(node.path("status").asString(), "status").isEqualTo("ERROR");
      requireThat(node.path("message").asString(), "message").contains("locked");
      requireThat(node.path("issue_id").asString(), "issueId").isEqualTo("2.1-locked-with-wt");
      requireThat(node.path("locked_by").asString(), "lockedBy").isEqualTo(otherSession);
      requireThat(node.path("lock_age_seconds").asLong(), "lockAgeSeconds").
        isGreaterThanOrEqualTo(0L);
      requireThat(node.path("worktree_path").asString(), "worktreePath").
        isEqualTo(worktreePath.toString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that execute returns READY when the current session already holds the lock and the
   * worktree directory already exists — resuming work seamlessly instead of returning ERROR.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsReadyWhenSessionOwnsLockAndWorktreeExists() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "resume-feature", "open");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add issue");

      // First call creates the worktree and acquires the lock
      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "2.1-resume-feature", TrustLevel.MEDIUM, false);

      String json1 = prepare.execute(input);
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node1 = mapper.readTree(json1);
      requireThat(node1.path("status").asString(), "firstStatus").isEqualTo("READY");
      worktreePath = Path.of(node1.path("worktree_path").asString());

      // Second call with same session ID — worktree exists, lock is still held by this session
      // Must return READY (resume), not ERROR
      String json2 = prepare.execute(input);
      JsonNode node2 = mapper.readTree(json2);

      requireThat(node2.path("status").asString(), "secondStatus").isEqualTo("READY");
      requireThat(node2.path("issue_id").asString(), "issueId").isEqualTo("2.1-resume-feature");
      requireThat(node2.path("worktree_path").asString(), "worktreePath").
        isEqualTo(worktreePath.toString());
      requireThat(node2.path("lock_acquired").asBoolean(), "lockAcquired").isTrue();
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that execute detects suspicious commits on the target branch and populates
   * potentiallyComplete and suspiciousCommits fields in the READY result.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeDetectsSuspiciousCommitsOnTargetBranch() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "suspicious-feature", "open");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add suspicious-feature issue");

      // Create a non-planning commit on the target branch that mentions the issue name
      Files.writeString(projectPath.resolve("impl.txt"), "implementation work");
      GitCommands.runGit(projectPath, "add", "impl.txt");
      GitCommands.runGit(projectPath, "commit", "-m",
        "feature: implement suspicious-feature changes");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "2.1-suspicious-feature", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      requireThat(node.path("potentially_complete").asBoolean(), "potentiallyComplete").isTrue();
      requireThat(node.path("suspicious_commits").asString(), "suspiciousCommits").isNotBlank();

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that execute includes hasExistingWork, existingCommits, and commitSummary fields
   * in the READY result. When a fresh issue branch is created from HEAD (no prior work),
   * hasExistingWork is false and existingCommits is 0.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeIncludesExistingWorkFieldsInReadyResult() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "fresh-feature", "open");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "2.1-fresh-feature", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      requireThat(node.has("has_existing_work"), "hasExistingWorkField").isTrue();
      requireThat(node.path("has_existing_work").asBoolean(), "hasExistingWork").isFalse();
      requireThat(node.has("existing_commits"), "hasExistingCommitsField").isTrue();
      requireThat(node.path("existing_commits").asInt(), "existingCommits").isEqualTo(0);
      requireThat(node.has("commit_summary"), "hasCommitSummaryField").isTrue();

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that execute returns READY with goal "No goal found" when plan.md lacks a Goal section.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsReadyWhenGoalSectionMissing() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "no-goal", "open");

      // Create a plan.md without a Goal section
      Path issueDir = projectPath.resolve(".cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("no-goal");
      String planContent = """
        # Plan

        ## Execution Steps

        1. Do the work
        """;
      Files.writeString(issueDir.resolve("plan.md"), planContent);

      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add issue without goal");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      requireThat(node.path("goal").asString(), "goal").isEqualTo("No goal found");

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that execute returns NO_ISSUES with blockedIssues diagnostic when an issue has
   * unresolved dependencies.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsNoIssuesWithBlockedIssuesDiagnostic() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // Create a dependency issue that is open (not closed)
      createIssue(projectPath, "2", "1", "dependency-issue", "open");

      // Create a blocked issue with dependencies
      createIssueWithDependencies(projectPath, "2", "1", "blocked-feature", "open",
        "2.1-dependency-issue");

      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add blocked issues");

      WorkPrepare prepare = new WorkPrepare(scope);
      // Exclude all issues so IssueDiscovery returns NotFound, triggering diagnostics
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("NO_ISSUES");
      requireThat(node.has("blocked_issues"), "hasBlockedIssues").isTrue();

      JsonNode blockedIssues = node.path("blocked_issues");
      requireThat(blockedIssues.size(), "blockedIssueCount").isGreaterThan(0);

      // Find the blocked-feature entry
      boolean foundBlockedFeature = false;
      for (JsonNode issue : blockedIssues)
      {
        if (issue.path("issue_id").asString().equals("2.1-blocked-feature"))
        {
          foundBlockedFeature = true;
          requireThat(issue.has("blocked_by"), "hasBlockedBy").isTrue();
          requireThat(issue.path("reason").asString(), "reason").contains("2.1-dependency-issue");
        }
      }
      requireThat(foundBlockedFeature, "foundBlockedFeature").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies the token boundary at 160,000: 32 files to create at 5000 each = 160,000 plus 10,000
   * base overhead = 170,000, which exceeds the 160,000 limit and triggers OVERSIZED.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsOversizedAtTokenBoundary() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "boundary-feature", "open");

      // 32 files to create: 32 * 5000 = 160,000 + 10,000 base = 170,000 > 160,000
      Path issueDir = projectPath.resolve(".cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("boundary-feature");
      StringBuilder planContent = new StringBuilder(800);
      planContent.append("# Plan\n\n## Files to Create\n\n");
      for (int i = 0; i < 32; ++i)
        planContent.append("- src/main/java/Feature").append(i).append(".java\n");

      planContent.append("\n## Execution Steps\n\n1. Implement\n");
      Files.writeString(issueDir.resolve("plan.md"), planContent.toString());

      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add boundary issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("OVERSIZED");
      // 32 * 5000 + 1 * 2000 + 10000 = 172000
      requireThat(node.path("estimated_tokens").asInt(), "estimatedTokens").isGreaterThan(160_000);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that run() returns JSON with status=ERROR when execute() throws IOException.
   * <p>
   * When a lock file with an empty worktrees map exists for an issue, IssueLock.acquire() throws
   * IOException. run() must catch this and produce a business-format JSON result (status=ERROR)
   * so that the work skill can parse and display the error rather than treating it as missing output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void runReturnsErrorStatusWhenExecuteThrowsIOException() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // Create an issue so execute() attempts lock acquisition
      createIssue(projectPath, "2", "1", "my-feature", "open");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add issue");

      // Create a corrupt lock file with an empty worktrees map — this causes IssueLock.acquire() to
      // throw IOException with a message containing actionable guidance (e.g. "run /cat:cleanup").
      Path locksDir = projectPath.resolve(".cat").resolve("work").resolve("locks");
      Files.createDirectories(locksDir);
      String lockContent = """
        {"session_id":"%s","worktrees":{},"created_at":%d}""".
        formatted(UUID.randomUUID(), Instant.now().getEpochSecond());
      Files.writeString(locksDir.resolve("2.1-my-feature.lock"), lockContent);

      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

      // Pass a valid UUID as session-id — TestClaudeTool returns "test-session" which IssueLock
      // rejects as non-UUID. run() uses --session-id when present, overriding the scope value.
      String sessionId = UUID.randomUUID().toString();
      WorkPrepare.run(scope, new String[]{"--session-id", sessionId}, out);

      String output = buffer.toString(StandardCharsets.UTF_8).strip();
      requireThat(output, "output").isNotBlank();

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode json = mapper.readTree(output);
      requireThat(json.path("status").asString(), "status").isEqualTo("ERROR");
      requireThat(json.path("message").asString(), "message").isNotBlank();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that after a READY return, the lock file's worktrees map contains the worktree path
   * that matches the worktreePath field in the JSON output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReadyLockContainsWorktreePath() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "my-feature", "open");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");

      worktreePath = Path.of(node.path("worktree_path").asString());
      String expectedWorktreePath = worktreePath.toString();

      // Read the lock file and verify the worktrees map is populated immediately
      Path lockFile = scope.getCatWorkPath().resolve("locks").resolve("2.1-my-feature.lock");
      String lockContent = Files.readString(lockFile);
      @SuppressWarnings("unchecked")
      Map<String, Object> lockData = mapper.readValue(lockContent, Map.class);
      @SuppressWarnings("unchecked")
      Map<String, String> worktrees = (Map<String, String>) lockData.get("worktrees");

      requireThat(worktrees, "worktrees").isNotNull();
      requireThat(worktrees.size(), "worktreesSize").isEqualTo(1);
      requireThat(worktrees.get(expectedWorktreePath), "worktreeValue").isEqualTo(sessionId);
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Creates a temporary project directory with a valid CAT structure but no git repo.
   *
   * @return the path to the created project directory
   */
  private Path createTempCatProject()
  {
    try
    {
      Path projectPath = Files.createTempDirectory("work-prepare-test");
      Path catDir = projectPath.resolve(".cat");
      Files.createDirectories(catDir.resolve("issues"));
      Files.writeString(catDir.resolve("config.json"), "{}");
      return projectPath;
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Creates a temporary git repository with a valid CAT structure and an initial commit.
   *
   * @param branchName the initial branch name
   * @return the path to the created project directory
   * @throws IOException if repository creation fails
   */
  private Path createTempGitCatProject(String branchName) throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo(branchName);

    // Add CAT structure
    Path catDir = projectPath.resolve(".cat");
    Files.createDirectories(catDir.resolve("issues"));
    Files.writeString(catDir.resolve("config.json"), "{}");

    // Commit the CAT structure so worktrees have it
    GitCommands.runGit(projectPath, "add", ".");
    GitCommands.runGit(projectPath, "commit", "-m", "Add CAT structure");

    return projectPath;
  }

  /**
   * Creates an issue directory with a minimal index.json.
   *
   * @param projectPath the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param issueName the issue name
   * @param status the issue status
   * @throws IOException if file creation fails
   */
  private void createIssue(Path projectPath, String major, String minor, String issueName,
    String status) throws IOException
  {
    Path issueDir = projectPath.resolve(".cat").resolve("issues").
      resolve("v" + major).resolve("v" + major + "." + minor).resolve(issueName);
    Files.createDirectories(issueDir);

    Files.writeString(issueDir.resolve("index.json"), "{\"status\":\"" + status + "\"}");

    String planContent = """
      # Plan: %s

      ## Goal

      Test issue for %s.
      """.formatted(issueName, issueName);

    Files.writeString(issueDir.resolve("plan.md"), planContent);
  }

  /**
   * Creates an issue directory with an index.json that has a single dependency.
   *
   * @param projectPath the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param issueName the issue name
   * @param status the issue status
   * @param dependency the dependency issue ID
   * @throws IOException if file creation fails
   */
  private void createIssueWithDependencies(Path projectPath, String major, String minor,
    String issueName, String status, String dependency) throws IOException
  {
    Path issueDir = projectPath.resolve(".cat").resolve("issues").
      resolve("v" + major).resolve("v" + major + "." + minor).resolve(issueName);
    Files.createDirectories(issueDir);

    Files.writeString(issueDir.resolve("index.json"),
      "{\"status\":\"" + status + "\",\"dependencies\":[\"" + dependency + "\"]}");

    String planContent = """
      # Plan: %s

      ## Goal

      Test issue for %s.
      """.formatted(issueName, issueName);

    Files.writeString(issueDir.resolve("plan.md"), planContent);
  }

  /**
   * Creates a plan.md with enough files and steps to produce an oversized estimate (&gt; 160000 tokens).
   *
   * @param projectPath the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param issueName the issue name
   * @throws IOException if file creation fails
   */
  private void createOversizedPlan(Path projectPath, String major, String minor,
    String issueName) throws IOException
  {
    Path issueDir = projectPath.resolve(".cat").resolve("issues").
      resolve("v" + major).resolve("v" + major + "." + minor).resolve(issueName);
    Files.createDirectories(issueDir);

    // 35 files to create at 5000 tokens each = 175000 tokens (exceeds 160000)
    StringBuilder planContent = new StringBuilder(1200);
    planContent.append("# Plan\n\n## Files to Create\n\n");
    for (int i = 0; i < 35; ++i)
      planContent.append("- src/main/java/Feature").append(i).append(".java\n");

    planContent.append("\n## Execution Steps\n\n1. Implement all features\n");

    Files.writeString(issueDir.resolve("plan.md"), planContent.toString());
  }

  /**
   * Creates a simple plan.md with a few files and steps.
   *
   * @param projectPath the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param issueName the issue name
   * @throws IOException if file creation fails
   */
  private void createSimplePlan(Path projectPath, String major, String minor,
    String issueName) throws IOException
  {
    Path issueDir = projectPath.resolve(".cat").resolve("issues").
      resolve("v" + major).resolve("v" + major + "." + minor).resolve(issueName);
    Files.createDirectories(issueDir);

    String planContent = """
      # Plan

      ## Files to Create

      - src/main/java/Feature.java
      - src/test/java/FeatureTest.java

      ## Files to Modify

      - src/main/java/module-info.java

      ## Execution Steps

      1. Create Feature.java
      2. Write FeatureTest.java
      3. Update module-info.java
      """;

    Files.writeString(issueDir.resolve("plan.md"), planContent);
  }

  /**
   * Creates a plan.md with a specific goal text.
   *
   * @param projectPath the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param issueName the issue name
   * @param goalText the goal text to include
   * @throws IOException if file creation fails
   */
  private void createPlanWithGoal(Path projectPath, String major, String minor,
    String issueName, String goalText) throws IOException
  {
    Path issueDir = projectPath.resolve(".cat").resolve("issues").
      resolve("v" + major).resolve("v" + major + "." + minor).resolve(issueName);
    Files.createDirectories(issueDir);

    String planContent = """
      # Plan

      ## Goal

      %s

      ## Execution Steps

      1. Do the work
      """.formatted(goalText);

    Files.writeString(issueDir.resolve("plan.md"), planContent);
  }

  /**
   * Verifies that execute resolves bare issue names (e.g., "fix-bug") to Scope.BARE_NAME correctly.
   * This ensures that bare names can resolve to any version when uniquely identifying an issue.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeBareIssueNameResolvedToBareNameScope() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // Create an issue with a bare name
      createIssue(projectPath, "2", "1", "fix-bug", "open");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add bare-named issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      // Pass bare issue name (no version prefix) - should resolve via BARE_NAME scope
      PrepareInput input = new PrepareInput(sessionId, "", "fix-bug", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      // Should succeed with READY status, resolving the bare name to the qualified issue
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      requireThat(node.path("issue_id").asString(), "issueId").isEqualTo("2.1-fix-bug");
      requireThat(node.path("issue_name").asString(), "issueName").isEqualTo("fix-bug");

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that bare issue names (e.g., 'fix-bug') resolve via Scope.BARE_NAME
   * and fully-qualified issue names (e.g., '2.1-fix-bug') resolve via Scope.ISSUE.
   *
   * This test explicitly checks that the scope detection pattern correctly
   * distinguishes between bare names and qualified names.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeScopeDetectionForBareVsQualifiedNames() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath1 = null;
    Path worktreePath2 = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // Create a bare-named issue
      createIssue(projectPath, "2", "1", "fix-bug", "open");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add fix-bug issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();

      // Test 1: Bare name 'fix-bug' should resolve via BARE_NAME scope
      PrepareInput bareNameInput = new PrepareInput(sessionId, "", "fix-bug", TrustLevel.MEDIUM, false);
      String bareNameJson = prepare.execute(bareNameInput);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode bareNameNode = mapper.readTree(bareNameJson);
      requireThat(bareNameNode.path("status").asString(), "bareNameStatus").isEqualTo("READY");
      requireThat(bareNameNode.path("issue_id").asString(), "bareNameIssueId").isEqualTo("2.1-fix-bug");
      worktreePath1 = Path.of(bareNameNode.path("worktree_path").asString());

      // Release the lock by removing the worktree and lock file so we can test qualified name
      if (worktreePath1 != null && Files.exists(worktreePath1))
      {
        GitCommands.runGit(projectPath, "worktree", "remove", worktreePath1.toString(), "--force");
        worktreePath1 = null;
      }
      // Clean up the lock file for the bare name issue
      Path locksDir = scope.getCatWorkPath().resolve("locks");
      Path lockFile = locksDir.resolve("2.1-fix-bug.lock");
      if (Files.exists(lockFile))
        Files.delete(lockFile);

      // Test 2: Qualified name '2.1-fix-bug' should resolve via ISSUE scope
      sessionId = UUID.randomUUID().toString();
      PrepareInput qualifiedNameInput = new PrepareInput(sessionId, "", "2.1-fix-bug", TrustLevel.MEDIUM, false);
      String qualifiedNameJson = prepare.execute(qualifiedNameInput);

      JsonNode qualifiedNameNode = mapper.readTree(qualifiedNameJson);
      requireThat(qualifiedNameNode.path("status").asString(), "qualifiedNameStatus").isEqualTo("READY");
      requireThat(qualifiedNameNode.path("issue_id").asString(), "qualifiedNameIssueId").isEqualTo("2.1-fix-bug");
      worktreePath2 = Path.of(qualifiedNameNode.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath1);
      cleanupWorktreeIfExists(projectPath, worktreePath2);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that bare issue names passed via --arguments detect as Scope.BARE_NAME
   * and qualified names detect as Scope.ISSUE.
   *
   * This test ensures that the scope detection pattern correctly distinguishes
   * between bare names (e.g., 'fix-bug') and qualified names (e.g., '2.1-fix-bug').
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeBareNameDetectionViaArguments() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // Create an issue with a bare name
      createIssue(projectPath, "2", "1", "fix-bug", "open");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add fix-bug issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();

      // Pass bare issue name directly - must be detected as BARE_NAME scope
      // and resolve to the correct qualified issue
      PrepareInput input = new PrepareInput(sessionId, "", "fix-bug", TrustLevel.MEDIUM, false);
      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);

      // Must succeed with READY status when bare name resolves
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      // Must resolve to the fully-qualified issue ID
      requireThat(node.path("issue_id").asString(), "issueId").isEqualTo("2.1-fix-bug");
      // Must have bare issue name extracted correctly
      requireThat(node.path("issue_name").asString(), "issueName").isEqualTo("fix-bug");

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that diagnostic output correctly resolves issue dependencies even when the project has
   * more than 333 issues (each with ~3 files = ~1000 filesystem entries). With the old scan limit of
   * 1000 entries, issues beyond that limit would appear as not_found in blockedIssues diagnostic
   * output, causing false positives. With the fixed unlimited scan, a closed dependency is correctly
   * resolved, so an issue depending only on closed issues does not appear in blockedIssues at all.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void diagnosticAccurateWithMoreThan333Issues() throws IOException
  {
    Path projectPath = createTempCatProject();
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // Create 350 issues to exceed the old 1000-entry limit (each issue has ~3 files)
      // Issues are distributed across multiple minor versions for realism
      for (int i = 0; i < 350; ++i)
      {
        String minor = String.valueOf(i / 50 + 1);
        createIssue(projectPath, "2", minor, "issue-" + i, "closed");
      }

      // Create a dependency issue that would be pushed past entry 999 in the old limit
      createIssue(projectPath, "2", "8", "dependency-far", "closed");
      createIssueWithDependencies(projectPath, "2", "8", "blocked-by-far", "open",
        "2.8-dependency-far");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("NO_ISSUES");

      // With the fixed unlimited scan, dependency-far is correctly resolved as "closed".
      // Since its only dependency is closed, blocked-by-far must NOT appear in blockedIssues.
      // (The old broken behavior was: dependency-far not found → blocked-by-far falsely blocked.)
      JsonNode blockedIssues = node.path("blocked_issues");
      boolean foundBlockedByFar = false;
      for (JsonNode issue : blockedIssues)
      {
        if (issue.path("issue_id").asString().equals("2.8-blocked-by-far"))
          foundBlockedByFar = true;
      }
      requireThat(foundBlockedByFar, "foundBlockedByFar").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that when there are enough filesystem entries that would cause the old 1000-entry limit
   * to truncate results, the diagnostic scan completes without error (no silent truncation).
   * The threshold for an actual error should be much higher than realistic usage.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void diagnosticScanCompletesWithoutErrorFor400Issues() throws IOException
  {
    Path projectPath = createTempCatProject();
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // Create 400 issues — enough to have exceeded the old 1000 file limit
      for (int i = 0; i < 400; ++i)
      {
        String minor = String.valueOf(i / 50 + 1);
        createIssue(projectPath, "2", minor, "issue-" + i, "closed");
      }

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      // Should complete normally (not ERROR), just report NO_ISSUES with full diagnostics
      requireThat(node.path("status").asString(), "status").isEqualTo("NO_ISSUES");
      requireThat(node.path("total_count").asInt(), "totalCount").isGreaterThanOrEqualTo(400);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that circular dependencies (A depends on B, B depends on A) are detected and reported
   * in the diagnostic output under a "circularDependencies" field.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void diagnosticReportsSimpleCircularDependency() throws IOException
  {
    Path projectPath = createTempCatProject();
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // A depends on B, B depends on A — simple cycle
      createIssueWithDependencies(projectPath, "2", "1", "issue-a", "open", "2.1-issue-b");
      createIssueWithDependencies(projectPath, "2", "1", "issue-b", "open", "2.1-issue-a");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("NO_ISSUES");

      // Circular dependencies must be reported
      requireThat(node.has("circular_dependencies"), "hasCircularDependencies").isTrue();
      JsonNode cycles = node.path("circular_dependencies");
      requireThat(cycles.size(), "cycleCount").isGreaterThan(0);

      // Verify cycle contains the expected issue IDs
      boolean foundCycle = false;
      for (JsonNode cycle : cycles)
      {
        String cycleStr = cycle.asString();
        if (cycleStr.contains("2.1-issue-a") && cycleStr.contains("2.1-issue-b"))
        {
          foundCycle = true;
          break;
        }
      }
      requireThat(foundCycle, "foundCycle").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that complex circular dependencies (A depends on B, B depends on C, C depends on A)
   * are detected and reported with the full cycle path.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void diagnosticReportsComplexCircularDependency() throws IOException
  {
    Path projectPath = createTempCatProject();
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // A -> B -> C -> A (3-node cycle)
      createIssueWithDependencies(projectPath, "2", "1", "issue-a", "open", "2.1-issue-b");
      createIssueWithDependencies(projectPath, "2", "1", "issue-b", "open", "2.1-issue-c");
      createIssueWithDependencies(projectPath, "2", "1", "issue-c", "open", "2.1-issue-a");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("NO_ISSUES");

      // Circular dependencies must be reported
      requireThat(node.has("circular_dependencies"), "hasCircularDependencies").isTrue();
      JsonNode cycles = node.path("circular_dependencies");
      requireThat(cycles.size(), "cycleCount").isGreaterThan(0);

      // The cycle path should contain all three nodes
      boolean foundAllNodes = false;
      for (JsonNode cycle : cycles)
      {
        String cycleStr = cycle.asString();
        if (cycleStr.contains("issue-a") && cycleStr.contains("issue-b") && cycleStr.contains("issue-c"))
        {
          foundAllNodes = true;
          break;
        }
      }
      requireThat(foundAllNodes, "foundAllNodes").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that non-circular dependency chains do not produce circular dependency entries
   * in the diagnostic output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void diagnosticDoesNotReportNonCircularDependencies() throws IOException
  {
    Path projectPath = createTempCatProject();
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // Linear chain: A depends on B, B depends on C (no cycle)
      createIssueWithDependencies(projectPath, "2", "1", "issue-a", "open", "2.1-issue-b");
      createIssueWithDependencies(projectPath, "2", "1", "issue-b", "open", "2.1-issue-c");
      createIssue(projectPath, "2", "1", "issue-c", "open");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("NO_ISSUES");

      // No circular dependencies should be reported for a linear chain
      boolean hasCircularDependencies = node.has("circular_dependencies") &&
        node.path("circular_dependencies").size() > 0;
      requireThat(hasCircularDependencies, "hasCircularDependencies").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that {@code buildIssueIndex} throws {@code IOException} when the filesystem walk
   * exceeds the configured diagnostic scan safety threshold.
   * <p>
   * Uses a small threshold (5 entries) so that only a few real files are needed to trigger the
   * error path, avoiding the need to create 100,000 files.
   *
   * @throws IOException if an I/O error occurs other than the expected threshold exception
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*safety threshold.*")
  public void buildIssueIndexThrowsWhenScanLimitExceeded() throws IOException
  {
    Path projectPath = createTempCatProject();
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // Create 2 issues sharing the same version dirs: issuesDir + v2 + v2.1 + 2*(dir+index.json) = 7 entries
      createIssue(projectPath, "2", "1", "issue-alpha", "closed");
      createIssue(projectPath, "2", "1", "issue-beta", "closed");

      // Use threshold=5 so that 7 entries > 5 triggers the error
      WorkPrepare prepare = new WorkPrepare(scope, 5, 1000);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM, false);

      prepare.execute(input);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that cycle detection throws {@code IOException} when the dependency chain exceeds
   * the configured maximum DFS recursion depth.
   * <p>
   * Uses depth limit 0 and two linked issues so the limit is exceeded on the very first
   * recursive call (depth 1 > 0), guaranteeing the exception regardless of traversal order.
   *
   * @throws IOException if an I/O error occurs other than the expected depth exception
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*maximum recursion depth.*")
  public void detectCyclesThrowsWhenDepthLimitExceeded() throws IOException
  {
    Path projectPath = createTempCatProject();
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // issue-a depends on issue-b; with maxDepth=0 the first recursive call (depth=1) triggers
      createIssueWithDependencies(projectPath, "2", "1", "issue-a", "open", "2.1-issue-b");
      createIssue(projectPath, "2", "1", "issue-b", "open");

      // Use maxCycleDetectionDepth=0 so any recursive call (depth=1 > 0) triggers the error
      WorkPrepare prepare = new WorkPrepare(scope, 100_000, 0);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM, false);

      prepare.execute(input);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that cycle detection completes successfully for a dependency chain exactly at the
   * maximum DFS recursion depth limit (not exceeding it).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void detectCyclesSucceedsAtDepthLimit() throws IOException
  {
    Path projectPath = createTempCatProject();
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // Build a linear chain: issue-0 -> ... -> issue-5 (6 nodes, max depth = 5 == limit, no throw)
      createIssueWithDependencies(projectPath, "2", "1", "issue-0", "open", "2.1-issue-1");
      createIssueWithDependencies(projectPath, "2", "1", "issue-1", "open", "2.1-issue-2");
      createIssueWithDependencies(projectPath, "2", "1", "issue-2", "open", "2.1-issue-3");
      createIssueWithDependencies(projectPath, "2", "1", "issue-3", "open", "2.1-issue-4");
      createIssueWithDependencies(projectPath, "2", "1", "issue-4", "open", "2.1-issue-5");
      createIssue(projectPath, "2", "1", "issue-5", "open");

      // Use maxCycleDetectionDepth=5 so depth 5 == limit, should NOT throw
      WorkPrepare prepare = new WorkPrepare(scope, 100_000, 5);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      // Should complete normally — no cycle in a linear chain
      requireThat(node.path("status").asString(), "status").isEqualTo("NO_ISSUES");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a cycle through a single decomposed parent is detected.
   * <p>
   * Scenario: A depends on decomposed parent B, and B's sub-issue C depends on A.
   * The cycle is: A -> B -> C -> A.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void diagnosticDetectsCycleThroughDecomposedParent() throws IOException
  {
    Path projectPath = createTempCatProject();
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // A depends on decomposed B (which has sub-issue C depending on A)
      createIssueWithDependencies(projectPath, "2", "1", "issue-a", "open", "2.1-issue-b");
      createDecomposedIssue(projectPath, "2", "1", "issue-b", "issue-c");
      createIssueWithDependencies(projectPath, "2", "1", "issue-c", "open", "2.1-issue-a");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("NO_ISSUES");

      requireThat(node.has("circular_dependencies"), "hasCircularDependencies").isTrue();
      JsonNode cycles = node.path("circular_dependencies");
      requireThat(cycles.size(), "cycleCount").isGreaterThan(0);

      String expectedCycle =
        "2.1-issue-c -> 2.1-issue-a -> 2.1-issue-b -> 2.1-issue-c";
      boolean foundCycle = false;
      for (JsonNode cycle : cycles)
      {
        if (cycle.asString().equals(expectedCycle))
        {
          foundCycle = true;
          break;
        }
      }
      requireThat(foundCycle, "foundCycle").withContext(expectedCycle, "expectedCycle").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a cycle through nested decomposed parents is detected.
   * <p>
   * Scenario: A depends on decomposed B, B has sub-issue C (also decomposed), C has sub-issue D,
   * and D depends on A. The cycle traverses both decomposed parents.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void diagnosticDetectsCycleThroughNestedDecomposedParents() throws IOException
  {
    Path projectPath = createTempCatProject();
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // A -> B(decomposed->C) -> C(decomposed->D) -> D -> A
      createIssueWithDependencies(projectPath, "2", "1", "issue-a", "open", "2.1-issue-b");
      createDecomposedIssue(projectPath, "2", "1", "issue-b", "issue-c");
      createDecomposedIssue(projectPath, "2", "1", "issue-c", "issue-d");
      createIssueWithDependencies(projectPath, "2", "1", "issue-d", "open", "2.1-issue-a");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("NO_ISSUES");

      requireThat(node.has("circular_dependencies"), "hasCircularDependencies").isTrue();
      JsonNode cycles = node.path("circular_dependencies");
      requireThat(cycles.size(), "cycleCount").isGreaterThan(0);

      String expectedCycle =
        "2.1-issue-a -> 2.1-issue-b -> 2.1-issue-c -> 2.1-issue-d -> 2.1-issue-a";
      boolean foundCycle = false;
      for (JsonNode cycle : cycles)
      {
        if (cycle.asString().equals(expectedCycle))
        {
          foundCycle = true;
          break;
        }
      }
      requireThat(foundCycle, "foundCycle").withContext(expectedCycle, "expectedCycle").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that no false positive is reported when A depends on decomposed B, but B's sub-issues
   * do not depend back on A.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void diagnosticNoFalsePositiveWithDecomposedParent() throws IOException
  {
    Path projectPath = createTempCatProject();
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // A depends on decomposed B; B has sub-issue C; C has no further deps
      createIssueWithDependencies(projectPath, "2", "1", "issue-a", "open", "2.1-issue-b");
      createDecomposedIssue(projectPath, "2", "1", "issue-b", "issue-c");
      createIssue(projectPath, "2", "1", "issue-c", "open");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("NO_ISSUES");

      boolean hasCircularDependencies = node.has("circular_dependencies") &&
        node.path("circular_dependencies").size() > 0;
      requireThat(hasCircularDependencies, "hasCircularDependencies").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that both direct cycles and cycles through decomposed parents are reported together.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void diagnosticDetectsBothDirectAndDecomposedCycles() throws IOException
  {
    Path projectPath = createTempCatProject();
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // Direct cycle: issue-x depends on issue-y, issue-y depends on issue-x
      createIssueWithDependencies(projectPath, "2", "1", "issue-x", "open", "2.1-issue-y");
      createIssueWithDependencies(projectPath, "2", "1", "issue-y", "open", "2.1-issue-x");

      // Decomposed cycle: issue-a depends on decomposed issue-b, issue-b's sub-issue issue-c depends on issue-a
      createIssueWithDependencies(projectPath, "2", "1", "issue-a", "open", "2.1-issue-b");
      createDecomposedIssue(projectPath, "2", "1", "issue-b", "issue-c");
      createIssueWithDependencies(projectPath, "2", "1", "issue-c", "open", "2.1-issue-a");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("NO_ISSUES");

      requireThat(node.has("circular_dependencies"), "hasCircularDependencies").isTrue();
      JsonNode cycles = node.path("circular_dependencies");
      requireThat(cycles.size(), "cycleCount").isGreaterThanOrEqualTo(2);

      String expectedDirectCycle = "2.1-issue-y -> 2.1-issue-x -> 2.1-issue-y";
      String expectedDecomposedCycle =
        "2.1-issue-c -> 2.1-issue-a -> 2.1-issue-b -> 2.1-issue-c";
      boolean foundDirectCycle = false;
      boolean foundDecomposedCycle = false;
      for (JsonNode cycle : cycles)
      {
        String cycleStr = cycle.asString();
        if (cycleStr.equals(expectedDirectCycle))
          foundDirectCycle = true;
        if (cycleStr.equals(expectedDecomposedCycle))
          foundDecomposedCycle = true;
      }
      requireThat(foundDirectCycle, "foundDirectCycle").
        withContext(expectedDirectCycle, "expectedDirectCycle").isTrue();
      requireThat(foundDecomposedCycle, "foundDecomposedCycle").
        withContext(expectedDecomposedCycle, "expectedDecomposedCycle").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a cycle through a decomposed parent is detected when the sub-issue name is
   * fully-qualified and directly references the cycling issue.
   * <p>
   * Scenario: Decomposed parent B lists fully-qualified name {@code 2.1-issue-sub}. {@code 2.1-issue-sub}
   * depends on A, creating a cycle: A -> B -> 2.1-issue-sub -> A.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void diagnosticResolvesAmbiguousSubIssueToAllCandidates() throws IOException
  {
    Path projectPath = createTempCatProject();
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // A depends on decomposed B; B lists fully-qualified "2.1-issue-sub" which cycles back to A
      createIssueWithDependencies(projectPath, "2", "1", "issue-a", "open", "2.1-issue-b");
      createDecomposedIssue(projectPath, "2", "1", "issue-b", "issue-sub");
      createIssue(projectPath, "2", "0", "issue-sub", "open");
      createIssueWithDependencies(projectPath, "2", "1", "issue-sub", "open", "2.1-issue-a");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.has("circular_dependencies"), "hasCircularDependencies").isTrue();
      JsonNode cycles = node.path("circular_dependencies");
      requireThat(cycles.size(), "cycleCount").isGreaterThan(0);

      boolean foundCycle = false;
      for (JsonNode cycle : cycles)
      {
        String cycleStr = cycle.asString();
        if (cycleStr.contains("2.1-issue-a") && cycleStr.contains("2.1-issue-b") &&
          cycleStr.contains("2.1-issue-sub"))
        {
          foundCycle = true;
          break;
        }
      }
      requireThat(foundCycle, "foundCycle").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that when a direct dependency uses a bare name that is ambiguous (matches multiple
   * qualified names in different versions), all candidates are resolved so cycle detection can
   * find cycles through any of them.
   * <p>
   * Scenario: {@code issue-a} depends on bare name "shared-dep". Two qualified issues share that
   * bare name: {@code 2.0-shared-dep} and {@code 2.1-shared-dep}. {@code 2.1-shared-dep} depends
   * back on {@code issue-a}, creating a cycle. The cycle must be detected because all candidates
   * are resolved from the ambiguous bare name.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void diagnosticDetectsDirectCycleThroughAmbiguousDependency() throws IOException
  {
    Path projectPath = createTempCatProject();
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // issue-a depends on bare name "shared-dep" (ambiguous: matches both 2.0 and 2.1 versions)
      createIssueWithDependencies(projectPath, "2", "1", "issue-a", "open", "shared-dep");
      // Two issues share the bare name "shared-dep"
      createIssue(projectPath, "2", "0", "shared-dep", "open");
      // Only the 2.1 variant cycles back to issue-a
      createIssueWithDependencies(projectPath, "2", "1", "shared-dep", "open", "2.1-issue-a");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.has("circular_dependencies"), "hasCircularDependencies").isTrue();
      JsonNode cycles = node.path("circular_dependencies");
      requireThat(cycles.size(), "cycleCount").isGreaterThan(0);

      boolean foundCycle = false;
      for (JsonNode cycle : cycles)
      {
        String cycleStr = cycle.asString();
        if (cycleStr.contains("2.1-issue-a") && cycleStr.contains("2.1-shared-dep"))
        {
          foundCycle = true;
          break;
        }
      }
      requireThat(foundCycle, "foundCycle").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that malformed "Decomposed Into" entries are gracefully skipped and do not cause errors.
   * <p>
   * Only fully-qualified names matching the pattern {@code <major>.<minor>-<bare-name>} are accepted.
   * Entries like {@code v2.1/issue-x}, {@code some.thing}, or bare names without a version prefix
   * should be silently ignored.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void diagnosticSkipsMalformedDecomposedEntries() throws IOException
  {
    Path projectPath = createTempCatProject();
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // B is a decomposed parent with one valid and several malformed entries
      createIssueWithDependencies(projectPath, "2", "1", "issue-a", "open", "2.1-issue-b");
      createDecomposedIssueWithMalformedEntries(projectPath, "2", "1", "issue-b", "issue-c");
      createIssue(projectPath, "2", "1", "issue-c", "open");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM, false);

      // Should complete without error; no cycles since C has no dep on A
      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      boolean hasCircularDependencies = node.has("circular_dependencies") &&
        node.path("circular_dependencies").size() > 0;
      requireThat(hasCircularDependencies, "hasCircularDependencies").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Creates a decomposed parent issue directory with an index.json listing a single sub-issue.
   * <p>
   * The parent issue has open status and a "decomposedInto" JSON array listing the sub-issue
   * using its fully-qualified name (e.g., {@code 2.1-issue-c}).
   * No plan.md is created, so the parent is treated as a decomposed parent only.
   *
   * @param projectPath the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param issueName the parent issue name
   * @param subIssueName the bare name of the sub-issue; will be prefixed with the version to form
   *         a fully-qualified name in the "decomposedInto" array
   * @throws IOException if file creation fails
   */
  private void createDecomposedIssue(Path projectPath, String major, String minor,
    String issueName, String subIssueName) throws IOException
  {
    Path issueDir = projectPath.resolve(".cat").resolve("issues").
      resolve("v" + major).resolve("v" + major + "." + minor).resolve(issueName);
    Files.createDirectories(issueDir);

    String qualifiedSubIssueName = major + "." + minor + "-" + subIssueName;
    Files.writeString(issueDir.resolve("index.json"),
      "{\"status\":\"open\",\"decomposedInto\":[\"" + qualifiedSubIssueName + "\"]}");
  }

  /**
   * Creates a decomposed parent issue directory whose index.json "decomposedInto" field contains
   * one valid fully-qualified name and several malformed entries (containing dots without the
   * version format, slashes, or other characters that are not valid qualified names).
   * <p>
   * The malformed entries should be silently skipped by the sub-issue resolver.
   *
   * @param projectPath the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param issueName the parent issue name
   * @param validSubIssueName the valid bare name of the sub-issue; will be prefixed with the
   *         version to form a fully-qualified name
   * @throws IOException if file creation fails
   */
  private void createDecomposedIssueWithMalformedEntries(Path projectPath, String major, String minor,
    String issueName, String validSubIssueName) throws IOException
  {
    Path issueDir = projectPath.resolve(".cat").resolve("issues").
      resolve("v" + major).resolve("v" + major + "." + minor).resolve(issueName);
    Files.createDirectories(issueDir);

    String qualifiedSubIssueName = major + "." + minor + "-" + validSubIssueName;
    // The malformed entries (v2.1/issue-bad-slash, some.dotted.name, bare-name) are included
    // to verify the sub-issue resolver silently skips entries that fail the qualified-name pattern.
    Files.writeString(issueDir.resolve("index.json"),
      "{\"status\":\"open\",\"decomposedInto\":[\"" + qualifiedSubIssueName + "\"," +
      "\"v2.1/issue-bad-slash\",\"some.dotted.name\",\"bare-name-without-version\"]}");
  }

  /**
   * Verifies that execute returns READY when an issue directory has only plan.md (no index.json).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsReadyForPlanMdOnlyIssue() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // Create issue with only plan.md - no index.json
      Path issueDir = projectPath.resolve(".cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("plan-only-issue");
      Files.createDirectories(issueDir);
      Files.writeString(issueDir.resolve("plan.md"), "# Plan\n\n## Goal\n\nTest goal\n");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add plan.md-only issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      requireThat(node.path("issue_id").asString(), "issueId").isEqualTo("2.1-plan-only-issue");

      worktreePath = Path.of(node.path("worktree_path").asString());
      requireThat(Files.isDirectory(worktreePath), "worktreeExists").isTrue();
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that execute creates index.json in the worktree for a plan.md-only issue.
   * <p>
   * The created index.json must contain in-progress status.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeCreatesIndexJsonInWorktreeForPlanMdOnlyIssue() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // Create issue with only plan.md - no index.json
      Path issueDir = projectPath.resolve(".cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("plan-only-issue");
      Files.createDirectories(issueDir);
      Files.writeString(issueDir.resolve("plan.md"), "# Plan\n\n## Goal\n\nTest goal\n");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add plan.md-only issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");

      worktreePath = Path.of(node.path("worktree_path").asString());

      // Verify index.json was created in the worktree (not in the main workspace)
      Path worktreeIssueDir = worktreePath.resolve(".cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("plan-only-issue");
      Path worktreeIndexJson = worktreeIssueDir.resolve("index.json");
      requireThat(Files.isRegularFile(worktreeIndexJson), "worktreeIndexJsonExists").isTrue();

      String indexJsonContent = Files.readString(worktreeIndexJson);
      requireThat(indexJsonContent, "content").contains("in-progress");

      // index.json must NOT be created in the main workspace
      Path mainWorkspaceIndexJson = issueDir.resolve("index.json");
      requireThat(Files.exists(mainWorkspaceIndexJson), "mainWorkspaceIndexJsonCreated").isFalse();
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that execute returns READY when index.json exists on disk in the main workspace but is
   * untracked by git (not committed), so it is absent from the worktree.
   * <p>
   * This is the scenario where a new issue directory was created and index.json written to disk, but
   * neither file was committed. IssueDiscovery sees index.json on disk and sets createIndexJson=false.
   * When the worktree is created from the branch, the untracked index.json is absent, causing
   * updateIndexJson() to fail. The fix falls back to createIndexJson() in this case.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsReadyWhenIndexJsonExistsOnDiskButIsUntrackedByGit() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // Commit plan.md but NOT index.json — simulates an untracked issue directory
      Path issueDir = projectPath.resolve(".cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("untracked-state-issue");
      Files.createDirectories(issueDir);
      Files.writeString(issueDir.resolve("plan.md"), "# Plan\n\n## Goal\n\nTest goal\n");
      // Write index.json to disk but do NOT commit it — it remains untracked
      Files.writeString(issueDir.resolve("index.json"), "{\"status\":\"open\"}");
      // Only add and commit plan.md — index.json stays untracked
      GitCommands.runGit(projectPath, "add", issueDir.resolve("plan.md").toString());
      GitCommands.runGit(projectPath, "commit", "-m", "Add plan.md only (index.json untracked)");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      // Must return READY, not ERROR
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      requireThat(node.path("issue_id").asString(), "issueId").isEqualTo("2.1-untracked-state-issue");

      worktreePath = Path.of(node.path("worktree_path").asString());

      // index.json must exist in the worktree with in-progress status
      Path worktreeIssueDir = worktreePath.resolve(".cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("untracked-state-issue");
      Path worktreeIndexJson = worktreeIssueDir.resolve("index.json");
      requireThat(Files.isRegularFile(worktreeIndexJson), "worktreeIndexJsonExists").isTrue();

      String indexJsonContent = Files.readString(worktreeIndexJson);
      requireThat(indexJsonContent, "content").contains("in-progress");
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that execute treats an index.json with a missing status field as open and returns READY.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsReadyWhenIndexJsonHasMissingStatusField() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      // Create issue with index.json that has no status field
      Path issueDir = projectPath.resolve(".cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("no-status-issue");
      Files.createDirectories(issueDir);
      Files.writeString(issueDir.resolve("index.json"), "{}");
      Files.writeString(issueDir.resolve("plan.md"), "# Plan\n\n## Goal\n\nTest goal\n");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add issue with missing status field");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      requireThat(node.path("issue_id").asString(), "issueId").isEqualTo("2.1-no-status-issue");

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that execute returns issuePath pointing to the worktree, not the main workspace.
   * <p>
   * Agents use issuePath to read plan.md via the Read tool, which is blocked by
   * EnforceWorktreePathIsolation when the path points outside the active worktree.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsIssuePathInsideWorktree() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      Path issueDir = projectPath.resolve(".cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("worktree-path-issue");
      Files.createDirectories(issueDir);
      Files.writeString(issueDir.resolve("plan.md"), "# Plan\n\n## Goal\n\nTest goal\n");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add worktree-path-issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");

      worktreePath = Path.of(node.path("worktree_path").asString());
      String issuePath = node.path("issue_path").asString();

      // issuePath must be inside the worktree, not the main workspace issue directory
      requireThat(issuePath, "issuePath").startsWith(worktreePath.toString());

      // issuePath must end with the correct relative issue directory
      requireThat(issuePath, "issuePath").endsWith(
        ".cat/issues/v2/v2.1/worktree-path-issue");

      // issuePath must NOT be the main workspace issue directory
      requireThat(issuePath, "issuePath").isNotEqualTo(
        projectPath.resolve(".cat/issues/v2/v2.1/worktree-path-issue").toString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  // -------------------------------------------------------------------------
  // parseRawArguments — keyword prefix stripping
  // -------------------------------------------------------------------------

  /**
   * Verifies that "resume &lt;issue-id&gt;" strips the "resume" keyword and resolves the issue ID.
   * rawArguments includes the catAgentId prefix as the first token.
   */
  @Test
  public void parseRawArgumentsStripsResumePrefix()
  {
    WorkPrepare.ParsedArguments result =
      WorkPrepare.parseRawArguments("a1b2c3d4-e5f6-7890-abcd-ef1234567890 resume 2.1-fix-bug", "", "");
    requireThat(result.issueId(), "issueId").isEqualTo("2.1-fix-bug");
    requireThat(result.excludePattern(), "excludePattern").isEmpty();
    requireThat(result.resume(), "resume").isTrue();
  }

  /**
   * Verifies that "continue &lt;issue-id&gt;" strips the "continue" keyword and resolves the issue ID.
   * rawArguments includes the catAgentId prefix as the first token.
   */
  @Test
  public void parseRawArgumentsStripesContinuePrefix()
  {
    WorkPrepare.ParsedArguments result =
      WorkPrepare.parseRawArguments("a1b2c3d4-e5f6-7890-abcd-ef1234567890 continue 2.1-fix-bug", "", "");
    requireThat(result.issueId(), "issueId").isEqualTo("2.1-fix-bug");
    requireThat(result.excludePattern(), "excludePattern").isEmpty();
    requireThat(result.resume(), "resume").isTrue();
  }

  /**
   * Verifies that keyword stripping handles multiple spaces after the keyword.
   * <p>
   * "resume  2.1-fix-bug" (two spaces) still matches {@code startsWith("resume ")} because there is at
   * least one space. After removing the keyword prefix, {@link String#strip()} collapses the extra space,
   * so the resolved issue ID is "2.1-fix-bug". rawArguments includes the catAgentId prefix as the first
   * token.
   */
  @Test
  public void parseRawArgumentsHandlesMultipleSpacesAfterKeyword()
  {
    // "resume  2.1-fix-bug" — two spaces after keyword.
    // startsWith("resume ") matches (the check only requires one space to be present).
    // After substring("resume".length()).strip(), the result is "2.1-fix-bug".
    WorkPrepare.ParsedArguments result =
      WorkPrepare.parseRawArguments("a1b2c3d4-e5f6-7890-abcd-ef1234567890 resume  2.1-fix-bug", "", "");
    requireThat(result.issueId(), "issueId").isEqualTo("2.1-fix-bug");
    requireThat(result.excludePattern(), "excludePattern").isEmpty();
    requireThat(result.resume(), "resume").isTrue();
  }

  /**
   * Verifies that keyword stripping is case-sensitive ("Resume" is not stripped).
   * rawArguments includes the catAgentId prefix as the first token.
   */
  @Test
  public void parseRawArgumentsKeywordStrippingIsCaseSensitive()
  {
    // "Resume 2.1-fix-bug" — capital R, not matched
    WorkPrepare.ParsedArguments result =
      WorkPrepare.parseRawArguments("a1b2c3d4-e5f6-7890-abcd-ef1234567890 Resume 2.1-fix-bug", "", "");
    requireThat(result.issueId(), "issueId").isEmpty();
    requireThat(result.excludePattern(), "excludePattern").isEmpty();
    requireThat(result.resume(), "resume").isFalse();
  }

  /**
   * Verifies that a bare issue ID (no keyword prefix) is resolved correctly.
   * rawArguments includes the catAgentId prefix as the first token.
   */
  @Test
  public void parseRawArgumentsResolvesBarePrefixedIssueId()
  {
    WorkPrepare.ParsedArguments result =
      WorkPrepare.parseRawArguments("a1b2c3d4-e5f6-7890-abcd-ef1234567890 2.1-fix-bug", "", "");
    requireThat(result.issueId(), "issueId").isEqualTo("2.1-fix-bug");
    requireThat(result.excludePattern(), "excludePattern").isEmpty();
    requireThat(result.resume(), "resume").isFalse();
  }

  /**
   * Verifies that a bare short-name issue ID (no version prefix) is resolved correctly.
   * rawArguments includes the catAgentId prefix as the first token.
   */
  @Test
  public void parseRawArgumentsResolvesShortNameIssueId()
  {
    WorkPrepare.ParsedArguments result =
      WorkPrepare.parseRawArguments("a1b2c3d4-e5f6-7890-abcd-ef1234567890 fix-bug", "", "");
    requireThat(result.issueId(), "issueId").isEqualTo("fix-bug");
    requireThat(result.excludePattern(), "excludePattern").isEmpty();
  }

  /**
   * Verifies that "resume" alone (no trailing issue ID) is treated as the issue ID itself.
   * <p>
   * "resume" does not start with "resume " (no trailing space), so keyword stripping is not applied.
   * The word "resume" matches the short-name issue ID pattern {@code ^[a-zA-Z][a-zA-Z0-9_-]*$},
   * so it is resolved as the issue ID. rawArguments includes the catAgentId prefix as the first token.
   */
  @Test
  public void parseRawArgumentsResumeAloneResolvesAsIssueId()
  {
    // "resume" without a trailing space: no keyword stripping; matches short-name regex.
    WorkPrepare.ParsedArguments result =
      WorkPrepare.parseRawArguments("a1b2c3d4-e5f6-7890-abcd-ef1234567890 resume", "", "");
    requireThat(result.issueId(), "issueId").isEqualTo("resume");
    requireThat(result.excludePattern(), "excludePattern").isEmpty();
  }

  /**
   * Verifies that explicit issueId flag takes precedence over raw arguments.
   */
  @Test
  public void parseRawArgumentsExplicitIssueIdTakesPrecedence()
  {
    WorkPrepare.ParsedArguments result =
      WorkPrepare.parseRawArguments("resume 2.1-other-issue", "2.1-explicit-issue", "");
    requireThat(result.issueId(), "issueId").isEqualTo("2.1-explicit-issue");
    requireThat(result.excludePattern(), "excludePattern").isEmpty();
  }

  /**
   * Verifies that explicit excludePattern flag takes precedence over raw arguments.
   */
  @Test
  public void parseRawArgumentsExplicitExcludePatternTakesPrecedence()
  {
    WorkPrepare.ParsedArguments result =
      WorkPrepare.parseRawArguments("resume 2.1-fix-bug", "", "*compress*");
    requireThat(result.issueId(), "issueId").isEmpty();
    requireThat(result.excludePattern(), "excludePattern").isEqualTo("*compress*");
  }

  /**
   * Verifies that blank raw arguments return empty fields without error.
   */
  @Test
  public void parseRawArgumentsBlankInputReturnsEmpty()
  {
    WorkPrepare.ParsedArguments result = WorkPrepare.parseRawArguments("", "", "");
    requireThat(result.issueId(), "issueId").isEmpty();
    requireThat(result.excludePattern(), "excludePattern").isEmpty();
  }

  /**
   * Verifies that "skip &lt;word&gt;" produces an exclude-pattern glob.
   * rawArguments includes the catAgentId prefix as the first token.
   */
  @Test
  public void parseRawArgumentsSkipProducesExcludePattern()
  {
    WorkPrepare.ParsedArguments result =
      WorkPrepare.parseRawArguments("a1b2c3d4-e5f6-7890-abcd-ef1234567890 skip compress", "", "");
    requireThat(result.issueId(), "issueId").isEmpty();
    requireThat(result.excludePattern(), "excludePattern").isEqualTo("*compress*");
  }

  /**
   * Verifies that non-blank rawArguments not starting with a catAgentId throws
   * {@link IllegalArgumentException}.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*catAgentId.*")
  public void parseRawArgumentsThrowsWhenCatAgentIdAbsent()
  {
    WorkPrepare.parseRawArguments("2.1-fix-bug", "", "");
  }

  /**
   * Verifies that "resume" followed by a "skip" argument does not strip the resume prefix
   * (since "skip" does not match the issue-id regex after stripping).
   * rawArguments includes the catAgentId prefix as the first token.
   */
  @Test
  public void parseRawArgumentsResumeWithSkipArgumentStripsKeywordThenProducesEmpty()
  {
    // "resume skip compress" — after stripping "resume ", raw = "skip compress"
    // "skip compress" is not an issue-id match, but starts with "skip " so excludePattern is set
    WorkPrepare.ParsedArguments result =
      WorkPrepare.parseRawArguments("a1b2c3d4-e5f6-7890-abcd-ef1234567890 resume skip compress", "", "");
    requireThat(result.issueId(), "issueId").isEmpty();
    requireThat(result.excludePattern(), "excludePattern").isEqualTo("*compress*");
    requireThat(result.resume(), "resume").isTrue();
  }

  // -------------------------------------------------------------------------
  // parseRawArguments — CAT agent ID prefix stripping
  // -------------------------------------------------------------------------

  /**
   * Verifies that a plain UUID prefix (CAT agent ID) is stripped and the issue ID is resolved.
   */
  @Test
  public void parseRawArgumentsStripsUuidPrefix()
  {
    WorkPrepare.ParsedArguments result =
      WorkPrepare.parseRawArguments("92289cdd-76a1-4d7e-8cf3-be5618ec270a 2.1-fix-bug", "", "");
    requireThat(result.issueId(), "issueId").isEqualTo("2.1-fix-bug");
    requireThat(result.excludePattern(), "excludePattern").isEmpty();
  }

  /**
   * Verifies that a subagent ID prefix ({@code uuid/subagents/name}) is stripped and the issue ID
   * is resolved.
   */
  @Test
  public void parseRawArgumentsStripsSubagentIdPrefix()
  {
    WorkPrepare.ParsedArguments result = WorkPrepare.parseRawArguments(
      "92289cdd-76a1-4d7e-8cf3-be5618ec270a/subagents/abc123 2.1-fix-bug", "", "");
    requireThat(result.issueId(), "issueId").isEqualTo("2.1-fix-bug");
    requireThat(result.excludePattern(), "excludePattern").isEmpty();
  }

  /**
   * Verifies that a UUID-only argument (no trailing issue ID) returns empty fields.
   */
  @Test
  public void parseRawArgumentsUuidOnlyReturnsEmpty()
  {
    WorkPrepare.ParsedArguments result =
      WorkPrepare.parseRawArguments("92289cdd-76a1-4d7e-8cf3-be5618ec270a", "", "");
    requireThat(result.issueId(), "issueId").isEmpty();
    requireThat(result.excludePattern(), "excludePattern").isEmpty();
  }

  /**
   * Verifies that a UUID prefix followed by a modifier keyword and issue ID is handled correctly:
   * UUID is stripped, then "resume" is stripped, leaving the issue ID.
   */
  @Test
  public void parseRawArgumentsStripsUuidThenResumeKeyword()
  {
    WorkPrepare.ParsedArguments result = WorkPrepare.parseRawArguments(
      "92289cdd-76a1-4d7e-8cf3-be5618ec270a resume 2.1-fix-bug", "", "");
    requireThat(result.issueId(), "issueId").isEqualTo("2.1-fix-bug");
    requireThat(result.excludePattern(), "excludePattern").isEmpty();
    requireThat(result.resume(), "resume").isTrue();
  }

  /**
   * Verifies that a UUID prefix followed by a "skip" argument produces the exclude pattern.
   */
  @Test
  public void parseRawArgumentsStripsUuidThenParsesSkip()
  {
    WorkPrepare.ParsedArguments result = WorkPrepare.parseRawArguments(
      "92289cdd-76a1-4d7e-8cf3-be5618ec270a skip compress", "", "");
    requireThat(result.issueId(), "issueId").isEmpty();
    requireThat(result.excludePattern(), "excludePattern").isEqualTo("*compress*");
  }

  /**
   * Verifies that when {@code --arguments} contains only a CAT agent ID UUID (no trailing issue name),
   * {@code run()} strips the UUID and returns READY for the next available issue (not NO_ISSUES).
   * <p>
   * This is the end-to-end regression test for the bug where UUIDs were matched as bare issue names.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void runReturnsReadyWhenArgumentsContainsOnlyUuid() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (TestClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "my-feature", "open");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "planning: add issue my-feature");

      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

      String sessionId = UUID.randomUUID().toString();
      // Pass UUID as the sole --arguments token — simulates /cat:work-agent invocation with no explicit issue
      String uuid = "92289cdd-76a1-4d7e-8cf3-be5618ec270a";
      WorkPrepare.run(scope, new String[]{"--session-id", sessionId, "--arguments", uuid}, out);

      String output = buffer.toString(StandardCharsets.UTF_8).strip();
      requireThat(output, "output").isNotBlank();

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(output);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");

      worktreePath = Path.of(node.path("worktree_path").asString());
      String issueId = node.path("issue_id").asString();
      requireThat(issueId, "issueId").endsWith("my-feature");
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that when {@code --arguments} contains a CAT agent ID UUID followed by an issue name,
   * {@code run()} strips the UUID and selects the named issue, returning READY with the correct
   * {@code issue_id}.
   * <p>
   * This is the end-to-end regression test for the bug where UUIDs were matched as bare issue names,
   * covering the case where the agent ID and issue name appear together in {@code --arguments}.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void runReturnsReadyWhenArgumentsContainsUuidAndIssueName() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (TestClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "my-feature", "open");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "planning: add issue my-feature");

      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

      String sessionId = UUID.randomUUID().toString();
      // Pass UUID + issue name as --arguments — simulates /cat:work-agent invocation with an explicit issue
      String arguments = "92289cdd-76a1-4d7e-8cf3-be5618ec270a my-feature";
      WorkPrepare.run(scope, new String[]{"--session-id", sessionId, "--arguments", arguments}, out);

      String output = buffer.toString(StandardCharsets.UTF_8).strip();
      requireThat(output, "output").isNotBlank();

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(output);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");

      // Confirm the UUID was stripped and the named issue was selected
      String issueId = node.path("issue_id").asString();
      requireThat(issueId, "issueId").endsWith("my-feature");

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a plan.md containing backtick-quoted text with regex metacharacters
   * (e.g., "[]") does not cause a PatternSyntaxException during execute.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void globToRegexHandlesMetacharacters() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "my-feature", "open");

      // Overwrite the plan.md with a "## Files to Modify" section whose backtick entry
      // contains * and [] — these are regex metacharacters that trigger the bug
      Path planPath = projectPath.resolve(".cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("my-feature").resolve("plan.md");
      Files.writeString(planPath, """
        # Plan: my-feature

        ## Goal

        Test regex metacharacter handling.

        ## Files to Modify

        - Remove the line `- **Dependencies:** []`
        """);

      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add issue with metacharacter plan.md");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isNotEqualTo("ERROR");

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a double-star glob in "Files to Modify" matches a file with zero intermediate
   * directory segments (e.g., {@code src/**} + {@code /Foo.java} matches {@code src/Foo.java}).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void doubleStarGlobMatchesZeroIntermediateSegments() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "glob-feature", "open");

      Path planPath = projectPath.resolve(".cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("glob-feature").resolve("plan.md");
      Files.writeString(planPath, """
        # Plan: glob-feature

        ## Goal

        Test double-star glob support.

        ## Files to Modify

        - `src/**/Foo.java`
        """);

      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "planning: add issue glob-feature");

      // Commit that touches src/Foo.java (zero intermediate segments) on the target branch
      Path srcDir = projectPath.resolve("src");
      Files.createDirectories(srcDir);
      Files.writeString(srcDir.resolve("Foo.java"), "class Foo {}");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "feature: touch src/Foo.java");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "2.1-glob-feature", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      requireThat(node.path("potentially_complete").asBoolean(), "potentiallyComplete").isTrue();
      requireThat(node.path("suspicious_commits").asString(), "suspiciousCommits").isNotBlank();

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a double-star glob in "Files to Modify" matches a file with one intermediate
   * directory segment (e.g., {@code src/**} + {@code /Foo.java} matches {@code src/sub/Foo.java}).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void doubleStarGlobMatchesOneIntermediateSegment() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "glob-feature", "open");

      Path planPath = projectPath.resolve(".cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("glob-feature").resolve("plan.md");
      Files.writeString(planPath, """
        # Plan: glob-feature

        ## Goal

        Test double-star glob support.

        ## Files to Modify

        - `src/**/Foo.java`
        """);

      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "planning: add issue glob-feature");

      // Commit that touches src/sub/Foo.java (one intermediate segment) on the target branch
      Path subDir = projectPath.resolve("src").resolve("sub");
      Files.createDirectories(subDir);
      Files.writeString(subDir.resolve("Foo.java"), "class Foo {}");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "feature: touch src/sub/Foo.java");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "2.1-glob-feature", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      requireThat(node.path("potentially_complete").asBoolean(), "potentiallyComplete").isTrue();
      requireThat(node.path("suspicious_commits").asString(), "suspiciousCommits").isNotBlank();

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a double-star glob in "Files to Modify" matches a file with multiple intermediate
   * directory segments (e.g., {@code src/**} + {@code /Foo.java} matches {@code src/a/b/c/Foo.java}).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void doubleStarGlobMatchesMultipleIntermediateSegments() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "glob-feature", "open");

      Path planPath = projectPath.resolve(".cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("glob-feature").resolve("plan.md");
      Files.writeString(planPath, """
        # Plan: glob-feature

        ## Goal

        Test double-star glob support.

        ## Files to Modify

        - `src/**/Foo.java`
        """);

      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "planning: add issue glob-feature");

      // Commit that touches src/a/b/c/Foo.java (multiple intermediate segments)
      Path deepDir = projectPath.resolve("src").resolve("a").resolve("b").resolve("c");
      Files.createDirectories(deepDir);
      Files.writeString(deepDir.resolve("Foo.java"), "class Foo {}");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "feature: touch src/a/b/c/Foo.java");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "2.1-glob-feature", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      requireThat(node.path("potentially_complete").asBoolean(), "potentiallyComplete").isTrue();
      requireThat(node.path("suspicious_commits").asString(), "suspiciousCommits").isNotBlank();

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a double-star glob in "Files to Modify" does NOT match a file with a different
   * name (e.g., {@code src/**} + {@code /Foo.java} must not match {@code src/Bar.java}).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void doubleStarGlobDoesNotMatchUnrelatedFile() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "glob-feature", "open");

      Path planPath = projectPath.resolve(".cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("glob-feature").resolve("plan.md");
      Files.writeString(planPath, """
        # Plan: glob-feature

        ## Goal

        Test double-star glob support.

        ## Files to Modify

        - `src/**/Foo.java`
        """);

      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "planning: add issue glob-feature");

      // Commit that touches src/Bar.java — different filename, must NOT match src/**/Foo.java
      Path srcDir = projectPath.resolve("src");
      Files.createDirectories(srcDir);
      Files.writeString(srcDir.resolve("Bar.java"), "class Bar {}");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "feature: touch src/Bar.java");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "2.1-glob-feature", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      // src/Bar.java must not match src/**/Foo.java — no suspicious commits
      requireThat(node.path("potentially_complete").asBoolean(), "potentiallyComplete").isFalse();

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a single-star glob in "Files to Modify" still matches correctly
   * after the token-based glob-to-regex rewrite, confirming no regression in the single-star behavior.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void singleStarGlobRegressionUnchanged() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "glob-feature", "open");

      Path planPath = projectPath.resolve(".cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("glob-feature").resolve("plan.md");
      Files.writeString(planPath, """
        # Plan: glob-feature

        ## Goal

        Test single-star glob regression.

        ## Files to Modify

        - `plugin/agents/stakeholder-*.md`
        """);

      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "planning: add issue glob-feature");

      // Commit that touches plugin/agents/stakeholder-concern-box-agent.md
      Path agentsDir = projectPath.resolve("plugin").resolve("agents");
      Files.createDirectories(agentsDir);
      Files.writeString(agentsDir.resolve("stakeholder-concern-box-agent.md"), "# Content");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m",
        "feature: touch stakeholder-concern-box-agent.md");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "2.1-glob-feature", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      requireThat(node.path("potentially_complete").asBoolean(), "potentiallyComplete").isTrue();
      requireThat(node.path("suspicious_commits").asString(), "suspiciousCommits").isNotBlank();

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that the worktree has no uncommitted files after execute returns READY.
   * <p>
   * The index.json update must be committed to the issue branch so that the implement phase
   * does not detect a dirty planning file and block with "BLOCKED: dirty planning file detected."
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeCommitsIndexJsonUpdateSoWorktreeIsClean() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "my-feature", "open");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");

      worktreePath = Path.of(node.path("worktree_path").asString());

      // The worktree must be clean (no uncommitted changes) so the implement phase can proceed.
      // If updateIndexJson() writes index.json without committing it, git status --porcelain
      // returns non-empty output and the implement phase blocks with "dirty planning file".
      String gitStatus = GitCommands.runGit(worktreePath, "status", "--porcelain");
      requireThat(gitStatus.strip(), "gitStatus").isEmpty();
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that a decomposed parent issue whose sub-issues are all closed returns READY (not OVERSIZED),
   * even when the parent plan.md is large enough to normally trigger the OVERSIZED threshold.
   * <p>
   * Regression test for the bug where the token estimation check ran before decomposed-parent detection,
   * causing the large parent plan.md to trigger the size limit even though only closure work is needed.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void decomposedParentWithAllSubIssuesClosedReturnsReady() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try
    {
      try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
      {
        // Create only the plan.md for the parent (createOversizedPlan writes only plan.md)
        createOversizedPlan(projectPath, "2", "1", "big-parent");

        // Write the parent's index.json manually with a decomposedInto array
        Path issueDir = projectPath.resolve(".cat").resolve("issues").
          resolve("v2").resolve("v2.1").resolve("big-parent");
        Files.writeString(issueDir.resolve("index.json"),
          "{\"status\":\"open\",\"decomposedInto\":[\"2.1-closed-sub\"]}");

        // Create the sub-issue with Status: closed
        createIssue(projectPath, "2", "1", "closed-sub", "closed");

        // Commit everything so IssueDiscovery can find the issues
        GitCommands.runGit(projectPath, "add", ".");
        GitCommands.runGit(projectPath, "commit", "-m", "Add decomposed parent issue");

        WorkPrepare prepare = new WorkPrepare(scope);
        PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "", "", TrustLevel.MEDIUM, false);

        String json = prepare.execute(input);

        JsonMapper mapper = scope.getJsonMapper();
        JsonNode node = mapper.readTree(json);

        // A decomposed parent with all sub-issues closed needs only closure work — must be READY
        requireThat(node.path("status").asString(), "status").isEqualTo("READY");
        // Token estimate must be the minimal closure estimate (at most 5000), not the large plan.md size
        requireThat(node.path("estimated_tokens").asInt(), "estimatedTokens").
          isLessThanOrEqualTo(5000);

        if (!node.path("worktree_path").isMissingNode())
          worktreePath = Path.of(node.path("worktree_path").asString());
      }
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that toErrorJson produces valid JSON with correctly escaped embedded double-quote.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void toErrorJsonEscapesEmbeddedDoubleQuote() throws IOException
  {
    Path tempDir = Files.createTempDirectory("work-prepare-json-test");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      String json = WorkPrepare.toErrorJson(scope, "error: field \"name\" is invalid");

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("ERROR");
      requireThat(node.path("message").asString(), "message").isEqualTo("error: field \"name\" is invalid");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that toErrorJson produces valid JSON with correctly escaped embedded backslash.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void toErrorJsonEscapesEmbeddedBackslash() throws IOException
  {
    Path tempDir = Files.createTempDirectory("work-prepare-json-test");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      String json = WorkPrepare.toErrorJson(scope, "path: C:\\Users\\foo");

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("ERROR");
      requireThat(node.path("message").asString(), "message").isEqualTo("path: C:\\Users\\foo");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that toErrorJson produces valid JSON with correctly escaped embedded newline.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void toErrorJsonEscapesEmbeddedNewline() throws IOException
  {
    Path tempDir = Files.createTempDirectory("work-prepare-json-test");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      String json = WorkPrepare.toErrorJson(scope, "line1\nline2");

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("ERROR");
      requireThat(node.path("message").asString(), "message").isEqualTo("line1\nline2");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that toErrorJson produces valid JSON with correctly escaped embedded tab.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void toErrorJsonEscapesEmbeddedTab() throws IOException
  {
    Path tempDir = Files.createTempDirectory("work-prepare-json-test");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      String json = WorkPrepare.toErrorJson(scope, "col1\tcol2");

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("ERROR");
      requireThat(node.path("message").asString(), "message").isEqualTo("col1\tcol2");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that parseRawArguments is safe to invoke concurrently from 20 threads.
   * <p>
   * Pattern is thread-safe but Matcher is not — each call must create its own Matcher instance.
   * This test surfaces any data races by invoking the method simultaneously from 20 threads
   * and asserting that every thread receives the correct result.
   *
   * @throws ExecutionException   if a thread threw an unexpected exception
   * @throws InterruptedException if the test thread is interrupted while waiting
   */
  @Test
  public void parseRawArgumentsIsSafeUnderConcurrentAccess()
    throws ExecutionException, InterruptedException
  {
    int threadCount = 20;
    String agentId = "92289cdd-76a1-4d7e-8cf3-be5618ec270a";
    String issueId = "2.1-fix-bug";
    String rawArguments = agentId + " resume " + issueId;

    try (ExecutorService executor = Executors.newFixedThreadPool(threadCount))
    {
      List<Future<WorkPrepare.ParsedArguments>> futures = new ArrayList<>(threadCount);
      for (int i = 0; i < threadCount; ++i)
        futures.add(executor.submit(() -> WorkPrepare.parseRawArguments(rawArguments, "", "")));

      for (int i = 0; i < threadCount; ++i)
      {
        WorkPrepare.ParsedArguments result = futures.get(i).get();
        requireThat(result.issueId(), "issueId[" + i + "]").isEqualTo(issueId);
        requireThat(result.excludePattern(), "excludePattern[" + i + "]").isEmpty();
      }
    }
  }

  // -------------------------------------------------------------------------
  // Resume mode — ExistingWorktree + resume=true
  // -------------------------------------------------------------------------

  /**
   * Verifies that execute returns READY when resume=true and a different session owns the lock.
   * The lock owner is replaced with the current session.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsReadyWhenResumeAndDifferentSessionOwnsLock() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "resume-diff-session", "open");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add issue");

      // Create a lock file owned by a different session with a recent timestamp
      Path locksDir = scope.getCatWorkPath().resolve("locks");
      Files.createDirectories(locksDir);
      String otherSession = UUID.randomUUID().toString();
      long recentTimestamp = Instant.now().getEpochSecond();
      String lockContent = """
        {
          "session_id": "%s",
          "worktrees": {"/some/worktree": "%s"},
          "created_at": %d,
          "created_iso": "2026-03-01T23:00:00Z"
        }""".formatted(otherSession, otherSession, recentTimestamp);
      Files.writeString(locksDir.resolve("2.1-resume-diff-session.lock"), lockContent);

      // Create an actual git worktree for the issue branch
      worktreePath = scope.getCatWorkPath().resolve("worktrees").resolve("2.1-resume-diff-session");
      Files.createDirectories(worktreePath.getParent());
      GitCommands.runGit(projectPath, "worktree", "add", "-b", "2.1-resume-diff-session",
        worktreePath.toString(), "HEAD");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "2.1-resume-diff-session",
        TrustLevel.MEDIUM, true);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      requireThat(node.path("issue_id").asString(), "issueId").
        isEqualTo("2.1-resume-diff-session");
      requireThat(node.path("worktree_path").asString(), "worktreePath").
        isEqualTo(worktreePath.toString());
      requireThat(node.path("lock_acquired").asBoolean(), "lockAcquired").isTrue();
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that execute returns READY when resume=true, the worktree exists, but no lock exists.
   * A fresh lock is acquired for the current session.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsReadyWhenResumeAndNoLockExists() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "resume-no-lock", "open");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add issue");

      // Create an actual git worktree but do NOT create a lock file
      worktreePath = scope.getCatWorkPath().resolve("worktrees").resolve("2.1-resume-no-lock");
      Files.createDirectories(worktreePath.getParent());
      GitCommands.runGit(projectPath, "worktree", "add", "-b", "2.1-resume-no-lock",
        worktreePath.toString(), "HEAD");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "2.1-resume-no-lock",
        TrustLevel.MEDIUM, true);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      requireThat(node.path("issue_id").asString(), "issueId").
        isEqualTo("2.1-resume-no-lock");
      requireThat(node.path("worktree_path").asString(), "worktreePath").
        isEqualTo(worktreePath.toString());
      requireThat(node.path("lock_acquired").asBoolean(), "lockAcquired").isTrue();
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that execute returns READY when resume=true and the current session already owns the
   * lock. This is the no-regression case: resume with current-session lock behaves identically to
   * the non-resume path.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsReadyWhenResumeAndCurrentSessionOwnsLock() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "resume-own-lock", "open");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add issue");

      // First call creates the worktree and acquires the lock
      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput firstInput = new PrepareInput(sessionId, "", "2.1-resume-own-lock",
        TrustLevel.MEDIUM, false);

      String json1 = prepare.execute(firstInput);
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node1 = mapper.readTree(json1);
      requireThat(node1.path("status").asString(), "firstStatus").isEqualTo("READY");
      worktreePath = Path.of(node1.path("worktree_path").asString());

      // Second call with resume=true and same session ID
      PrepareInput resumeInput = new PrepareInput(sessionId, "", "2.1-resume-own-lock",
        TrustLevel.MEDIUM, true);

      String json2 = prepare.execute(resumeInput);
      JsonNode node2 = mapper.readTree(json2);
      requireThat(node2.path("status").asString(), "secondStatus").isEqualTo("READY");
      requireThat(node2.path("issue_id").asString(), "issueId").
        isEqualTo("2.1-resume-own-lock");
      requireThat(node2.path("worktree_path").asString(), "worktreePath").
        isEqualTo(worktreePath.toString());
      requireThat(node2.path("lock_acquired").asBoolean(), "lockAcquired").isTrue();
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that execute returns READY when resume=true but no worktree exists. The normal
   * new-issue flow creates a fresh worktree.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsReadyWhenResumeAndNoWorktreeExists() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "resume-no-wt", "open");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "2.1-resume-no-wt",
        TrustLevel.MEDIUM, true);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      requireThat(node.path("issue_id").asString(), "issueId").
        isEqualTo("2.1-resume-no-wt");
      requireThat(node.has("worktree_path"), "hasWorktreePath").isTrue();
      worktreePath = Path.of(node.path("worktree_path").asString());
      requireThat(Files.isDirectory(worktreePath), "worktreeExists").isTrue();
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that execute returns ERROR when resume=false and an orphaned worktree exists (no lock).
   * This is the regression guard: without resume, orphaned worktrees still return ERROR.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsErrorWhenNoResumeAndOrphanedWorktree() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (ClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "orphan-wt", "open");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add issue");

      // Create an actual git worktree but do NOT create a lock file
      worktreePath = scope.getCatWorkPath().resolve("worktrees").resolve("2.1-orphan-wt");
      Files.createDirectories(worktreePath.getParent());
      GitCommands.runGit(projectPath, "worktree", "add", "-b", "2.1-orphan-wt",
        worktreePath.toString(), "HEAD");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "2.1-orphan-wt",
        TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("ERROR");
      requireThat(node.path("message").asString(), "message").contains("existing worktree");
      requireThat(node.path("issue_id").asString(), "issueId").isEqualTo("2.1-orphan-wt");
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that run() outputs an ERROR JSON response for an unknown flag, naming the unknown flag
   * and listing valid flags in the message.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void unknownFlagCausesError() throws IOException
  {
    Path tempDir = Files.createTempDirectory("work-prepare-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      WorkPrepare.run(scope, new String[]{"--include-pattern", "foo"}, out);

      String output = buffer.toString(StandardCharsets.UTF_8).strip();
      requireThat(output, "output").isNotBlank();

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode json = mapper.readTree(output);
      requireThat(json.path("status").asString(), "status").isEqualTo("ERROR");
      requireThat(json.path("message").asString(), "message").contains("--include-pattern");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Cleans up a worktree if it exists (best-effort, errors are swallowed).
   *
   * @param projectPath the project root directory
   * @param worktreePath the path to the worktree to remove, or null if no worktree was created
   */
  private void cleanupWorktreeIfExists(Path projectPath, Path worktreePath)
  {
    if (worktreePath != null && Files.exists(worktreePath))
    {
      try
      {
        GitCommands.runGit(projectPath, "worktree", "remove", worktreePath.toString(), "--force");
      }
      catch (IOException _)
      {
        // Best-effort
      }
    }
  }
}
