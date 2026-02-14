/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.skills.ProgressBanner;
import io.github.cowwoc.cat.hooks.skills.ProgressBanner.Phase;
import org.testng.annotations.Test;

import java.io.IOException;

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
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
    String result = banner.generateBanner("2.1-test-issue", Phase.PREPARING);
    requireThat(result, "result").isNotEmpty();
  }

  /**
   * Verifies that generateBanner includes issue ID in output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerIncludesIssueId() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
    String result = banner.generateBanner("2.1-test-issue", Phase.PREPARING);
    requireThat(result, "result").contains("2.1-test-issue");
  }

  /**
   * Verifies that generateBanner includes cat emoji.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerIncludesCatEmoji() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
    String result = banner.generateBanner("2.1-test-issue", Phase.PREPARING);
    requireThat(result, "result").contains("🐱");
  }

  /**
   * Verifies that generateBanner includes phase names.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerIncludesPhaseNames() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
    String result = banner.generateBanner("2.1-test-issue", Phase.PREPARING);
    requireThat(result, "result").contains("Preparing");
    requireThat(result, "result").contains("Implementing");
    requireThat(result, "result").contains("Confirming");
    requireThat(result, "result").contains("Reviewing");
    requireThat(result, "result").contains("Merging");
  }

  /**
   * Verifies that generateBanner produces box structure with corners.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerProducesBoxStructure() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
    String result = banner.generateBanner("2.1-test-issue", Phase.PREPARING);
    requireThat(result, "result").contains("╭");
    requireThat(result, "result").contains("╮");
    requireThat(result, "result").contains("╰");
    requireThat(result, "result").contains("╯");
  }

  /**
   * Verifies that generateBanner for preparing phase shows active symbol on Preparing.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerPreparingPhaseShowsActiveSymbol() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
    String result = banner.generateBanner("2.1-test-issue", Phase.PREPARING);
    requireThat(result, "result").contains("◉ Preparing");
    requireThat(result, "result").contains("○ Implementing");
  }

  /**
   * Verifies that generateBanner for implementing phase shows complete then active symbols.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerImplementingPhaseShowsCompleteAndActive() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
    String result = banner.generateBanner("2.1-test-issue", Phase.IMPLEMENTING);
    requireThat(result, "result").contains("● Preparing");
    requireThat(result, "result").contains("◉ Implementing");
    requireThat(result, "result").contains("○ Confirming");
    requireThat(result, "result").contains("○ Reviewing");
  }

  /**
   * Verifies that generateBanner for reviewing phase shows three complete then active.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerReviewingPhaseShowsProgressPattern() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
    String result = banner.generateBanner("2.1-test-issue", Phase.REVIEWING);
    requireThat(result, "result").contains("● Preparing");
    requireThat(result, "result").contains("● Implementing");
    requireThat(result, "result").contains("● Confirming");
    requireThat(result, "result").contains("◉ Reviewing");
    requireThat(result, "result").contains("○ Merging");
  }

  /**
   * Verifies that generateBanner for merging phase shows all complete except last active.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerMergingPhaseShowsFinalPattern() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
    String result = banner.generateBanner("2.1-test-issue", Phase.MERGING);
    requireThat(result, "result").contains("● Preparing");
    requireThat(result, "result").contains("● Implementing");
    requireThat(result, "result").contains("● Confirming");
    requireThat(result, "result").contains("● Reviewing");
    requireThat(result, "result").contains("◉ Merging");
  }

  /**
   * Verifies that generateBanner handles empty issue ID.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerHandlesEmptyIssueId() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
    String result = banner.generateBanner("", Phase.PREPARING);
    requireThat(result, "result").isNotEmpty();
    requireThat(result, "result").contains("🐱");
    requireThat(result, "result").contains("Preparing");
  }

  /**
   * Verifies that generateBanner rejects null issue ID.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void generateBannerRejectsNullIssueId() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
    banner.generateBanner(null, Phase.PREPARING);
  }

  /**
   * Verifies that generateBanner rejects null phase.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void generateBannerRejectsNullPhase() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
    banner.generateBanner("2.1-test-issue", null);
  }

  /**
   * Verifies that generateAllPhases produces output with all five phases.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateAllPhasesIncludesAllPhases() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
    String result = banner.generateAllPhases("2.1-test-issue");
    requireThat(result, "result").contains("**Preparing phase**");
    requireThat(result, "result").contains("**Implementing phase**");
    requireThat(result, "result").contains("**Confirming phase**");
    requireThat(result, "result").contains("**Reviewing phase**");
    requireThat(result, "result").contains("**Merging phase**");
  }

  /**
   * Verifies that generateAllPhases wraps banners in code blocks.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateAllPhasesUsesCodeBlocks() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
    String result = banner.generateAllPhases("2.1-test-issue");
    requireThat(result, "result").contains("```");
  }

  /**
   * Verifies that generateAllPhases includes issue ID in all banners.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateAllPhasesIncludesIssueId() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
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

  /**
   * Verifies that generateAllPhases rejects null issue ID.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void generateAllPhasesRejectsNullIssueId() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
    banner.generateAllPhases(null);
  }

  /**
   * Verifies that generateGenericPreparingBanner produces output without issue ID.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateGenericPreparingBannerProducesOutput() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
    String result = banner.generateGenericPreparingBanner();
    requireThat(result, "result").isNotEmpty();
    requireThat(result, "result").contains("🐱");
    requireThat(result, "result").contains("```");
  }

  /**
   * Verifies that generateGenericPreparingBanner includes explanatory text.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateGenericPreparingBannerIncludesExplanation() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
    String result = banner.generateGenericPreparingBanner();
    requireThat(result, "result").contains("Issue will be identified after preparation completes");
  }

  /**
   * Verifies that generateBanner produces three lines.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerProducesThreeLines() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
    String result = banner.generateBanner("2.1-test-issue", Phase.PREPARING);
    String[] lines = result.split("\n");
    requireThat(lines.length, "lineCount").isEqualTo(3);
  }

  /**
   * Verifies that generateBanner first line starts with top-left corner.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerFirstLineStartsWithTopLeft() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
    String result = banner.generateBanner("2.1-test-issue", Phase.PREPARING);
    String[] lines = result.split("\n");
    requireThat(lines[0], "firstLine").startsWith("╭");
  }

  /**
   * Verifies that generateBanner last line starts with bottom-left corner.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerLastLineStartsWithBottomLeft() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
    String result = banner.generateBanner("2.1-test-issue", Phase.PREPARING);
    String[] lines = result.split("\n");
    requireThat(lines[2], "lastLine").startsWith("╰");
  }

  /**
   * Verifies that generateBanner middle line starts with vertical bar.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerMiddleLineStartsWithVertical() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
    String result = banner.generateBanner("2.1-test-issue", Phase.PREPARING);
    String[] lines = result.split("\n");
    requireThat(lines[1], "middleLine").startsWith("│");
  }

  /**
   * Verifies that generateBanner handles long issue IDs by producing valid box structure.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerHandlesLongIssueId() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
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

  /**
   * Verifies that generateBanner handles Unicode characters in issue ID.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerHandlesUnicodeIssueId() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
    String unicodeId = "2.1-tëst-üñicödé";
    String result = banner.generateBanner(unicodeId, Phase.PREPARING);

    requireThat(result, "result").contains(unicodeId);
    requireThat(result, "result").contains("╭");
    requireThat(result, "result").contains("╮");
  }

  /**
   * Verifies that generateBanner produces consistent structure with proper alignment.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerProducesConsistentStructure() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
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

  /**
   * Verifies that generateBanner for confirming phase shows correct symbol pattern.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void generateBannerConfirmingPhaseShowsCorrectPattern() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
    String result = banner.generateBanner("2.1-test-issue", Phase.CONFIRMING);
    requireThat(result, "result").contains("● Preparing");
    requireThat(result, "result").contains("● Implementing");
    requireThat(result, "result").contains("◉ Confirming");
    requireThat(result, "result").contains("○ Reviewing");
    requireThat(result, "result").contains("○ Merging");
  }

  /**
   * Verifies that phaseSymbol returns correct symbols relative to CONFIRMING phase.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void confirmingPhaseSymbolsAreCorrect() throws IOException
  {
    ProgressBanner banner = new ProgressBanner(tools.jackson.databind.json.JsonMapper.builder().build());
    String result = banner.generateBanner("2.1-test-issue", Phase.CONFIRMING);

    requireThat(result, "result").contains("● Preparing");
    requireThat(result, "result").contains("● Implementing");
    requireThat(result, "result").contains("◉ Confirming");
    requireThat(result, "result").contains("○ Reviewing");
    requireThat(result, "result").contains("○ Merging");
  }
}
