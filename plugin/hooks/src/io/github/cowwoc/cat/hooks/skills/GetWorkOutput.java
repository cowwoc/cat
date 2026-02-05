package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.JvmScope;

import java.util.ArrayList;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Output generator for /cat:work skill.
 *
 * Provides status boxes for the work skill (script output).
 * Progress banners are handled by silent preprocessing via get-progress-banner.sh.
 */
public final class GetWorkOutput
{
  /**
   * The JVM scope for accessing shared services.
   */
  private final JvmScope scope;

  /**
   * Creates a GetWorkOutput instance.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if scope is null
   */
  public GetWorkOutput(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Represents an approach option for the fork in the road display.
   *
   * @param name the approach name (e.g., "Minimal changes")
   * @param description a brief description of the approach
   * @param risk the risk level (e.g., "Low", "Medium", "High")
   * @param scope the number of files affected
   * @param configAlignment the configuration alignment percentage (0-100)
   */
  public record Approach(String name, String description, String risk, int scope, int configAlignment)
  {
    /**
     * Creates an approach record.
     *
     * @param name the approach name
     * @param description a brief description
     * @param risk the risk level
     * @param scope the number of files affected
     * @param configAlignment the configuration alignment percentage
     * @throws NullPointerException if name, description, or risk is null
     * @throws IllegalArgumentException if name, description, or risk is blank, or configAlignment is out of
     *                                  range
     */
    public Approach
    {
      requireThat(name, "name").isNotBlank();
      requireThat(description, "description").isNotBlank();
      requireThat(risk, "risk").isNotBlank();
      requireThat(scope, "scope").isNotNegative();
      requireThat(configAlignment, "configAlignment").isBetween(0, 100);
    }
  }

  /**
   * Builds the no executable tasks box.
   *
   * @return the formatted box
   */
  public String getNoExecutableTasks()
  {
    DisplayUtils display = scope.getDisplayUtils();
    return display.buildSimpleBox(
      "ℹ️",
      "No executable tasks",
      List.of(
        "",
        "Run /cat:status to see available tasks"));
  }

  /**
   * Builds the issue not found box.
   *
   * @param issueName the issue name that was not found
   * @param suggestion the suggested issue name (may be empty if no suggestion)
   * @return the formatted box
   * @throws NullPointerException if issueName or suggestion is null
   * @throws IllegalArgumentException if issueName is blank
   */
  public String getIssueNotFound(String issueName, String suggestion)
  {
    requireThat(issueName, "issueName").isNotBlank();
    requireThat(suggestion, "suggestion").isNotNull();

    DisplayUtils display = scope.getDisplayUtils();
    List<String> content = new ArrayList<>();
    content.add("");
    if (!suggestion.isEmpty())
      content.add("Did you mean: " + suggestion + "?");
    content.add("Run /cat:status to see all issues");

    return display.buildSimpleBox(
      "❔",
      "Issue \"" + issueName + "\" not found",
      content);
  }

  /**
   * Builds the fork in the road box.
   *
   * @param issueName the current issue name
   * @param approaches the list of approach options (minimum 2)
   * @return the formatted box
   * @throws NullPointerException if issueName or approaches is null
   * @throws IllegalArgumentException if issueName is blank or approaches has fewer than 2 elements
   */
  public String getForkInTheRoad(String issueName, List<Approach> approaches)
  {
    requireThat(issueName, "issueName").isNotBlank();
    requireThat(approaches, "approaches").isNotNull().size().isGreaterThanOrEqualTo(2);

    DisplayUtils display = scope.getDisplayUtils();
    String pathSeparator = "   " + DisplayUtils.HORIZONTAL_LINE.repeat(69);
    List<String> contentLines = new ArrayList<>();
    contentLines.add("   Issue: " + issueName);
    contentLines.add("");
    contentLines.add("   Multiple viable paths - how would you prefer to proceed?");
    contentLines.add("");
    contentLines.add("   CHOOSE YOUR PATH");
    contentLines.add(pathSeparator);
    contentLines.add("");

    char letter = 'A';
    for (Approach approach : approaches)
    {
      contentLines.add("   [" + letter + "] " + approach.name());
      contentLines.add("       " + approach.description());
      String metrics = "       Risk: " + approach.risk() + " | Scope: " + approach.scope() +
                       " files | Config alignment: " + approach.configAlignment() + "%";
      contentLines.add(metrics);
      contentLines.add("");
      ++letter;
    }

    // Calculate max width
    int maxWidth = 0;
    for (String line : contentLines)
    {
      int w = display.displayWidth(line);
      if (w > maxWidth)
        maxWidth = w;
    }

    List<String> lines = new ArrayList<>();
    lines.add(DisplayUtils.EMOJI_SHUFFLE + " FORK IN THE ROAD");
    lines.add("╭" + DisplayUtils.HORIZONTAL_LINE.repeat(maxWidth + 2) + "╮");
    for (String content : contentLines)
      lines.add(display.buildLine(content, maxWidth));
    lines.add("╰" + DisplayUtils.HORIZONTAL_LINE.repeat(maxWidth + 2) + "╯");
    return String.join("\n", lines);
  }

  /**
   * Builds the checkpoint issue complete box.
   *
   * @param issueName the completed issue name
   * @param minutes the time spent in minutes
   * @param tokens the number of tokens used
   * @param percentage the percentage of context used
   * @param issueBranch the issue branch name
   * @return the formatted box
   * @throws NullPointerException if issueName or issueBranch is null
   * @throws IllegalArgumentException if issueName or issueBranch is blank, or numeric values are negative
   */
  public String getCheckpointIssueComplete(String issueName, int minutes, int tokens, int percentage,
                                          String issueBranch)
  {
    requireThat(issueName, "issueName").isNotBlank();
    requireThat(minutes, "minutes").isNotNegative();
    requireThat(tokens, "tokens").isNotNegative();
    requireThat(percentage, "percentage").isBetween(0, 100);
    requireThat(issueBranch, "issueBranch").isNotBlank();

    List<String> contentLines = List.of(
      "",
      "**Issue:** " + issueName,
      "");
    List<String> metricsLines = List.of(
      "**Time:** " + minutes + " minutes | **Tokens:** " + tokens + " (" + percentage + "% of context)");
    List<String> branchLines = List.of(
      "**Branch:** " + issueBranch,
      "");

    return buildMultiSectionBox(DisplayUtils.EMOJI_CHECKMARK + " **CHECKPOINT: Issue Complete**",
                                contentLines, metricsLines, branchLines);
  }

  /**
   * Builds the checkpoint feedback applied box.
   *
   * @param issueName the issue name
   * @param iteration the feedback iteration number
   * @param subagentTokensK the subagent tokens in thousands
   * @param totalTokensK the total tokens in thousands
   * @param issueBranch the issue branch name
   * @return the formatted box
   * @throws NullPointerException if issueName or issueBranch is null
   * @throws IllegalArgumentException if issueName or issueBranch is blank, or numeric values are negative
   */
  public String getCheckpointFeedbackApplied(String issueName, int iteration, int subagentTokensK,
                                             int totalTokensK, String issueBranch)
  {
    requireThat(issueName, "issueName").isNotBlank();
    requireThat(iteration, "iteration").isPositive();
    requireThat(subagentTokensK, "subagentTokensK").isNotNegative();
    requireThat(totalTokensK, "totalTokensK").isNotNegative();
    requireThat(issueBranch, "issueBranch").isNotBlank();

    List<String> contentLines = List.of(
      "",
      "**Issue:** " + issueName,
      "**Feedback iteration:** " + iteration,
      "");
    List<String> metricsLines = List.of(
      "**Feedback subagent:** " + subagentTokensK + "K tokens",
      "**Total tokens (all iterations):** " + totalTokensK + "K");
    List<String> branchLines = List.of(
      "**Branch:** " + issueBranch,
      "");

    return buildMultiSectionBox(DisplayUtils.EMOJI_CHECKMARK + " **CHECKPOINT: Feedback Applied**",
                                contentLines, metricsLines, branchLines);
  }

  /**
   * Builds the issue complete with next issue box.
   *
   * @param issueName the completed issue name
   * @param nextIssueName the next issue name
   * @param nextGoal the goal from PLAN.md for the next issue
   * @return the formatted box
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if any parameter is blank
   */
  public String getIssueCompleteWithNext(String issueName, String nextIssueName, String nextGoal)
  {
    requireThat(issueName, "issueName").isNotBlank();
    requireThat(nextIssueName, "nextIssueName").isNotBlank();
    requireThat(nextGoal, "nextGoal").isNotBlank();

    String header = "✓ Issue Complete";

    List<String> contentLines = List.of(
      "",
      "**" + issueName + "** merged to main.",
      "");

    List<String> separatorContent = List.of(
      "**Next:** " + nextIssueName,
      nextGoal,
      "",
      "Continuing to next issue...",
      DisplayUtils.BULLET + " Type \"stop\" to pause after this issue",
      DisplayUtils.BULLET + " Type \"abort\" to cancel immediately");

    List<String> footerContent = List.of("");

    return buildIssueBox(header, contentLines, separatorContent, footerContent);
  }

  /**
   * Builds the issue already complete box.
   *
   * @param issueName the issue name
   * @param commitHash the commit hash where the issue was completed
   * @param nextIssueName the next issue name
   * @param nextGoal the goal from PLAN.md for the next issue
   * @return the formatted box
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if any parameter is blank
   */
  public String getIssueAlreadyComplete(String issueName, String commitHash, String nextIssueName,
                                       String nextGoal)
  {
    requireThat(issueName, "issueName").isNotBlank();
    requireThat(commitHash, "commitHash").isNotBlank();
    requireThat(nextIssueName, "nextIssueName").isNotBlank();
    requireThat(nextGoal, "nextGoal").isNotBlank();

    String header = "✓ Issue Already Complete";

    List<String> contentLines = List.of(
      "",
      "**" + issueName + "** was already implemented.",
      "Commit: " + commitHash,
      "",
      "STATE.md updated to reflect completion.",
      "");

    List<String> separatorContent = List.of(
      "**Next:** " + nextIssueName,
      nextGoal,
      "",
      "Continuing to next issue...",
      DisplayUtils.BULLET + " Type \"stop\" to pause after this issue",
      DisplayUtils.BULLET + " Type \"abort\" to cancel immediately");

    List<String> footerContent = List.of("");

    return buildIssueBox(header, contentLines, separatorContent, footerContent);
  }

  /**
   * Builds the scope complete box.
   *
   * @param scopeDescription the scope description (e.g., "v0.5" or "All versions")
   * @param completionMessage the completion message (e.g., "v0.5 complete" or "All versions complete!")
   * @return the formatted box
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if any parameter is blank
   */
  public String getScopeComplete(String scopeDescription, String completionMessage)
  {
    requireThat(scopeDescription, "scopeDescription").isNotBlank();
    requireThat(completionMessage, "completionMessage").isNotBlank();

    DisplayUtils display = scope.getDisplayUtils();
    String header = "✓ Scope Complete";

    List<String> contentLines = List.of(
      "",
      "**" + scopeDescription + "** - all tasks complete!",
      "",
      completionMessage,
      "");

    int maxWidth = calculateMaxWidth(display, header, contentLines);

    List<String> lines = new ArrayList<>();
    lines.add(display.buildHeaderTop(header, maxWidth));
    for (String content : contentLines)
      lines.add(display.buildLine(content, maxWidth));
    lines.add(display.buildBorder(maxWidth, false));

    return String.join("\n", lines);
  }

  /**
   * Builds the issue complete low trust box.
   *
   * @param issueName the completed issue name
   * @param nextIssueName the next issue name
   * @param nextGoal the goal from PLAN.md for the next issue
   * @return the formatted box
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if any parameter is blank
   */
  public String getIssueCompleteLowTrust(String issueName, String nextIssueName, String nextGoal)
  {
    requireThat(issueName, "issueName").isNotBlank();
    requireThat(nextIssueName, "nextIssueName").isNotBlank();
    requireThat(nextGoal, "nextGoal").isNotBlank();

    String header = "✓ Issue Complete";

    List<String> contentLines = List.of(
      "",
      "**" + issueName + "** merged to main.",
      "");

    List<String> separatorContent = List.of(
      "**Next Up:** " + nextIssueName,
      nextGoal,
      "",
      "`/clear` then `/cat:work` to continue");

    List<String> footerContent = List.of("");

    return buildIssueBox(header, contentLines, separatorContent, footerContent);
  }

  /**
   * Builds the version boundary gate box.
   *
   * @param currentVersion the completed version number
   * @param issuesCompleted the number of issues completed in this version
   * @param nextVersion the next version number
   * @param nextIssueName the next issue name
   * @return the formatted box
   * @throws NullPointerException if any string parameter is null
   * @throws IllegalArgumentException if any string parameter is blank, or issuesCompleted is negative
   */
  public String getVersionBoundaryGate(String currentVersion, int issuesCompleted, String nextVersion,
                                       String nextIssueName)
  {
    requireThat(currentVersion, "currentVersion").isNotBlank();
    requireThat(issuesCompleted, "issuesCompleted").isNotNegative();
    requireThat(nextVersion, "nextVersion").isNotBlank();
    requireThat(nextIssueName, "nextIssueName").isNotBlank();

    String header = "✓ Version Complete";

    List<String> contentLines = List.of(
      "",
      "**v" + currentVersion + "** is now complete!",
      "");

    List<String> summaryContent = List.of(
      "**Summary:**",
      DisplayUtils.BULLET + " Issues completed: " + issuesCompleted,
      "",
      "**Before continuing, consider:**",
      DisplayUtils.BULLET + " Publishing/releasing this version",
      DisplayUtils.BULLET + " Tagging the release in git",
      DisplayUtils.BULLET + " Updating documentation",
      "");

    List<String> nextVersionContent = List.of(
      "**Next Version:** v" + nextVersion,
      nextIssueName,
      "");

    return buildIssueBox(header, contentLines, summaryContent, nextVersionContent);
  }

  /**
   * Builds a multi-section box with three content sections.
   *
   * @param header the header text
   * @param content1 the first section content
   * @param content2 the second section content
   * @param content3 the third section content
   * @return the formatted box
   */
  private String buildMultiSectionBox(String header,
                                      List<String> content1,
                                      List<String> content2,
                                      List<String> content3)
  {
    DisplayUtils display = scope.getDisplayUtils();
    List<String> allContent = new ArrayList<>();
    allContent.addAll(content1);
    allContent.addAll(content2);
    allContent.addAll(content3);

    int headerWidth = display.displayWidth(header);
    int maxWidth = headerWidth;
    for (String line : allContent)
    {
      int w = display.displayWidth(line);
      if (w > maxWidth)
        maxWidth = w;
    }

    List<String> lines = new ArrayList<>();
    lines.add(header);
    lines.add("╭" + DisplayUtils.HORIZONTAL_LINE.repeat(maxWidth + 2) + "╮");
    for (String content : content1)
      lines.add(display.buildLine(content, maxWidth));
    lines.add(display.buildSeparator(maxWidth));
    for (String content : content2)
      lines.add(display.buildLine(content, maxWidth));
    lines.add(display.buildSeparator(maxWidth));
    for (String content : content3)
      lines.add(display.buildLine(content, maxWidth));
    lines.add(display.buildBorder(maxWidth, false));

    return String.join("\n", lines);
  }

  /**
   * Builds an issue box with separators between sections.
   *
   * @param header the header text
   * @param contentLines the main content
   * @param separatorContent the middle section after first separator
   * @param footerContent the footer section after second separator
   * @return the formatted box
   */
  private String buildIssueBox(String header,
                              List<String> contentLines,
                              List<String> separatorContent,
                              List<String> footerContent)
  {
    DisplayUtils display = scope.getDisplayUtils();
    List<String> allContent = new ArrayList<>();
    allContent.addAll(contentLines);
    allContent.addAll(separatorContent);
    allContent.addAll(footerContent);

    int maxWidth = calculateMaxWidth(display, header, allContent);

    List<String> lines = new ArrayList<>();
    lines.add(display.buildHeaderTop(header, maxWidth));
    for (String content : contentLines)
      lines.add(display.buildLine(content, maxWidth));
    lines.add(display.buildSeparator(maxWidth));
    for (String content : separatorContent)
      lines.add(display.buildLine(content, maxWidth));
    lines.add(display.buildSeparator(maxWidth));
    for (String content : footerContent)
      lines.add(display.buildLine(content, maxWidth));
    lines.add(display.buildBorder(maxWidth, false));

    return String.join("\n", lines);
  }

  /**
   * Calculates the maximum width needed for a box.
   *
   * @param display the display utilities
   * @param header the header text
   * @param contentLines the content lines
   * @return the maximum width
   */
  private int calculateMaxWidth(DisplayUtils display, String header, List<String> contentLines)
  {
    int headerWidth = display.displayWidth(header) + 5;  // Account for "--- header ---" format
    int maxWidth = headerWidth;
    for (String line : contentLines)
    {
      int w = display.displayWidth(line);
      if (w > maxWidth)
        maxWidth = w;
    }
    return maxWidth;
  }
}
