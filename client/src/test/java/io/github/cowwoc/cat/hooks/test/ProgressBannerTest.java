/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.ProgressBanner;
import io.github.cowwoc.cat.hooks.skills.ProgressBanner.Phase;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for ProgressBanner functionality.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class ProgressBannerTest
{
  /**
   * Verifies that generateBanner produces non-empty output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerProducesOutput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      String result = banner.generateBanner("2.1-test-issue", Phase.PREPARING);
      requireThat(result, "result").isNotEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generateBanner includes issue ID in output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerIncludesIssueId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      String result = banner.generateBanner("2.1-test-issue", Phase.PREPARING);
      requireThat(result, "result").contains("2.1-test-issue");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generateBanner includes cat emoji.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerIncludesCatEmoji() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      String result = banner.generateBanner("2.1-test-issue", Phase.PREPARING);
      requireThat(result, "result").contains("🐱");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generateBanner includes phase names.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerIncludesPhaseNames() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      String result = banner.generateBanner("2.1-test-issue", Phase.PREPARING);
      requireThat(result, "result").contains("Preparing");
      requireThat(result, "result").contains("Implementing");
      requireThat(result, "result").contains("Confirming");
      requireThat(result, "result").contains("Reviewing");
      requireThat(result, "result").contains("Merging");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generateBanner produces box structure with corners.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerProducesBoxStructure() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      String result = banner.generateBanner("2.1-test-issue", Phase.PREPARING);
      requireThat(result, "result").contains("╭");
      requireThat(result, "result").contains("╮");
      requireThat(result, "result").contains("╰");
      requireThat(result, "result").contains("╯");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generateBanner for preparing phase shows active symbol on Preparing.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerPreparingPhaseShowsActiveSymbol() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      String result = banner.generateBanner("2.1-test-issue", Phase.PREPARING);
      requireThat(result, "result").contains("◉ Preparing");
      requireThat(result, "result").contains("○ Implementing");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generateBanner for implementing phase shows complete then active symbols.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerImplementingPhaseShowsCompleteAndActive() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      String result = banner.generateBanner("2.1-test-issue", Phase.IMPLEMENTING);
      requireThat(result, "result").contains("● Preparing");
      requireThat(result, "result").contains("◉ Implementing");
      requireThat(result, "result").contains("○ Confirming");
      requireThat(result, "result").contains("○ Reviewing");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generateBanner for reviewing phase shows three complete then active.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerReviewingPhaseShowsProgressPattern() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      String result = banner.generateBanner("2.1-test-issue", Phase.REVIEWING);
      requireThat(result, "result").contains("● Preparing");
      requireThat(result, "result").contains("● Implementing");
      requireThat(result, "result").contains("● Confirming");
      requireThat(result, "result").contains("◉ Reviewing");
      requireThat(result, "result").contains("○ Merging");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generateBanner for merging phase shows all complete except last active.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerMergingPhaseShowsFinalPattern() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      String result = banner.generateBanner("2.1-test-issue", Phase.MERGING);
      requireThat(result, "result").contains("● Preparing");
      requireThat(result, "result").contains("● Implementing");
      requireThat(result, "result").contains("● Confirming");
      requireThat(result, "result").contains("● Reviewing");
      requireThat(result, "result").contains("◉ Merging");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generateBanner handles empty issue ID.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerHandlesEmptyIssueId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      String result = banner.generateBanner("", Phase.PREPARING);
      requireThat(result, "result").isNotEmpty();
      requireThat(result, "result").contains("🐱");
      requireThat(result, "result").contains("Preparing");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generateBanner rejects null issue ID.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void generateBannerRejectsNullIssueId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      banner.generateBanner(null, Phase.PREPARING);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generateBanner rejects null phase.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void generateBannerRejectsNullPhase() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      banner.generateBanner("2.1-test-issue", null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generateAllPhases produces output with all five phases.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateAllPhasesIncludesAllPhases() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      String result = banner.generateAllPhases("2.1-test-issue");
      requireThat(result, "result").contains("**Preparing phase**");
      requireThat(result, "result").contains("**Implementing phase**");
      requireThat(result, "result").contains("**Confirming phase**");
      requireThat(result, "result").contains("**Reviewing phase**");
      requireThat(result, "result").contains("**Merging phase**");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generateAllPhases wraps banners in code blocks.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateAllPhasesUsesCodeBlocks() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      String result = banner.generateAllPhases("2.1-test-issue");
      requireThat(result, "result").contains("```");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generateAllPhases includes issue ID in all banners.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateAllPhasesIncludesIssueId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      String result = banner.generateAllPhases("2.1-my-issue");

      String[] lines = result.split("\n");
      int issueIdCount = 0;
      for (String line : lines)
      {
        if (line.contains("2.1-my-issue"))
          ++issueIdCount;
      }
      requireThat(issueIdCount, "issueIdCount").isGreaterThanOrEqualTo(5);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generateAllPhases rejects null issue ID.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void generateAllPhasesRejectsNullIssueId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      banner.generateAllPhases(null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generateGenericPreparingBanner produces output without issue ID.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateGenericPreparingBannerProducesOutput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      String result = banner.generateGenericPreparingBanner();
      requireThat(result, "result").isNotEmpty();
      requireThat(result, "result").contains("🐱");
      requireThat(result, "result").contains("```");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generateGenericPreparingBanner includes explanatory text.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateGenericPreparingBannerIncludesExplanation() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      String result = banner.generateGenericPreparingBanner();
      requireThat(result, "result").contains("Issue will be identified after preparation completes");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generateBanner produces three lines.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerProducesThreeLines() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      String result = banner.generateBanner("2.1-test-issue", Phase.PREPARING);
      String[] lines = result.split("\n");
      requireThat(lines.length, "lineCount").isEqualTo(3);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generateBanner first line starts with top-left corner.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerFirstLineStartsWithTopLeft() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      String result = banner.generateBanner("2.1-test-issue", Phase.PREPARING);
      String[] lines = result.split("\n");
      requireThat(lines[0], "firstLine").startsWith("╭");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generateBanner last line starts with bottom-left corner.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerLastLineStartsWithBottomLeft() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      String result = banner.generateBanner("2.1-test-issue", Phase.PREPARING);
      String[] lines = result.split("\n");
      requireThat(lines[2], "lastLine").startsWith("╰");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generateBanner middle line starts with vertical bar.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerMiddleLineStartsWithVertical() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      String result = banner.generateBanner("2.1-test-issue", Phase.PREPARING);
      String[] lines = result.split("\n");
      requireThat(lines[1], "middleLine").startsWith("│");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generateBanner handles long issue IDs by producing valid box structure.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerHandlesLongIssueId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      String longId = "2.1-" + "a".repeat(100);
      String result = banner.generateBanner(longId, Phase.PREPARING);

      String[] lines = result.split("\n");
      requireThat(lines.length, "lineCount").isEqualTo(3);
      requireThat(result, "result").contains("╭");
      requireThat(result, "result").contains("╮");
      requireThat(result, "result").contains("╰");
      requireThat(result, "result").contains("╯");
      requireThat(result, "result").contains(longId);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generateBanner handles Unicode characters in issue ID.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerHandlesUnicodeIssueId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      String unicodeId = "2.1-tëst-üñicödé";
      String result = banner.generateBanner(unicodeId, Phase.PREPARING);

      requireThat(result, "result").contains(unicodeId);
      requireThat(result, "result").contains("╭");
      requireThat(result, "result").contains("╮");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generateBanner produces consistent structure with proper alignment.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerProducesConsistentStructure() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      String result = banner.generateBanner("2.1-test-issue", Phase.PREPARING);

      String[] lines = result.split("\n");
      requireThat(lines.length, "lineCount").isEqualTo(3);

      requireThat(lines[0], "topLine").startsWith("╭─ 🐱 2.1-test-issue");
      requireThat(lines[0], "topLine").endsWith("╮");
      requireThat(lines[1], "middleLine").startsWith("│");
      requireThat(lines[1], "middleLine").endsWith("│");
      requireThat(lines[1], "middleLine").contains("◉ Preparing");
      requireThat(lines[1], "middleLine").contains("○ Implementing");
      requireThat(lines[1], "middleLine").contains("○ Confirming");
      requireThat(lines[1], "middleLine").contains("○ Reviewing");
      requireThat(lines[1], "middleLine").contains("○ Merging");
      requireThat(lines[2], "bottomLine").startsWith("╰");
      requireThat(lines[2], "bottomLine").endsWith("╯");

      int topLength = lines[0].length();
      int middleLength = lines[1].length();
      int bottomLength = lines[2].length();
      requireThat(topLength, "topLength").isEqualTo(bottomLength);
      requireThat(middleLength, "middleLength").isEqualTo(bottomLength);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that generateBanner for confirming phase shows correct symbol pattern.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerConfirmingPhaseShowsCorrectPattern() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      String result = banner.generateBanner("2.1-test-issue", Phase.CONFIRMING);
      requireThat(result, "result").contains("● Preparing");
      requireThat(result, "result").contains("● Implementing");
      requireThat(result, "result").contains("◉ Confirming");
      requireThat(result, "result").contains("○ Reviewing");
      requireThat(result, "result").contains("○ Merging");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that phaseSymbol returns correct symbols relative to CONFIRMING phase.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void confirmingPhaseSymbolsAreCorrect() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      ProgressBanner banner = new ProgressBanner(scope);
      String result = banner.generateBanner("2.1-test-issue", Phase.CONFIRMING);

      requireThat(result, "result").contains("● Preparing");
      requireThat(result, "result").contains("● Implementing");
      requireThat(result, "result").contains("◉ Confirming");
      requireThat(result, "result").contains("○ Reviewing");
      requireThat(result, "result").contains("○ Merging");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
