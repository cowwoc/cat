/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.util.GitCommands;
import io.github.cowwoc.cat.claude.hook.util.TrustLevel;
import io.github.cowwoc.cat.claude.hook.util.WorkPrepare;
import io.github.cowwoc.cat.claude.hook.util.WorkPrepare.PrepareInput;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for job-section token estimation in plan.md.
 * <p>
 * Tests verify that WorkPrepare correctly counts items in ## Jobs / ### Job N
 * sections for token estimation.
 */
public class ParallelSubagentTest
{
  /**
   * Verifies that token estimation works correctly with Jobs format.
   * Plans with two jobs (Job 1 with 2 items, Job 2 with 1 item) should count 3 items total.
   * Expected tokens: 3 items * 2000 + 10000 base = 16000.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void tokenEstimationCountsWaveItems() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (TestClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "wave-estimate", "open");
      createPlanWithExecutionWaves(projectPath, "2", "1", "wave-estimate");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add issue with execution waves");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      String status = node.path("status").asString();
      requireThat(status, "status").isEqualTo("READY");
      int tokens = node.path("estimated_tokens").asInt();
      requireThat(tokens, "tokens").isEqualTo(16_000);

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that token estimation correctly ignores sub-items (indented bullets).
   * Job items with indented sub-bullets should not be counted as top-level items.
   * Expected tokens: 2 top-level items * 2000 + 10000 base = 14000.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void tokenEstimationIgnoresSubItems() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (TestClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "sub-items", "open");
      createPlanWithWaveSubItems(projectPath, "2", "1", "sub-items");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add issue with wave sub-items");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      String status = node.path("status").asString();
      requireThat(status, "status").isEqualTo("READY");
      int tokens = node.path("estimated_tokens").asInt();
      // 2 top-level items only (sub-items with indentation ignored)
      requireThat(tokens, "tokens").isEqualTo(14_000);

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that token estimation returns the base estimate when the plan has no execution section.
   * Expected tokens: 0 items * 2000 + 10000 base = 10000.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void tokenEstimationReturnsDefaultWhenNoExecutionSection() throws IOException
  {
    Path projectPath = createTempGitCatProject("v2.1");
    Path worktreePath = null;
    try (TestClaudeTool scope = new TestClaudeTool(projectPath, projectPath))
    {
      createIssue(projectPath, "2", "1", "no-execution", "open");
      createPlanWithoutExecutionSection(projectPath, "2", "1", "no-execution");
      GitCommands.runGit(projectPath, "add", ".");
      GitCommands.runGit(projectPath, "commit", "-m", "Add issue without execution section");

      WorkPrepare prepare = new WorkPrepare(scope);
      String sessionId = UUID.randomUUID().toString();
      PrepareInput input = new PrepareInput(sessionId, "", "", TrustLevel.MEDIUM, false);

      String json = prepare.execute(input);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode node = mapper.readTree(json);
      String status = node.path("status").asString();
      requireThat(status, "status").isEqualTo("READY");
      int tokens = node.path("estimated_tokens").asInt();
      // No files to create/modify and no execution items: base estimate only
      requireThat(tokens, "tokens").isEqualTo(10_000);

      worktreePath = Path.of(node.path("worktree_path").asString());
    }
    finally
    {
      cleanupWorktreeIfExists(projectPath, worktreePath);
      TestUtils.deleteDirectoryRecursively(projectPath);
    }
  }

  // ===== Helper Methods =====

  /**
   * Creates a temporary git CAT project structure.
   *
   * @param versionName the version name
   * @return the project root directory
   * @throws IOException if an I/O error occurs
   */
  private Path createTempGitCatProject(String versionName) throws IOException
  {
    Path projectPath = Files.createTempDirectory("cat-project-");
    GitCommands.runGit(projectPath, "init");
    GitCommands.runGit(projectPath, "config", "user.email", "test@example.com");
    GitCommands.runGit(projectPath, "config", "user.name", "Test User");

    // Create .cat structure
    Path catDir = projectPath.resolve(".cat");
    Files.createDirectories(catDir);
    Files.writeString(catDir.resolve("config.json"), "{}");

    // Create version and issues directories
    Path versionDir = catDir.resolve("issues").
      resolve(versionName);
    Files.createDirectories(versionDir);

    // Create initial commit
    Files.writeString(projectPath.resolve("README.md"), "# Test Project\n");
    GitCommands.runGit(projectPath, "add", ".");
    GitCommands.runGit(projectPath, "commit", "-m", "Initial commit");

    return projectPath;
  }

  /**
   * Creates an issue directory with index.json.
   *
   * @param projectPath the project root directory
   * @param major the major version
   * @param minor the minor version
   * @param issueName the issue name
   * @param status the issue status
   * @throws IOException if an I/O error occurs
   */
  private void createIssue(Path projectPath, String major, String minor, String issueName, String status)
    throws IOException
  {
    Path issueDir = projectPath.resolve(".cat").
      resolve("issues").
      resolve("v" + major).
      resolve("v" + major + "." + minor).
      resolve(issueName);
    Files.createDirectories(issueDir);

    Files.writeString(issueDir.resolve("index.json"), "{\"status\":\"" + status + "\"}");
  }

  /**
   * Creates a plan.md with ## Jobs section.
   *
   * @param projectPath the project root directory
   * @param major the major version
   * @param minor the minor version
   * @param issueName the issue name
   * @throws IOException if an I/O error occurs
   */
  private void createPlanWithExecutionWaves(Path projectPath, String major, String minor,
    String issueName) throws IOException
  {
    Path issueDir = projectPath.resolve(".cat").
      resolve("issues").
      resolve("v" + major).
      resolve("v" + major + "." + minor).
      resolve(issueName);

    String planMd = """
      # Plan: %s

      ## Goal
      Test feature with jobs.

      ## Jobs

      ### Job 1
      - Implement module A
      - Add tests for module A

      ### Job 2
      - Integrate with system

      ## Post-conditions
      - [ ] Implementation complete
      """.formatted(issueName);

    Files.writeString(issueDir.resolve("plan.md"), planMd);
  }

  /**
   * Creates a plan.md with job sections containing sub-items.
   *
   * @param projectPath the project root directory
   * @param major the major version
   * @param minor the minor version
   * @param issueName the issue name
   * @throws IOException if an I/O error occurs
   */
  private void createPlanWithWaveSubItems(Path projectPath, String major, String minor,
    String issueName) throws IOException
  {
    Path issueDir = projectPath.resolve(".cat").
      resolve("issues").
      resolve("v" + major).
      resolve("v" + major + "." + minor).
      resolve(issueName);

    String planMd = """
      # Plan: %s

      ## Goal
      Test feature with job sub-items.

      ## Jobs

      ### Job 1
      - Implement module A
        - Files: ModuleA.java
        - Sub-item should be ignored
      - Add tests for module A

      ## Post-conditions
      - [ ] Implementation complete
      """.formatted(issueName);

    Files.writeString(issueDir.resolve("plan.md"), planMd);
  }

  /**
   * Creates a plan.md without execution section.
   *
   * @param projectPath the project root directory
   * @param major the major version
   * @param minor the minor version
   * @param issueName the issue name
   * @throws IOException if an I/O error occurs
   */
  private void createPlanWithoutExecutionSection(Path projectPath, String major, String minor,
    String issueName) throws IOException
  {
    Path issueDir = projectPath.resolve(".cat").
      resolve("issues").
      resolve("v" + major).
      resolve("v" + major + "." + minor).
      resolve(issueName);

    String planMd = """
      # Plan: %s

      ## Goal
      Test feature without execution section.

      ## Post-conditions
      - [ ] Implementation complete
      """.formatted(issueName);

    Files.writeString(issueDir.resolve("plan.md"), planMd);
  }

  /**
   * Cleans up a worktree if it exists.
   *
   * @param projectPath the project root directory
   * @param worktreePath the worktree path to clean up
   * @throws IOException if an I/O error occurs
   */
  private void cleanupWorktreeIfExists(Path projectPath, Path worktreePath) throws IOException
  {
    if (worktreePath != null && Files.exists(worktreePath))
    {
      GitCommands.runGit(projectPath, "worktree", "remove", "--force", worktreePath.toString());
    }
  }
}
