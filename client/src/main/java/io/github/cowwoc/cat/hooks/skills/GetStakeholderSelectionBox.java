/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Generates the stakeholder selection box for /cat:stakeholder-review skill.
 * <p>
 * Displays the context-aware stakeholder selection, showing which stakeholders are running
 * and which were skipped with reasons.
 */
public final class GetStakeholderSelectionBox
{
  /**
   * The JVM scope for accessing shared services.
   */
  private final JvmScope scope;

  /**
   * Represents a skipped stakeholder with reason.
   *
   * @param stakeholder the stakeholder name
   * @param reason      the reason for skipping
   */
  public record SkippedStakeholder(String stakeholder, String reason)
  {
    /**
     * Creates a skipped stakeholder record.
     *
     * @param stakeholder the stakeholder name
     * @param reason      the reason for skipping
     * @throws NullPointerException     if any parameter is null
     * @throws IllegalArgumentException if any parameter is blank
     */
    public SkippedStakeholder
    {
      requireThat(stakeholder, "stakeholder").isNotBlank();
      requireThat(reason, "reason").isNotBlank();
    }
  }

  /**
   * Creates a GetStakeholderSelectionBox instance.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if {@code scope} is null
   */
  public GetStakeholderSelectionBox(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Build the stakeholder selection box.
   *
   * @param selectedCount the number of stakeholders selected
   * @param totalCount    the total number of stakeholders available
   * @param runningList   the list of stakeholder names currently running
   * @param skipped       the list of skipped stakeholders with reasons
   * @return the formatted selection box
   * @throws NullPointerException     if {@code runningList} or {@code skipped} are null
   * @throws IllegalArgumentException if counts are negative or selectedCount exceeds totalCount
   */
  public String getSelectionBox(int selectedCount, int totalCount, List<String> runningList,
                                List<SkippedStakeholder> skipped)
  {
    requireThat(selectedCount, "selectedCount").isNotNegative();
    requireThat(totalCount, "totalCount").isNotNegative();
    requireThat(selectedCount, "selectedCount").isLessThanOrEqualTo(totalCount);
    requireThat(runningList, "runningList").isNotNull();
    requireThat(skipped, "skipped").isNotNull();

    List<String> content = new ArrayList<>();
    content.add("");
    content.add("Stakeholder Review: " + selectedCount + " of " + totalCount + " stakeholders selected");
    content.add("");
    content.add("Running: " + String.join(", ", runningList));
    content.add("");
    content.add("Skipped:");
    for (SkippedStakeholder s : skipped)
      content.add("  - " + s.stakeholder() + ": " + s.reason());
    content.add("");

    return scope.getDisplayUtils().buildHeaderBox("STAKEHOLDER SELECTION", content);
  }

  /**
   * Main method for command-line execution.
   * <p>
   * Usage:
   * <pre>
   * get-stakeholder-selection-box &lt;selected-count&gt; &lt;total-count&gt; &lt;running&gt; &lt;skipped&gt;
   * </pre>
   * Where:
   * <ul>
   *   <li>{@code selected-count} - number of stakeholders selected</li>
   *   <li>{@code total-count} - total number of stakeholders available</li>
   *   <li>{@code running} - comma-separated stakeholder names (e.g., "architect,design,testing")</li>
   *   <li>{@code skipped} - comma-separated colon pairs (e.g., "ux:No UI changes,sales:Internal tooling")</li>
   * </ul>
   *
   * @param args command-line arguments
   * @throws IOException if the operation fails
   */
  public static void main(String[] args) throws IOException
  {
    if (args.length != 4)
    {
      System.err.println("Expected 4 arguments but got " + args.length);
      printUsage();
      System.exit(1);
    }

    int selectedCount;
    try
    {
      selectedCount = Integer.parseInt(args[0]);
    }
    catch (NumberFormatException _)
    {
      System.err.println("selected-count must be an integer but got: " + args[0]);
      printUsage();
      System.exit(1);
      return;
    }
    int totalCount;
    try
    {
      totalCount = Integer.parseInt(args[1]);
    }
    catch (NumberFormatException _)
    {
      System.err.println("total-count must be an integer but got: " + args[1]);
      printUsage();
      System.exit(1);
      return;
    }
    String running = args[2];
    String skipped = args[3];

    try (JvmScope scope = new MainJvmScope())
    {
      GetStakeholderSelectionBox box = new GetStakeholderSelectionBox(scope);
      List<String> runningList = parseCommaSeparated(running);
      List<SkippedStakeholder> skippedList = parseSkipped(skipped);
      System.out.print(box.getSelectionBox(selectedCount, totalCount, runningList, skippedList));
    }
  }

  /**
   * Parses a comma-separated string into a list of non-empty strings.
   *
   * @param value the comma-separated string
   * @return the list of non-empty strings
   * @throws NullPointerException if {@code value} is null
   */
  private static List<String> parseCommaSeparated(String value)
  {
    requireThat(value, "value").isNotNull();
    if (value.isEmpty())
      return List.of();
    List<String> result = new ArrayList<>();
    for (String part : value.split(","))
    {
      String stripped = part.strip();
      if (!stripped.isEmpty())
        result.add(stripped);
    }
    return result;
  }

  /**
   * Parses a comma-separated list of "stakeholder:reason" pairs into skipped stakeholders.
   *
   * @param value the comma-separated string of "stakeholder:reason" pairs
   * @return the list of skipped stakeholders
   * @throws NullPointerException if {@code value} is null
   */
  private static List<SkippedStakeholder> parseSkipped(String value)
  {
    requireThat(value, "value").isNotNull();
    if (value.isEmpty())
      return List.of();
    List<SkippedStakeholder> result = new ArrayList<>();
    for (String pair : value.split(","))
    {
      int colonIndex = pair.indexOf(':');
      if (colonIndex < 0)
        continue;
      String name = pair.substring(0, colonIndex).strip();
      String reason = pair.substring(colonIndex + 1).strip();
      if (!name.isEmpty() && !reason.isEmpty())
        result.add(new SkippedStakeholder(name, reason));
    }
    return result;
  }

  /**
   * Prints usage information to stderr.
   */
  private static void printUsage()
  {
    System.err.println("""
      Usage:
        get-stakeholder-selection-box <selected-count> <total-count> <running> <skipped>

      Arguments:
        selected-count  Number of stakeholders selected
        total-count     Total number of stakeholders available
        running         Comma-separated stakeholder names (e.g., "architect,design,testing")
        skipped         Comma-separated colon pairs (e.g., "ux:No UI changes,sales:Internal tooling")
      """);
  }
}
