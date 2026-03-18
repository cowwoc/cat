/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.SharedSecrets;
import io.github.cowwoc.cat.hooks.util.IssueDiscovery;
import io.github.cowwoc.cat.hooks.util.IssueDiscovery.DiscoveryResult;
import io.github.cowwoc.cat.hooks.util.IssueDiscovery.SearchOptions;
import io.github.cowwoc.cat.hooks.util.IssueDiscovery.Scope;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.regex.Pattern;

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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectPath, "2", "1", "my-feature", "open");

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
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectPath, "2", "1", "my-feature", "in-progress");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectPath, "2", "1", "done-feature", "closed");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.NotFound.class);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectPath, "2", "1", "blocked-feature", "blocked");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.NotFound.class);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectPath, "2", "1", "my-feature", "open");

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
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectPath, "2", "1", "done-feature", "closed");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ISSUE, "2.1-done-feature", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.AlreadyComplete.class);
        DiscoveryResult.AlreadyComplete complete = (DiscoveryResult.AlreadyComplete) result;
        requireThat(complete.issueId(), "issueId").isEqualTo("2.1-done-feature");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
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
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create a dep issue that is open (not closed)
        createIssue(projectPath, "2", "1", "dep-issue", "open");
        // Create a feature that depends on dep-issue
        createIssueWithDependencies(projectPath, "2", "1", "my-feature", "open",
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
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create a closed dep issue
        createIssue(projectPath, "2", "1", "dep-issue", "closed");
        // Create a feature that depends on dep-issue (which is closed)
        createIssueWithDependencies(projectPath, "2", "1", "my-feature", "open",
          "[2.1-dep-issue]");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        // Search by specific issue ID
        SearchOptions options = new SearchOptions(Scope.ISSUE, "2.1-my-feature", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create an open dep issue (not closed)
        createIssue(projectPath, "2", "1", "dep-issue", "open");
        // Create a feature with dependency on open issue
        createIssueWithDependencies(projectPath, "2", "1", "my-feature", "open",
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
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create a sub-issue
        createIssue(projectPath, "2", "1", "sub-task", "open");
        // Create a decomposed parent
        createDecomposedParent(projectPath, "2", "1", "parent-task", "open",
          "2.1-sub-task");

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
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create a closed sub-issue
        createIssue(projectPath, "2", "1", "sub-task", "closed");
        // Create a decomposed parent with in-progress status (all sub-issues closed)
        createDecomposedParent(projectPath, "2", "1", "parent-task", "in-progress",
          "2.1-sub-task");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ISSUE, "2.1-parent-task", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectPath, "2", "1", "compress-docs", "open");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "2.1-compress*", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.NotFound.class);
        DiscoveryResult.NotFound notFound = (DiscoveryResult.NotFound) result;
        requireThat(notFound.excludedCount(), "excludedCount").isGreaterThan(0);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectPath, "2", "1", "v21-feature", "open");
        createIssue(projectPath, "2", "2", "v22-feature", "open");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.VERSION, "2.2", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.2-v22-feature");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectPath, "2", "1", "my-feature", "open");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.BARE_NAME, "my-feature", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-my-feature");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
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
        TestUtils.deleteDirectoryRecursively(projectPath);
      }
    }
  }

  /**
   * Verifies that constructor throws on invalid project directory.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Not a CAT project.*")
  public void constructorThrowsOnInvalidProjectDirectory()
  {
    try (JvmScope scope = new TestJvmScope())
    {
      new IssueDiscovery(scope);
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
        "2.1-my-feature", "2", "1", "", "my-feature", "/path/to/issue", "all", false, false, false);

      String json = found.toJson(mapper);

      requireThat(json, "json").contains("\"status\"");
      requireThat(json, "json").contains("\"found\"");
      requireThat(json, "json").contains("\"issue_id\"");
      requireThat(json, "json").contains("\"2.1-my-feature\"");
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectPath, "2", "1", "my-feature", "open");
        createIssue(projectPath, "2", "1", "other-feature", "open");

        // Create a fake worktree for my-feature
        Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
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
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectPath, "2", "1", "my-feature", "open");

        // Create a fake worktree
        Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
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
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create a regular open issue
        createIssue(projectPath, "2", "1", "regular-feature", "open");
        // Create post-condition issue
        createIssue(projectPath, "2", "1", "exit-gate-issue", "open");
        // Create a PLAN.md marking exit-gate-issue as a post-condition issue
        createVersionPlanWithExitGate(projectPath, "2", "1", "exit-gate-issue");

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
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectPath, "2", "1", "v2-feature", "open");
        createIssue(projectPath, "3", "1", "v3-feature", "open");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.VERSION, "3", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("3.1-v3-feature");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create a regular open issue (unsatisfied prerequisite for post-condition issue)
        createIssue(projectPath, "2", "1", "regular-feature", "open");
        // Create a post-condition issue
        createIssue(projectPath, "2", "1", "exit-gate-issue", "open");
        // Mark exit-gate-issue as a post-condition issue in PLAN.md
        createVersionPlanWithExitGate(projectPath, "2", "1", "exit-gate-issue");

        IssueDiscovery discovery = new IssueDiscovery(scope);

        // First confirm: without overridePostconditions, exit-gate-issue is skipped in scan
        SearchOptions scanWithoutOverride = new SearchOptions(Scope.ALL, "", sessionId, "2.1-regular-feature", false);
        DiscoveryResult withoutOverride = discovery.findNextIssue(scanWithoutOverride);
        requireThat(withoutOverride, "withoutOverride").isInstanceOf(DiscoveryResult.NotFound.class);

        // With overridePostconditions=true, post-condition evaluation is skipped so exit-gate-issue is returned
        SearchOptions scanWithOverride = new SearchOptions(Scope.ALL, "", sessionId, "2.1-regular-feature", true);
        DiscoveryResult withOverride = discovery.findNextIssue(scanWithOverride);

        requireThat(withOverride, "withOverride").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) withOverride;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-exit-gate-issue");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
      }
    }
  }

  /**
   * Returns status values that are canonical and should be accepted.
   *
   * @return the canonical status values
   */
  @DataProvider
  public Object[][] canonicalStatusProvider()
  {
    return new Object[][]
      {
        {"open"},
        {"in-progress"},
        {"closed"},
        {"blocked"}
      };
  }

  /**
   * Verifies that canonical status values are accepted by getIssueStatus().
   *
   * @param status the canonical status value
   * @throws IOException if an I/O error occurs
   */
  @Test(dataProvider = "canonicalStatusProvider")
  public void canonicalStatusIsAccepted(String status) throws IOException
  {
    List<String> lines = List.of("- **Status:** " + status);
    Path fakePath = Path.of("fake/STATE.md");
    String result = SharedSecrets.getIssueStatus(lines, fakePath);
    requireThat(result, "result").isEqualTo(status);
  }

  /**
   * Returns status values that are non-canonical aliases and should be rejected.
   *
   * @return the non-canonical status aliases
   */
  @DataProvider
  public Object[][] nonCanonicalStatusProvider()
  {
    return new Object[][]
      {
        {"pending"},
        {"completed"},
        {"complete"},
        {"done"},
        {"in_progress"},
        {"active"}
      };
  }

  /**
   * Verifies that non-canonical status aliases are rejected by getIssueStatus().
   *
   * @param status the non-canonical status alias
   * @throws IOException if an I/O error occurs
   */
  @Test(dataProvider = "nonCanonicalStatusProvider",
    expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*Unknown status.*")
  public void nonCanonicalStatusIsRejected(String status) throws IOException
  {
    List<String> lines = List.of("- **Status:** " + status);
    Path fakePath = Path.of("fake/STATE.md");
    SharedSecrets.getIssueStatus(lines, fakePath);
  }

  /**
   * Verifies that a version with unsatisfied version-level dependencies is skipped during ALL scope search.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void versionWithUnsatisfiedDependenciesIsSkipped() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create v2.0 with an open issue (the dependency that is not yet closed)
        createIssue(projectPath, "2", "0", "prev-release", "open");
        // Create v2.1 STATE.md with a dependency on v2.0 (not closed)
        createVersionStateWithDependencies(projectPath, "2", "1", "[2.0-prev-release]");
        // Create an open issue in v2.1
        createIssue(projectPath, "2", "1", "blocked-issue", "open");

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
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create dep-a (closed), dep-b (open), dep-c (open)
        createIssue(projectPath, "2", "1", "dep-a", "closed");
        createIssue(projectPath, "2", "1", "dep-b", "open");
        createIssue(projectPath, "2", "1", "dep-c", "open");
        // Create issue depending on all three
        createIssueWithDependencies(projectPath, "2", "1", "my-feature", "open",
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
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create the same issue name in v2.1 and v3.1
        createIssue(projectPath, "2", "1", "shared-feature", "open");
        createIssue(projectPath, "3", "1", "shared-feature", "open");

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
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Issue name contains characters that are regex-special in some contexts
        createIssue(projectPath, "2", "1", "fix-bug-v1.0", "open");
        createIssue(projectPath, "2", "1", "other-feature", "open");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        // Pattern with a dot (regex wildcard) and glob wildcard - should treat as glob
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "2.1-fix-bug-v1.0", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        // fix-bug-v1.0 should be excluded, other-feature should be found
        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-other-feature");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createPatchIssue(projectPath, "2", "1", "3", "patch-fix", "open");

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
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createPatchIssue(projectPath, "2", "1", "3", "patch-fix", "open");

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
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createPatchIssue(projectPath, "2", "1", "3", "patch-fix", "open");

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
        TestUtils.deleteDirectoryRecursively(projectPath);
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
        "2.1.3-patch-fix", "2", "1", "3", "patch-fix", "/path/to/issue", "all", false, false, false);

      String json = found.toJson(mapper);

      requireThat(json, "json").contains("\"status\"");
      requireThat(json, "json").contains("\"found\"");
      requireThat(json, "json").contains("\"issue_id\"");
      requireThat(json, "json").contains("\"2.1.3-patch-fix\"");
      requireThat(json, "json").contains("\"patch\"");
      requireThat(json, "json").contains("\"3\"");
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createMajorOnlyIssue(projectPath, "2", "my-feature", "open");

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
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createMajorOnlyIssue(projectPath, "2", "my-feature", "open");

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
        TestUtils.deleteDirectoryRecursively(projectPath);
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
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createMajorOnlyIssue(projectPath, "2", "my-feature", "open");

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
        TestUtils.deleteDirectoryRecursively(projectPath);
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
        "2-my-feature", "2", "", "", "my-feature", "/path/to/issue", "all", false, false, false);

      String json = found.toJson(mapper);

      requireThat(json, "json").contains("\"status\"");
      requireThat(json, "json").contains("\"found\"");
      requireThat(json, "json").contains("\"issue_id\"");
      requireThat(json, "json").contains("\"2-my-feature\"");
      requireThat(json, "json").contains("\"major\"");
      requireThat(json, "json").doesNotContain("\"minor\"");
      requireThat(json, "json").doesNotContain("\"patch\"");
    }
  }

  /**
   * Creates an issue directory with STATE.md in the specified project.
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
    createIssueWithDependencies(projectPath, major, minor, issueName, status, "[]");
  }

  /**
   * Creates an issue directory with STATE.md and specified dependencies.
   *
   * @param projectPath the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param issueName the issue name
   * @param status the issue status
   * @param dependencies the dependencies in array notation (e.g., {@code [dep1, dep2]})
   * @throws IOException if file creation fails
   */
  private void createIssueWithDependencies(Path projectPath, String major, String minor,
    String issueName, String status, String dependencies) throws IOException
  {
    Path issueDir = projectPath.resolve(".cat").resolve("issues").
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

    String planContent = """
      # Plan: %s

      ## Goal

      Test issue for %s.
      """.formatted(issueName, issueName);

    Files.writeString(issueDir.resolve("PLAN.md"), planContent);
  }

  /**
   * Creates a decomposed parent issue with a sub-issue reference in STATE.md.
   *
   * @param projectPath the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param issueName the parent issue name
   * @param status the parent issue status
   * @param subIssueName the sub-issue name to reference
   * @throws IOException if file creation fails
   */
  private void createDecomposedParent(Path projectPath, String major, String minor,
    String issueName, String status, String subIssueName) throws IOException
  {
    Path issueDir = projectPath.resolve(".cat").resolve("issues").
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
   * @param projectPath the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param dependencies the dependencies in array notation (e.g., {@code [dep1, dep2]})
   * @throws IOException if file creation fails
   */
  private void createVersionStateWithDependencies(Path projectPath, String major, String minor,
    String dependencies) throws IOException
  {
    Path versionDir = projectPath.resolve(".cat").resolve("issues").
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
   * @param projectPath the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param postconditionIssueName the name of the post-condition issue
   * @throws IOException if file creation fails
   */
  private void createVersionPlanWithExitGate(Path projectPath, String major, String minor,
    String postconditionIssueName) throws IOException
  {
    Path versionDir = projectPath.resolve(".cat").resolve("issues").
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
   * @param projectPath the project root directory
   * @param major the major version number
   * @param issueName the issue name
   * @param status the issue status
   * @throws IOException if file creation fails
   */
  private void createMajorOnlyIssue(Path projectPath, String major, String issueName,
    String status) throws IOException
  {
    Path issueDir = projectPath.resolve(".cat").resolve("issues").
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
   * @param projectPath the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param patch the patch version number
   * @param issueName the issue name
   * @param status the issue status
   * @throws IOException if file creation fails
   */
  private void createPatchIssue(Path projectPath, String major, String minor, String patch,
    String issueName, String status) throws IOException
  {
    Path issueDir = projectPath.resolve(".cat").resolve("issues").
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

  /**
   * Verifies that an issue involved in a simple circular dependency (A depends on B, B depends on A)
   * is treated as blocked (not returned as a found issue).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void issueInSimpleCircularDependencyIsBlocked() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssueWithDependencies(projectPath, "2", "1", "issue-a", "open", "[2.1-issue-b]");
        createIssueWithDependencies(projectPath, "2", "1", "issue-b", "open", "[2.1-issue-a]");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        // Both issues are mutually blocked - neither should be found
        requireThat(result, "result").isInstanceOf(DiscoveryResult.NotFound.class);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
      }
    }
  }

  /**
   * Verifies that an issue involved in a complex 3-node circular dependency
   * (A depends on B, B depends on C, C depends on A) is treated as blocked.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void issueInComplexCircularDependencyIsBlocked() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssueWithDependencies(projectPath, "2", "1", "issue-a", "open", "[2.1-issue-b]");
        createIssueWithDependencies(projectPath, "2", "1", "issue-b", "open", "[2.1-issue-c]");
        createIssueWithDependencies(projectPath, "2", "1", "issue-c", "open", "[2.1-issue-a]");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        // All three issues are involved in a cycle - none should be found
        requireThat(result, "result").isInstanceOf(DiscoveryResult.NotFound.class);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
      }
    }
  }

  /**
   * Verifies that issues NOT involved in a circular dependency remain unaffected.
   * When a separate open issue exists alongside a cycle, it should still be found.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void issueNotInCycleIsFoundDespiteExistingCycle() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create a cycle
        createIssueWithDependencies(projectPath, "2", "1", "issue-a", "open", "[2.1-issue-b]");
        createIssueWithDependencies(projectPath, "2", "1", "issue-b", "open", "[2.1-issue-a]");
        // Create an unrelated open issue
        createIssue(projectPath, "2", "1", "unrelated-feature", "open");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        // The unrelated issue should be found
        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-unrelated-feature");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
      }
    }
  }

  /**
   * Verifies that when multiple open issues exist, the one whose STATE.md was committed earliest is
   * selected first, not the alphabetically first one.
   * <p>
   * issue-b is committed first (older), issue-a is committed second (newer but alphabetically first).
   * Expects issue-b to be returned.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void selectsOldestIssueFirst() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();

        // Set up the .cat/issues directory structure
        Path issuesDir = projectPath.resolve(".cat").resolve("issues");
        Path minorDir = issuesDir.resolve("v2").resolve("v2.1");

        // Create issue-b first (older commit date)
        Path issueBDir = minorDir.resolve("issue-b");
        Files.createDirectories(issueBDir);
        String stateContent = """
          # State

          - **Status:** open
          - **Progress:** 0%
          - **Dependencies:** []
          - **Blocks:** []
          """;
        Files.writeString(issueBDir.resolve("STATE.md"), stateContent);
        TestUtils.runGit(projectPath, "add", ".cat/issues/v2/v2.1/issue-b/STATE.md");
        TestUtils.runGit(projectPath, "commit", "--date=2026-01-01T00:00:01Z",
          "--author=Test User <test@example.com>", "-m", "Add issue-b");

        // Create issue-a second (newer commit date, but alphabetically first)
        Path issueADir = minorDir.resolve("issue-a");
        Files.createDirectories(issueADir);
        Files.writeString(issueADir.resolve("STATE.md"), stateContent);
        TestUtils.runGit(projectPath, "add", ".cat/issues/v2/v2.1/issue-a/STATE.md");
        TestUtils.runGit(projectPath, "commit", "--date=2026-01-01T00:00:02Z",
          "--author=Test User <test@example.com>", "-m", "Add issue-a");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        // issue-b was committed earlier so it should be selected first (not alphabetical issue-a)
        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-issue-b");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
      }
    }
  }

  /**
   * Verifies that when issues are in a non-git directory (no git history), they fall back to
   * {@code Long.MAX_VALUE} for creation time and are sorted alphabetically.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void nonGitEnvironmentFallsBackToAlphabeticalSort() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create two issues in a non-git directory; no git history available
        createIssue(projectPath, "2", "1", "issue-b", "open");
        createIssue(projectPath, "2", "1", "issue-a", "open");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        // Without git history, both issues get MAX_VALUE timestamp; alphabetical tiebreaker selects issue-a
        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-issue-a");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
      }
    }
  }

  /**
   * Verifies that when two issues have identical commit timestamps, the alphabetically earlier name
   * is selected first as the tiebreaker.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void tiedTimestampsUsesAlphabeticalTiebreaker() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();

        Path issuesDir = projectPath.resolve(".cat").resolve("issues");
        Path minorDir = issuesDir.resolve("v2").resolve("v2.1");

        String stateContent = """
          # State

          - **Status:** open
          - **Progress:** 0%
          - **Dependencies:** []
          - **Blocks:** []
          """;

        // Commit both issues with the same timestamp
        Path issueBDir = minorDir.resolve("issue-b");
        Files.createDirectories(issueBDir);
        Files.writeString(issueBDir.resolve("STATE.md"), stateContent);

        Path issueADir = minorDir.resolve("issue-a");
        Files.createDirectories(issueADir);
        Files.writeString(issueADir.resolve("STATE.md"), stateContent);

        TestUtils.runGit(projectPath, "add", ".");
        TestUtils.runGit(projectPath, "commit", "--date=2026-01-01T00:00:01Z",
          "--author=Test User <test@example.com>", "-m", "Add both issues");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        // Identical timestamps — alphabetical tiebreaker selects issue-a before issue-b
        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-issue-a");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
      }
    }
  }

  /**
   * Verifies that when the git log command fails (e.g., STATE.md not yet committed),
   * the issue creation time falls back to {@code Long.MAX_VALUE}, and alphabetical order determines
   * the result.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void gitCommandFailureFallsBackToAlphabeticalSort() throws IOException
  {
    Path projectPath = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create issues but do NOT commit them — git log will return empty output
        createIssue(projectPath, "2", "1", "issue-b", "open");
        createIssue(projectPath, "2", "1", "issue-a", "open");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        // No git history for these files — both fall back to MAX_VALUE; alphabetical selects issue-a
        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-issue-a");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
      }
    }
  }

  /**
   * Verifies that an issue directory containing only PLAN.md (no STATE.md) is included as a candidate
   * by findIssueInDir and treated as open.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void findIssueInDirIncludesPlanMdOnlyDir() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();

        // Create an issue with only PLAN.md - no STATE.md
        Path issueDir = projectPath.resolve(".cat").resolve("issues").
          resolve("v2").resolve("v2.1").resolve("plan-only-issue");
        Files.createDirectories(issueDir);
        Files.writeString(issueDir.resolve("PLAN.md"), "# Plan\n\n## Goal\n\nTest goal\n");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-plan-only-issue");
        requireThat(found.createStateMd(), "createStateMd").isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
      }
    }
  }

  /**
   * Verifies that hasOpenIssues returns true when a minor directory contains an issue
   * directory with only PLAN.md (no STATE.md).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void hasOpenIssuesReturnsTrueForPlanMdOnlyDir() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();

        // Create a closed issue (normally would prevent hasOpenIssues from returning true)
        createIssue(projectPath, "2", "1", "closed-issue", "closed");

        // Create an issue with only PLAN.md - no STATE.md
        Path issueDir = projectPath.resolve(".cat").resolve("issues").
          resolve("v2").resolve("v2.1").resolve("plan-only-issue");
        Files.createDirectories(issueDir);
        Files.writeString(issueDir.resolve("PLAN.md"), "# Plan\n\n## Goal\n\nTest goal\n");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        // The PLAN.md-only issue should be found since it's treated as open
        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
      }
    }
  }

  /**
   * Verifies that getIssueStatus returns "open" when the STATE.md file has no status field.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueStatusReturnOpenWhenStatusFieldMissing() throws IOException
  {
    List<String> lines = List.of("# State", "", "- **Progress:** 0%");
    Path fakePath = Path.of("fake/STATE.md");
    String result = SharedSecrets.getIssueStatus(lines, fakePath);
    requireThat(result, "result").isEqualTo("open");
  }

  /**
   * Verifies that getIssueStatus returns "open" when the STATE.md file is empty.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueStatusReturnOpenWhenFileIsEmpty() throws IOException
  {
    List<String> lines = List.of();
    Path fakePath = Path.of("fake/STATE.md");
    String result = SharedSecrets.getIssueStatus(lines, fakePath);
    requireThat(result, "result").isEqualTo("open");
  }

  /**
   * Verifies that an issue with only PLAN.md is found when looked up by specific issue ID.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void findSpecificIssueWithNullStateMdTreatsAsOpen() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();

        // Create an issue with only PLAN.md - no STATE.md
        Path issueDir = projectPath.resolve(".cat").resolve("issues").
          resolve("v2").resolve("v2.1").resolve("plan-only-issue");
        Files.createDirectories(issueDir);
        Files.writeString(issueDir.resolve("PLAN.md"), "# Plan\n\n## Goal\n\nTest goal\n");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ISSUE, "2.1-plan-only-issue", sessionId, "",
          false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-plan-only-issue");
        requireThat(found.createStateMd(), "createStateMd").isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
      }
    }
  }

  /**
   * Verifies that ALL-scope discovery releases the lock before skipping an issue with an existing
   * worktree, ensuring no orphaned lock files are left behind.
   * <p>
   * When a worktree directory exists for an issue during ALL-scope discovery, the lock must be
   * released before continuing to the next candidate issue. This invariant prevents stale lock files
   * from blocking future work-prepare invocations.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void allScopeDiscoveryReleasesLockWhenWorktreeExists() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectPath, "2", "1", "my-feature", "open");
        createIssue(projectPath, "2", "1", "other-feature", "open");

        // Create a fake worktree for my-feature so it is skipped during discovery
        Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
        Files.createDirectories(worktreesDir.resolve("2.1-my-feature"));

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        // Discovery should return other-feature
        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-other-feature");

        // The lock for my-feature must NOT exist — it should have been released before the skip
        Path lockDir = scope.getCatWorkPath().resolve("locks");
        Path lockFile = lockDir.resolve("2.1-my-feature.lock");
        requireThat(Files.exists(lockFile), "lockFileExists").isFalse();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
      }
    }
  }

  /**
   * Verifies that isCorrupt is true when STATE.md exists but PLAN.md is missing.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void foundResultIsCorruptWhenStateMdExistsButNoPlanMd() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();

        // Create issue directory with STATE.md only (no PLAN.md)
        Path issueDir = projectPath.resolve(".cat").resolve("issues").
          resolve("v2").resolve("v2.1").resolve("corrupt-feature");
        Files.createDirectories(issueDir);

        String stateContent = """
          # State

          - **Status:** open
          - **Progress:** 0%
          - **Dependencies:** []
          - **Blocks:** []
          """;
        Files.writeString(issueDir.resolve("STATE.md"), stateContent);
        // Deliberately no PLAN.md — this is the corrupt condition

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-corrupt-feature");
        requireThat(found.isCorrupt(), "isCorrupt").isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
      }
    }
  }

  /**
   * Verifies that isCorrupt is false when both STATE.md and PLAN.md exist.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void foundResultIsNotCorruptWhenBothStateMdAndPlanMdExist() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        createIssue(projectPath, "2", "1", "valid-feature", "open");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-valid-feature");
        requireThat(found.isCorrupt(), "isCorrupt").isFalse();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
      }
    }
  }

  /**
   * Verifies that constructing a Found record with both isCorrupt=true and createStateMd=true
   * is valid (directory has neither STATE.md nor PLAN.md).
   */
  @Test
  public void foundRecordAllowsIsCorruptAndCreateStateMdBothTrue()
  {
    DiscoveryResult.Found found = new DiscoveryResult.Found("2.1-some-issue", "2", "1", "", "some-issue",
      "/path/to/issue", "all", true, true, false);
    requireThat(found.isCorrupt(), "isCorrupt").isTrue();
    requireThat(found.createStateMd(), "createStateMd").isTrue();
  }

  /**
   * Verifies that isCorrupt is true and createStateMd is true when an issue directory has neither
   * STATE.md nor PLAN.md.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void foundResultIsCorruptAndCreateStateMdWhenNeitherFileExists() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();

        // Create issue directory with neither STATE.md nor PLAN.md
        Path issueDir = projectPath.resolve(".cat").resolve("issues").
          resolve("v2").resolve("v2.1").resolve("empty-feature");
        Files.createDirectories(issueDir);
        // Deliberately no STATE.md and no PLAN.md

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ALL, "", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-empty-feature");
        requireThat(found.isCorrupt(), "isCorrupt").isTrue();
        requireThat(found.createStateMd(), "createStateMd").isTrue();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
      }
    }
  }

  /**
   * Verifies that a decomposed parent with fully-qualified sub-issue names is processed correctly.
   * <p>
   * When all sub-issues are closed, the parent becomes eligible. The qualified name format
   * (e.g., {@code 2.1-parser-lexer}) allows {@code allSubissuesClosed()} to locate the sub-issue
   * directory and read its status.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void decomposedParentWithQualifiedSubissueNamesIsProcessed() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create a closed sub-issue using qualified name format
        createIssue(projectPath, "2", "1", "parser-lexer", "closed");
        // Create a decomposed parent referencing the sub-issue by qualified name
        createDecomposedParent(projectPath, "2", "1", "parent-task", "in-progress",
          "2.1-parser-lexer");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ISSUE, "2.1-parent-task", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        // Parent should be eligible since the qualified sub-issue is closed
        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-parent-task");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
      }
    }
  }

  /**
   * Verifies that bare names in the "Decomposed Into" section are silently skipped by
   * {@code allSubissuesClosed()}.
   * <p>
   * Bare names (e.g., {@code parser-lexer} without a version prefix) do not match
   * {@code QUALIFIED_NAME_PATTERN} and are ignored. The parent is treated as if those entries
   * do not exist. If all remaining qualified entries are closed (or there are none), the parent
   * becomes eligible.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void decomposedParentWithBareSubissueNamesSkipsBareEntries() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create a decomposed parent referencing only bare names (no version prefix)
        // Bare names are silently skipped — the parent is treated as if no sub-issues are listed
        createDecomposedParent(projectPath, "2", "1", "parent-task", "in-progress",
          "parser-lexer");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ISSUE, "2.1-parent-task", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        // Bare names are skipped; no qualified sub-issues remain to check, so parent is eligible
        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-parent-task");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
      }
    }
  }

  /**
   * Verifies that a decomposed parent whose "Decomposed Into" section contains bare sub-issue names
   * (without version prefix) is treated as {@code Decomposed} because {@code allSubissuesClosed()}
   * skips bare names and returns {@code true} (empty list), making the parent appear eligible — but
   * before migration the parent remains in a state where the sub-issue is actually open.
   * <p>
   * After running the Phase 17 migration (which qualifies the bare names), the parent correctly
   * returns {@code Decomposed} when the sub-issue is open and {@code Found} when the sub-issue is
   * closed.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void phase17MigrationQualifiesBareNamesAndAllSubissuesClosedBehavesCorrectly()
    throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-migration-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();

        // Create a sub-task issue (open)
        createIssue(projectPath, "2", "1", "sub-task", "open");

        // Create a decomposed parent with a BARE sub-issue name (pre-migration state)
        createDecomposedParentWithBareName(projectPath, "2", "1", "parent-task", "open", "sub-task");

        // Before migration: bare names are skipped by allSubissuesClosed(), so it returns true,
        // and the parent is treated as eligible (Found) rather than Decomposed.
        // This is the bug that the migration fixes.
        IssueDiscovery discoveryBefore = new IssueDiscovery(scope);
        SearchOptions optionsBefore = new SearchOptions(Scope.ISSUE, "2.1-parent-task", sessionId,
          "", false);
        // With bare names, allSubissuesClosed() returns true (no qualified names to check),
        // so the parent is Found (incorrectly eligible despite open sub-issue).
        DiscoveryResult resultBefore = discoveryBefore.findNextIssue(optionsBefore);
        requireThat(resultBefore, "resultBefore").isInstanceOf(DiscoveryResult.Found.class);

        // Apply Phase 17 migration: qualify the bare sub-issue name in the parent STATE.md
        Path parentStatePath = projectPath.resolve(".cat").resolve("issues").
          resolve("v2").resolve("v2.1").resolve("parent-task").resolve("STATE.md");
        applyPhase17Migration(parentStatePath, "2.1-");

        // After migration: "sub-task" becomes "2.1-sub-task" in the Decomposed Into section.
        // Verify STATE.md was updated correctly.
        String migratedContent = Files.readString(parentStatePath);
        requireThat(migratedContent, "migratedContent").contains("- 2.1-sub-task");
        requireThat(migratedContent, "migratedContent").doesNotContain("- sub-task\n");

        // After migration with sub-issue open: parent is Decomposed (cannot proceed)
        IssueDiscovery discoveryAfterOpen = new IssueDiscovery(scope);
        SearchOptions optionsAfterOpen = new SearchOptions(Scope.ISSUE, "2.1-parent-task", sessionId,
          "", false);
        DiscoveryResult resultAfterOpen = discoveryAfterOpen.findNextIssue(optionsAfterOpen);
        requireThat(resultAfterOpen, "resultAfterOpen").isInstanceOf(DiscoveryResult.Decomposed.class);
        DiscoveryResult.Decomposed decomposed = (DiscoveryResult.Decomposed) resultAfterOpen;
        requireThat(decomposed.issueId(), "issueId").isEqualTo("2.1-parent-task");

        // Now close the sub-issue
        Path subTaskStatePath = projectPath.resolve(".cat").resolve("issues").
          resolve("v2").resolve("v2.1").resolve("sub-task").resolve("STATE.md");
        String closedState = """
          # State

          - **Status:** closed
          - **Progress:** 100%
          - **Dependencies:** []
          - **Blocks:** []
          """;
        Files.writeString(subTaskStatePath, closedState);

        // After migration with sub-issue closed: parent is Found (eligible to proceed)
        IssueDiscovery discoveryAfterClosed = new IssueDiscovery(scope);
        SearchOptions optionsAfterClosed = new SearchOptions(Scope.ISSUE, "2.1-parent-task", sessionId,
          "", false);
        DiscoveryResult resultAfterClosed = discoveryAfterClosed.findNextIssue(optionsAfterClosed);
        requireThat(resultAfterClosed, "resultAfterClosed").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) resultAfterClosed;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-parent-task");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
      }
    }
  }

  /**
   * Creates a decomposed parent issue with a bare (unqualified) sub-issue name in STATE.md.
   * This represents the pre-migration state that Phase 17 corrects.
   *
   * @param projectPath the project root directory
   * @param major the major version number
   * @param minor the minor version number
   * @param issueName the parent issue name
   * @param status the parent issue status
   * @param bareSubIssueName the bare (unqualified) sub-issue name to reference
   * @throws IOException if file creation fails
   */
  private void createDecomposedParentWithBareName(Path projectPath, String major, String minor,
    String issueName, String status, String bareSubIssueName) throws IOException
  {
    Path issueDir = projectPath.resolve(".cat").resolve("issues").
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
      """.formatted(status, bareSubIssueName);

    Files.writeString(issueDir.resolve("STATE.md"), stateContent);
  }

  /**
   * Applies the Phase 17 migration transformation to a STATE.md file.
   * Qualifies bare sub-issue names in the "Decomposed Into" section by prepending the version prefix.
   * Names already matching {@code digit.digit-} are left unchanged (idempotent).
   *
   * @param statePath the path to the STATE.md file to transform
   * @param versionPrefix the version prefix to prepend (e.g., {@code "2.1-"})
   * @throws IOException if reading or writing the file fails
   */
  private static void applyPhase17Migration(Path statePath, String versionPrefix) throws IOException
  {
    Pattern qualifiedPattern = Pattern.compile("^- \\d+\\.\\d+.*-.*");
    List<String> lines = Files.readAllLines(statePath);
    List<String> result = new ArrayList<>();
    boolean inDecomposedSection = false;
    for (String line : lines)
    {
      if (line.equals("## Decomposed Into"))
      {
        inDecomposedSection = true;
        result.add(line);
        continue;
      }
      if (inDecomposedSection && line.startsWith("## "))
        inDecomposedSection = false;
      if (inDecomposedSection && line.startsWith("- ") && !qualifiedPattern.matcher(line).matches())
        result.add("- " + versionPrefix + line.substring(2));
      else
        result.add(line);
    }
    StringJoiner joiner = new StringJoiner("\n");
    for (String line : result)
      joiner.add(line);
    Files.writeString(statePath, joiner.toString() + "\n");
  }

  /**
   * Verifies that letter-suffixed version prefixes (e.g., {@code 2.1a-}) are treated as qualified
   * names by {@code allSubissuesClosed()}.
   * <p>
   * The pattern {@code QUALIFIED_NAME_PATTERN} accepts an optional lowercase letter after the minor
   * version number (e.g., {@code 2.1a-sub-issue}). Such entries are processed the same way as
   * standard qualified names — the sub-issue directory is resolved and its status is checked.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void decomposedParentWithLetterSuffixedVersionPrefixIsHandled() throws IOException
  {
    Path projectPath = TestUtils.createTempCatProject("issue-discovery-test");
    try (JvmScope scope = new TestJvmScope(projectPath, projectPath))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();
        // Create a closed sub-issue in v2.1 (standard minor version)
        createIssue(projectPath, "2", "1", "sub-issue", "closed");
        // Create a decomposed parent that references the sub-issue with a letter-suffixed prefix
        // Note: the parent lives in v2.1; the sub-issue reference uses "2.1a-" prefix.
        // Since "2.1a-sub-issue" is a qualified name, allSubissuesClosed() tries to look it up
        // in the parent's version directory. The directory "sub-issue" exists and is closed,
        // so the parent becomes eligible.
        createDecomposedParent(projectPath, "2", "1", "parent-task", "in-progress",
          "2.1a-sub-issue");

        IssueDiscovery discovery = new IssueDiscovery(scope);
        SearchOptions options = new SearchOptions(Scope.ISSUE, "2.1-parent-task", sessionId, "", false);
        DiscoveryResult result = discovery.findNextIssue(options);

        // "2.1a-sub-issue" is a qualified name so the directory "sub-issue" is checked.
        // The directory exists and is closed, so the parent should be eligible.
        requireThat(result, "result").isInstanceOf(DiscoveryResult.Found.class);
        DiscoveryResult.Found found = (DiscoveryResult.Found) result;
        requireThat(found.issueId(), "issueId").isEqualTo("2.1-parent-task");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(projectPath);
      }
    }
  }
}
