package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.GetWorkOutput;
import io.github.cowwoc.cat.hooks.skills.GetWorkOutput.Approach;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetWorkOutput functionality.
 * <p>
 * Tests verify that work output generation produces correctly formatted
 * displays for various workflow states and checkpoints.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class GetWorkOutputTest
{
  /**
   * Verifies that getNoExecutableTasks returns non-null output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getNoExecutableTasksReturnsOutput() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getNoExecutableTasks();
      requireThat(result, "result").length().isGreaterThan(0);
    }
  }

  /**
   * Verifies that getNoExecutableTasks contains helpful message.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getNoExecutableTasksContainsMessage() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getNoExecutableTasks();
      requireThat(result, "result").contains("No executable");
    }
  }

  /**
   * Verifies that getIssueNotFound returns formatted output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueNotFoundReturnsFormattedOutput() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getIssueNotFound("my-task", "Did you mean: other-task?");

      requireThat(result, "result").contains("my-task");
    }
  }

  /**
   * Verifies that getIssueNotFound includes suggestion.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueNotFoundIncludesSuggestion() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getIssueNotFound("my-task", "Did you mean: other-task?");

      requireThat(result, "result").contains("Did you mean").contains("other-task");
    }
  }

  /**
   * Verifies that getForkInTheRoad returns formatted output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getForkInTheRoadReturnsFormattedOutput() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      List<Approach> approaches = List.of(
        new Approach("Approach A", "Fast implementation", "low", 5, 80),
      new Approach("Approach B", "High quality", "medium", 10, 95));

      String result = handler.getForkInTheRoad("my-task", approaches);

      requireThat(result, "result").contains("my-task");
    }
  }

  /**
   * Verifies that getForkInTheRoad includes approach names.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getForkInTheRoadIncludesApproachNames() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      List<Approach> approaches = List.of(
        new Approach("Approach A", "Description", "low", 3, 90),
      new Approach("Approach B", "Alternative", "medium", 5, 85));

      String result = handler.getForkInTheRoad("my-task", approaches);

      requireThat(result, "result").contains("Approach A");
    }
  }

  /**
   * Verifies that getCheckpointIssueComplete returns formatted output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getCheckpointIssueCompleteReturnsFormattedOutput() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getCheckpointIssueComplete(
        "my-task",
        45,
        15_000,
        75,
        "2.0-my-task");

      requireThat(result, "result").contains("my-task");
    }
  }

  /**
   * Verifies that getCheckpointIssueComplete includes time metrics.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getCheckpointIssueCompleteIncludesTimeMetrics() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getCheckpointIssueComplete(
        "my-task",
        45,
        15_000,
        75,
        "2.0-my-task");

      requireThat(result, "result").contains("45");
    }
  }

  /**
   * Verifies that getCheckpointIssueComplete includes token metrics.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getCheckpointIssueCompleteIncludesTokenMetrics() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getCheckpointIssueComplete(
        "my-task",
        45,
        15_000,
        75,
        "2.0-my-task");

      requireThat(result, "result").contains("15");
    }
  }

  /**
   * Verifies that getCheckpointFeedbackApplied returns formatted output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getCheckpointFeedbackAppliedReturnsFormattedOutput() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getCheckpointFeedbackApplied(
        "my-task",
        2,
        25,
        50,
        "2.0-my-task");

      requireThat(result, "result").contains("my-task");
    }
  }

  /**
   * Verifies that getCheckpointFeedbackApplied includes iteration number.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getCheckpointFeedbackAppliedIncludesIteration() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getCheckpointFeedbackApplied(
        "my-task",
        2,
        25,
        50,
        "2.0-my-task");

      requireThat(result, "result").contains("2");
    }
  }

  /**
   * Verifies that getIssueCompleteWithNext returns formatted output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueCompleteWithNextReturnsFormattedOutput() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getIssueCompleteWithNext(
        "current-task",
        "next-task",
        "Implement feature X");

      requireThat(result, "result").contains("current-task");
    }
  }

  /**
   * Verifies that getIssueCompleteWithNext includes next task name.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueCompleteWithNextIncludesNextTask() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getIssueCompleteWithNext(
        "current-task",
        "next-task",
        "Implement feature X");

      requireThat(result, "result").contains("next-task");
    }
  }

  /**
   * Verifies that getIssueCompleteWithNext includes next goal.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueCompleteWithNextIncludesGoal() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getIssueCompleteWithNext(
        "current-task",
        "next-task",
        "Implement feature X");

      requireThat(result, "result").contains("Implement feature X");
    }
  }

  /**
   * Verifies that getIssueCompleteWithNext contains merged message.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueCompleteWithNextContainsMergedMessage() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getIssueCompleteWithNext(
        "current-task",
        "next-task",
        "Implement feature X");

      requireThat(result, "result").contains("merged to main");
    }
  }

  /**
   * Verifies that getIssueCompleteWithNext contains Issue Complete header.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueCompleteWithNextContainsHeader() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getIssueCompleteWithNext(
        "current-task",
        "next-task",
        "Implement feature X");

      requireThat(result, "result").contains("Issue Complete");
    }
  }

  /**
   * Verifies that getIssueCompleteWithNext contains continuing message.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueCompleteWithNextContainsContinuingMessage() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getIssueCompleteWithNext(
        "current-task",
        "next-task",
        "Implement feature X");

      requireThat(result, "result").contains("Continuing to next issue");
    }
  }

  /**
   * Verifies that getIssueCompleteWithNext contains stop and abort instructions.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueCompleteWithNextContainsStopAbortInstructions() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getIssueCompleteWithNext(
        "current-task",
        "next-task",
        "Implement feature X");

      requireThat(result, "result").contains("stop").contains("abort");
    }
  }

  /**
   * Verifies that getIssueCompleteWithNext has box structure.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueCompleteWithNextContainsBoxStructure() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getIssueCompleteWithNext(
        "current-task",
        "next-task",
        "Implement feature X");

      requireThat(result, "result").contains("╭").contains("╰").contains("│");
    }
  }

  /**
   * Verifies that getIssueAlreadyComplete returns formatted output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueAlreadyCompleteReturnsFormattedOutput() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getIssueAlreadyComplete(
        "completed-task",
        "abc123",
        "next-task",
        "Next goal");

      requireThat(result, "result").contains("completed-task");
    }
  }

  /**
   * Verifies that getIssueAlreadyComplete includes commit hash.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueAlreadyCompleteIncludesCommitHash() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getIssueAlreadyComplete(
        "completed-task",
        "abc123",
        "next-task",
        "Next goal");

      requireThat(result, "result").contains("abc123");
    }
  }

  /**
   * Verifies that getScopeComplete returns formatted output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getScopeCompleteReturnsFormattedOutput() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getScopeComplete(
        "Version 2.0",
        "All version 2.0 features complete");

      requireThat(result, "result").contains("Version 2.0");
    }
  }

  /**
   * Verifies that getScopeComplete includes completion message.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getScopeCompleteIncludesMessage() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getScopeComplete(
        "Version 2.0",
        "All version 2.0 features complete");

      requireThat(result, "result").contains("All version 2.0 features complete");
    }
  }

  /**
   * Verifies that getScopeComplete contains Scope Complete header.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getScopeCompleteContainsHeader() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getScopeComplete(
        "Version 2.0",
        "All version 2.0 features complete");

      requireThat(result, "result").contains("Scope Complete");
    }
  }

  /**
   * Verifies that getScopeComplete contains all tasks complete message.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getScopeCompleteContainsTasksCompleteMessage() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getScopeComplete(
        "Version 2.0",
        "All version 2.0 features complete");

      requireThat(result, "result").contains("all tasks complete");
    }
  }

  /**
   * Verifies that getScopeComplete has box structure.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getScopeCompleteContainsBoxStructure() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getScopeComplete(
        "Version 2.0",
        "All version 2.0 features complete");

      requireThat(result, "result").contains("╭").contains("╰").contains("│");
    }
  }

  /**
   * Verifies that getIssueCompleteLowTrust returns formatted output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueCompleteLowTrustReturnsFormattedOutput() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getIssueCompleteLowTrust(
        "my-task",
        "next-task",
        "Next goal");

      requireThat(result, "result").contains("my-task");
    }
  }

  /**
   * Verifies that getIssueCompleteLowTrust contains /cat:work command reference.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueCompleteLowTrustContainsCatWorkCommand() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getIssueCompleteLowTrust(
        "my-task",
        "next-task",
        "Next goal");

      requireThat(result, "result").contains("/cat:work");
    }
  }

  /**
   * Verifies that getIssueCompleteLowTrust contains Next Up label.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueCompleteLowTrustContainsNextUp() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getIssueCompleteLowTrust(
        "my-task",
        "next-task",
        "Next goal");

      requireThat(result, "result").contains("Next Up:");
    }
  }

  /**
   * Verifies that getVersionBoundaryGate returns formatted output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getVersionBoundaryGateReturnsFormattedOutput() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getVersionBoundaryGate(
        "2.0",
        15,
        "2.1",
        "Version 2.1 planning");

      requireThat(result, "result").contains("2.0");
    }
  }

  /**
   * Verifies that getVersionBoundaryGate includes issue count.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getVersionBoundaryGateIncludesIssueCount() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getVersionBoundaryGate(
        "2.0",
        15,
        "2.1",
        "Version 2.1 planning");

      requireThat(result, "result").contains("15");
    }
  }

  // --- Structural assertions ---

  /**
   * Verifies that getCheckpointIssueComplete output contains box-drawing characters.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getCheckpointIssueCompleteContainsBoxStructure() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getCheckpointIssueComplete(
        "my-task",
        45,
        15_000,
        75,
        "2.0-my-task");

      requireThat(result, "result").contains("╭").contains("╰").contains("│");
    }
  }

  /**
   * Verifies that getForkInTheRoad output contains box-drawing characters.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getForkInTheRoadContainsBoxStructure() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      List<Approach> approaches = List.of(
        new Approach("Approach A", "Fast", "low", 3, 80),
        new Approach("Approach B", "Quality", "medium", 8, 95));

      String result = handler.getForkInTheRoad("my-task", approaches);

      requireThat(result, "result").contains("╭").contains("╰").contains("│");
    }
  }

  /**
   * Verifies that getIssueNotFound output contains box-drawing characters.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getIssueNotFoundContainsBoxStructure() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetWorkOutput handler = new GetWorkOutput(scope);
      String result = handler.getIssueNotFound("my-task", "suggestion");

      requireThat(result, "result").contains("╭").contains("╰").contains("│");
    }
  }
}
