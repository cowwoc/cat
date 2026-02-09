package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.DefaultJvmScope;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.GetStakeholderOutput;
import io.github.cowwoc.cat.hooks.skills.GetStakeholderOutput.ReviewerStatus;
import io.github.cowwoc.cat.hooks.skills.GetStakeholderOutput.SkippedStakeholder;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetStakeholderOutput functionality.
 * <p>
 * Tests verify that stakeholder output generation for selection, review, and concern boxes
 * produces correctly formatted displays with proper structure and content.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class GetStakeholderOutputTest
{
  /**
   * Verifies that selection box contains the header.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void selectionBoxContainsHeader() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStakeholderOutput handler = new GetStakeholderOutput(scope);
      String result = handler.getSelectionBox(
        2, 5,
        List.of("design", "testing"),
        List.of(new SkippedStakeholder("security", "not relevant")));

      requireThat(result, "result").contains("STAKEHOLDER SELECTION");
    }
  }

  /**
   * Verifies that selection box shows stakeholder counts.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void selectionBoxShowsCounts() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStakeholderOutput handler = new GetStakeholderOutput(scope);
      String result = handler.getSelectionBox(
        3, 7,
        List.of("design"),
        List.of());

      requireThat(result, "result").contains("3 of 7 stakeholders selected");
    }
  }

  /**
   * Verifies that selection box includes running stakeholders.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void selectionBoxIncludesRunningList() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStakeholderOutput handler = new GetStakeholderOutput(scope);
      String result = handler.getSelectionBox(
        2, 4,
        List.of("design", "testing"),
        List.of());

      requireThat(result, "result").contains("Running:").contains("design").contains("testing");
    }
  }

  /**
   * Verifies that selection box includes skipped stakeholders with reasons.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void selectionBoxIncludesSkippedWithReasons() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStakeholderOutput handler = new GetStakeholderOutput(scope);
      String result = handler.getSelectionBox(
        1, 3,
        List.of("design"),
        List.of(
          new SkippedStakeholder("security", "not relevant"),
          new SkippedStakeholder("performance", "no perf changes")));

      requireThat(result, "result").contains("Skipped:").
        contains("security").contains("not relevant").
        contains("performance").contains("no perf changes");
    }
  }

  /**
   * Verifies that selection box has rounded box structure.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void selectionBoxHasBoxStructure() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStakeholderOutput handler = new GetStakeholderOutput(scope);
      String result = handler.getSelectionBox(
        1, 2,
        List.of("design"),
        List.of());

      requireThat(result, "result").contains("╭").contains("╰").contains("│");
    }
  }

  /**
   * Verifies that selection box handles empty running list.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void selectionBoxHandlesEmptyRunningList() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStakeholderOutput handler = new GetStakeholderOutput(scope);
      String result = handler.getSelectionBox(
        0, 3,
        List.of(),
        List.of(new SkippedStakeholder("all", "none needed")));

      requireThat(result, "result").contains("Running:").contains("STAKEHOLDER SELECTION");
    }
  }

  /**
   * Verifies that review box contains the header.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void reviewBoxContainsHeader() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStakeholderOutput handler = new GetStakeholderOutput(scope);
      String result = handler.getReviewBox(
        "v2.1-my-issue",
        List.of(new ReviewerStatus("design", "APPROVED")),
        "APPROVED",
        "All reviewers approved");

      requireThat(result, "result").contains("STAKEHOLDER REVIEW");
    }
  }

  /**
   * Verifies that review box shows the issue name.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void reviewBoxShowsIssueName() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStakeholderOutput handler = new GetStakeholderOutput(scope);
      String result = handler.getReviewBox(
        "v2.1-important-task",
        List.of(new ReviewerStatus("design", "APPROVED")),
        "APPROVED",
        "Looks good");

      requireThat(result, "result").contains("Issue: v2.1-important-task");
    }
  }

  /**
   * Verifies that review box includes reviewer statuses.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void reviewBoxIncludesReviewerStatuses() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStakeholderOutput handler = new GetStakeholderOutput(scope);
      String result = handler.getReviewBox(
        "v2.1-task",
        List.of(
          new ReviewerStatus("design", "APPROVED"),
          new ReviewerStatus("testing", "CONCERNS")),
        "CONCERNS",
        "Testing has concerns");

      requireThat(result, "result").contains("design").contains("APPROVED").
        contains("testing").contains("CONCERNS");
    }
  }

  /**
   * Verifies that review box shows the result and summary.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void reviewBoxShowsResultAndSummary() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStakeholderOutput handler = new GetStakeholderOutput(scope);
      String result = handler.getReviewBox(
        "v2.1-task",
        List.of(new ReviewerStatus("design", "APPROVED")),
        "APPROVED",
        "All clear");

      requireThat(result, "result").contains("Result: APPROVED").contains("All clear");
    }
  }

  /**
   * Verifies that review box uses tree-style prefixes for reviewers.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void reviewBoxUsesTreePrefixes() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStakeholderOutput handler = new GetStakeholderOutput(scope);
      String result = handler.getReviewBox(
        "v2.1-task",
        List.of(
          new ReviewerStatus("design", "APPROVED"),
          new ReviewerStatus("testing", "APPROVED")),
        "APPROVED",
        "All approved");

      requireThat(result, "result").contains("├──").contains("└──");
    }
  }

  /**
   * Verifies that review box has separators between sections.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void reviewBoxHasSeparators() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStakeholderOutput handler = new GetStakeholderOutput(scope);
      String result = handler.getReviewBox(
        "v2.1-task",
        List.of(new ReviewerStatus("design", "APPROVED")),
        "APPROVED",
        "Done");

      requireThat(result, "result").contains("├").contains("┤");
    }
  }

  /**
   * Verifies that review box has rounded box structure.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void reviewBoxHasBoxStructure() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStakeholderOutput handler = new GetStakeholderOutput(scope);
      String result = handler.getReviewBox(
        "v2.1-task",
        List.of(new ReviewerStatus("design", "APPROVED")),
        "APPROVED",
        "Done");

      requireThat(result, "result").contains("╭").contains("╰").contains("│");
    }
  }

  /**
   * Verifies that critical concern box contains severity label.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void criticalConcernBoxContainsSeverity() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStakeholderOutput handler = new GetStakeholderOutput(scope);
      String result = handler.getCriticalConcernBox(
        "design", "Missing error handling", "src/Main.java:42");

      requireThat(result, "result").contains("CRITICAL");
    }
  }

  /**
   * Verifies that critical concern box includes stakeholder and description.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void criticalConcernBoxIncludesContent() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStakeholderOutput handler = new GetStakeholderOutput(scope);
      String result = handler.getCriticalConcernBox(
        "security", "SQL injection risk", "src/Database.java:15");

      requireThat(result, "result").contains("[security]").contains("SQL injection risk").
        contains("src/Database.java:15");
    }
  }

  /**
   * Verifies that critical concern box has box structure.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void criticalConcernBoxHasBoxStructure() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStakeholderOutput handler = new GetStakeholderOutput(scope);
      String result = handler.getCriticalConcernBox(
        "design", "Issue found", "file.java:1");

      requireThat(result, "result").contains("╭").contains("╰").contains("│");
    }
  }

  /**
   * Verifies that high concern box contains severity label.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void highConcernBoxContainsSeverity() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStakeholderOutput handler = new GetStakeholderOutput(scope);
      String result = handler.getHighConcernBox(
        "testing", "Missing unit tests", "src/Utils.java:30");

      requireThat(result, "result").contains("HIGH");
    }
  }

  /**
   * Verifies that high concern box includes stakeholder and description.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void highConcernBoxIncludesContent() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStakeholderOutput handler = new GetStakeholderOutput(scope);
      String result = handler.getHighConcernBox(
        "architect", "Circular dependency", "src/Module.java:5");

      requireThat(result, "result").contains("[architect]").contains("Circular dependency").
        contains("src/Module.java:5");
    }
  }

  /**
   * Verifies that concern box shows file location with tree connector.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void concernBoxShowsFileLocationWithConnector() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStakeholderOutput handler = new GetStakeholderOutput(scope);
      String result = handler.getCriticalConcernBox(
        "design", "Missing validation", "src/Config.java:10");

      requireThat(result, "result").contains("└─ src/Config.java:10");
    }
  }

  /**
   * Verifies that content lines in selection box have consistent width.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void selectionBoxContentLinesHaveConsistentWidth() throws IOException
  {
    try (JvmScope scope = new DefaultJvmScope())
    {
      GetStakeholderOutput handler = new GetStakeholderOutput(scope);
      String result = handler.getSelectionBox(
        2, 5,
        List.of("design", "testing"),
        List.of(new SkippedStakeholder("security", "not relevant")));

      String[] lines = result.split("\n");
      // Check that content lines (starting with │) have consistent width
      int firstContentWidth = -1;
      for (String line : lines)
      {
        if (line.startsWith("│"))
        {
          int width = line.length();
          if (firstContentWidth == -1)
            firstContentWidth = width;
          requireThat(width, "width").isEqualTo(firstContentWidth);
        }
      }
    }
  }
}
