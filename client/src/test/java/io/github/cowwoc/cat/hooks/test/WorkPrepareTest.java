/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.GitCommands;
import io.github.cowwoc.cat.hooks.util.TrustLevel;
import io.github.cowwoc.cat.hooks.util.WorkPrepare;
import io.github.cowwoc.cat.hooks.util.WorkPrepare.PrepareInput;
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
 * Tests verify JSON output contracts for all status codes: READY, NO_ISSUES, LOCKED, OVERSIZED, and ERROR.
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
   * Verifies that execute returns NO_ISSUES when there are no executable issues.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsNoIssuesWhenNoExecutableIssues() throws IOException
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
      requireThat(node.path("status").asString(), "status").isEqualTo("NO_ISSUES");
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
   * Verifies that execute applies exclude pattern and returns NO_ISSUES when all issues are excluded.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsNoIssuesWhenAllIssuesExcluded() throws IOException
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
      requireThat(node.path("status").asString(), "status").isEqualTo("NO_ISSUES");
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
   * Verifies that execute returns NO_ISSUES with blocked_issues diagnostic when an issue has
   * unresolved dependencies.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsNoIssuesWithBlockedIssuesDiagnostic() throws IOException
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
    Path projectDir = createTempGitCatProject("v2.1");
    Path worktreePath1 = null;
    Path worktreePath2 = null;
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      // Create a bare-named issue
      createIssue(projectDir, "2", "1", "fix-bug", "open");
      GitCommands.runGit(projectDir, "add", ".");
      GitCommands.runGit(projectDir, "commit", "-m", "Add fix-bug issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();

      // Test 1: Bare name 'fix-bug' should resolve via BARE_NAME scope
      PrepareInput bareNameInput = new PrepareInput(sessionId, "", "fix-bug", TrustLevel.MEDIUM);
      String bareNameJson = prepare.execute(bareNameInput);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode bareNameNode = mapper.readTree(bareNameJson);
      requireThat(bareNameNode.path("status").asString(), "bareNameStatus").isEqualTo("READY");
      requireThat(bareNameNode.path("issue_id").asString(), "bareNameIssueId").isEqualTo("2.1-fix-bug");
      worktreePath1 = Path.of(bareNameNode.path("worktree_path").asString());

      // Release the lock by removing the worktree and lock file so we can test qualified name
      if (worktreePath1 != null && Files.exists(worktreePath1))
      {
        GitCommands.runGit(projectDir, "worktree", "remove", worktreePath1.toString(), "--force");
        worktreePath1 = null;
      }
      // Clean up the lock file for the bare name issue
      Path locksDir = projectDir.resolve(".claude").resolve("cat").resolve("locks");
      Path lockFile = locksDir.resolve("2.1-fix-bug.lock");
      if (Files.exists(lockFile))
        Files.delete(lockFile);

      // Test 2: Qualified name '2.1-fix-bug' should resolve via ISSUE scope
      sessionId = UUID.randomUUID().toString();
      PrepareInput qualifiedNameInput = new PrepareInput(sessionId, "", "2.1-fix-bug", TrustLevel.MEDIUM);
      String qualifiedNameJson = prepare.execute(qualifiedNameInput);

      JsonNode qualifiedNameNode = mapper.readTree(qualifiedNameJson);
      requireThat(qualifiedNameNode.path("status").asString(), "qualifiedNameStatus").isEqualTo("READY");
      requireThat(qualifiedNameNode.path("issue_id").asString(), "qualifiedNameIssueId").isEqualTo("2.1-fix-bug");
      worktreePath2 = Path.of(qualifiedNameNode.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectDir, worktreePath1);
      cleanupWorktreeIfExists(projectDir, worktreePath2);
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      // Create an issue with a bare name
      createIssue(projectDir, "2", "1", "fix-bug", "open");
      GitCommands.runGit(projectDir, "add", ".");
      GitCommands.runGit(projectDir, "commit", "-m", "Add fix-bug issue");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();

      // Pass bare issue name directly - must be detected as BARE_NAME scope
      // and resolve to the correct qualified issue
      PrepareInput input = new PrepareInput(sessionId, "", "fix-bug", TrustLevel.MEDIUM);
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
      cleanupWorktreeIfExists(projectDir, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that diagnostic output correctly resolves issue dependencies even when the project has
   * more than 333 issues (each with ~3 files = ~1000 filesystem entries). With the old scan limit of
   * 1000 entries, issues beyond that limit would appear as not_found in blocked_issues diagnostic
   * output, causing false positives. With the fixed unlimited scan, a closed dependency is correctly
   * resolved, so an issue depending only on closed issues does not appear in blocked_issues at all.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void diagnosticAccurateWithMoreThan333Issues() throws IOException
  {
    Path projectDir = createTempCatProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      // Create 350 issues to exceed the old 1000-entry limit (each issue has ~3 files)
      // Issues are distributed across multiple minor versions for realism
      for (int i = 0; i < 350; ++i)
      {
        String minor = String.valueOf(i / 50 + 1);
        createIssue(projectDir, "2", minor, "issue-" + i, "closed");
      }

      // Create a dependency issue that would be pushed past entry 999 in the old limit
      createIssue(projectDir, "2", "8", "dependency-far", "closed");
      createIssueWithDependencies(projectDir, "2", "8", "blocked-by-far", "open",
        "2.8-dependency-far");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      requireThat(node.path("status").asString(), "status").isEqualTo("NO_ISSUES");

      // With the fixed unlimited scan, dependency-far is correctly resolved as "closed".
      // Since its only dependency is closed, blocked-by-far must NOT appear in blocked_issues.
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
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = createTempCatProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      // Create 400 issues — enough to have exceeded the old 1000 file limit
      for (int i = 0; i < 400; ++i)
      {
        String minor = String.valueOf(i / 50 + 1);
        createIssue(projectDir, "2", minor, "issue-" + i, "closed");
      }

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      // Should complete normally (not ERROR), just report NO_ISSUES with full diagnostics
      requireThat(node.path("status").asString(), "status").isEqualTo("NO_ISSUES");
      requireThat(node.path("total_count").asInt(), "totalCount").isGreaterThanOrEqualTo(400);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Verifies that circular dependencies (A depends on B, B depends on A) are detected and reported
   * in the diagnostic output under a "circular_dependencies" field.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void diagnosticReportsSimpleCircularDependency() throws IOException
  {
    Path projectDir = createTempCatProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      // A depends on B, B depends on A — simple cycle
      createIssueWithDependencies(projectDir, "2", "1", "issue-a", "open", "2.1-issue-b");
      createIssueWithDependencies(projectDir, "2", "1", "issue-b", "open", "2.1-issue-a");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM);

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
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = createTempCatProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      // A -> B -> C -> A (3-node cycle)
      createIssueWithDependencies(projectDir, "2", "1", "issue-a", "open", "2.1-issue-b");
      createIssueWithDependencies(projectDir, "2", "1", "issue-b", "open", "2.1-issue-c");
      createIssueWithDependencies(projectDir, "2", "1", "issue-c", "open", "2.1-issue-a");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM);

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
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = createTempCatProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      // Linear chain: A depends on B, B depends on C (no cycle)
      createIssueWithDependencies(projectDir, "2", "1", "issue-a", "open", "2.1-issue-b");
      createIssueWithDependencies(projectDir, "2", "1", "issue-b", "open", "2.1-issue-c");
      createIssue(projectDir, "2", "1", "issue-c", "open");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM);

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
      TestUtils.deleteDirectoryRecursively(projectDir);
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
  @Test
  public void buildIssueIndexThrowsWhenScanLimitExceeded() throws IOException
  {
    Path projectDir = createTempCatProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      // Create 2 issues sharing the same version dirs: issuesDir + v2 + v2.1 + 2*(dir+STATE.md) = 7 entries
      createIssue(projectDir, "2", "1", "issue-alpha", "closed");
      createIssue(projectDir, "2", "1", "issue-beta", "closed");

      // Use threshold=5 so that 7 entries > 5 triggers the error
      WorkPrepare prepare = new WorkPrepare(scope, 5, 1000);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM);

      try
      {
        prepare.execute(input);
      }
      catch (IOException e)
      {
        requireThat(e.getMessage(), "message").contains("safety threshold");
        requireThat(e.getMessage(), "message").contains("at least 5");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
  @Test
  public void detectCyclesThrowsWhenDepthLimitExceeded() throws IOException
  {
    Path projectDir = createTempCatProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      // issue-a depends on issue-b; with maxDepth=0 the first recursive call (depth=1) triggers
      createIssueWithDependencies(projectDir, "2", "1", "issue-a", "open", "2.1-issue-b");
      createIssue(projectDir, "2", "1", "issue-b", "open");

      // Use maxCycleDetectionDepth=0 so any recursive call (depth=1 > 0) triggers the error
      WorkPrepare prepare = new WorkPrepare(scope, 100_000, 0);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM);

      try
      {
        prepare.execute(input);
      }
      catch (IOException e)
      {
        requireThat(e.getMessage(), "message").contains("maximum recursion depth");
        requireThat(e.getMessage(), "message").contains("0");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = createTempCatProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      // Build a linear chain: issue-0 -> ... -> issue-5 (6 nodes, max depth = 5 == limit, no throw)
      createIssueWithDependencies(projectDir, "2", "1", "issue-0", "open", "2.1-issue-1");
      createIssueWithDependencies(projectDir, "2", "1", "issue-1", "open", "2.1-issue-2");
      createIssueWithDependencies(projectDir, "2", "1", "issue-2", "open", "2.1-issue-3");
      createIssueWithDependencies(projectDir, "2", "1", "issue-3", "open", "2.1-issue-4");
      createIssueWithDependencies(projectDir, "2", "1", "issue-4", "open", "2.1-issue-5");
      createIssue(projectDir, "2", "1", "issue-5", "open");

      // Use maxCycleDetectionDepth=5 so depth 5 == limit, should NOT throw
      WorkPrepare prepare = new WorkPrepare(scope, 100_000, 5);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      // Should complete normally — no cycle in a linear chain
      requireThat(node.path("status").asString(), "status").isEqualTo("NO_ISSUES");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = createTempCatProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      // A depends on decomposed B (which has sub-issue C depending on A)
      createIssueWithDependencies(projectDir, "2", "1", "issue-a", "open", "2.1-issue-b");
      createDecomposedIssue(projectDir, "2", "1", "issue-b", "issue-c");
      createIssueWithDependencies(projectDir, "2", "1", "issue-c", "open", "2.1-issue-a");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM);

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
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = createTempCatProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      // A -> B(decomposed->C) -> C(decomposed->D) -> D -> A
      createIssueWithDependencies(projectDir, "2", "1", "issue-a", "open", "2.1-issue-b");
      createDecomposedIssue(projectDir, "2", "1", "issue-b", "issue-c");
      createDecomposedIssue(projectDir, "2", "1", "issue-c", "issue-d");
      createIssueWithDependencies(projectDir, "2", "1", "issue-d", "open", "2.1-issue-a");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM);

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
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = createTempCatProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      // A depends on decomposed B; B has sub-issue C; C has no further deps
      createIssueWithDependencies(projectDir, "2", "1", "issue-a", "open", "2.1-issue-b");
      createDecomposedIssue(projectDir, "2", "1", "issue-b", "issue-c");
      createIssue(projectDir, "2", "1", "issue-c", "open");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM);

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
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = createTempCatProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      // Direct cycle: issue-x depends on issue-y, issue-y depends on issue-x
      createIssueWithDependencies(projectDir, "2", "1", "issue-x", "open", "2.1-issue-y");
      createIssueWithDependencies(projectDir, "2", "1", "issue-y", "open", "2.1-issue-x");

      // Decomposed cycle: issue-a depends on decomposed issue-b, issue-b's sub-issue issue-c depends on issue-a
      createIssueWithDependencies(projectDir, "2", "1", "issue-a", "open", "2.1-issue-b");
      createDecomposedIssue(projectDir, "2", "1", "issue-b", "issue-c");
      createIssueWithDependencies(projectDir, "2", "1", "issue-c", "open", "2.1-issue-a");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM);

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
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = createTempCatProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      // A depends on decomposed B; B lists fully-qualified "2.1-issue-sub" which cycles back to A
      createIssueWithDependencies(projectDir, "2", "1", "issue-a", "open", "2.1-issue-b");
      createDecomposedIssue(projectDir, "2", "1", "issue-b", "issue-sub");
      createIssue(projectDir, "2", "0", "issue-sub", "open");
      createIssueWithDependencies(projectDir, "2", "1", "issue-sub", "open", "2.1-issue-a");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM);

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
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = createTempCatProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      // issue-a depends on bare name "shared-dep" (ambiguous: matches both 2.0 and 2.1 versions)
      createIssueWithDependencies(projectDir, "2", "1", "issue-a", "open", "shared-dep");
      // Two issues share the bare name "shared-dep"
      createIssue(projectDir, "2", "0", "shared-dep", "open");
      // Only the 2.1 variant cycles back to issue-a
      createIssueWithDependencies(projectDir, "2", "1", "shared-dep", "open", "2.1-issue-a");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM);

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
      TestUtils.deleteDirectoryRecursively(projectDir);
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
    Path projectDir = createTempCatProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      // B is a decomposed parent with one valid and several malformed entries
      createIssueWithDependencies(projectDir, "2", "1", "issue-a", "open", "2.1-issue-b");
      createDecomposedIssueWithMalformedEntries(projectDir, "2", "1", "issue-b", "issue-c");
      createIssue(projectDir, "2", "1", "issue-c", "open");

      WorkPrepare prepare = new WorkPrepare(scope);
      PrepareInput input = new PrepareInput(UUID.randomUUID().toString(), "*", "", TrustLevel.MEDIUM);

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
      TestUtils.deleteDirectoryRecursively(projectDir);
    }
  }

  /**
   * Creates a decomposed parent issue directory with a STATE.md listing a single sub-issue.
   * <p>
   * The parent issue has open status and a "## Decomposed Into" section listing the sub-issue
   * using its fully-qualified name (e.g., {@code 2.1-issue-c}).
   * No PLAN.md is created, so the parent is treated as a decomposed parent only.
   *
   * @param projectDir the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param issueName the parent issue name
   * @param subIssueName the bare name of the sub-issue; will be prefixed with the version to form
   *         a fully-qualified name in the "Decomposed Into" section
   * @throws IOException if file creation fails
   */
  private void createDecomposedIssue(Path projectDir, String major, String minor,
    String issueName, String subIssueName) throws IOException
  {
    Path issueDir = projectDir.resolve(".claude").resolve("cat").resolve("issues").
      resolve("v" + major).resolve("v" + major + "." + minor).resolve(issueName);
    Files.createDirectories(issueDir);

    String qualifiedSubIssueName = major + "." + minor + "-" + subIssueName;
    String stateContent = """
      # State

      - **Status:** open
      - **Progress:** 0%%
      - **Dependencies:** []
      - **Blocks:** []

      ## Decomposed Into
      - %s
      """.formatted(qualifiedSubIssueName);

    Files.writeString(issueDir.resolve("STATE.md"), stateContent);
  }

  /**
   * Creates a decomposed parent issue directory whose STATE.md "Decomposed Into" section contains
   * one valid fully-qualified name and several malformed entries (containing dots without the
   * version format, slashes, or other characters that are not valid qualified names).
   * <p>
   * The malformed entries should be silently skipped by the sub-issue resolver.
   *
   * @param projectDir the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param issueName the parent issue name
   * @param validSubIssueName the valid bare name of the sub-issue; will be prefixed with the
   *         version to form a fully-qualified name
   * @throws IOException if file creation fails
   */
  private void createDecomposedIssueWithMalformedEntries(Path projectDir, String major, String minor,
    String issueName, String validSubIssueName) throws IOException
  {
    Path issueDir = projectDir.resolve(".claude").resolve("cat").resolve("issues").
      resolve("v" + major).resolve("v" + major + "." + minor).resolve(issueName);
    Files.createDirectories(issueDir);

    String qualifiedSubIssueName = major + "." + minor + "-" + validSubIssueName;
    String stateContent = """
      # State

      - **Status:** open
      - **Progress:** 0%%
      - **Dependencies:** []
      - **Blocks:** []

      ## Decomposed Into
      - %s
      - v2.1/issue-bad-slash
      - some.dotted.name
      - bare-name-without-version
      """.formatted(qualifiedSubIssueName);

    Files.writeString(issueDir.resolve("STATE.md"), stateContent);
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
