package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.JvmScope;

import java.util.ArrayList;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Output generator for /cat:work skill.
 *
 * Provides pre-rendered status boxes for the work skill.
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
   * Builds the task not found box.
   *
   * @param taskName the task name that was not found
   * @param suggestion the suggested task name (may be empty if no suggestion)
   * @return the formatted box
   * @throws NullPointerException if taskName or suggestion is null
   * @throws IllegalArgumentException if taskName is blank
   */
  public String getTaskNotFound(String taskName, String suggestion)
  {
    requireThat(taskName, "taskName").isNotBlank();
    requireThat(suggestion, "suggestion").isNotNull();

    DisplayUtils display = scope.getDisplayUtils();
    List<String> content = new ArrayList<>();
    content.add("");
    if (!suggestion.isEmpty())
      content.add("Did you mean: " + suggestion + "?");
    content.add("Run /cat:status to see all tasks");

    return display.buildSimpleBox(
      "❔",
      "Task \"" + taskName + "\" not found",
      content);
  }

  /**
   * Builds the fork in the road box.
   *
   * @param taskName the current task name
   * @param approaches the list of approach options (minimum 2)
   * @return the formatted box
   * @throws NullPointerException if taskName or approaches is null
   * @throws IllegalArgumentException if taskName is blank or approaches has fewer than 2 elements
   */
  public String getForkInTheRoad(String taskName, List<Approach> approaches)
  {
    requireThat(taskName, "taskName").isNotBlank();
    requireThat(approaches, "approaches").isNotNull().size().isGreaterThanOrEqualTo(2);

    DisplayUtils display = scope.getDisplayUtils();
    String pathSeparator = "   " + DisplayUtils.HORIZONTAL_LINE.repeat(69);
    List<String> contentLines = new ArrayList<>();
    contentLines.add("   Task: " + taskName);
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
   * Builds the checkpoint task complete box.
   *
   * @param taskName the completed task name
   * @param minutes the time spent in minutes
   * @param tokens the number of tokens used
   * @param percentage the percentage of context used
   * @param taskBranch the task branch name
   * @return the formatted box
   * @throws NullPointerException if taskName or taskBranch is null
   * @throws IllegalArgumentException if taskName or taskBranch is blank, or numeric values are negative
   */
  public String getCheckpointTaskComplete(String taskName, int minutes, int tokens, int percentage,
                                          String taskBranch)
  {
    requireThat(taskName, "taskName").isNotBlank();
    requireThat(minutes, "minutes").isNotNegative();
    requireThat(tokens, "tokens").isNotNegative();
    requireThat(percentage, "percentage").isBetween(0, 100);
    requireThat(taskBranch, "taskBranch").isNotBlank();

    List<String> contentLines = List.of(
      "",
      "**Task:** " + taskName,
      "");
    List<String> metricsLines = List.of(
      "**Time:** " + minutes + " minutes | **Tokens:** " + tokens + " (" + percentage + "% of context)");
    List<String> branchLines = List.of(
      "**Branch:** " + taskBranch,
      "");

    return buildMultiSectionBox(DisplayUtils.EMOJI_CHECKMARK + " **CHECKPOINT: Task Complete**",
                                contentLines, metricsLines, branchLines);
  }

  /**
   * Builds the checkpoint feedback applied box.
   *
   * @param taskName the task name
   * @param iteration the feedback iteration number
   * @param subagentTokensK the subagent tokens in thousands
   * @param totalTokensK the total tokens in thousands
   * @param taskBranch the task branch name
   * @return the formatted box
   * @throws NullPointerException if taskName or taskBranch is null
   * @throws IllegalArgumentException if taskName or taskBranch is blank, or numeric values are negative
   */
  public String getCheckpointFeedbackApplied(String taskName, int iteration, int subagentTokensK,
                                             int totalTokensK, String taskBranch)
  {
    requireThat(taskName, "taskName").isNotBlank();
    requireThat(iteration, "iteration").isPositive();
    requireThat(subagentTokensK, "subagentTokensK").isNotNegative();
    requireThat(totalTokensK, "totalTokensK").isNotNegative();
    requireThat(taskBranch, "taskBranch").isNotBlank();

    List<String> contentLines = List.of(
      "",
      "**Task:** " + taskName,
      "**Feedback iteration:** " + iteration,
      "");
    List<String> metricsLines = List.of(
      "**Feedback subagent:** " + subagentTokensK + "K tokens",
      "**Total tokens (all iterations):** " + totalTokensK + "K");
    List<String> branchLines = List.of(
      "**Branch:** " + taskBranch,
      "");

    return buildMultiSectionBox(DisplayUtils.EMOJI_CHECKMARK + " **CHECKPOINT: Feedback Applied**",
                                contentLines, metricsLines, branchLines);
  }

  /**
   * Builds the task complete with next task box.
   *
   * @param taskName the completed task name
   * @param nextTaskName the next task name
   * @param nextGoal the goal from PLAN.md for the next task
   * @return the formatted box
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if any parameter is blank
   */
  public String getTaskCompleteWithNext(String taskName, String nextTaskName, String nextGoal)
  {
    requireThat(taskName, "taskName").isNotBlank();
    requireThat(nextTaskName, "nextTaskName").isNotBlank();
    requireThat(nextGoal, "nextGoal").isNotBlank();

    String header = "✓ Task Complete";

    List<String> contentLines = List.of(
      "",
      "**" + taskName + "** merged to main.",
      "");

    List<String> separatorContent = List.of(
      "**Next:** " + nextTaskName,
      nextGoal,
      "",
      "Auto-continuing in 3s...",
      DisplayUtils.BULLET + " Type \"stop\" to pause after this task",
      DisplayUtils.BULLET + " Type \"abort\" to cancel immediately");

    List<String> footerContent = List.of("");

    return buildTaskBox(header, contentLines, separatorContent, footerContent);
  }

  /**
   * Builds the task already complete box.
   *
   * @param taskName the task name
   * @param commitHash the commit hash where the task was completed
   * @param nextTaskName the next task name
   * @param nextGoal the goal from PLAN.md for the next task
   * @return the formatted box
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if any parameter is blank
   */
  public String getTaskAlreadyComplete(String taskName, String commitHash, String nextTaskName,
                                       String nextGoal)
  {
    requireThat(taskName, "taskName").isNotBlank();
    requireThat(commitHash, "commitHash").isNotBlank();
    requireThat(nextTaskName, "nextTaskName").isNotBlank();
    requireThat(nextGoal, "nextGoal").isNotBlank();

    String header = "✓ Task Already Complete";

    List<String> contentLines = List.of(
      "",
      "**" + taskName + "** was already implemented.",
      "Commit: " + commitHash,
      "",
      "STATE.md updated to reflect completion.",
      "");

    List<String> separatorContent = List.of(
      "**Next:** " + nextTaskName,
      nextGoal,
      "",
      "Auto-continuing in 3s...",
      DisplayUtils.BULLET + " Type \"stop\" to pause after this task",
      DisplayUtils.BULLET + " Type \"abort\" to cancel immediately");

    List<String> footerContent = List.of("");

    return buildTaskBox(header, contentLines, separatorContent, footerContent);
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
   * Builds the task complete low trust box.
   *
   * @param taskName the completed task name
   * @param nextTaskName the next task name
   * @param nextGoal the goal from PLAN.md for the next task
   * @return the formatted box
   * @throws NullPointerException if any parameter is null
   * @throws IllegalArgumentException if any parameter is blank
   */
  public String getTaskCompleteLowTrust(String taskName, String nextTaskName, String nextGoal)
  {
    requireThat(taskName, "taskName").isNotBlank();
    requireThat(nextTaskName, "nextTaskName").isNotBlank();
    requireThat(nextGoal, "nextGoal").isNotBlank();

    String header = "✓ Task Complete";

    List<String> contentLines = List.of(
      "",
      "**" + taskName + "** merged to main.",
      "");

    List<String> separatorContent = List.of(
      "**Next Up:** " + nextTaskName,
      nextGoal,
      "",
      "`/clear` then `/cat:work` to continue");

    List<String> footerContent = List.of("");

    return buildTaskBox(header, contentLines, separatorContent, footerContent);
  }

  /**
   * Builds the version boundary gate box.
   *
   * @param currentVersion the completed version number
   * @param tasksCompleted the number of tasks completed in this version
   * @param nextVersion the next version number
   * @param nextTaskName the next task name
   * @return the formatted box
   * @throws NullPointerException if any string parameter is null
   * @throws IllegalArgumentException if any string parameter is blank, or tasksCompleted is negative
   */
  public String getVersionBoundaryGate(String currentVersion, int tasksCompleted, String nextVersion,
                                       String nextTaskName)
  {
    requireThat(currentVersion, "currentVersion").isNotBlank();
    requireThat(tasksCompleted, "tasksCompleted").isNotNegative();
    requireThat(nextVersion, "nextVersion").isNotBlank();
    requireThat(nextTaskName, "nextTaskName").isNotBlank();

    String header = "✓ Version Complete";

    List<String> contentLines = List.of(
      "",
      "**v" + currentVersion + "** is now complete!",
      "");

    List<String> summaryContent = List.of(
      "**Summary:**",
      DisplayUtils.BULLET + " Tasks completed: " + tasksCompleted,
      "",
      "**Before continuing, consider:**",
      DisplayUtils.BULLET + " Publishing/releasing this version",
      DisplayUtils.BULLET + " Tagging the release in git",
      DisplayUtils.BULLET + " Updating documentation",
      "");

    List<String> nextVersionContent = List.of(
      "**Next Version:** v" + nextVersion,
      nextTaskName,
      "");

    return buildTaskBox(header, contentLines, summaryContent, nextVersionContent);
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
   * Builds a task box with separators between sections.
   *
   * @param header the header text
   * @param contentLines the main content
   * @param separatorContent the middle section after first separator
   * @param footerContent the footer section after second separator
   * @return the formatted box
   */
  private String buildTaskBox(String header,
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
