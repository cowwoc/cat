package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.GetCheckpointOutput;
import org.testng.annotations.Test;

import java.io.IOException;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetCheckpointOutput functionality.
 * <p>
 * Tests verify that checkpoint boxes are rendered correctly with
 * proper headers, borders, separators, and content sections.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class GetCheckpointOutputTest
{
  /**
   * Verifies that getCheckpointIssueComplete returns output containing the issue name and checkpoint header.
   */
  @Test
  public void getCheckpointIssueCompleteContainsIssueNameAndHeader() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetCheckpointOutput output = new GetCheckpointOutput(scope);
      String result = output.getCheckpointIssueComplete("2.1-test", "50000", "25", "v2.1");
      requireThat(result, "result").contains("2.1-test").contains("CHECKPOINT");
    }
  }

  /**
   * Verifies that getCheckpointIssueComplete output contains header.
   */
  @Test
  public void getCheckpointIssueCompleteContainsHeader() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetCheckpointOutput output = new GetCheckpointOutput(scope);
      String result = output.getCheckpointIssueComplete("2.1-test", "50000", "25", "v2.1");
      requireThat(result, "result").contains("CHECKPOINT").contains("Issue Complete");
    }
  }

  /**
   * Verifies that getCheckpointIssueComplete output contains issue name.
   */
  @Test
  public void getCheckpointIssueCompleteContainsIssueName() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetCheckpointOutput output = new GetCheckpointOutput(scope);
      String result = output.getCheckpointIssueComplete("2.1-add-feature", "50000", "25", "v2.1");
      requireThat(result, "result").contains("2.1-add-feature");
    }
  }

  /**
   * Verifies that getCheckpointIssueComplete output contains token metrics.
   */
  @Test
  public void getCheckpointIssueCompleteContainsTokens() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetCheckpointOutput output = new GetCheckpointOutput(scope);
      String result = output.getCheckpointIssueComplete("2.1-test", "50000", "25", "v2.1");
      requireThat(result, "result").contains("50000").contains("25%");
    }
  }

  /**
   * Verifies that getCheckpointIssueComplete output contains branch.
   */
  @Test
  public void getCheckpointIssueCompleteContainsBranch() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetCheckpointOutput output = new GetCheckpointOutput(scope);
      String result = output.getCheckpointIssueComplete("2.1-test", "50000", "25", "v2.1");
      requireThat(result, "result").contains("v2.1");
    }
  }

  /**
   * Verifies that getCheckpointIssueComplete has box structure.
   */
  @Test
  public void getCheckpointIssueCompleteHasBoxStructure() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetCheckpointOutput output = new GetCheckpointOutput(scope);
      String result = output.getCheckpointIssueComplete("2.1-test", "50000", "25", "v2.1");
      String[] lines = result.split("\n");
      requireThat(lines[0], "firstLine").contains("✅");
      requireThat(lines[lines.length - 1], "lastLine").startsWith("╰");
    }
  }

  /**
   * Verifies that getCheckpointFeedbackApplied returns output containing the issue name and checkpoint header.
   */
  @Test
  public void getCheckpointFeedbackAppliedContainsIssueNameAndHeader() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetCheckpointOutput output = new GetCheckpointOutput(scope);
      String result = output.getCheckpointFeedbackApplied("2.1-test", "1", "30", "130", "v2.1");
      requireThat(result, "result").contains("2.1-test").contains("CHECKPOINT");
    }
  }

  /**
   * Verifies that getCheckpointFeedbackApplied output contains header.
   */
  @Test
  public void getCheckpointFeedbackAppliedContainsHeader() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetCheckpointOutput output = new GetCheckpointOutput(scope);
      String result = output.getCheckpointFeedbackApplied("2.1-test", "1", "30", "130", "v2.1");
      requireThat(result, "result").contains("CHECKPOINT").contains("Feedback Applied");
    }
  }

  /**
   * Verifies that getCheckpointFeedbackApplied output contains iteration.
   */
  @Test
  public void getCheckpointFeedbackAppliedContainsIteration() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetCheckpointOutput output = new GetCheckpointOutput(scope);
      String result = output.getCheckpointFeedbackApplied("2.1-test", "2", "30", "130", "v2.1");
      requireThat(result, "result").contains("iteration").contains("2");
    }
  }

  /**
   * Verifies that getCheckpointFeedbackApplied output contains token metrics.
   */
  @Test
  public void getCheckpointFeedbackAppliedContainsTokenMetrics() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetCheckpointOutput output = new GetCheckpointOutput(scope);
      String result = output.getCheckpointFeedbackApplied("2.1-test", "1", "30", "130", "v2.1");
      requireThat(result, "result").contains("30K").contains("130K");
    }
  }

  /**
   * Verifies that getCheckpointFeedbackApplied has box structure.
   */
  @Test
  public void getCheckpointFeedbackAppliedHasBoxStructure() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetCheckpointOutput output = new GetCheckpointOutput(scope);
      String result = output.getCheckpointFeedbackApplied("2.1-test", "1", "30", "130", "v2.1");
      String[] lines = result.split("\n");
      requireThat(lines[0], "firstLine").contains("✅");
      requireThat(lines[lines.length - 1], "lastLine").startsWith("╰");
    }
  }

  /**
   * Verifies that getCheckpointIssueComplete throws for null issueName.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void getCheckpointIssueCompleteThrowsOnNullIssueName() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetCheckpointOutput output = new GetCheckpointOutput(scope);
      output.getCheckpointIssueComplete(null, "50000", "25", "v2.1");
    }
  }

  /**
   * Verifies that getCheckpointIssueComplete throws for blank issueName.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void getCheckpointIssueCompleteThrowsOnBlankIssueName() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetCheckpointOutput output = new GetCheckpointOutput(scope);
      output.getCheckpointIssueComplete("", "50000", "25", "v2.1");
    }
  }

  /**
   * Verifies that getCheckpointIssueComplete throws for null tokens.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void getCheckpointIssueCompleteThrowsOnNullTokens() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetCheckpointOutput output = new GetCheckpointOutput(scope);
      output.getCheckpointIssueComplete("2.1-test", null, "25", "v2.1");
    }
  }

  /**
   * Verifies that getCheckpointIssueComplete throws for blank tokens.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void getCheckpointIssueCompleteThrowsOnBlankTokens() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetCheckpointOutput output = new GetCheckpointOutput(scope);
      output.getCheckpointIssueComplete("2.1-test", "", "25", "v2.1");
    }
  }

  /**
   * Verifies that getCheckpointFeedbackApplied throws for null iteration.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void getCheckpointFeedbackAppliedThrowsOnNullIteration() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetCheckpointOutput output = new GetCheckpointOutput(scope);
      output.getCheckpointFeedbackApplied("2.1-test", null, "30", "130", "v2.1");
    }
  }

  /**
   * Verifies that getCheckpointFeedbackApplied throws for blank iteration.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void getCheckpointFeedbackAppliedThrowsOnBlankIteration() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GetCheckpointOutput output = new GetCheckpointOutput(scope);
      output.getCheckpointFeedbackApplied("2.1-test", "", "30", "130", "v2.1");
    }
  }

  /**
   * Verifies that constructor throws NullPointerException for null scope.
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void constructorThrowsOnNullScope()
  {
    new GetCheckpointOutput(null);
  }
}
