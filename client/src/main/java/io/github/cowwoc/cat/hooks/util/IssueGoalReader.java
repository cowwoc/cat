/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the goal from a PLAN.md file.
 */
public final class IssueGoalReader
{
  private IssueGoalReader()
  {
  }

  /**
   * Reads the goal from PLAN.md.
   * <p>
   * Extracts the first paragraph of text under the {@code ## Goal} heading.
   *
   * @param planPath the path to PLAN.md
   * @return the goal text, or {@code "No goal found"} if absent
   * @throws NullPointerException if {@code planPath} is null
   */
  public static String readGoalFromPlan(Path planPath)
  {
    if (!Files.isRegularFile(planPath))
      return "No goal found";

    List<String> lines;
    try
    {
      lines = Files.readAllLines(planPath);
    }
    catch (IOException _)
    {
      return "No goal found";
    }

    // Find ## Goal heading
    int goalStart = -1;
    for (int i = 0; i < lines.size(); ++i)
    {
      if (lines.get(i).strip().startsWith("## Goal"))
      {
        goalStart = i + 1;
        break;
      }
    }

    if (goalStart < 0)
      return "No goal found";

    // Extract text until next ## heading or end of file
    List<String> goalLines = new ArrayList<>();
    for (int i = goalStart; i < lines.size(); ++i)
    {
      String line = lines.get(i);
      if (line.strip().startsWith("##"))
        break;
      goalLines.add(line.stripTrailing());
    }

    String goal = String.join("\n", goalLines).strip();

    // Return first paragraph
    int blankIndex = goal.indexOf("\n\n");
    if (blankIndex >= 0)
      return goal.substring(0, blankIndex).strip();
    if (goal.isEmpty())
      return "No goal found";
    return goal;
  }
}
