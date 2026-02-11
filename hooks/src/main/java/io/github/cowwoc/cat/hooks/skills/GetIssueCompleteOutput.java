package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.MainJvmScope;
import io.github.cowwoc.cat.hooks.JvmScope;

import java.util.ArrayList;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Output generator for issue complete boxes.
 *
 * Generates issue complete and scope complete boxes for completion notifications.
 */
public final class GetIssueCompleteOutput
{
  private static final int DEFAULT_PREFIX_LENGTH = 4;

  /**
   * The JVM scope for accessing shared services.
   */
  private final JvmScope scope;

  /**
   * Creates a GetIssueCompleteOutput instance.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if scope is null
   */
  public GetIssueCompleteOutput(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * CLI entry point for generating issue complete boxes.
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    try
    {
      String issueName = "";
      String nextIssue = "";
      String nextGoal = "";
      String baseBranch = "main";
      String scopeComplete = "";

      for (int i = 0; i + 1 < args.length; i += 2)
      {
        switch (args[i])
        {
          case "--issue-name" -> issueName = args[i + 1];
          case "--next-issue" -> nextIssue = args[i + 1];
          case "--next-goal" -> nextGoal = args[i + 1];
          case "--base-branch" -> baseBranch = args[i + 1];
          case "--scope-complete" -> scopeComplete = args[i + 1];
          default ->
          {
          }
        }
      }

      try (JvmScope scope = new MainJvmScope())
      {
        GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);

        if (!scopeComplete.isEmpty())
        {
          String box = output.getScopeCompleteBox(scopeComplete);
          System.out.println(box);
        }
        else
        {
          if (issueName.isEmpty() || nextIssue.isEmpty() || nextGoal.isEmpty())
          {
            System.err.println("--issue-name, --next-issue, and --next-goal are required " +
              "unless --scope-complete is used");
            System.exit(1);
          }
          String box = output.getIssueCompleteBox(issueName, nextIssue, nextGoal, baseBranch);
          System.out.println(box);
        }
      }
    }
    catch (Exception e)
    {
      System.err.println("Error: " + e.getMessage());
      System.exit(1);
    }
  }

  /**
   * Builds the issue complete box with next task information.
   *
   * @param issueName the completed issue name
   * @param nextIssue the next issue name
   * @param nextGoal the goal of the next issue
   * @param baseBranch the base branch that was merged to
   * @return the formatted box
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if any parameter is blank
   */
  public String getIssueCompleteBox(String issueName, String nextIssue, String nextGoal, String baseBranch)
  {
    requireThat(issueName, "issueName").isNotBlank();
    requireThat(nextIssue, "nextIssue").isNotBlank();
    requireThat(nextGoal, "nextGoal").isNotBlank();
    requireThat(baseBranch, "baseBranch").isNotBlank();

    DisplayUtils display = scope.getDisplayUtils();
    String header = "✓ Issue Complete";

    List<String> content = List.of(
      "",
      issueName + " merged to " + baseBranch + ".",
      "");

    List<String> sep = List.of(
      "Next: " + nextIssue,
      nextGoal,
      "",
      "Continuing to next issue...",
      "• Type \"stop\" to pause after this issue",
      "• Type \"abort\" to cancel immediately");

    List<String> footer = List.of("");

    List<String> allContent = new ArrayList<>();
    allContent.addAll(content);
    allContent.addAll(sep);
    allContent.addAll(footer);

    int headerWidth = display.displayWidth(header) + 5;
    int maxWidth = headerWidth;
    for (String line : allContent)
    {
      int w = display.displayWidth(line);
      if (w > maxWidth)
        maxWidth = w;
    }

    List<String> lines = new ArrayList<>();
    lines.add(buildHeaderTop(display, header, maxWidth));
    for (String c : content)
      lines.add(display.buildLine(c, maxWidth));
    lines.add(display.buildSeparator(maxWidth));
    for (String c : sep)
      lines.add(display.buildLine(c, maxWidth));
    lines.add(display.buildSeparator(maxWidth));
    for (String c : footer)
      lines.add(display.buildLine(c, maxWidth));
    lines.add(display.buildBottomBorder(maxWidth));

    return String.join("\n", lines);
  }

  /**
   * Builds the scope complete box.
   *
   * @param scopeName the scope description (e.g., "v2.1")
   * @return the formatted box
   * @throws NullPointerException if scopeName is null
   * @throws IllegalArgumentException if scopeName is blank
   */
  public String getScopeCompleteBox(String scopeName)
  {
    requireThat(scopeName, "scopeName").isNotBlank();

    DisplayUtils display = this.scope.getDisplayUtils();
    String header = "✓ Scope Complete";

    List<String> content = List.of(
      "",
      scopeName + " - all issues complete!",
      "");

    int headerWidth = display.displayWidth(header) + 5;
    int maxWidth = headerWidth;
    for (String line : content)
    {
      int w = display.displayWidth(line);
      if (w > maxWidth)
        maxWidth = w;
    }

    List<String> lines = new ArrayList<>();
    lines.add(buildHeaderTop(display, header, maxWidth));
    for (String c : content)
      lines.add(display.buildLine(c, maxWidth));
    lines.add(display.buildBottomBorder(maxWidth));

    return String.join("\n", lines);
  }

  /**
   * Builds a top border with embedded header text.
   *
   * @param display the display utilities
   * @param header the header text
   * @param maxWidth the maximum content width
   * @return the formatted header top line
   */
  private String buildHeaderTop(DisplayUtils display, String header, int maxWidth)
  {
    int innerWidth = maxWidth + 2;
    int headerWidth = display.displayWidth(header);
    String prefixDashes = DisplayUtils.BOX_HORIZONTAL + DisplayUtils.BOX_HORIZONTAL +
                          DisplayUtils.BOX_HORIZONTAL + " ";
    int suffixDashesCount = innerWidth - DEFAULT_PREFIX_LENGTH - headerWidth - 1;
    if (suffixDashesCount < 1)
      suffixDashesCount = 1;
    String suffixDashes = DisplayUtils.BOX_HORIZONTAL.repeat(suffixDashesCount);
    return DisplayUtils.BOX_TOP_LEFT + prefixDashes + header + " " + suffixDashes +
           DisplayUtils.BOX_TOP_RIGHT;
  }
}
