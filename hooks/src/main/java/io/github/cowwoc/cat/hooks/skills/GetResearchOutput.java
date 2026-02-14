/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Output generator for /cat:research skill.
 *
 * Provides utility functions for rendering stakeholder research display elements:
 * - Rating circles conversion
 * - Scorecard box building
 * - Comparison table building
 * - Total calculations
 *
 * Since research content is reasoning-based, this generator provides templates and
 * calculation functions rather than preprocessor output.
 */
public final class GetResearchOutput
{
  // Box width for displays
  private static final int BOX_WIDTH = 74;

  /**
   * Creates a GetResearchOutput instance.
   */
  public GetResearchOutput()
  {
  }

  /**
   * Return template instructions for research display.
   *
   * Since research involves AI reasoning to generate content, this generator
   * provides the template structure and utility function references.
   *
   * @return the formatted template instructions
   */
  public String getOutput()
  {
    // Example ratings for template demonstration
    Map<String, Integer> exampleRatings = Map.ofEntries(
      Map.entry("Speed", 4),
      Map.entry("Cost", 3),
      Map.entry("Quality", 3),
      Map.entry("Architect", 4),
      Map.entry("Security", 5),
      Map.entry("Tester", 3),
      Map.entry("Performance", 3),
      Map.entry("UX", 4),
      Map.entry("Sales", 4),
      Map.entry("Marketing", 3),
      Map.entry("Legal", 5));

    List<String> exampleScorecard = buildScorecard(exampleRatings);
    int[] totals = sumRatings(exampleRatings);

    return "## Rating System\n" +
           "\n" +
           "Use these script output circle patterns for ratings 1-5:\n" +
           "- 5 → ●●●●●  (Excellent)\n" +
           "- 4 → ●●●●○  (Good)\n" +
           "- 3 → ●●●○○  (Moderate)\n" +
           "- 2 → ●●○○○  (Poor)\n" +
           "- 1 → ●○○○○  (Very Poor)\n" +
           "\n" +
           "## 11 Rating Dimensions\n" +
           "\n" +
           "**Top-level metrics (1-5):**\n" +
           "1. Speed - Time to implement and deploy\n" +
           "2. Cost - Total cost of ownership\n" +
           "3. Quality - Code quality and maintainability\n" +
           "\n" +
           "**Stakeholder dimensions (1-5):**\n" +
           "4. Architect - Addresses architectural concerns\n" +
           "5. Security - Addresses security concerns\n" +
           "6. Testing - Addresses testing concerns\n" +
           "7. Performance - Addresses performance concerns\n" +
           "8. UX - Addresses user experience concerns\n" +
           "9. Sales - Addresses sales/value concerns\n" +
           "10. Marketing - Addresses marketing concerns\n" +
           "11. Legal - Addresses legal/compliance concerns\n" +
           "\n" +
           "## Scorecard Template\n" +
           "\n" +
           "Example scorecard (total: " + totals[0] + "/" + totals[1] + "):\n" +
           "```\n" +
           String.join("\n", exampleScorecard) + "\n" +
           "```\n" +
           "\n" +
           "## Calculation Gates (VERIFY BEFORE OUTPUT)\n" +
           "\n" +
           "- [ ] All ratings are integers 1-5\n" +
           "- [ ] Each option has all 11 dimensions rated\n" +
           "- [ ] Total = sum of all 11 ratings\n" +
           "- [ ] Max possible = 55 (11 x 5)\n" +
           "- [ ] Circle pattern matches rating number\n" +
           "\n" +
           "## Display Sequence\n" +
           "\n" +
           "1. CONCERNS BOX - Present stakeholder concerns FIRST\n" +
           "2. OPTIONS WITH SCORECARDS - Each option with its rating scorecard\n" +
           "3. COMPARISON TABLE - Side-by-side with all options\n" +
           "4. WIZARD - Use AskUserQuestion for selection\n" +
           "\n" +
           "## Provider Research\n" +
           "\n" +
           "When an option refers to a category (e.g., \"Payment Orchestration Platform\"):\n" +
           "- Research top 3 specific providers\n" +
           "- List with brief rationale for each";
  }

  /**
   * Convert a 1-5 rating to filled/empty circle display.
   *
   * @param rating the rating value (1-5)
   * @return a string of circles representing the rating
   */
  public static String ratingToCircles(int rating)
  {
    return DisplayUtils.ratingToCircles(rating);
  }

  /**
   * Calculate total score from a ratings map.
   *
   * @param ratings map of dimension to rating value
   * @return array of [total_score, max_possible]
   * @throws NullPointerException if ratings is null
   */
  public static int[] sumRatings(Map<String, Integer> ratings)
  {
    requireThat(ratings, "ratings").isNotNull();

    int total = 0;
    for (int value : ratings.values())
      total += value;
    int maxPossible = ratings.size() * 5;
    return new int[]{total, maxPossible};
  }

  /**
   * Build a pair of ratings on one line.
   *
   * @param label1 first dimension name
   * @param rating1 first rating (1-5)
   * @param label2 second dimension name
   * @param rating2 second rating (1-5)
   * @return formatted string
   */
  public static String buildScorecardRowPair(String label1, int rating1, String label2, int rating2)
  {
    String circles1 = ratingToCircles(rating1);
    String circles2 = ratingToCircles(rating2);
    String content = String.format("%-12s %s  %-12s %s", label1, circles1, label2, circles2);
    int innerWidth = 69;
    int padding = innerWidth - 2 - content.length();
    return DisplayUtils.BOX_VERTICAL + " " + content + " ".repeat(padding) + " " + DisplayUtils.BOX_VERTICAL;
  }

  /**
   * Build a triple of ratings on one line.
   *
   * @param label1 first dimension name
   * @param rating1 first rating (1-5)
   * @param label2 second dimension name
   * @param rating2 second rating (1-5)
   * @param label3 third dimension name
   * @param rating3 third rating (1-5)
   * @return formatted string
   */
  public static String buildScorecardRowTriple(String label1, int rating1,
                                               String label2, int rating2,
                                               String label3, int rating3)
  {
    String circles1 = ratingToCircles(rating1);
    String circles2 = ratingToCircles(rating2);
    String circles3 = ratingToCircles(rating3);
    String content = String.format("%-12s %s  %-12s %s  %-11s %s",
                                   label1, circles1, label2, circles2, label3, circles3);
    int innerWidth = 69;
    int padding = innerWidth - 2 - content.length();
    return DisplayUtils.BOX_VERTICAL + " " + content + " ".repeat(padding) + " " + DisplayUtils.BOX_VERTICAL;
  }

  /**
   * Build a complete rating scorecard box.
   *
   * @param ratings map with keys: Speed, Cost, Quality, Architect, Security, Tester,
   *                Performance, UX, Sales, Marketing, Legal
   * @return list of strings forming the scorecard box
   * @throws NullPointerException if ratings is null
   */
  public static List<String> buildScorecard(Map<String, Integer> ratings)
  {
    requireThat(ratings, "ratings").isNotNull();

    int innerWidth = 69;
    List<String> lines = new ArrayList<>();

    lines.add(DisplayUtils.BOX_TOP_LEFT + DisplayUtils.BOX_HORIZONTAL.repeat(innerWidth) +
              DisplayUtils.BOX_TOP_RIGHT);
    lines.add(DisplayUtils.BOX_VERTICAL + " RATING SCORECARD" + " ".repeat(innerWidth - 17) +
              DisplayUtils.BOX_VERTICAL);
    lines.add(DisplayUtils.BOX_LEFT_INTERSECTION + DisplayUtils.BOX_HORIZONTAL.repeat(innerWidth) +
              DisplayUtils.BOX_RIGHT_INTERSECTION);

    lines.add(buildScorecardRowTriple(
      "Speed", ratings.getOrDefault("Speed", 3),
      "Cost", ratings.getOrDefault("Cost", 3),
      "Quality", ratings.getOrDefault("Quality", 3)));

    lines.add(DisplayUtils.BOX_LEFT_INTERSECTION + DisplayUtils.BOX_HORIZONTAL.repeat(innerWidth) +
              DisplayUtils.BOX_RIGHT_INTERSECTION);

    lines.add(buildScorecardRowTriple(
      "Architect", ratings.getOrDefault("Architect", 3),
      "Security", ratings.getOrDefault("Security", 3),
      "Tester", ratings.getOrDefault("Tester", 3)));

    lines.add(buildScorecardRowTriple(
      "Performance", ratings.getOrDefault("Performance", 3),
      "UX", ratings.getOrDefault("UX", 3),
      "Sales", ratings.getOrDefault("Sales", 3)));

    lines.add(buildScorecardRowPair(
      "Marketing", ratings.getOrDefault("Marketing", 3),
      "Legal", ratings.getOrDefault("Legal", 3)));

    lines.add(DisplayUtils.BOX_BOTTOM_LEFT + DisplayUtils.BOX_HORIZONTAL.repeat(innerWidth) +
              DisplayUtils.BOX_BOTTOM_RIGHT);

    return lines;
  }

  /**
   * Build a single row of the comparison table.
   *
   * @param dimension row label
   * @param ratings list of ratings for each option
   * @return formatted table row string
   * @throws NullPointerException if dimension or ratings is null
   */
  public static String buildComparisonRow(String dimension, List<Integer> ratings)
  {
    requireThat(dimension, "dimension").isNotNull();
    requireThat(ratings, "ratings").isNotNull();

    List<String> cols = new ArrayList<>();
    for (int r : ratings)
    {
      if (r > 0)
        cols.add(ratingToCircles(r));
      else
        cols.add("     ");
    }
    // Pad to 3 columns
    while (cols.size() < 3)
      cols.add("     ");

    return String.format(DisplayUtils.BOX_VERTICAL + " %-14s" + DisplayUtils.BOX_VERTICAL +
                         " %-14s" + DisplayUtils.BOX_VERTICAL + " %-14s" + DisplayUtils.BOX_VERTICAL +
                         " %-14s" + DisplayUtils.BOX_VERTICAL,
                         dimension, cols.get(0), cols.get(1), cols.get(2));
  }

  /**
   * Build the stakeholder concerns display box.
   *
   * @param concerns map of stakeholder name to list of concern strings
   * @return list of strings forming the concerns box
   * @throws NullPointerException if concerns is null
   */
  public static List<String> buildConcernsBox(Map<String, List<String>> concerns)
  {
    requireThat(concerns, "concerns").isNotNull();

    List<String> lines = new ArrayList<>();
    lines.add(DisplayUtils.BOX_TOP_LEFT + DisplayUtils.BOX_HORIZONTAL.repeat(BOX_WIDTH) +
              DisplayUtils.BOX_TOP_RIGHT);
    lines.add(DisplayUtils.BOX_VERTICAL + " " + DisplayUtils.EMOJI_MAGNIFIER +
              " Stakeholder Concerns" + " ".repeat(BOX_WIDTH - 25) + DisplayUtils.BOX_VERTICAL);
    lines.add(DisplayUtils.BOX_LEFT_INTERSECTION + DisplayUtils.BOX_HORIZONTAL.repeat(BOX_WIDTH) +
              DisplayUtils.BOX_RIGHT_INTERSECTION);
    lines.add(DisplayUtils.BOX_VERTICAL + " ".repeat(BOX_WIDTH) + DisplayUtils.BOX_VERTICAL);

    List<String> stakeholderOrder = List.of(
      "ARCHITECT", "SECURITY", "QUALITY", "TESTER", "PERFORMANCE",
      "UX", "SALES", "MARKETING", "LEGAL");

    for (String stakeholder : stakeholderOrder)
    {
      List<String> concernList = concerns.getOrDefault(stakeholder, List.of());
      String headerText = stakeholder + " concerns:";
      lines.add(DisplayUtils.BOX_VERTICAL + " " + headerText +
                " ".repeat(BOX_WIDTH - headerText.length() - 1) + DisplayUtils.BOX_VERTICAL);

      int count = 0;
      for (String concern : concernList)
      {
        if (count >= 3)  // Max 3 concerns per stakeholder
          break;
        // Truncate long concerns
        String truncated = concern;
        if (truncated.length() > BOX_WIDTH - 7)
          truncated = truncated.substring(0, BOX_WIDTH - 10) + "...";
        String bulletText = "  " + DisplayUtils.BULLET + " " + truncated;
        lines.add(DisplayUtils.BOX_VERTICAL + " " + bulletText +
                  " ".repeat(BOX_WIDTH - bulletText.length() - 1) + DisplayUtils.BOX_VERTICAL);
        ++count;
      }
      lines.add(DisplayUtils.BOX_VERTICAL + " ".repeat(BOX_WIDTH) + DisplayUtils.BOX_VERTICAL);
    }

    lines.add(DisplayUtils.BOX_BOTTOM_LEFT + DisplayUtils.BOX_HORIZONTAL.repeat(BOX_WIDTH) +
              DisplayUtils.BOX_BOTTOM_RIGHT);
    return lines;
  }

  /**
   * Build the header for the recommended approaches box.
   *
   * @return list of header lines
   */
  public static List<String> buildOptionsBoxHeader()
  {
    return List.of(
      DisplayUtils.BOX_TOP_LEFT + DisplayUtils.BOX_HORIZONTAL.repeat(BOX_WIDTH) +
        DisplayUtils.BOX_TOP_RIGHT,
      DisplayUtils.BOX_VERTICAL + " " + DisplayUtils.EMOJI_CLIPBOARD + " Recommended Approaches" +
        " ".repeat(BOX_WIDTH - 27) + DisplayUtils.BOX_VERTICAL,
      DisplayUtils.BOX_LEFT_INTERSECTION + DisplayUtils.BOX_HORIZONTAL.repeat(BOX_WIDTH) +
        DisplayUtils.BOX_RIGHT_INTERSECTION,
      DisplayUtils.BOX_VERTICAL + " ".repeat(BOX_WIDTH) + DisplayUtils.BOX_VERTICAL);
  }

  /**
   * Build the footer for the recommended approaches box.
   *
   * @return the footer line
   */
  public static String buildOptionsBoxFooter()
  {
    return DisplayUtils.BOX_BOTTOM_LEFT + DisplayUtils.BOX_HORIZONTAL.repeat(BOX_WIDTH) +
           DisplayUtils.BOX_BOTTOM_RIGHT;
  }

  /**
   * Build a divider between options.
   *
   * @return the divider line
   */
  public static String buildOptionSectionDivider()
  {
    return DisplayUtils.BOX_LEFT_INTERSECTION + DisplayUtils.BOX_HORIZONTAL.repeat(BOX_WIDTH) +
           DisplayUtils.BOX_RIGHT_INTERSECTION;
  }
}
