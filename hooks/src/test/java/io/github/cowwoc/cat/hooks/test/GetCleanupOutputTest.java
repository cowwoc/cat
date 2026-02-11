package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.MainJvmScope;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.GetCleanupOutput;
import io.github.cowwoc.cat.hooks.skills.GetCleanupOutput.Lock;
import io.github.cowwoc.cat.hooks.skills.GetCleanupOutput.RemovedCounts;
import io.github.cowwoc.cat.hooks.skills.GetCleanupOutput.StaleRemote;
import io.github.cowwoc.cat.hooks.skills.GetCleanupOutput.Worktree;
import io.github.cowwoc.cat.hooks.skills.GetCleanupOutput.WorktreeToRemove;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;

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
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getSurveyOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null);

      requireThat(result, "result").contains("🔍 Survey Results");
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
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getSurveyOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null);

      requireThat(result, "result").contains("📁 Worktrees");
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
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getSurveyOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null);

      requireThat(result, "result").contains("🔒 Issue Locks");
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
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getSurveyOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null);

      requireThat(result, "result").contains("🌿 CAT Branches");
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
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getSurveyOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null);

      requireThat(result, "result").contains("⏳ Stale Remotes");
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
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getSurveyOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        ".claude/context.md");

      requireThat(result, "result").contains("📝 Context: .claude/context.md");
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
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getSurveyOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null);

      requireThat(result, "result").contains("📝 Context: None");
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
    try (JvmScope scope = new MainJvmScope())
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
        null);

      requireThat(result, "result").contains("/path/to/worktree1").contains("branch1").
        contains("[detached]").contains("/path/to/worktree2").contains("branch2");
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
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      List<Lock> locks = List.of(
        new Lock("v2.0-my-task", "session123", 300));

      String result = handler.getSurveyOutput(
        List.of(),
        locks,
        List.of(),
        List.of(),
        null);

      requireThat(result, "result").contains("v2.0-my-task").contains("session1").
        contains("300s");
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
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      List<String> branches = List.of("2.0-task1", "2.0-task2");

      String result = handler.getSurveyOutput(
        List.of(),
        List.of(),
        branches,
        List.of(),
        null);

      requireThat(result, "result").contains("2.0-task1").contains("2.0-task2");
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
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      List<StaleRemote> remotes = List.of(
        new StaleRemote("2.0-old-task", "user123", "3 days ago", "old"));

      String result = handler.getSurveyOutput(
        List.of(),
        List.of(),
        List.of(),
        remotes,
        null);

      requireThat(result, "result").contains("2.0-old-task").contains("user123").
        contains("3 days ago");
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
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getSurveyOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null);

      requireThat(result, "result").contains("None found");
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
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getSurveyOutput(
        List.of(new Worktree("/path", "branch", "")),
        List.of(new Lock("task", "sess", 10)),
        List.of("branch1", "branch2"),
        List.of(new StaleRemote("old", "user", "1d", "stale")),
        null);

      requireThat(result, "result").contains("Found: 1 worktrees").contains("1 locks").
        contains("2 branches").contains("1 stale remotes");
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
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getPlanOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of());

      requireThat(result, "result").contains("Cleanup Plan");
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
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      List<WorktreeToRemove> worktrees = List.of(
        new WorktreeToRemove("/path/to/wt", "task-branch"));

      String result = handler.getPlanOutput(
        List.of(),
        worktrees,
        List.of(),
        List.of());

      requireThat(result, "result").contains("/path/to/wt").contains("task-branch");
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
    try (JvmScope scope = new MainJvmScope())
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
  }

  /**
   * Verifies that plan output includes stale remotes.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void planOutputIncludesStaleRemotes() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
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
  }

  /**
   * Verifies that plan output contains confirmation prompt.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void planOutputContainsConfirmationPrompt() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getPlanOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of());

      requireThat(result, "result").contains("Confirm cleanup?");
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
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getPlanOutput(
        List.of("lock1", "lock2"),
        List.of(new WorktreeToRemove("/path", "branch")),
        List.of("branch1"),
        List.of());

      requireThat(result, "result").contains("Total items to remove: 4");
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
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getPlanOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of());

      requireThat(result, "result").contains("(none)");
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
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getPlanOutput(
        List.of("my-lock-id"),
        List.of(),
        List.of(),
        List.of());

      requireThat(result, "result").contains("Locks to Remove").contains("my-lock-id");
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
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getVerifyOutput(
        List.of(),
        List.of(),
        List.of(),
        new RemovedCounts(0, 0, 0));

      requireThat(result, "result").contains("✅ Cleanup Complete");
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
    try (JvmScope scope = new MainJvmScope())
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
  }

  /**
   * Verifies that verify output shows zero counts.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void verifyOutputShowsZeroCounts() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
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
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getSurveyOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null);

      requireThat(result, "result").contains("╭").contains("╰").contains("│");
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
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getPlanOutput(
        List.of(),
        List.of(),
        List.of(),
        List.of());

      requireThat(result, "result").contains("╭").contains("╰").contains("│");
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
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getVerifyOutput(
        List.of(),
        List.of(),
        List.of(),
        new RemovedCounts(0, 0, 0));

      requireThat(result, "result").contains("╭").contains("╰").contains("│");
    }
  }

  // --- SCRIPT OUTPUT and INSTRUCTION marker tests ---

  /**
   * Verifies that survey output is a non-empty string (equivalent to Python's isinstance check).
   * <p>
   * The Python test checks for SCRIPT OUTPUT marker, but the Java production code does not
   * wrap output with SCRIPT OUTPUT markers -- those are added by the hook handler layer, not
   * the output generator. This test verifies the output is non-empty instead.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void surveyOutputIsNonEmpty() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getSurveyOutput(
        List.of(new Worktree("/path/a", "branch-a", ""),
          new Worktree("/path/b", "branch-b", "locked")),
        List.of(new Lock("task-a", "abc123def456", 3600),
          new Lock("task-b", "xyz789uvw012", 7200)),
        List.of("branch-a", "branch-b", "branch-c"),
        List.of(new StaleRemote("old-task", "user@example.com", "3 days ago", "stale")),
        ".claude/context.md");

      requireThat(result, "result").isNotEmpty();
    }
  }

  /**
   * Verifies that plan output is a non-empty string.
   * <p>
   * The Python test checks for SCRIPT OUTPUT and INSTRUCTION markers added by the hook
   * handler layer. The Java output generator does not add those markers, so this test
   * verifies the output is non-empty.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void planOutputIsNonEmpty() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getPlanOutput(
        List.of("lock-a", "lock-b"),
        List.of(new WorktreeToRemove("/workspace/.worktrees/task-a", "task-a")),
        List.of("branch-a", "branch-b"),
        List.of(new StaleRemote("old-branch", "user", "5 days ago", "5 days")));

      requireThat(result, "result").isNotEmpty();
    }
  }

  /**
   * Verifies that verify output is a non-empty string.
   * <p>
   * The Python test checks for SCRIPT OUTPUT and INSTRUCTION markers added by the hook
   * handler layer. The Java output generator does not add those markers, so this test
   * verifies the output is non-empty.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void verifyOutputIsNonEmpty() throws IOException
  {
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getVerifyOutput(
        List.of("/workspace/.worktrees/active-task"),
        List.of("active-task", "main"),
        List.of(),
        new RemovedCounts(2, 1, 3));

      requireThat(result, "result").isNotEmpty();
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
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getVerifyOutput(
        List.of("/workspace/.worktrees/active-task"),
        List.of("active-task", "main"),
        List.of(),
        new RemovedCounts(2, 1, 3));

      requireThat(result, "result").contains("Remaining Worktrees").contains("active-task");
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
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getVerifyOutput(
        List.of(),
        List.of("active-task", "main"),
        List.of(),
        new RemovedCounts(0, 0, 0));

      requireThat(result, "result").contains("Remaining CAT Branches").contains("main");
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
    try (JvmScope scope = new MainJvmScope())
    {
      GetCleanupOutput handler = new GetCleanupOutput(scope);
      String result = handler.getVerifyOutput(
        List.of(),
        List.of(),
        List.of(),
        new RemovedCounts(0, 0, 0));

      requireThat(result, "result").contains("Remaining Locks").contains("(none)");
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
    try (JvmScope scope = new MainJvmScope())
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
  }
}
