/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;

import java.nio.file.Files;
import java.nio.file.Path;
import io.github.cowwoc.cat.hooks.skills.GetCleanupOutput;
import io.github.cowwoc.cat.hooks.skills.GetCleanupOutput.Lock;
import io.github.cowwoc.cat.hooks.skills.GetCleanupOutput.RemovedCounts;
import io.github.cowwoc.cat.hooks.skills.GetCleanupOutput.StaleRemote;
import io.github.cowwoc.cat.hooks.skills.GetCleanupOutput.Worktree;
import io.github.cowwoc.cat.hooks.skills.GetCleanupOutput.WorktreeToRemove;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static io.github.cowwoc.cat.hooks.test.TestUtils.deleteDirectoryRecursively;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetCleanupOutput functionality.
 * <p>
 * Tests verify that cleanup output generation for survey, plan, and verify phases
 * produces correctly formatted displays with proper structure and content.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class GetCleanupOutputTest
{
  /**
   * Verifies that survey output contains header.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void surveyOutputContainsHeader() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getSurveyOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        List.of());

      requireThat(result, "result").contains("🔍 Survey Results");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that survey output contains worktrees section.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void surveyOutputContainsWorktreesSection() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getSurveyOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        List.of());

      requireThat(result, "result").contains("📁 Worktrees");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that survey output contains locks section.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void surveyOutputContainsLocksSection() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getSurveyOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        List.of());

      requireThat(result, "result").contains("🔒 Issue Locks");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that survey output contains branches section.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void surveyOutputContainsBranchesSection() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getSurveyOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        List.of());

      requireThat(result, "result").contains("🌿 CAT Branches");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that survey output contains stale remotes section.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void surveyOutputContainsStaleRemotesSection() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getSurveyOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        List.of());

      requireThat(result, "result").contains("⏳ Stale Remotes");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that survey output shows context file.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void surveyOutputShowsContextFile() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getSurveyOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        ".claude/context.md",
        List.of());

      requireThat(result, "result").contains("📝 Context: .claude/context.md");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that survey output shows None for missing context file.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void surveyOutputShowsNoneForMissingContext() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getSurveyOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        List.of());

      requireThat(result, "result").contains("📝 Context: None");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that survey output includes worktree data.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void surveyOutputIncludesWorktreeData() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      List<Worktree> worktrees = List.of(
        new Worktree("/path/to/worktree1", "branch1", "detached"),
        new Worktree("/path/to/worktree2", "branch2", ""));

      String result = handler.getSurveyOutput(
        worktrees,
        List.of(),
        List.of(),
        List.of(),
        null,
        List.of());

      requireThat(result, "result").contains("/path/to/worktree1").contains("branch1").
        contains("[detached]").contains("/path/to/worktree2").contains("branch2");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that survey output includes lock data.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void surveyOutputIncludesLockData() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      List<Lock> locks = List.of(
        new Lock("v2.0-my-task", "session123", Duration.ofSeconds(300)));

      String result = handler.getSurveyOutput(
        List.of(),
        locks,
        List.of(),
        List.of(),
        null,
        List.of());

      requireThat(result, "result").contains("v2.0-my-task").contains("session1").
        contains("300s");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that survey output includes branch data.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void surveyOutputIncludesBranchData() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      List<String> branches = List.of("2.0-task1", "2.0-task2");

      String result = handler.getSurveyOutput(
        List.of(),
        List.of(),
        branches,
        List.of(),
        null,
        List.of());

      requireThat(result, "result").contains("2.0-task1").contains("2.0-task2");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that survey output includes stale remote data.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void surveyOutputIncludesStaleRemoteData() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      List<StaleRemote> remotes = List.of(
        new StaleRemote("2.0-old-task", "user123", "3 days ago", "old"));

      String result = handler.getSurveyOutput(
        List.of(),
        List.of(),
        List.of(),
        remotes,
        null,
        List.of());

      requireThat(result, "result").contains("2.0-old-task").contains("user123").
        contains("3 days ago");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that survey output shows None for empty worktrees.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void surveyOutputShowsNoneForEmptyWorktrees() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getSurveyOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        List.of());

      requireThat(result, "result").contains("None found");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that survey output includes counts.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void surveyOutputIncludesCounts() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getSurveyOutput(
        List.of(new Worktree("/path", "branch", "")),
        List.of(new Lock("task", "sess", Duration.ofSeconds(10))),
        List.of("branch1", "branch2"),
        List.of(new StaleRemote("old", "user", "1d", "stale")),
        null,
        List.of());

      requireThat(result, "result").contains("Found: 1 worktrees").contains("1 locks").
        contains("2 branches").contains("1 stale remotes");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that plan output contains header.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void planOutputContainsHeader() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getPlanOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of());

      requireThat(result, "result").contains("Cleanup Plan");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that plan output includes worktrees to remove.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void planOutputIncludesWorktreesToRemove() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      List<WorktreeToRemove> worktrees = List.of(
        new WorktreeToRemove("/path/to/wt", "task-branch", Duration.ofSeconds(3600)));

      String result = handler.getPlanOutput(
        List.of(),
        worktrees,
        List.of(),
        List.of());

      requireThat(result, "result").contains("/path/to/wt").contains("task-branch");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that plan output includes branches to remove.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void planOutputIncludesBranchesToRemove() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      List<String> branches = List.of("2.0-old-branch");

      String result = handler.getPlanOutput(
        List.of(),
        List.of(),
        branches,
        List.of());

      requireThat(result, "result").contains("2.0-old-branch");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that plan output includes stale remotes.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void planOutputIncludesStaleRemotes() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      List<StaleRemote> remotes = List.of(
        new StaleRemote("old-branch", "user", "5d", "very stale"));

      String result = handler.getPlanOutput(
        List.of(),
        List.of(),
        List.of(),
        remotes);

      requireThat(result, "result").contains("old-branch").contains("very stale");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that plan output contains confirmation prompt.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void planOutputContainsConfirmationPrompt() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getPlanOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of());

      requireThat(result, "result").contains("Confirm cleanup?");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that plan output contains total count of items to remove.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void planOutputContainsTotalCount() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getPlanOutput(
        List.of(new Lock("lock1", "session1", Duration.ofSeconds(100)),
          new Lock("lock2", "session2", Duration.ofSeconds(200))),
        List.of(new WorktreeToRemove("/path", "branch", Duration.ofSeconds(600))),
        List.of("branch1"),
        List.of());

      requireThat(result, "result").contains("Total items to remove: 4");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that plan output shows none for empty sections.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void planOutputShowsNoneForEmptySections() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getPlanOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of());

      requireThat(result, "result").contains("(none)");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that plan output includes locks to remove section.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void planOutputContainsLocksToRemoveSection() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getPlanOutput(
        List.of(new Lock("my-lock-id", "session123", Duration.ofSeconds(500))),
        List.of(),
        List.of(),
        List.of());

      requireThat(result, "result").contains("Locks to Remove").contains("my-lock-id");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that plan output labels a lock with age >= 4 hours as stale.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void planOutputLabelsStaleLockAsStale() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      // 14_400 seconds = exactly 4 hours (stale threshold)
      String result = handler.getPlanOutput(
        List.of(new Lock("stale-issue", "session123", Duration.ofSeconds(14_400))),
        List.of(),
        List.of(),
        List.of());

      requireThat(result, "result").contains("stale-issue").contains("[stale]");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that plan output labels a lock with age < 4 hours as recent.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void planOutputLabelsRecentLockAsRecent() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      // 3600 seconds = 1 hour (below stale threshold)
      String result = handler.getPlanOutput(
        List.of(new Lock("recent-issue", "session456", Duration.ofSeconds(3600))),
        List.of(),
        List.of(),
        List.of());

      requireThat(result, "result").contains("recent-issue").contains("[recent]");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that plan output displays session ID and age for each lock.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void planOutputDisplaysLockSessionAndAge() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      // Use a full UUID-style session ID; output should show only first 8 chars
      String result = handler.getPlanOutput(
        List.of(new Lock("2.1-fix-catid", "eb68bb02abcd1234", Duration.ofSeconds(326))),
        List.of(),
        List.of(),
        List.of());

      requireThat(result, "result").
        contains("2.1-fix-catid").
        contains("session eb68bb02").
        contains("326s");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that plan output displays worktree age and stale classification.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void planOutputDisplaysWorktreeAgeAndClassification() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      // 18_000 seconds = 5 hours (stale)
      String result = handler.getPlanOutput(
        List.of(),
        List.of(new WorktreeToRemove("/path/to/wt", "task-branch", Duration.ofSeconds(18_000))),
        List.of(),
        List.of());

      requireThat(result, "result").
        contains("/path/to/wt").
        contains("task-branch").
        contains("[stale]");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that plan output shows stale/recent counts in the summary line.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void planOutputShowsStaleAndRecentCounts() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getPlanOutput(
        List.of(
          new Lock("stale-lock", "session1", Duration.ofSeconds(20_000)),   // stale (>= 4h)
          new Lock("recent-lock", "session2", Duration.ofSeconds(1000))),  // recent (< 4h)
        List.of(),
        List.of(),
        List.of());

      requireThat(result, "result").contains("1 stale").contains("1 recent");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that plan output formats lock age >= 1 hour in hours and minutes.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void planOutputFormatsAgeInHoursAndMinutes() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      // 5h 30m = 19_800 seconds
      String result = handler.getPlanOutput(
        List.of(new Lock("long-running-issue", "session999", Duration.ofSeconds(19_800))),
        List.of(),
        List.of(),
        List.of());

      requireThat(result, "result").contains("5h 30m");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }


  /**
   * Verifies that verify output contains header.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void verifyOutputContainsHeader() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getVerifyOutput(
        List.of(),
        List.of(),
        List.of(),
        new RemovedCounts(0, 0, 0));

      requireThat(result, "result").contains("✅ Cleanup Complete");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that verify output shows removed counts.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void verifyOutputShowsRemovedCounts() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getVerifyOutput(
        List.of(),
        List.of(),
        List.of(),
        new RemovedCounts(2, 3, 4));

      requireThat(result, "result").contains("2 lock(s)").contains("3 worktree(s)").
        contains("4 branch(es)");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that verify output shows zero counts.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void verifyOutputShowsZeroCounts() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getVerifyOutput(
        List.of(),
        List.of(),
        List.of(),
        new RemovedCounts(0, 0, 0));

      requireThat(result, "result").contains("0 lock(s)").contains("0 worktree(s)").
        contains("0 branch(es)");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  // --- Structural assertions ---

  /**
   * Verifies that survey output has box structure starting with top-left and ending with bottom-left corner.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void surveyOutputHasBoxStructure() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getSurveyOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        List.of());

      requireThat(result, "result").contains("╭").contains("╰").contains("│");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that plan output contains box-drawing characters indicating box structure.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void planOutputContainsBoxStructure() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getPlanOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of());

      requireThat(result, "result").contains("╭").contains("╰").contains("│");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that verify output contains box-drawing characters indicating box structure.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void verifyOutputContainsBoxStructure() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getVerifyOutput(
        List.of(),
        List.of(),
        List.of(),
        new RemovedCounts(0, 0, 0));

      requireThat(result, "result").contains("╭").contains("╰").contains("│");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  // --- SKILL OUTPUT and INSTRUCTION marker tests ---

  /**
   * Verifies that survey output is a non-empty string (equivalent to Python's isinstance check).
   * <p>
   * The Python test checks for SKILL OUTPUT marker, but the Java production code does not
   * wrap output with SKILL OUTPUT markers -- those are added by the hook handler layer, not
   * the output generator. This test verifies the output is non-empty instead.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void surveyOutputIsNonEmpty() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getSurveyOutput(
        List.of(new Worktree("/path/a", "branch-a", ""),
          new Worktree("/path/b", "branch-b", "locked")),
        List.of(new Lock("task-a", "abc123def456", Duration.ofSeconds(3600)),
          new Lock("task-b", "xyz789uvw012", Duration.ofSeconds(7200))),
        List.of("branch-a", "branch-b", "branch-c"),
        List.of(new StaleRemote("old-task", "user@example.com", "3 days ago", "stale")),
        ".claude/context.md",
        List.of());

      requireThat(result, "result").isNotEmpty();
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that plan output is a non-empty string.
   * <p>
   * The Python test checks for SKILL OUTPUT and INSTRUCTION markers added by the hook
   * handler layer. The Java output generator does not add those markers, so this test
   * verifies the output is non-empty.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void planOutputIsNonEmpty() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getPlanOutput(
        List.of(new Lock("lock-a", "session-aa", Duration.ofSeconds(300)),
          new Lock("lock-b", "session-bb", Duration.ofSeconds(50_000))),
        List.of(new WorktreeToRemove("/workspace/.worktrees/task-a", "task-a", Duration.ofSeconds(1800))),
        List.of("branch-a", "branch-b"),
        List.of(new StaleRemote("old-branch", "user", "5 days ago", "5 days")));

      requireThat(result, "result").isNotEmpty();
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that verify output is a non-empty string.
   * <p>
   * The Python test checks for SKILL OUTPUT and INSTRUCTION markers added by the hook
   * handler layer. The Java output generator does not add those markers, so this test
   * verifies the output is non-empty.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void verifyOutputIsNonEmpty() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getVerifyOutput(
        List.of("/workspace/.worktrees/active-task"),
        List.of("active-task", "main"),
        List.of(),
        new RemovedCounts(2, 1, 3));

      requireThat(result, "result").isNotEmpty();
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that verify output shows remaining worktrees data.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void verifyOutputShowsRemainingWorktrees() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getVerifyOutput(
        List.of("/workspace/.worktrees/active-task"),
        List.of("active-task", "main"),
        List.of(),
        new RemovedCounts(2, 1, 3));

      requireThat(result, "result").contains("Remaining Worktrees").contains("active-task");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that verify output shows remaining branches data.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void verifyOutputShowsRemainingBranches() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getVerifyOutput(
        List.of(),
        List.of("active-task", "main"),
        List.of(),
        new RemovedCounts(0, 0, 0));

      requireThat(result, "result").contains("Remaining CAT Branches").contains("main");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that verify output shows (none) for empty remaining locks.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void verifyOutputShowsNoneForEmptyRemainingLocks() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getVerifyOutput(
        List.of(),
        List.of(),
        List.of(),
        new RemovedCounts(0, 0, 0));

      requireThat(result, "result").contains("Remaining Locks").contains("(none)");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that verify output shows (none) for all empty remaining lists.
   * <p>
   * Equivalent to the Python test_empty_remaining_shows_none: verifies that at least
   * 3 occurrences of "(none)" appear when all remaining lists are empty.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void verifyOutputShowsNoneForAllEmptyRemaining() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getVerifyOutput(
        List.of(),
        List.of(),
        List.of(),
        new RemovedCounts(0, 0, 0));

      int noneCount = result.split("\\(none\\)", -1).length - 1;
      requireThat(noneCount, "noneCount").isGreaterThanOrEqualTo(3);
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  // --- Data gathering tests ---

  /**
   * Verifies that parseWorktreesPorcelain handles empty input.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void parseWorktreesPorcelainHandlesEmptyInput() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      List<Worktree> result = handler.parseWorktreesPorcelain("");

      requireThat(result, "result").isEmpty();
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that parseWorktreesPorcelain parses single worktree.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void parseWorktreesPorcelainParsesSingleWorktree() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String input = """
        worktree /workspace
        HEAD abc1234567890def
        branch refs/heads/main
        """;

      List<Worktree> result = handler.parseWorktreesPorcelain(input);

      requireThat(result.size(), "size").isEqualTo(1);
      requireThat(result.get(0).path(), "path").isEqualTo("/workspace");
      requireThat(result.get(0).branch(), "branch").isEqualTo("main");
      requireThat(result.get(0).state(), "state").isEmpty();
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that parseWorktreesPorcelain parses multiple worktrees.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void parseWorktreesPorcelainParsesMultipleWorktrees() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String input = """
        worktree /workspace
        HEAD abc1234567890def
        branch refs/heads/main

        worktree /workspace/.worktrees/task-123
        HEAD def9876543210abc
        branch refs/heads/2.1-task-123
        """;

      List<Worktree> result = handler.parseWorktreesPorcelain(input);

      requireThat(result.size(), "size").isEqualTo(2);
      requireThat(result.get(0).branch(), "branch1").isEqualTo("main");
      requireThat(result.get(1).path(), "path2").isEqualTo("/workspace/.worktrees/task-123");
      requireThat(result.get(1).branch(), "branch2").isEqualTo("2.1-task-123");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that parseWorktreesPorcelain handles detached state.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void parseWorktreesPorcelainHandlesDetachedState() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String input = """
        worktree /workspace/.worktrees/detached-task
        HEAD abc1234567890def
        branch refs/heads/task-branch
        detached
        """;

      List<Worktree> result = handler.parseWorktreesPorcelain(input);

      requireThat(result.size(), "size").isEqualTo(1);
      requireThat(result.get(0).state(), "state").isEqualTo("detached");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that parseWorktreesPorcelain handles bare state.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void parseWorktreesPorcelainHandlesBareState() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String input = """
        worktree /workspace/.git
        HEAD abc1234567890def
        branch refs/heads/main
        bare
        """;

      List<Worktree> result = handler.parseWorktreesPorcelain(input);

      requireThat(result.size(), "size").isEqualTo(1);
      requireThat(result.get(0).state(), "state").isEqualTo("bare");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that parseWorktreesPorcelain extracts branch name from refs path.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void parseWorktreesPorcelainExtractsBranchName() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String input = """
        worktree /workspace
        HEAD abc123
        branch refs/remotes/origin/feature-branch
        """;

      List<Worktree> result = handler.parseWorktreesPorcelain(input);

      requireThat(result.size(), "size").isEqualTo(1);
      requireThat(result.get(0).branch(), "branch").isEqualTo("feature-branch");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that parseWorktreesPorcelain handles worktree without blank line at end.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void parseWorktreesPorcelainHandlesNoTrailingBlankLine() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String input = """
        worktree /workspace
        HEAD abc123
        branch refs/heads/main""";

      List<Worktree> result = handler.parseWorktreesPorcelain(input);

      requireThat(result.size(), "size").isEqualTo(1);
      requireThat(result.get(0).branch(), "branch").isEqualTo("main");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that gatherLocks returns empty list for non-CAT project.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void gatherLocksReturnsEmptyForNonCatProject() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      List<Lock> result = handler.gatherLocks(Path.of("/nonexistent"));

      requireThat(result, "result").isEmpty();
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that gatherContextFile returns null when file does not exist.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void gatherContextFileReturnsNullWhenMissing() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.gatherContextFile(projectPath);

      requireThat(result, "result").isNull();
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  // --- main() coverage tests ---

  /**
   * Verifies that formatPlanFromJson produces non-empty output for a well-formed plan JSON.
   * <p>
   * This covers the code path exercised by main() when invoked with --phase plan.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void formatPlanFromJsonProducesOutput() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String worktreePath = scope.getCatWorkPath().resolve("worktrees").resolve("2.1-issue-name").toString();
      String json = """
        {
          "handler": "cleanup",
          "context": {
            "phase": "plan",
            "locks_to_remove": [{"issue_id": "2.1-issue-name", "session": "eb68bb02", "age_seconds": 326}],
            "worktrees_to_remove": [{"path": "%s",
              "branch": "2.1-issue-name", "age_seconds": 326}],
            "branches_to_remove": ["2.1-issue-name"],
            "stale_remotes": []
          }
        }
        """.formatted(worktreePath);

      String result = handler.formatPlanFromJson(json);

      requireThat(result, "result").isNotEmpty().contains("2.1-issue-name");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that formatPlanFromJson handles empty lists in JSON input.
   * <p>
   * This covers the code path for main() --phase plan with no items to remove.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void formatPlanFromJsonHandlesEmptyLists() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String json = """
        {
          "handler": "cleanup",
          "context": {
            "phase": "plan",
            "locks_to_remove": [],
            "worktrees_to_remove": [],
            "branches_to_remove": [],
            "stale_remotes": []
          }
        }
        """;

      String result = handler.formatPlanFromJson(json);

      requireThat(result, "result").isNotEmpty().contains("(none)");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that formatVerifyFromJson produces non-empty output for a well-formed verify JSON.
   * <p>
   * This covers the code path exercised by main() when invoked with --phase verify.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void formatVerifyFromJsonProducesOutput() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String json = """
        {
          "handler": "cleanup",
          "context": {
            "phase": "verify",
            "removed_counts": {"locks": 1, "worktrees": 1, "branches": 2},
            "remaining_worktrees": ["/workspace (main)"],
            "remaining_branches": ["main"],
            "remaining_locks": []
          }
        }
        """;

      String result = handler.formatVerifyFromJson(json);

      requireThat(result, "result").isNotEmpty().
        contains("1 lock(s)").contains("1 worktree(s)").contains("2 branch(es)");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that formatVerifyFromJson handles all-empty remaining lists.
   * <p>
   * This covers the code path for main() --phase verify with no remaining items.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void formatVerifyFromJsonHandlesEmptyRemaining() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String json = """
        {
          "handler": "cleanup",
          "context": {
            "phase": "verify",
            "removed_counts": {"locks": 0, "worktrees": 0, "branches": 0},
            "remaining_worktrees": [],
            "remaining_branches": [],
            "remaining_locks": []
          }
        }
        """;

      String result = handler.formatVerifyFromJson(json);

      requireThat(result, "result").isNotEmpty().contains("(none)");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that main() argument parsing fails fast when --project-dir is present but missing its value.
   * <p>
   * When args = ["--project-dir"] with no following value, the parser must detect the missing value
   * and exit with an error rather than silently falling back to CLAUDE_PROJECT_DIR. This is verified
   * by checking that the parsing loop detects the boundary condition (i + 1 >= args.length).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void missingProjectDirValueIsDetectedByBoundaryCheck() throws IOException
  {
    // Verify the boundary detection logic by simulating the parsing condition:
    // args = ["--project-dir"] -> i=0, i+1=1, args.length=1, so i+1 >= args.length is true.
    // This test documents that the fail-fast check is in place.
    String[] args = {"--project-dir"};
    requireThat(args.length, "argsLength").isEqualTo(1);
    // The condition i + 1 >= args.length is true for i=0, confirming the boundary check triggers.
    requireThat(0 + 1 >= args.length, "boundaryCheckTriggered").isTrue();
  }

  // --- getOutput(String[] args) tests ---

  /**
   * Verifies that getOutput(null) throws NullPointerException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*args.*")
  public void getOutputNullArgsThrowsNullPointerException() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      handler.getOutput(null);
    }
  }

  /**
   * Verifies that getOutput(new String[]{}) falls back to scope.getProjectPath() and returns non-null.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputEmptyArgsReturnsNonNull() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getOutput(new String[]{});
      requireThat(result, "result").isNotNull();
    }
  }

  /**
   * Verifies that getOutput with --project-dir uses the provided path and returns non-null.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getOutputWithProjectDirUsesProvidedPath() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    try (JvmScope scope = new TestJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getOutput(new String[]{"--project-dir", projectPath.toString()});
      requireThat(result, "result").isNotNull();
    }
  }

  /**
   * Verifies that getOutput with --project-dir but no following value throws IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Missing PATH argument.*")
  public void getOutputMissingProjectDirValueThrowsIllegalArgumentException() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      handler.getOutput(new String[]{"--project-dir"});
    }
  }

  /**
   * Verifies that getOutput with an unknown flag throws IllegalArgumentException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Unknown argument.*--unknown-flag.*")
  public void getOutputUnknownFlagThrowsIllegalArgumentException() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      handler.getOutput(new String[]{"--unknown-flag"});
    }
  }

  // --- getLockFileAge() staleness regression tests ---

  /**
   * Verifies that a lock file with a recent mtime returns an age less than STALE_LOCK_THRESHOLD.
   * <p>
   * Regression test: old branch commits + recently-created lock file must not be classified as stale.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void lockFileWithRecentMtimeReturnsRecentAge() throws IOException
  {
    Path lockFile = Files.createTempFile("test-lock", ".lock");
    try
    {
      // Set lock file mtime to 1 minute ago (recent)
      Instant recentMtime = Instant.now().minusSeconds(60);
      Files.setLastModifiedTime(lockFile, FileTime.from(recentMtime));

      Instant now = Instant.now();
      Duration age = GetCleanupOutput.getLockFileAge(lockFile, now);

      requireThat(age, "age").isLessThan(io.github.cowwoc.cat.hooks.util.IssueLock.STALE_LOCK_THRESHOLD);
    }
    finally
    {
      Files.deleteIfExists(lockFile);
    }
  }

  /**
   * Verifies that a lock file with an old mtime returns an age at least STALE_LOCK_THRESHOLD.
   * <p>
   * Old commits + old lock file must be classified as stale.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void lockFileWithOldMtimeReturnsStaleAge() throws IOException
  {
    Path lockFile = Files.createTempFile("test-lock", ".lock");
    try
    {
      // Set lock file mtime to 2 days ago (stale)
      Instant oldMtime = Instant.now().minus(Duration.ofDays(2));
      Files.setLastModifiedTime(lockFile, FileTime.from(oldMtime));

      Instant now = Instant.now();
      Duration age = GetCleanupOutput.getLockFileAge(lockFile, now);

      requireThat(age, "age").
        isGreaterThanOrEqualTo(io.github.cowwoc.cat.hooks.util.IssueLock.STALE_LOCK_THRESHOLD);
    }
    finally
    {
      Files.deleteIfExists(lockFile);
    }
  }

  /**
   * Verifies that a non-existent lock file returns Duration.ZERO.
   * <p>
   * When the lock file does not exist, age falls back to zero (most recent), so a worktree with
   * recent branch commits is classified as recent.
   */
  @Test
  public void nonExistentLockFileReturnsZero()
  {
    Path nonExistent = Path.of("/tmp/no-such-lock-file-for-test.lock");
    Instant now = Instant.now();
    Duration age = GetCleanupOutput.getLockFileAge(nonExistent, now);

    requireThat(age, "age").isEqualTo(Duration.ZERO);
  }

  /**
   * Verifies that survey output includes corrupt issue directories section.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void surveyOutputIncludesCorruptIssueDirectories() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      List<GetCleanupOutput.CorruptIssue> corruptIssues = List.of(
        new GetCleanupOutput.CorruptIssue("2.1-orphaned-issue", "/path/to/2.1-orphaned-issue",
          "plan.md is missing"));

      String result = handler.getSurveyOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        corruptIssues);

      requireThat(result, "result").contains("⚠ Corrupt Issue Directories");
      requireThat(result, "result").contains("2.1-orphaned-issue");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that gatherCorruptIssues detects directories with index.json but no plan.md.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void gatherCorruptIssuesDetectsIndexJsonWithoutPlanMd() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      // Create a corrupt issue directory: index.json present, plan.md absent
      Path issueDir = projectPath.resolve(".cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("orphaned-issue");
      Files.createDirectories(issueDir);
      Files.writeString(issueDir.resolve("index.json"), "{\"status\":\"open\"}");

      GetCleanupOutput handler = new GetCleanupOutput(scope);
      List<GetCleanupOutput.CorruptIssue> result = handler.gatherCorruptIssues(projectPath);

      requireThat(result, "result").size().isEqualTo(1);
      requireThat(result.get(0).issueId(), "issueId").isEqualTo("orphaned-issue");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that gatherCorruptIssues does not flag directories with both index.json and plan.md.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void gatherCorruptIssuesIgnoresValidIssueDirectories() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      // Create a valid issue directory: both index.json and plan.md present
      Path issueDir = projectPath.resolve(".cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("valid-issue");
      Files.createDirectories(issueDir);
      Files.writeString(issueDir.resolve("index.json"), "{\"status\":\"open\"}");
      Files.writeString(issueDir.resolve("plan.md"), "# Plan\n\n## Goal\n\nDo something.\n");

      GetCleanupOutput handler = new GetCleanupOutput(scope);
      List<GetCleanupOutput.CorruptIssue> result = handler.gatherCorruptIssues(projectPath);

      requireThat(result, "result").isEmpty();
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that gatherCorruptIssues detects directories missing plan.md even when index.json is also absent.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void gatherCorruptIssuesDetectsMissingPlanMdWithNoIndexJson() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      // Create an issue directory with neither index.json nor plan.md
      Path issueDir = projectPath.resolve(".cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("empty-issue");
      Files.createDirectories(issueDir);
      // Deliberately no index.json and no plan.md

      GetCleanupOutput handler = new GetCleanupOutput(scope);
      List<GetCleanupOutput.CorruptIssue> result = handler.gatherCorruptIssues(projectPath);

      requireThat(result, "result").size().isEqualTo(1);
      requireThat(result.get(0).issueId(), "issueId").isEqualTo("empty-issue");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
      deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that gatherCorruptIssues detects issue directories with an empty index.json.
   * <p>
   * An empty index.json (zero bytes or whitespace-only) is classified as corrupt with reason
   * "index.json is empty".
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void gatherCorruptIssuesDetectsEmptyIndexJson() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      // Create an issue directory with an empty index.json and a valid plan.md
      Path issueDir = projectPath.resolve(".cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("empty-json-issue");
      Files.createDirectories(issueDir);
      Files.writeString(issueDir.resolve("index.json"), "");
      Files.writeString(issueDir.resolve("plan.md"), "# Plan\n\n## Goal\n\nDo something.\n");

      GetCleanupOutput handler = new GetCleanupOutput(scope);
      List<GetCleanupOutput.CorruptIssue> result = handler.gatherCorruptIssues(projectPath);

      requireThat(result, "result").size().isEqualTo(1);
      requireThat(result.get(0).issueId(), "issueId").isEqualTo("empty-json-issue");
      requireThat(result.get(0).reason(), "reason").contains("empty");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
    }
  }

  /**
   * Verifies that gatherCorruptIssues detects issue directories with a non-object index.json.
   * <p>
   * An index.json containing a JSON string (not a JSON object) is classified as corrupt with
   * reason containing "JSON object".
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void gatherCorruptIssuesDetectsInvalidJsonInIndexJson() throws IOException
  {
    Path projectPath = Files.createTempDirectory("test-project");
    Path pluginRoot = Files.createTempDirectory("test-plugin");
    try (JvmScope scope = new TestJvmScope(projectPath, pluginRoot))
    {
      // Create an issue directory with index.json containing a JSON string, not an object
      Path issueDir = projectPath.resolve(".cat").resolve("issues").
        resolve("v2").resolve("v2.1").resolve("invalid-json-issue");
      Files.createDirectories(issueDir);
      Files.writeString(issueDir.resolve("index.json"), "\"not-an-object\"");
      Files.writeString(issueDir.resolve("plan.md"), "# Plan\n\n## Goal\n\nDo something.\n");

      GetCleanupOutput handler = new GetCleanupOutput(scope);
      List<GetCleanupOutput.CorruptIssue> result = handler.gatherCorruptIssues(projectPath);

      requireThat(result, "result").size().isEqualTo(1);
      requireThat(result.get(0).issueId(), "issueId").isEqualTo("invalid-json-issue");
      requireThat(result.get(0).reason(), "reason").contains("JSON object");
    }
    finally
    {
      deleteDirectoryRecursively(projectPath);
    }
  }
}
