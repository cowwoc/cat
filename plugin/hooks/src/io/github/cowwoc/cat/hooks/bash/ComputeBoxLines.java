package io.github.cowwoc.cat.hooks.bash;

import io.github.cowwoc.cat.hooks.BashHandler;
import io.github.cowwoc.cat.hooks.util.ProcessRunner;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Compute box lines via hook interception.
 *
 * <p>M192: Agent calculated box widths correctly but re-typed output from memory,
 * causing alignment errors. This handler executes Python-based computation
 * and returns results via additionalContext.</p>
 *
 * <p>USAGE: Agent invokes Bash with marker comment:
 * Bash("#BOX_COMPUTE\ncontent1\ncontent2\ncontent3")</p>
 */
public final class ComputeBoxLines implements BashHandler
{
  private static final String BOX_COMPUTE_MARKER = "#BOX_COMPUTE";

  /**
   * Creates a new handler for computing box lines.
   */
  public ComputeBoxLines()
  {
    // Handler class
  }

  @Override
  @SuppressWarnings("UnusedVariable")
  public Result check(String command, JsonNode _toolInput, JsonNode _toolResult, String _sessionId)
  {
    // Check for the BOX_COMPUTE marker
    String[] lines = command.split("\n");
    if (lines.length == 0 || !lines[0].trim().startsWith(BOX_COMPUTE_MARKER))
      return Result.allow();

    // Extract content items (all lines after the marker)
    List<String> contentItems = extractContentItems(lines);
    if (contentItems.isEmpty())
      return Result.block("BOX_COMPUTE: No content items provided");

    // Execute the Python script to build the box
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
   * Executes the Python box computation script.
   *
   * @param contentItems the content items to format
   * @return a block result with the computed box output
   */
  private Result executeBoxComputation(List<String> contentItems)
  {
    try
    {
      List<String> cmdArgs = new ArrayList<>();
      cmdArgs.add("python3");
      cmdArgs.add("plugin/scripts/build_box_lines.py");
      cmdArgs.add("--format");
      cmdArgs.add("lines");
      cmdArgs.addAll(contentItems);

      String boxOutput = ProcessRunner.runAndCapture(cmdArgs);
      if (boxOutput == null)
        return Result.block("BOX_COMPUTE: Error executing build_box_lines.py");

      return Result.block(
        "BOX_COMPUTE result (use this output exactly):\n\n" + boxOutput,
        "Pre-computed box (copy exactly):\n```\n" + boxOutput + "\n```");
    }
    catch (Exception e)
    {
      return Result.block("BOX_COMPUTE: Error computing box: " + e.getMessage());
    }
  }
}
