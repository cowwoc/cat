/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.GitCommands;
import io.github.cowwoc.cat.hooks.util.WorkPrepare;
import io.github.cowwoc.cat.hooks.util.WorkPrepare.PrepareInput;
import io.github.cowwoc.cat.hooks.util.WorkPrepare.TrustLevel;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for WorkPrepare.
 * <p>
 * Tests verify JSON output contracts for all status codes: READY, NO_TASKS, LOCKED, OVERSIZED, and ERROR.
 * Each test is self-contained with temporary git repositories to support parallel execution.
 */
public class WorkPrepareTest
{
  /**
   * Verifies that execute returns ERROR when the project has no .claude/cat/cat-config.json.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsErrorWhenNoCatStructure() throws IOException
  {
    Path projectDir = Files.createTempDirectory("work-prepare-test");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "", "", TrustLevel.MEDIUM);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("ERROR");
      requireThat(node.path("message").asString(), "message").
        contains("cat-config.json");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that execute returns NO_TASKS when there are no executable issues.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsNoTasksWhenNoExecutableIssues() throws IOException
  {
    Path projectDir = createTempCatProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      // Create a closed issue only
      createIssue(projectDir, "2", "1", "done-feature", "closed");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "", "", TrustLevel.MEDIUM);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("NO_TASKS");
      requireThat(node.path("message").asString(), "message").isNotBlank();
      requireThat(node.path("closed_count").asInt(), "closedCount").isEqualTo(1);
      requireThat(node.path("total_count").asInt(), "totalCount").isEqualTo(1);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that execute returns OVERSIZED when the PLAN.md estimates too many tokens.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsOversizedWhenTokensExceedLimit() throws IOException
  {
    Path projectDir = createTempGitCatProject("v2.1");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      createIssue(projectDir, "2", "1", "huge-feature", "open");
      createOversizedPlan(projectDir, "2", "1", "huge-feature");

      // Commit the issue so it's visible in the git repo
      GitCommands.runGit(projectDir, "add", ".");
      GitCommands.runGit(projectDir, "commit", "-m", "Add oversized issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "", "", TrustLevel.MEDIUM);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("OVERSIZED");
      requireThat(node.path("issue_id").asString(), "issueId").isEqualTo("2.1-huge-feature");
      requireThat(node.path("estimated_tokens").asInt(), "estimatedTokens").isGreaterThan(160_000);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      createIssue(projectDir, "2", "1", "my-feature", "open");
      GitCommands.runGit(projectDir, "add", ".");
      GitCommands.runGit(projectDir, "commit", "-m", "Add issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);

      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      requireThat(node.path("issue_id").asString(), "issueId").isEqualTo("2.1-my-feature");
      requireThat(node.path("major").asString(), "major").isEqualTo("2");
      requireThat(node.path("minor").asString(), "minor").isEqualTo("1");
      requireThat(node.path("issue_name").asString(), "issueName").isEqualTo("my-feature");
      requireThat(node.path("branch").asString(), "branch").isEqualTo("2.1-my-feature");
      requireThat(node.path("base_branch").asString(), "baseBranch").isEqualTo("v2.1");
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
      cleanupWorktreeIfExists(projectDir, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that execute sets STATE.md to in-progress after creating a worktree.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeUpdatesStateMdToInProgress() throws IOException
  {
    Path projectDir = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      createIssue(projectDir, "2", "1", "my-feature", "open");
      GitCommands.runGit(projectDir, "add", ".");
      GitCommands.runGit(projectDir, "commit", "-m", "Add issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");

      worktreePath = Path.of(node.path("worktree_path").asString());

      // Read the STATE.md from the worktree
      Path stateFile = worktreePath.resolve(".claude").resolve("cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("my-feature").resolve("STATE.md");
      String stateContent = Files.readString(stateFile);

      requireThat(stateContent, "stateContent").contains("in-progress");
    }
    finally
    {
      cleanupWorktreeIfExists(projectDir, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      createIssue(projectDir, "2", "1", "first-feature", "open");
      createIssue(projectDir, "2", "1", "second-feature", "open");
      GitCommands.runGit(projectDir, "add", ".");
      GitCommands.runGit(projectDir, "commit", "-m", "Add issues");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      // Request specific issue that would not be selected by priority
      PrepareInput input = new PrepareInput(sessionId, "", "2.1-second-feature", TrustLevel.MEDIUM);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      requireThat(node.path("issue_id").asString(), "issueId").isEqualTo("2.1-second-feature");

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectDir, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      createIssue(projectDir, "2", "1", "fix-bug", "open");
      GitCommands.runGit(projectDir, "add", ".");
      GitCommands.runGit(projectDir, "commit", "-m", "Add fix-bug issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "fix-bug", TrustLevel.MEDIUM);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      requireThat(node.path("issue_id").asString(), "issueId").isEqualTo("2.1-fix-bug");

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectDir, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that execute applies exclude pattern and returns NO_TASKS when all issues are excluded.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsNoTasksWhenAllIssuesExcluded() throws IOException
  {
    Path projectDir = createTempCatProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      createIssue(projectDir, "2", "1", "compress-feature", "open");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*compress*", "", TrustLevel.MEDIUM);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("NO_TASKS");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that execute includes estimated_tokens and percent_of_threshold in READY result.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeIncludesEstimatedTokensInReadyResult() throws IOException
  {
    Path projectDir = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      createIssue(projectDir, "2", "1", "my-feature", "open");
      createSimplePlan(projectDir, "2", "1", "my-feature");
      GitCommands.runGit(projectDir, "add", ".");
      GitCommands.runGit(projectDir, "commit", "-m", "Add issue with plan");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM);

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
      cleanupWorktreeIfExists(projectDir, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that execute includes goal in READY result when PLAN.md has a Goal section.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeIncludesGoalFromPlanMd() throws IOException
  {
    Path projectDir = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      createIssue(projectDir, "2", "1", "my-feature", "open");
      createPlanWithGoal(projectDir, "2", "1", "my-feature", "Implement the best feature ever");
      GitCommands.runGit(projectDir, "add", ".");
      GitCommands.runGit(projectDir, "commit", "-m", "Add issue with goal");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM);

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
      cleanupWorktreeIfExists(projectDir, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that execute includes preconditions in READY result when PLAN.md has a Pre-conditions section.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeIncludesPreconditionsFromPlanMd() throws IOException
  {
    Path projectDir = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      createIssue(projectDir, "2", "1", "my-feature", "open");
      createPlanWithPreconditions(projectDir, "2", "1", "my-feature",
        new String[]{"All tests pass", "Branch is up to date"});
      GitCommands.runGit(projectDir, "add", ".");
      GitCommands.runGit(projectDir, "commit", "-m", "Add issue with preconditions");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      JsonNode preconditions = node.path("preconditions");
      requireThat(preconditions.isMissingNode(), "hasPreconditions").isFalse();
      requireThat(preconditions.isArray(), "preconditionsIsArray").isTrue();
      requireThat(preconditions.size(), "preconditionCount").isEqualTo(2);
      requireThat(preconditions.get(0).asString(), "precondition0").isEqualTo("All tests pass");
      requireThat(preconditions.get(1).asString(), "precondition1").isEqualTo("Branch is up to date");

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectDir, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that execute returns an empty preconditions list when PLAN.md has no Pre-conditions section.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsEmptyPreconditionsWhenSectionMissing() throws IOException
  {
    Path projectDir = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      createIssue(projectDir, "2", "1", "no-preconditions", "open");

      // Create a PLAN.md without a Pre-conditions section
      Path issueDir = projectDir.resolve(".claude").resolve("cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("no-preconditions");
      String planContent = """
        # Plan

        ## Goal

        A simple feature

        ## Execution Steps

        1. Do the work
        """;
      Files.writeString(issueDir.resolve("PLAN.md"), planContent);

      GitCommands.runGit(projectDir, "add", ".");
      GitCommands.runGit(projectDir, "commit", "-m", "Add issue without preconditions");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      JsonNode preconditions = node.path("preconditions");
      requireThat(preconditions.isMissingNode(), "hasPreconditions").isFalse();
      requireThat(preconditions.isArray(), "preconditionsIsArray").isTrue();
      requireThat(preconditions.size(), "preconditionCount").isEqualTo(0);

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectDir, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = createTempGitCatProject("v2.1");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      createIssue(projectDir, "2", "1", "locked-feature", "open");
      GitCommands.runGit(projectDir, "add", ".");
      GitCommands.runGit(projectDir, "commit", "-m", "Add issue");

      // Create a lock file owned by a different session
      Path locksDir = projectDir.resolve(".claude").resolve("cat").resolve("locks");
      Files.createDirectories(locksDir);
      String lockContent = """
        {
          "session_id": "%s",
          "created_at": 1700000000,
          "worktree": "",
          "created_iso": "2026-02-01T00:00:00Z"
        }""".formatted(UUID.randomUUID().toString());
      Files.writeString(locksDir.resolve("2.1-locked-feature.lock"), lockContent);

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "2.1-locked-feature", TrustLevel.MEDIUM);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("LOCKED");
      requireThat(node.path("message").asString(), "message").contains("locked");
      requireThat(node.path("issue_id").asString(), "issueId").isEqualTo("2.1-locked-feature");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that execute detects suspicious commits on the base branch and populates
   * potentially_complete and suspicious_commits fields in the READY result.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeDetectsSuspiciousCommitsOnBaseBranch() throws IOException
  {
    Path projectDir = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      createIssue(projectDir, "2", "1", "suspicious-feature", "open");
      GitCommands.runGit(projectDir, "add", ".");
      GitCommands.runGit(projectDir, "commit", "-m", "Add suspicious-feature issue");

      // Create a non-planning commit on the base branch that mentions the issue name
      Files.writeString(projectDir.resolve("impl.txt"), "implementation work");
      GitCommands.runGit(projectDir, "add", "impl.txt");
      GitCommands.runGit(projectDir, "commit", "-m",
        "feature: implement suspicious-feature changes");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "2.1-suspicious-feature", TrustLevel.MEDIUM);

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
      cleanupWorktreeIfExists(projectDir, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that execute includes has_existing_work, existing_commits, and commit_summary fields
   * in the READY result. When a fresh issue branch is created from HEAD (no prior work),
   * has_existing_work is false and existing_commits is 0.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeIncludesExistingWorkFieldsInReadyResult() throws IOException
  {
    Path projectDir = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      createIssue(projectDir, "2", "1", "fresh-feature", "open");
      GitCommands.runGit(projectDir, "add", ".");
      GitCommands.runGit(projectDir, "commit", "-m", "Add issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "2.1-fresh-feature", TrustLevel.MEDIUM);

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
      cleanupWorktreeIfExists(projectDir, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that execute returns READY with goal "No goal found" when PLAN.md lacks a Goal section.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsReadyWhenGoalSectionMissing() throws IOException
  {
    Path projectDir = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      createIssue(projectDir, "2", "1", "no-goal", "open");

      // Create a PLAN.md without a Goal section
      Path issueDir = projectDir.resolve(".claude").resolve("cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("no-goal");
      String planContent = """
        # Plan

        ## Execution Steps

        1. Do the work
        """;
      Files.writeString(issueDir.resolve("PLAN.md"), planContent);

      GitCommands.runGit(projectDir, "add", ".");
      GitCommands.runGit(projectDir, "commit", "-m", "Add issue without goal");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      requireThat(node.path("goal").asString(), "goal").isEqualTo("No goal found");

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectDir, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that execute returns NO_TASKS with blocked_tasks diagnostic when an issue has
   * unresolved dependencies.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsNoTasksWithBlockedTasksDiagnostic() throws IOException
  {
    Path projectDir = createTempGitCatProject("v2.1");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      // Create a dependency issue that is open (not closed)
      createIssue(projectDir, "2", "1", "dependency-issue", "open");

      // Create a blocked issue with dependencies
      createIssueWithDependencies(projectDir, "2", "1", "blocked-feature", "open",
        "2.1-dependency-issue");

      GitCommands.runGit(projectDir, "add", ".");
      GitCommands.runGit(projectDir, "commit", "-m", "Add blocked issues");

      WorkPrepare prepare = new WorkPrepare(scope);
      // Exclude all issues so IssueDiscovery returns NotFound, triggering diagnostics
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("NO_TASKS");
      requireThat(node.has("blocked_tasks"), "hasBlockedTasks").isTrue();

      JsonNode blockedTasks = node.path("blocked_tasks");
      requireThat(blockedTasks.size(), "blockedTaskCount").isGreaterThan(0);

      // Find the blocked-feature entry
      boolean foundBlockedFeature = false;
      for (JsonNode task : blockedTasks)
      {
        if (task.path("issue_id").asString().equals("2.1-blocked-feature"))
        {
          foundBlockedFeature = true;
          requireThat(task.has("blocked_by"), "hasBlockedBy").isTrue();
          requireThat(task.path("reason").asString(), "reason").contains("2.1-dependency-issue");
        }
      }
      requireThat(foundBlockedFeature, "foundBlockedFeature").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = createTempGitCatProject("v2.1");
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      createIssue(projectDir, "2", "1", "boundary-feature", "open");

      // 32 files to create: 32 * 5000 = 160,000 + 10,000 base = 170,000 > 160,000
      Path issueDir = projectDir.resolve(".claude").resolve("cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("boundary-feature");
      StringBuilder planContent = new StringBuilder(800);
      planContent.append("# Plan\n\n## Files to Create\n\n");
      for (int i = 0; i < 32; ++i)
        planContent.append("- src/main/java/Feature").append(i).append(".java\n");

      planContent.append("\n## Execution Steps\n\n1. Implement\n");
      Files.writeString(issueDir.resolve("PLAN.md"), planContent.toString());

      GitCommands.runGit(projectDir, "add", ".");
      GitCommands.runGit(projectDir, "commit", "-m", "Add boundary issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "", "", TrustLevel.MEDIUM);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("OVERSIZED");
      // 32 * 5000 + 1 * 2000 + 10000 = 172000
      requireThat(node.path("estimated_tokens").asInt(), "estimatedTokens").isGreaterThan(160_000);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
      Path projectDir = Files.createTempDirectory("work-prepare-test");
      Path catDir = projectDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir.resolve("issues"));
      Files.writeString(catDir.resolve("cat-config.json"), "{}");
      return projectDir;
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
    Path projectDir = TestUtils.createTempGitRepo(branchName);

    // Add CAT structure
    Path catDir = projectDir.resolve(".claude").resolve("cat");
    Files.createDirectories(catDir.resolve("issues"));
    Files.writeString(catDir.resolve("cat-config.json"), "{}");

    // Commit the CAT structure so worktrees have it
    GitCommands.runGit(projectDir, "add", ".");
    GitCommands.runGit(projectDir, "commit", "-m", "Add CAT structure");

    return projectDir;
  }

  /**
   * Creates an issue directory with a minimal STATE.md.
   *
   * @param projectDir the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param issueName the issue name
   * @param status the issue status
   * @throws IOException if file creation fails
   */
  private void createIssue(Path projectDir, String major, String minor, String issueName,
    String status) throws IOException
  {
    Path issueDir = projectDir.resolve(".claude").resolve("cat").resolve("issues").
      resolve("v" + major).resolve("v" + major + "." + minor).resolve(issueName);
    Files.createDirectories(issueDir);

    String stateContent = """
      # State

      - **Status:** %s
      - **Progress:** 0%%
      - **Dependencies:** []
      - **Blocks:** []
      """.formatted(status);

    Files.writeString(issueDir.resolve("STATE.md"), stateContent);
  }

  /**
   * Creates an issue directory with a STATE.md that has dependencies.
   *
   * @param projectDir the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param issueName the issue name
   * @param status the issue status
   * @param dependencies comma-separated dependency IDs
   * @throws IOException if file creation fails
   */
  private void createIssueWithDependencies(Path projectDir, String major, String minor,
    String issueName, String status, String dependencies) throws IOException
  {
    Path issueDir = projectDir.resolve(".claude").resolve("cat").resolve("issues").
      resolve("v" + major).resolve("v" + major + "." + minor).resolve(issueName);
    Files.createDirectories(issueDir);

    String stateContent = """
      # State

      - **Status:** %s
      - **Progress:** 0%%
      - **Dependencies:** [%s]
      - **Blocks:** []
      """.formatted(status, dependencies);

    Files.writeString(issueDir.resolve("STATE.md"), stateContent);
  }

  /**
   * Creates a PLAN.md with enough files and steps to produce an oversized estimate (&gt; 160000 tokens).
   *
   * @param projectDir the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param issueName the issue name
   * @throws IOException if file creation fails
   */
  private void createOversizedPlan(Path projectDir, String major, String minor,
    String issueName) throws IOException
  {
    Path issueDir = projectDir.resolve(".claude").resolve("cat").resolve("issues").
      resolve("v" + major).resolve("v" + major + "." + minor).resolve(issueName);
    Files.createDirectories(issueDir);

    // 35 files to create at 5000 tokens each = 175000 tokens (exceeds 160000)
    StringBuilder planContent = new StringBuilder(1200);
    planContent.append("# Plan\n\n## Files to Create\n\n");
    for (int i = 0; i < 35; ++i)
      planContent.append("- src/main/java/Feature").append(i).append(".java\n");

    planContent.append("\n## Execution Steps\n\n1. Implement all features\n");

    Files.writeString(issueDir.resolve("PLAN.md"), planContent.toString());
  }

  /**
   * Creates a simple PLAN.md with a few files and steps.
   *
   * @param projectDir the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param issueName the issue name
   * @throws IOException if file creation fails
   */
  private void createSimplePlan(Path projectDir, String major, String minor,
    String issueName) throws IOException
  {
    Path issueDir = projectDir.resolve(".claude").resolve("cat").resolve("issues").
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

    Files.writeString(issueDir.resolve("PLAN.md"), planContent);
  }

  /**
   * Creates a PLAN.md with a specific goal text.
   *
   * @param projectDir the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param issueName the issue name
   * @param goalText the goal text to include
   * @throws IOException if file creation fails
   */
  private void createPlanWithGoal(Path projectDir, String major, String minor,
    String issueName, String goalText) throws IOException
  {
    Path issueDir = projectDir.resolve(".claude").resolve("cat").resolve("issues").
      resolve("v" + major).resolve("v" + major + "." + minor).resolve(issueName);
    Files.createDirectories(issueDir);

    String planContent = """
      # Plan

      ## Goal

      %s

      ## Execution Steps

      1. Do the work
      """.formatted(goalText);

    Files.writeString(issueDir.resolve("PLAN.md"), planContent);
  }

  /**
   * Verifies that execute includes pre-conditions with checked items (- [x]) in the READY result.
   * Checked items represent completed pre-conditions and must still be included.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeIncludesPreconditionsWithCheckedItems() throws IOException
  {
    Path projectDir = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      createIssue(projectDir, "2", "1", "my-feature", "open");
      createPlanWithMixedPreconditions(projectDir, "2", "1", "my-feature",
        new String[]{"Unchecked item"},
        new String[]{"Checked item"});
      GitCommands.runGit(projectDir, "add", ".");
      GitCommands.runGit(projectDir, "commit", "-m", "Add issue with checked preconditions");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      JsonNode preconditions = node.path("preconditions");
      requireThat(preconditions.size(), "preconditionCount").isEqualTo(2);
      requireThat(preconditions.get(0).asString(), "precondition0").isEqualTo("Unchecked item");
      requireThat(preconditions.get(1).asString(), "precondition1").isEqualTo("Checked item");

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectDir, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that execute returns an empty preconditions list when the Pre-conditions section exists
   * but contains no checkbox items.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsEmptyPreconditionsWhenSectionHasNoItems() throws IOException
  {
    Path projectDir = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      createIssue(projectDir, "2", "1", "empty-precond", "open");

      Path issueDir = projectDir.resolve(".claude").resolve("cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("empty-precond");
      String planContent = """
        # Plan

        ## Pre-conditions

        ## Execution Steps

        1. Do the work
        """;
      Files.writeString(issueDir.resolve("PLAN.md"), planContent);

      GitCommands.runGit(projectDir, "add", ".");
      GitCommands.runGit(projectDir, "commit", "-m", "Add issue with empty preconditions section");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      JsonNode preconditions = node.path("preconditions");
      requireThat(preconditions.isMissingNode(), "hasPreconditions").isFalse();
      requireThat(preconditions.isArray(), "preconditionsIsArray").isTrue();
      requireThat(preconditions.size(), "preconditionCount").isEqualTo(0);

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectDir, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that execute correctly reads preconditions when the Pre-conditions section is the last
   * section in PLAN.md with no trailing newline.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeIncludesPreconditionsWhenSectionAtEndOfFile() throws IOException
  {
    Path projectDir = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      createIssue(projectDir, "2", "1", "eof-precond", "open");

      Path issueDir = projectDir.resolve(".claude").resolve("cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("eof-precond");
      // No trailing newline after the last item
      String planContent = "# Plan\n\n## Execution Steps\n\n1. Do the work\n\n## Pre-conditions\n\n- [ ] Final check";
      Files.writeString(issueDir.resolve("PLAN.md"), planContent);

      GitCommands.runGit(projectDir, "add", ".");
      GitCommands.runGit(projectDir, "commit", "-m", "Add issue with preconditions at EOF");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("READY");
      JsonNode preconditions = node.path("preconditions");
      requireThat(preconditions.size(), "preconditionCount").isEqualTo(1);
      requireThat(preconditions.get(0).asString(), "precondition0").isEqualTo("Final check");

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectDir, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Creates a PLAN.md with a Pre-conditions section containing the given checkbox items.
   *
   * @param projectDir the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param issueName the issue name
   * @param preconditions the pre-condition text items to include as unchecked checkboxes
   * @throws IOException if file creation fails
   */
  private void createPlanWithPreconditions(Path projectDir, String major, String minor,
    String issueName, String[] preconditions) throws IOException
  {
    Path issueDir = projectDir.resolve(".claude").resolve("cat").resolve("issues").
      resolve("v" + major).resolve("v" + major + "." + minor).resolve(issueName);
    Files.createDirectories(issueDir);

    StringBuilder planContent = new StringBuilder(128);
    planContent.append("# Plan\n\n## Pre-conditions\n\n");
    for (String precondition : preconditions)
      planContent.append("- [ ] ").append(precondition).append('\n');
    planContent.append("\n## Execution Steps\n\n1. Do the work\n");

    Files.writeString(issueDir.resolve("PLAN.md"), planContent.toString());
  }

  /**
   * Creates a PLAN.md with a Pre-conditions section containing both unchecked and checked checkbox items.
   *
   * @param projectDir the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param issueName the issue name
   * @param uncheckedItems the unchecked pre-condition items to include as unchecked checkboxes
   * @param checkedItems the checked pre-condition items to include as checked checkboxes
   * @throws IOException if file creation fails
   */
  private void createPlanWithMixedPreconditions(Path projectDir, String major, String minor,
    String issueName, String[] uncheckedItems, String[] checkedItems) throws IOException
  {
    Path issueDir = projectDir.resolve(".claude").resolve("cat").resolve("issues").
      resolve("v" + major).resolve("v" + major + "." + minor).resolve(issueName);
    Files.createDirectories(issueDir);

    StringBuilder planContent = new StringBuilder(128);
    planContent.append("# Plan\n\n## Pre-conditions\n\n");
    for (String item : uncheckedItems)
      planContent.append("- [ ] ").append(item).append('\n');
    for (String item : checkedItems)
      planContent.append("- [x] ").append(item).append('\n');
    planContent.append("\n## Execution Steps\n\n1. Do the work\n");

    Files.writeString(issueDir.resolve("PLAN.md"), planContent.toString());
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
    Path projectDir = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      // Create an issue with a bare name
      createIssue(projectDir, "2", "1", "fix-bug", "open");
      GitCommands.runGit(projectDir, "add", ".");
      GitCommands.runGit(projectDir, "commit", "-m", "Add bare-named issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      // Pass bare issue name (no version prefix) - should resolve via BARE_NAME scope
      PrepareInput input = new PrepareInput(sessionId, "", "fix-bug", TrustLevel.MEDIUM);

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
      cleanupWorktreeIfExists(projectDir, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Cleans up a worktree if it exists (best-effort, errors are swallowed).
   *
   * @param projectDir the project root directory
   * @param worktreePath the path to the worktree to remove, or null if no worktree was created
   */
  private void cleanupWorktreeIfExists(Path projectDir, Path worktreePath)
  {
    if (worktreePath != null && Files.exists(worktreePath))
    {
      try
      {
        GitCommands.runGit(projectDir, "worktree", "remove", worktreePath.toString(), "--force");
      }
      catch (IOException _)
      {
        // Best-effort
      }
    }
  }
}
