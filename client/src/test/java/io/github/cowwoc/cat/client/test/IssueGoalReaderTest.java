/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.util.IssueGoalReader;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Dedicated unit tests for {@link IssueGoalReader#readGoalFromPlan(Path)}.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained with no shared state.
 */
public class IssueGoalReaderTest
{
  /**
   * Verifies that a missing file returns "No goal found".
   */
  @Test
  public void missingFileReturnsNoGoalFound() throws IOException
  {
    Path nonExistent = Path.of(System.getProperty("java.io.tmpdir"),
      "issue-goal-reader-test-missing-" + System.nanoTime() + ".md");
    String goal = IssueGoalReader.readGoalFromPlan(nonExistent);
    requireThat(goal, "goal").isEqualTo("No goal found");
  }

  /**
   * Verifies that a file without a {@code ## Goal} heading returns "No goal found".
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void missingGoalHeadingReturnsNoGoalFound() throws IOException
  {
    Path planFile = Files.createTempFile("IssueGoalReader-no-heading-", ".md");
    try
    {
      Files.writeString(planFile, "# Plan\n\n## Steps\n\n- Step 1\n");
      String goal = IssueGoalReader.readGoalFromPlan(planFile);
      requireThat(goal, "goal").isEqualTo("No goal found");
    }
    finally
    {
      Files.deleteIfExists(planFile);
    }
  }

  /**
   * Verifies that an empty goal section returns "No goal found".
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void emptyGoalSectionReturnsNoGoalFound() throws IOException
  {
    Path planFile = Files.createTempFile("IssueGoalReader-empty-goal-", ".md");
    try
    {
      Files.writeString(planFile, "## Goal\n\n## Steps\n\n- Step 1\n");
      String goal = IssueGoalReader.readGoalFromPlan(planFile);
      requireThat(goal, "goal").isEqualTo("No goal found");
    }
    finally
    {
      Files.deleteIfExists(planFile);
    }
  }

  /**
   * Verifies that a single-paragraph goal returns the goal text.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void singleParagraphGoalReturnsGoalText() throws IOException
  {
    Path planFile = Files.createTempFile("IssueGoalReader-single-para-", ".md");
    try
    {
      Files.writeString(planFile, "## Goal\n\nImplement the authentication module.\n");
      String goal = IssueGoalReader.readGoalFromPlan(planFile);
      requireThat(goal, "goal").isEqualTo("Implement the authentication module.");
    }
    finally
    {
      Files.deleteIfExists(planFile);
    }
  }

  /**
   * Verifies that a multi-paragraph goal returns only the first paragraph.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void multiParagraphGoalReturnsOnlyFirstParagraph() throws IOException
  {
    Path planFile = Files.createTempFile("IssueGoalReader-multi-para-", ".md");
    try
    {
      Files.writeString(planFile,
        "## Goal\n\nFirst paragraph text.\n\nSecond paragraph text.\n\nThird paragraph text.\n");
      String goal = IssueGoalReader.readGoalFromPlan(planFile);
      requireThat(goal, "goal").isEqualTo("First paragraph text.");
    }
    finally
    {
      Files.deleteIfExists(planFile);
    }
  }

  /**
   * Verifies that leading and trailing whitespace in the goal section is trimmed.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void goalWithWhitespaceIsTrimmed() throws IOException
  {
    Path planFile = Files.createTempFile("IssueGoalReader-whitespace-", ".md");
    try
    {
      Files.writeString(planFile, "## Goal\n\n   Goal text with surrounding spaces.   \n");
      String goal = IssueGoalReader.readGoalFromPlan(planFile);
      requireThat(goal, "goal").isEqualTo("Goal text with surrounding spaces.");
    }
    finally
    {
      Files.deleteIfExists(planFile);
    }
  }

  /**
   * Verifies that goal extraction stops at the next {@code ##} heading.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void goalFollowedByNextHeadingStopsAtHeading() throws IOException
  {
    Path planFile = Files.createTempFile("IssueGoalReader-next-heading-", ".md");
    try
    {
      Files.writeString(planFile,
        "## Goal\n\nExtract the goal text.\n\n## Implementation\n\nDo things here.\n");
      String goal = IssueGoalReader.readGoalFromPlan(planFile);
      requireThat(goal, "goal").isEqualTo("Extract the goal text.");
    }
    finally
    {
      Files.deleteIfExists(planFile);
    }
  }

  /**
   * Verifies that an IOException during file read returns "No goal found".
   * <p>
   * Simulates the IOException path by attempting to read a directory as a file, which throws IOException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void ioExceptionReturnsNoGoalFound() throws IOException
  {
    Path planDir = Files.createTempDirectory("IssueGoalReader-ioexception-");
    try
    {
      // Attempting to read a directory as a file will throw IOException
      String goal = IssueGoalReader.readGoalFromPlan(planDir);
      requireThat(goal, "goal").isEqualTo("No goal found");
    }
    finally
    {
      Files.deleteIfExists(planDir);
    }
  }

  /**
   * Verifies that a Goal heading as the last line with no content returns "No goal found".
   * <p>
   * This is an edge case where the Goal heading exists but there is no content after it.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void goalHeadingAsLastLineReturnsNoGoalFound() throws IOException
  {
    Path planFile = Files.createTempFile("IssueGoalReader-goal-last-line-", ".md");
    try
    {
      Files.writeString(planFile, "## Steps\n\n- Step 1\n\n## Goal\n");
      String goal = IssueGoalReader.readGoalFromPlan(planFile);
      requireThat(goal, "goal").isEqualTo("No goal found");
    }
    finally
    {
      Files.deleteIfExists(planFile);
    }
  }

  /**
   * Verifies that a null path throws {@link NullPointerException}.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void nullInputThrowsNullPointerException() throws IOException
  {
    IssueGoalReader.readGoalFromPlan(null);
  }
}
