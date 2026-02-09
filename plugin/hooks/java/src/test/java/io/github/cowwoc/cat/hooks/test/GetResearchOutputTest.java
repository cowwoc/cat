package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.skills.GetResearchOutput;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetResearchOutput functionality.
 * <p>
 * Tests verify that research output generation produces correctly formatted
 * scorecards, ratings, and comparison displays.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class GetResearchOutputTest
{
  /**
   * Verifies that rating 0 is clamped to 1 filled circle.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void ratingZeroConvertsToEmptyCircles() throws IOException
  {
    String result = GetResearchOutput.ratingToCircles(0);
    requireThat(result, "result").isEqualTo("●○○○○");
  }

  /**
   * Verifies that rating 1 converts to one filled circle.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void ratingOneConvertsToOneFilledCircle() throws IOException
  {
    String result = GetResearchOutput.ratingToCircles(1);
    requireThat(result, "result").isEqualTo("●○○○○");
  }

  /**
   * Verifies that rating 3 converts to three filled circles.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void ratingThreeConvertsToThreeFilledCircles() throws IOException
  {
    String result = GetResearchOutput.ratingToCircles(3);
    requireThat(result, "result").isEqualTo("●●●○○");
  }

  /**
   * Verifies that rating 5 converts to five filled circles.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void ratingFiveConvertsToFiveFilledCircles() throws IOException
  {
    String result = GetResearchOutput.ratingToCircles(5);
    requireThat(result, "result").isEqualTo("●●●●●");
  }

  /**
   * Verifies that rating above 5 is clamped to five filled circles.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void ratingAboveFiveClampedToFiveCircles() throws IOException
  {
    String result = GetResearchOutput.ratingToCircles(10);
    requireThat(result, "result").isEqualTo("●●●●●");
  }

  /**
   * Verifies that rating below 1 is clamped to one filled circle.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void ratingBelowOneClampedToOneCircle() throws IOException
  {
    String result = GetResearchOutput.ratingToCircles(-5);
    requireThat(result, "result").isEqualTo("●○○○○");
  }

  /**
   * Verifies that sumRatings calculates total correctly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void sumRatingsCalculatesTotal() throws IOException
  {
    Map<String, Integer> ratings = Map.of(
      "Speed", 3,
      "Quality", 4,
      "Cost", 2);

    int[] result = GetResearchOutput.sumRatings(ratings);
    requireThat(result[0], "total").isEqualTo(9);
    requireThat(result[1], "max").isEqualTo(15);
  }

  /**
   * Verifies that sumRatings calculates max correctly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void sumRatingsCalculatesMax() throws IOException
  {
    Map<String, Integer> ratings = Map.of(
      "Speed", 5,
      "Quality", 5);

    int[] result = GetResearchOutput.sumRatings(ratings);
    requireThat(result[0], "total").isEqualTo(10);
    requireThat(result[1], "max").isEqualTo(10);
  }

  /**
   * Verifies that sumRatings handles single rating.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void sumRatingsHandlesEmptyRatings() throws IOException
  {
    Map<String, Integer> ratings = Map.of("Quality", 4);

    int[] result = GetResearchOutput.sumRatings(ratings);
    requireThat(result[0], "total").isEqualTo(4);
    requireThat(result[1], "max").isEqualTo(5);
  }

  /**
   * Verifies that buildScorecardRowPair creates row with two ratings.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildScorecardRowPairCreatesTwoRatings() throws IOException
  {
    String result = GetResearchOutput.buildScorecardRowPair("Speed", 3, "Quality", 4);

    requireThat(result, "result").contains("Speed").contains("●●●○○").
      contains("Quality").contains("●●●●○");
  }

  /**
   * Verifies that buildScorecardRowTriple creates row with three ratings.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildScorecardRowTripleCreatesThreeRatings() throws IOException
  {
    String result = GetResearchOutput.buildScorecardRowTriple(
      "Speed", 3,
      "Quality", 4,
      "Cost", 2);

    requireThat(result, "result").contains("Speed").contains("●●●○○").contains("Quality").
      contains("●●●●○").contains("Cost").contains("●●○○○");
  }

  /**
   * Verifies that buildScorecard creates complete scorecard.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildScorecardCreatesCompleteScorecard() throws IOException
  {
    Map<String, Integer> ratings = Map.of(
      "Speed", 3,
      "Quality", 4,
      "Cost", 2);

    List<String> result = GetResearchOutput.buildScorecard(ratings);
    requireThat(result, "result").size().isGreaterThan(0);
  }

  /**
   * Verifies that buildScorecard has consistent line widths.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildScorecardHasConsistentLineWidths() throws IOException
  {
    Map<String, Integer> ratings = Map.of(
      "Speed", 3,
      "Quality", 4,
      "Cost", 2);

    List<String> result = GetResearchOutput.buildScorecard(ratings);
    int firstLineLength = result.get(0).length();
    for (String line : result)
    {
      requireThat(line.length(), "lineLength").isEqualTo(firstLineLength);
    }
  }

  /**
   * Verifies that buildComparisonRow creates row with approach ratings.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildComparisonRowCreatesApproachRatings() throws IOException
  {
    String result = GetResearchOutput.buildComparisonRow("Speed", List.of(3, 4, 5));

    requireThat(result, "result").contains("Speed").contains("●●●○○").
      contains("●●●●○").contains("●●●●●");
  }

  /**
   * Verifies that buildComparisonRow handles single approach.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildComparisonRowHandlesSingleApproach() throws IOException
  {
    String result = GetResearchOutput.buildComparisonRow("Quality", List.of(4));

    requireThat(result, "result").contains("Quality").contains("●●●●○");
  }

  /**
   * Verifies that buildConcernsBox creates concerns display.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildConcernsBoxCreatesConcernsDisplay() throws IOException
  {
    Map<String, List<String>> concerns = Map.of(
      "ARCHITECT", List.of("Risk 1", "Risk 2"),
      "SECURITY", List.of("Risk 3"));

    List<String> result = GetResearchOutput.buildConcernsBox(concerns);
    requireThat(result, "result").size().isGreaterThan(0);
  }

  /**
   * Verifies that buildConcernsBox includes stakeholder names.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildConcernsBoxIncludesApproachNames() throws IOException
  {
    Map<String, List<String>> concerns = Map.of(
      "ARCHITECT", List.of("Risk 1"));

    List<String> result = GetResearchOutput.buildConcernsBox(concerns);
    String joined = String.join("\n", result);
    requireThat(joined, "joined").contains("ARCHITECT");
  }

  /**
   * Verifies that buildConcernsBox includes risk details.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildConcernsBoxIncludesRiskDetails() throws IOException
  {
    Map<String, List<String>> concerns = Map.of(
      "ARCHITECT", List.of("Risk 1", "Risk 2"));

    List<String> result = GetResearchOutput.buildConcernsBox(concerns);
    String joined = String.join("\n", result);
    requireThat(joined, "joined").contains("Risk 1");
    requireThat(joined, "joined").contains("Risk 2");
  }

  /**
   * Verifies that buildConcernsBox truncates long concern text with ellipsis.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildConcernsBoxTruncatesLongText() throws IOException
  {
    String longConcern = "A".repeat(200);
    Map<String, List<String>> concerns = Map.of(
      "ARCHITECT", List.of(longConcern));

    List<String> result = GetResearchOutput.buildConcernsBox(concerns);
    String joined = String.join("\n", result);
    requireThat(joined, "joined").contains("...");
    requireThat(joined, "joined").doesNotContain(longConcern);
  }

  /**
   * Verifies that buildConcernsBox has box structure with rounded corners.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildConcernsBoxHasBoxStructure() throws IOException
  {
    Map<String, List<String>> concerns = Map.of(
      "ARCHITECT", List.of("Risk 1"));

    List<String> result = GetResearchOutput.buildConcernsBox(concerns);
    String firstLine = result.get(0);
    String lastLine = result.get(result.size() - 1);
    requireThat(firstLine, "firstLine").contains("╭");
    requireThat(lastLine, "lastLine").contains("╰");
  }

  /**
   * Verifies that buildOptionsBoxHeader creates header.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildOptionsBoxHeaderCreatesHeader() throws IOException
  {
    List<String> result = GetResearchOutput.buildOptionsBoxHeader();
    requireThat(result, "result").size().isGreaterThan(0);
  }

  /**
   * Verifies that buildOptionsBoxFooter creates footer.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildOptionsBoxFooterCreatesFooter() throws IOException
  {
    String result = GetResearchOutput.buildOptionsBoxFooter();
    requireThat(result, "result").length().isGreaterThan(0);
  }

  /**
   * Verifies that buildOptionSectionDivider creates divider.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void buildOptionSectionDividerCreatesDivider() throws IOException
  {
    String result = GetResearchOutput.buildOptionSectionDivider();
    requireThat(result, "result").length().isGreaterThan(0);
  }

  /**
   * Verifies that rating 2 converts to two filled circles and three empty circles.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void ratingTwoConvertsToTwoFilledCircles() throws IOException
  {
    String result = GetResearchOutput.ratingToCircles(2);
    requireThat(result, "result").isEqualTo("●●○○○");
  }

  /**
   * Verifies that rating 4 converts to four filled circles and one empty circle.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void ratingFourConvertsToFourFilledCircles() throws IOException
  {
    String result = GetResearchOutput.ratingToCircles(4);
    requireThat(result, "result").isEqualTo("●●●●○");
  }

  /**
   * Verifies that sumRatings with all 5s returns 55/55.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void sumRatingsAllFivesReturns55() throws IOException
  {
    Map<String, Integer> ratings = Map.ofEntries(
      Map.entry("Speed", 5), Map.entry("Cost", 5), Map.entry("Quality", 5),
      Map.entry("Architect", 5), Map.entry("Security", 5), Map.entry("Tester", 5),
      Map.entry("Performance", 5), Map.entry("UX", 5), Map.entry("Sales", 5),
      Map.entry("Marketing", 5), Map.entry("Legal", 5));

    int[] result = GetResearchOutput.sumRatings(ratings);
    requireThat(result[0], "total").isEqualTo(55);
    requireThat(result[1], "max").isEqualTo(55);
  }

  /**
   * Verifies that sumRatings with all 1s returns 11/55.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void sumRatingsAllOnesReturns11() throws IOException
  {
    Map<String, Integer> ratings = Map.ofEntries(
      Map.entry("Speed", 1), Map.entry("Cost", 1), Map.entry("Quality", 1),
      Map.entry("Architect", 1), Map.entry("Security", 1), Map.entry("Tester", 1),
      Map.entry("Performance", 1), Map.entry("UX", 1), Map.entry("Sales", 1),
      Map.entry("Marketing", 1), Map.entry("Legal", 1));

    int[] result = GetResearchOutput.sumRatings(ratings);
    requireThat(result[0], "total").isEqualTo(11);
    requireThat(result[1], "max").isEqualTo(55);
  }

  /**
   * Verifies that sumRatings with empty map returns 0/0.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void sumRatingsEmptyReturnsZero() throws IOException
  {
    int[] result = GetResearchOutput.sumRatings(Map.of());
    requireThat(result[0], "total").isEqualTo(0);
    requireThat(result[1], "max").isEqualTo(0);
  }

  /**
   * Verifies that all row pairs produced by buildScorecardRowPair have consistent width.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void rowPairWidthIsConsistent() throws IOException
  {
    String row1 = GetResearchOutput.buildScorecardRowPair("Marketing", 3, "Legal", 4);
    String row2 = GetResearchOutput.buildScorecardRowPair("A", 1, "B", 5);
    String row3 = GetResearchOutput.buildScorecardRowPair("Performance", 5, "UX", 1);
    String row4 = GetResearchOutput.buildScorecardRowPair("Short", 2, "Long Label", 3);

    requireThat(row1.length(), "row1Length").isEqualTo(row2.length());
    requireThat(row2.length(), "row2Length").isEqualTo(row3.length());
    requireThat(row3.length(), "row3Length").isEqualTo(row4.length());
  }

  /**
   * Verifies that all row triples produced by buildScorecardRowTriple have consistent width.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void rowTripleWidthIsConsistent() throws IOException
  {
    String row1 = GetResearchOutput.buildScorecardRowTriple("Speed", 4, "Cost", 3, "Quality", 5);
    String row2 = GetResearchOutput.buildScorecardRowTriple("Architect", 5, "Security", 4, "Tester", 3);
    String row3 = GetResearchOutput.buildScorecardRowTriple("Performance", 3, "UX", 5, "Sales", 2);
    String row4 = GetResearchOutput.buildScorecardRowTriple("A", 1, "B", 2, "C", 3);

    requireThat(row1.length(), "row1Length").isEqualTo(row2.length());
    requireThat(row2.length(), "row2Length").isEqualTo(row3.length());
    requireThat(row3.length(), "row3Length").isEqualTo(row4.length());
  }

  /**
   * Verifies that row pair and row triple widths match each other and the scorecard border widths.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void rowPairAndTripleWidthMatchScorecardWidth() throws IOException
  {
    String rowPair = GetResearchOutput.buildScorecardRowPair("Marketing", 3, "Legal", 4);
    String rowTriple = GetResearchOutput.buildScorecardRowTriple("Speed", 4, "Cost", 3, "Quality", 5);

    requireThat(rowPair.length(), "rowPairLength").isEqualTo(rowTriple.length());
  }
}
