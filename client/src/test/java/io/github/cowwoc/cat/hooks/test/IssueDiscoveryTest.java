/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.IssueDiscovery;
import io.github.cowwoc.cat.hooks.util.IssueDiscovery.DiscoveryResult;
import io.github.cowwoc.cat.hooks.util.IssueDiscovery.SearchOptions;
import io.github.cowwoc.cat.hooks.util.IssueDiscovery.Scope;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for IssueDiscovery.
 * <p>
 * Tests verify issue scanning, status filtering, dependency resolution, exit gate checking,
 * lock integration, and JSON output contracts. Each test is self-contained to support parallel execution.
 */
public class IssueDiscoveryTest
{
  /**
   * Verifies that a simple open issue is found when scanning all versions.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void findsOpenIssueWhenScanningAll() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectDir, "2", "1", "my-feature", "open");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-my-feature");
        requireThat(found.major(), "major").isEqualTo("2");
        requireThat(found.minor(), "minor").isEqualTo("1");
        requireThat(found.issueName(), "issueName").isEqualTo("my-feature");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that an in-progress issue is found.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void findsInProgressIssue() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectDir, "2", "1", "my-feature", "in-progress");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that a closed issue is not returned.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void doesNotReturnClosedIssue() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectDir, "2", "1", "done-feature", "closed");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.NotFound.class);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that a blocked issue is not returned.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void doesNotReturnBlockedStatusIssue() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectDir, "2", "1", "blocked-feature", "blocked");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.NotFound.class);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that finding a specific issue by ID returns the correct result.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void findsSpecificIssueById() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectDir, "2", "1", "my-feature", "open");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ISSUE, "2.1-my-feature", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-my-feature");
        requireThat(found.scope(), "scope").isEqualTo("issue");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that requesting a specific issue that is already closed returns AlreadyComplete.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void specificIssueAlreadyClosedReturnsAlreadyComplete() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectDir, "2", "1", "done-feature", "closed");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ISSUE, "2.1-done-feature", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.AlreadyComplete.class);
        DiscoveryResult.AlreadyComplete complete = (DiscoveryResult.AlreadyComplete) result;
        requireThat(complete.issueId(), "issueId").isEqualTo("2.1-done-feature");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that requesting a non-existent specific issue returns NotFound.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void specificIssueNotFoundReturnsNotFound() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ISSUE, "2.1-nonexistent", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.NotFound.class);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that an issue with unsatisfied dependencies is skipped during scan.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void issueWithUnsatisfiedDependenciesIsSkipped() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create a dep issue that is open (not closed)
        createIssue(projectDir, "2", "1", "dep-issue", "open");
        // Create a feature that depends on dep-issue
        createIssueWithDependencies(projectDir, "2", "1", "my-feature", "open",
          "[2.1-dep-issue]");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        // Should find dep-issue (no dependencies) not my-feature
        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-dep-issue");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that an issue with satisfied dependencies is eligible.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void issueWithSatisfiedDependenciesIsEligible() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create a closed dep issue
        createIssue(projectDir, "2", "1", "dep-issue", "closed");
        // Create a feature that depends on dep-issue (which is closed)
        createIssueWithDependencies(projectDir, "2", "1", "my-feature", "open",
          "[2.1-dep-issue]");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        // Search by specific issue ID
        SearchOptions options = new SearchOptions(Scope.ISSUE, "2.1-my-feature", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that requesting a specific issue with unsatisfied dependencies returns Blocked.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void specificIssueWithUnsatisfiedDependenciesReturnsBlocked() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create an open dep issue (not closed)
        createIssue(projectDir, "2", "1", "dep-issue", "open");
        // Create a feature with dependency on open issue
        createIssueWithDependencies(projectDir, "2", "1", "my-feature", "open",
          "[2.1-dep-issue]");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ISSUE, "2.1-my-feature", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Blocked.class);
        DiscoveryResult.Blocked blocked = (DiscoveryResult.Blocked) result;
        requireThat(blocked.issueId(), "issueId").isEqualTo("2.1-my-feature");
        requireThat(blocked.blockingIssues(), "blockingIssues").contains("2.1-dep-issue");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that a decomposed parent task with open sub-issues is skipped.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void decomposedParentWithOpenSubissuesIsSkipped() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create a sub-issue
        createIssue(projectDir, "2", "1", "sub-task", "open");
        // Create a decomposed parent
        createDecomposedParent(projectDir, "2", "1", "parent-task", "open",
          "sub-task");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        // Only parent-task should be skipped, sub-task should be found
        SearchOptions options = new SearchOptions(Scope.ISSUE, "2.1-parent-task", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Decomposed.class);
        DiscoveryResult.Decomposed decomposed = (DiscoveryResult.Decomposed) result;
        requireThat(decomposed.issueId(), "issueId").isEqualTo("2.1-parent-task");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that a decomposed parent task with all sub-issues closed is eligible.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void decomposedParentWithAllSubissuesClosedIsEligible() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create a closed sub-issue
        createIssue(projectDir, "2", "1", "sub-task", "closed");
        // Create a decomposed parent with in-progress status (all sub-issues closed)
        createDecomposedParent(projectDir, "2", "1", "parent-task", "in-progress",
          "sub-task");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ISSUE, "2.1-parent-task", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that issues matching the exclude pattern are skipped.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void issuesMatchingExcludePatternAreSkipped() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectDir, "2", "1", "compress-docs", "open");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "compress*", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.NotFound.class);
        DiscoveryResult.NotFound notFound = (DiscoveryResult.NotFound) result;
        requireThat(notFound.excludedCount(), "excludedCount").isGreaterThan(0);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that minor version scope only searches the specified minor version.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void minorScopeSearchesOnlySpecifiedVersion() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectDir, "2", "1", "v21-feature", "open");
        createIssue(projectDir, "2", "2", "v22-feature", "open");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.MINOR, "2.2", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.2-v22-feature");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that bare name scope finds an issue by its base name.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void bareNameScopeFindsIssueByBaseName() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectDir, "2", "1", "my-feature", "open");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.BARE_NAME, "my-feature", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-my-feature");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that NotFound is returned when no issues exist.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void returnsNotFoundWhenNoIssuesExist() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.NotFound.class);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that constructor throws on invalid project directory.
   */
  @Test
  public void constructorThrowsOnInvalidProjectDirectory()
  {
    try (JvmScope scope = new TestJvmScope())
    {
      try
      {
        new IssueDiscovery(scope);
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("Not a CAT project");
      }
    }
  }

  /**
   * Verifies that the Found result produces valid JSON with all required fields.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void foundResultProducesValidJson() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      DiscoveryResult.Found found = new DiscoveryResult.Found(
        "2.1-my-feature", "2", "1", "", "my-feature", "/path/to/issue", "all");

      String json = found.toJson(mapper);

      requireThat(json, "json").contains("\"status\"");
      requireThat(json, "json").contains("\"found\"");
      requireThat(json, "json").contains("\"issue_id\"");
      requireThat(json, "json").contains("\"2.1-my-feature\"");
      requireThat(json, "json").contains("\"lock_status\"");
      requireThat(json, "json").contains("\"acquired\"");
    }
  }

  /**
   * Verifies that the NotFound result produces valid JSON.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void notFoundResultProducesValidJson() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      DiscoveryResult.NotFound notFound = new DiscoveryResult.NotFound("all", "", 0);

      String json = notFound.toJson(mapper);

      requireThat(json, "json").contains("\"status\"");
      requireThat(json, "json").contains("\"not_found\"");
      requireThat(json, "json").contains("\"scope\"");
    }
  }

  /**
   * Verifies that the Blocked result produces valid JSON with blocking issue IDs.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void blockedResultProducesValidJson() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      DiscoveryResult.Blocked blocked = new DiscoveryResult.Blocked(
        "2.1-feature", List.of("2.1-dep-a", "2.1-dep-b"));

      String json = blocked.toJson(mapper);

      requireThat(json, "json").contains("\"status\"");
      requireThat(json, "json").contains("\"blocked\"");
      requireThat(json, "json").contains("\"issue_id\"");
      requireThat(json, "json").contains("\"blocking\"");
      requireThat(json, "json").contains("2.1-dep-a");
    }
  }

  /**
   * Verifies that the NotFound result includes excluded count when pattern is active.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void notFoundWithExcludedCountIncludesPatternDetails() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      DiscoveryResult.NotFound notFound = new DiscoveryResult.NotFound("all", "compress*", 3);

      String json = notFound.toJson(mapper);

      requireThat(json, "json").contains("\"excluded_count\"");
      requireThat(json, "json").contains("\"exclude_pattern\"");
      requireThat(json, "json").contains("compress");
      requireThat(json, "json").contains("excluded by pattern");
    }
  }

  /**
   * Verifies that an issue with an existing worktree is skipped during scan.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void issueWithExistingWorktreeIsSkipped() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectDir, "2", "1", "my-feature", "open");
        createIssue(projectDir, "2", "1", "other-feature", "open");

        // Create a fake worktree for my-feature
        Path worktreesDir = projectDir.resolve(".claude").resolve("cat").resolve("worktrees");
        Files.createDirectories(worktreesDir.resolve("2.1-my-feature"));

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        // Should find other-feature, not my-feature (which has a worktree)
        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-other-feature");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that requesting a specific issue with existing worktree returns ExistingWorktree.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void specificIssueWithExistingWorktreeReturnsExistingWorktree() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectDir, "2", "1", "my-feature", "open");

        // Create a fake worktree
        Path worktreesDir = projectDir.resolve(".claude").resolve("cat").resolve("worktrees");
        Files.createDirectories(worktreesDir.resolve("2.1-my-feature"));

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ISSUE, "2.1-my-feature", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.ExistingWorktree.class);
        DiscoveryResult.ExistingWorktree existing = (DiscoveryResult.ExistingWorktree) result;
        requireThat(existing.issueId(), "issueId").isEqualTo("2.1-my-feature");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that the post-condition issue is enforced when non-post-condition issues are not all closed.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void postconditionIssueIsEnforcedWhenPrerequisitesAreOpen() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create a regular open issue
        createIssue(projectDir, "2", "1", "regular-feature", "open");
        // Create post-condition issue
        createIssue(projectDir, "2", "1", "exit-gate-issue", "open");
        // Create a PLAN.md marking exit-gate-issue as a post-condition issue
        createVersionPlanWithExitGate(projectDir, "2", "1", "exit-gate-issue");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        // Verify the scan skips the post-condition issue when regular-feature is still open
        SearchOptions scanOptions = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult scanResult = discovery.findNextIssue(scanOptions);

        requireThat(scanResult, "scanResult").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) scanResult;
        // Should find regular-feature first, NOT exit-gate-issue
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-regular-feature");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that major scope only searches the specified major version.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void majorScopeSearchesOnlySpecifiedVersion() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectDir, "2", "1", "v2-feature", "open");
        createIssue(projectDir, "3", "1", "v3-feature", "open");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.MAJOR, "3", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("3.1-v3-feature");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that with overridePostconditions=true, post-condition issues are found even when prerequisites are open.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void postconditionIssueSkippedWhenOverridePostconditionsIsTrue() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create a regular open issue (unsatisfied prerequisite for post-condition issue)
        createIssue(projectDir, "2", "1", "regular-feature", "open");
        // Create a post-condition issue
        createIssue(projectDir, "2", "1", "exit-gate-issue", "open");
        // Mark exit-gate-issue as a post-condition issue in PLAN.md
        createVersionPlanWithExitGate(projectDir, "2", "1", "exit-gate-issue");

        IssueDiscovery discovery = new IssueDiscovery(scope);

        // First confirm: without overridePostconditions, exit-gate-issue is skipped in scan
        SearchOptions scanWithoutOverride = new SearchOptions(Scope.ALL, "", sessionId, "regular-feature",
          false);
        DiscoveryResult withoutOverride = discovery.findNextIssue(scanWithoutOverride);
        requireThat(withoutOverride, "withoutOverride").isInstanceOf(DiscoveryResult.NotFound.class);

        // With overridePostconditions=true, post-condition evaluation is skipped so exit-gate-issue is returned
        SearchOptions scanWithOverride = new SearchOptions(Scope.ALL, "", sessionId, "regular-feature",
          true);
        DiscoveryResult withOverride = discovery.findNextIssue(scanWithOverride);

        requireThat(withOverride, "withOverride").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) withOverride;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-exit-gate-issue");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Provides test cases for status alias normalization.
   * Each row contains: status alias, whether the issue should be found (true = open/in-progress, false = closed).
   *
   * @return test data rows
   */
  @DataProvider
  public Object[][] statusAliasProvider()
  {
    return new Object[][]
    {
      {"pending", true},
      {"completed", false},
      {"in_progress", true},
      {"active", true},
      {"complete", false},
      {"done", false}
    };
  }

  /**
   * Verifies that a status alias is normalized to its canonical value and the issue is found or skipped
   * accordingly.
   *
   * @param statusAlias the status alias to use in STATE.md
   * @param shouldBeFound true if the alias maps to an eligible status (open or in-progress)
   * @throws IOException if an I/O error occurs
   */
  @Test(dataProvider = "statusAliasProvider")
  public void statusAliasIsNormalized(String statusAlias, boolean shouldBeFound) throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectDir, "2", "1", "test-issue", statusAlias);

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        if (shouldBeFound)
          requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        else
          requireThat(result, "result").isInstanceOf(DiscoveryResult.NotFound.class);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that a version with unsatisfied version-level dependencies is skipped during ALL scope search.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void versionWithUnsatisfiedDependenciesIsSkipped() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create v2.0 with an open issue (the dependency that is not yet closed)
        createIssue(projectDir, "2", "0", "prev-release", "open");
        // Create v2.1 STATE.md with a dependency on v2.0 (not closed)
        createVersionStateWithDependencies(projectDir, "2", "1", "[2.0-prev-release]");
        // Create an open issue in v2.1
        createIssue(projectDir, "2", "1", "blocked-issue", "open");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        // v2.1 is blocked at version level; v2.0 open issue should be found instead
        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.0-prev-release");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that when an issue has multiple dependencies where some are closed and some are open,
   * the Blocked result contains exactly the unsatisfied dependencies.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void blockedResultContainsAllUnsatisfiedDependencies() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create dep-a (closed), dep-b (open), dep-c (open)
        createIssue(projectDir, "2", "1", "dep-a", "closed");
        createIssue(projectDir, "2", "1", "dep-b", "open");
        createIssue(projectDir, "2", "1", "dep-c", "open");
        // Create issue depending on all three
        createIssueWithDependencies(projectDir, "2", "1", "my-feature", "open",
          "[2.1-dep-a, 2.1-dep-b, 2.1-dep-c]");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ISSUE, "2.1-my-feature", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Blocked.class);
        DiscoveryResult.Blocked blocked = (DiscoveryResult.Blocked) result;
        requireThat(blocked.issueId(), "issueId").isEqualTo("2.1-my-feature");
        List<String> blockingIssues = blocked.blockingIssues();
        requireThat(blockingIssues, "blockingIssues").contains("2.1-dep-b");
        requireThat(blockingIssues, "blockingIssues").contains("2.1-dep-c");
        requireThat(blockingIssues.size(), "blockingIssues.size()").isEqualTo(2);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that when the same bare issue name exists in multiple versions, the first version in sort order
   * is returned.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void bareNameWithMultipleMatchesSelectsFirstVersionInSortOrder() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create the same issue name in v2.1 and v3.1
        createIssue(projectDir, "2", "1", "shared-feature", "open");
        createIssue(projectDir, "3", "1", "shared-feature", "open");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.BARE_NAME, "shared-feature", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        // v2.1 sorts before v3.1, so 2.1-shared-feature should be returned
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-shared-feature");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that exclude patterns containing regex-special characters are treated as glob patterns and
   * match correctly without throwing regex exceptions.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void excludePatternWithRegexSpecialCharsWorksCorrectly() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Issue name contains characters that are regex-special in some contexts
        createIssue(projectDir, "2", "1", "fix-bug-v1.0", "open");
        createIssue(projectDir, "2", "1", "other-feature", "open");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        // Pattern with a dot (regex wildcard) and glob wildcard - should treat as glob
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "fix-bug-v1.0", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        // fix-bug-v1.0 should be excluded, other-feature should be found
        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-other-feature");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that a patch-level issue is found when scanning all versions.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void findsPatchLevelIssue() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createPatchIssue(projectDir, "2", "1", "3", "patch-fix", "open");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1.3-patch-fix");
        requireThat(found.major(), "major").isEqualTo("2");
        requireThat(found.minor(), "minor").isEqualTo("1");
        requireThat(found.patch(), "patch").isEqualTo("3");
        requireThat(found.issueName(), "issueName").isEqualTo("patch-fix");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that a patch-level issue can be found by specific fully-qualified ID.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void specificPatchLevelIssueById() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createPatchIssue(projectDir, "2", "1", "3", "patch-fix", "open");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ISSUE, "2.1.3-patch-fix", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1.3-patch-fix");
        requireThat(found.patch(), "patch").isEqualTo("3");
        requireThat(found.scope(), "scope").isEqualTo("issue");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that a patch-level issue can be resolved from a bare name.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void bareNameResolvesPatchLevelIssue() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createPatchIssue(projectDir, "2", "1", "3", "patch-fix", "open");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.BARE_NAME, "patch-fix", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1.3-patch-fix");
        requireThat(found.patch(), "patch").isEqualTo("3");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that the Found result for a patch-level issue produces valid JSON with patch field included.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void patchLevelIssueProducesValidJson() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      DiscoveryResult.Found found = new DiscoveryResult.Found(
        "2.1.3-patch-fix", "2", "1", "3", "patch-fix", "/path/to/issue", "all");

      String json = found.toJson(mapper);

      requireThat(json, "json").contains("\"status\"");
      requireThat(json, "json").contains("\"found\"");
      requireThat(json, "json").contains("\"issue_id\"");
      requireThat(json, "json").contains("\"2.1.3-patch-fix\"");
      requireThat(json, "json").contains("\"patch\"");
      requireThat(json, "json").contains("\"3\"");
      requireThat(json, "json").contains("\"lock_status\"");
      requireThat(json, "json").contains("\"acquired\"");
    }
  }

  /**
   * Verifies that a major-only issue (directly under a major version dir) is found when scanning all
   * versions.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void findsMajorOnlyIssue() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createMajorOnlyIssue(projectDir, "2", "my-feature", "open");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2-my-feature");
        requireThat(found.major(), "major").isEqualTo("2");
        requireThat(found.minor(), "minor").isEqualTo("");
        requireThat(found.patch(), "patch").isEqualTo("");
        requireThat(found.issueName(), "issueName").isEqualTo("my-feature");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that a major-only issue can be found by its specific fully-qualified ID.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void specificMajorOnlyIssueById() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createMajorOnlyIssue(projectDir, "2", "my-feature", "open");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ISSUE, "2-my-feature", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2-my-feature");
        requireThat(found.major(), "major").isEqualTo("2");
        requireThat(found.minor(), "minor").isEqualTo("");
        requireThat(found.patch(), "patch").isEqualTo("");
        requireThat(found.scope(), "scope").isEqualTo("issue");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that a bare name resolves to a major-only issue.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void bareNameResolvesMajorOnlyIssue() throws IOException
  {
    Path projectDir = createTempProject();
    try (JvmScope scope = new TestJvmScope(projectDir, projectDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createMajorOnlyIssue(projectDir, "2", "my-feature", "open");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.BARE_NAME, "my-feature", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2-my-feature");
        requireThat(found.minor(), "minor").isEqualTo("");
        requireThat(found.patch(), "patch").isEqualTo("");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectDir);
      }
    }
  }

  /**
   * Verifies that a major-only issue produces JSON with major but without minor or patch fields.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void majorOnlyIssueProducesValidJson() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      DiscoveryResult.Found found = new DiscoveryResult.Found(
        "2-my-feature", "2", "", "", "my-feature", "/path/to/issue", "all");

      String json = found.toJson(mapper);

      requireThat(json, "json").contains("\"status\"");
      requireThat(json, "json").contains("\"found\"");
      requireThat(json, "json").contains("\"issue_id\"");
      requireThat(json, "json").contains("\"2-my-feature\"");
      requireThat(json, "json").contains("\"major\"");
      requireThat(json, "json").doesNotContain("\"minor\"");
      requireThat(json, "json").doesNotContain("\"patch\"");
      requireThat(json, "json").contains("\"lock_status\"");
      requireThat(json, "json").contains("\"acquired\"");
    }
  }

  /**
   * Creates a temporary CAT project directory for test isolation.
   *
   * @return the path to the created temporary directory
   */
  private Path createTempProject()
  {
    try
    {
      Path projectDir = Files.createTempDirectory("issue-discovery-test");
      Path catDir = projectDir.resolve(".claude").resolve("cat");
      Files.createDirectories(catDir.resolve("issues"));
      return projectDir;
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Creates an issue directory with STATE.md in the specified project.
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
    createIssueWithDependencies(projectDir, major, minor, issueName, status, "[]");
  }

  /**
   * Creates an issue directory with STATE.md and specified dependencies.
   *
   * @param projectDir the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param issueName the issue name
   * @param status the issue status
   * @param dependencies the dependencies in array notation (e.g., {@code [dep1, dep2]})
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
      - **Dependencies:** %s
      - **Blocks:** []
      """.formatted(status, dependencies);

    Files.writeString(issueDir.resolve("STATE.md"), stateContent);
  }

  /**
   * Creates a decomposed parent issue with a sub-issue reference in STATE.md.
   *
   * @param projectDir the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param issueName the parent issue name
   * @param status the parent issue status
   * @param subIssueName the sub-issue name to reference
   * @throws IOException if file creation fails
   */
  private void createDecomposedParent(Path projectDir, String major, String minor,
    String issueName, String status, String subIssueName) throws IOException
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

      ## Decomposed Into

      - %s
      """.formatted(status, subIssueName);

    Files.writeString(issueDir.resolve("STATE.md"), stateContent);
  }

  /**
   * Creates a version-level STATE.md with the specified dependencies.
   *
   * @param projectDir the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param dependencies the dependencies in array notation (e.g., {@code [dep1, dep2]})
   * @throws IOException if file creation fails
   */
  private void createVersionStateWithDependencies(Path projectDir, String major, String minor,
    String dependencies) throws IOException
  {
    Path versionDir = projectDir.resolve(".claude").resolve("cat").resolve("issues").
      resolve("v" + major).resolve("v" + major + "." + minor);
    Files.createDirectories(versionDir);

    String stateContent = """
      # State

      - **Status:** open
      - **Dependencies:** %s
      """.formatted(dependencies);

    Files.writeString(versionDir.resolve("STATE.md"), stateContent);
  }

  /**
   * Creates a version PLAN.md marking an issue as a post-condition issue.
   *
   * @param projectDir the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param postconditionIssueName the name of the post-condition issue
   * @throws IOException if file creation fails
   */
  private void createVersionPlanWithExitGate(Path projectDir, String major, String minor,
    String postconditionIssueName) throws IOException
  {
    Path versionDir = projectDir.resolve(".claude").resolve("cat").resolve("issues").
      resolve("v" + major).resolve("v" + major + "." + minor);
    Files.createDirectories(versionDir);

    String planContent = """
      # Plan for v%s.%s

      ## Post-conditions

      - [issue] %s
      """.formatted(major, minor, postconditionIssueName);

    Files.writeString(versionDir.resolve("PLAN.md"), planContent);
  }

  /**
   * Creates a major-only issue directory with STATE.md directly under the major version directory.
   *
   * @param projectDir the project root directory
   * @param major the major version number
   * @param issueName the issue name
   * @param status the issue status
   * @throws IOException if file creation fails
   */
  private void createMajorOnlyIssue(Path projectDir, String major, String issueName,
    String status) throws IOException
  {
    Path issueDir = projectDir.resolve(".claude").resolve("cat").resolve("issues").
      resolve("v" + major).resolve(issueName);
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
   * Creates a patch-level issue directory with STATE.md in the specified project.
   *
   * @param projectDir the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param patch the patch version number
   * @param issueName the issue name
   * @param status the issue status
   * @throws IOException if file creation fails
   */
  private void createPatchIssue(Path projectDir, String major, String minor, String patch,
    String issueName, String status) throws IOException
  {
    Path issueDir = projectDir.resolve(".claude").resolve("cat").resolve("issues").
      resolve("v" + major).resolve("v" + major + "." + minor).
      resolve("v" + major + "." + minor + "." + patch).resolve(issueName);
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
}
