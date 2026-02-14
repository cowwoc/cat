/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.bash;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.DisplayUtils;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Compute box lines via hook interception.
 * <p>
 * M192: Agent calculated box widths correctly but re-typed output from memory,
 * causing alignment errors. This handler computes box lines with correct padding
 * and returns results via additionalContext.
 * <p>
 * USAGE: Agent invokes Bash with marker comment:
 * Bash("#BOX_COMPUTE\ncontent1\ncontent2\ncontent3")
 */
public final class ComputeBoxLines implements BashHandler
{
  private static final String BOX_COMPUTE_MARKER = "#BOX_COMPUTE";
  private final JvmScope scope;

  /**
   * Creates a new handler for computing box lines.
   *
   * @param scope the JVM scope providing access to DisplayUtils
   * @throws NullPointerException if {@code scope} is null
   */
  public ComputeBoxLines(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  @Override
  public Result check(String command, JsonNode toolInput, JsonNode toolResult, String sessionId)
  {
    // Check for the BOX_COMPUTE marker
    String[] lines = command.split("\n");
    if (lines.length == 0 || !lines[0].startsWith(BOX_COMPUTE_MARKER))
      return Result.allow();

    // Extract content items (all lines after the marker)
    List<String> contentItems = extractContentItems(lines);
    if (contentItems.isEmpty())
      return Result.block("BOX_COMPUTE: No content items provided");

    // Build the box natively in Java
    return executeBoxComputation(contentItems);
  }

  /**
   * Extracts content items from command lines (all lines after the marker).
   *
   * @param lines the command split into lines
   * @return list of content items
   */
  private List<String> extractContentItems(String[] lines)
  {
    List<String> contentItems = new ArrayList<>();
    for (int i = 1; i < lines.length; ++i)
      contentItems.add(lines[i]);
    return contentItems;
  }

  /**
   * Computes box lines natively in Java using DisplayUtils.
   *
   * @param contentItems the content items to format
   * @return a block result with the computed box output
   */
  private Result executeBoxComputation(List<String> contentItems)
  {
    DisplayUtils displayUtils = scope.getDisplayUtils();

    // Calculate max content width
    int maxContentWidth = 0;
    for (String content : contentItems)
    {
      int width = displayUtils.displayWidth(content);
      if (width > maxContentWidth)
        maxContentWidth = width;
    }

    // Build box lines
    StringJoiner boxLines = new StringJoiner("\n");
    boxLines.add(displayUtils.buildTopBorder(maxContentWidth));
    for (String content : contentItems)
      boxLines.add(displayUtils.buildLine(content, maxContentWidth));
    boxLines.add(displayUtils.buildBottomBorder(maxContentWidth));

    String boxOutput = boxLines.toString();

    return Result.block(
      "BOX_COMPUTE result (use this output exactly):\n\n" + boxOutput,
      "Script output box (copy exactly):\n```\n" + boxOutput + "\n```");
  }
}
