/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import static io.github.cowwoc.cat.hooks.Strings.block;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.ClaudeTool;
import io.github.cowwoc.cat.hooks.MainClaudeTool;
import io.github.cowwoc.cat.hooks.util.IssueDiscovery;
import io.github.cowwoc.cat.hooks.util.IssueDiscovery.DiscoveryResult;
import io.github.cowwoc.cat.hooks.util.IssueDiscovery.Scope;
import io.github.cowwoc.cat.hooks.util.IssueDiscovery.SearchOptions;
import io.github.cowwoc.cat.hooks.util.IssueGoalReader;
import io.github.cowwoc.cat.hooks.util.SkillOutput;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Output generator for issue complete boxes.
 * <p>
 * Generates issue complete and scope complete boxes for completion notifications.
 */
public final class GetIssueCompleteOutput implements SkillOutput
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
   * Generates the output for this skill.
   * <p>
   * Routes to getIssueCompleteBox() or getScopeCompleteBox() based on available arguments.
   * With 2 arguments (issueName, targetBranch), discovers the next available issue internally using
   * IssueDiscovery and renders the appropriate box. With 1 argument (scopeName), renders the scope
   * complete box directly.
   *
   * @param args the arguments from the dispatcher: [issueName, targetBranch] for issue-complete with
   *             internal discovery, or [scopeName] for scope-complete
   * @return the formatted issue or scope complete box
   * @throws NullPointerException     if {@code args} is null
   * @throws IllegalArgumentException if insufficient arguments provided
   * @throws IOException              if an I/O error occurs
   */
  @Override
  public String getOutput(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();

    if (args.length == 0)
      return "";

    if (args.length == 2)
    {
      // Issue complete: issueName, targetBranch — discover next issue internally
      String issueName = args[0];
      String targetBranch = args[1];
      return discoverAndRender(issueName, targetBranch);
    }
    if (args.length == 1)
      // Scope complete: scopeName
      return getScopeCompleteBox(args[0]);
    throw new IllegalArgumentException(
      "Expected 1 or 2 arguments (scope-name OR issue-name target-branch), got " + args.length);
  }

  /**
   * Discovers the next available issue and renders the appropriate completion box.
   * <p>
   * Extracts the version prefix from {@code issueName} (e.g., {@code 2.1} from {@code 2.1-fix-bug},
   * {@code 2.1.3} from {@code 2.1.3-fix-bug}, or {@code 2} from {@code 2-fix-bug}), searches for
   * the next open issue in that version scope using IssueDiscovery with no lock acquisition,
   * and renders either the issue-complete box (if a next issue is found) or the scope-complete box.
   * The completed issue is excluded from discovery by its full qualified ID.
   * <p>
   * Determines the appropriate {@link IssueDiscovery.Scope} from the version format:
   * uses {@code Scope.VERSION} which handles all version formats. Compare with
   * {@link GetNextIssueOutput}'s discovery, which uses {@code Scope.ALL} to find the globally next
   * issue for the banner display.
   *
   * @param issueName    the completed issue name (e.g., {@code 2.1-fix-bug})
   * @param targetBranch the target branch that was merged to
   * @return the formatted issue or scope complete box
   * @throws NullPointerException     if any parameter is null
   * @throws IllegalArgumentException if any parameter is blank
   * @throws IOException              if an I/O error occurs
   */
  public String discoverAndRender(String issueName, String targetBranch) throws IOException
  {
    requireThat(issueName, "issueName").isNotBlank();
    requireThat(targetBranch, "targetBranch").isNotBlank();

    // Extract version prefix from issueName (e.g., "2.1" from "2.1-fix-bug",
    // "2.1.3" from "2.1.3-fix-bug", or "2" from "2-fix-bug")
    int dashIndex = issueName.indexOf('-');
    if (dashIndex < 0)
    {
      // Cannot determine version; fall back to scope-complete
      return getScopeCompleteBox("v" + issueName);
    }
    String version = issueName.substring(0, dashIndex);

    IssueDiscovery discovery = new IssueDiscovery(scope);
    SearchOptions options = new SearchOptions(Scope.VERSION, version, "", issueName, false);
    DiscoveryResult result = discovery.findNextIssue(options);

    if (result instanceof DiscoveryResult.Found found)
    {
      Path planPath = Path.of(found.issuePath()).resolve("plan.md");
      String nextGoal = IssueGoalReader.readGoalFromPlan(planPath);
      return getIssueCompleteBox(issueName, found.issueId(), nextGoal, targetBranch);
    }
    // No next issue found — scope is complete
    return getScopeCompleteBox("v" + version);
  }

  /**
   * CLI entry point for generating issue complete boxes.
   * <p>
   * Usage:
   * <ul>
   *   <li>{@code --issue-name <name> --target-branch <branch>} — issue complete with internal next-issue
   *       discovery</li>
   *   <li>{@code --scope-complete <scope>} — scope complete box</li>
   * </ul>
   *
   * @param args command line arguments
   */
  public static void main(String[] args)
  {
    try (ClaudeTool scope = new MainClaudeTool())
    {
      try
      {
        String issueName = "";
        String targetBranch = "main";
        String scopeComplete = "";

        for (int i = 0; i + 1 < args.length; i += 2)
        {
          switch (args[i])
          {
            case "--issue-name" -> issueName = args[i + 1];
            case "--target-branch" -> targetBranch = args[i + 1];
            case "--scope-complete" -> scopeComplete = args[i + 1];
            default ->
            {
            }
          }
        }

        GetIssueCompleteOutput output = new GetIssueCompleteOutput(scope);

        if (!scopeComplete.isEmpty())
        {
          String box = output.getScopeCompleteBox(scopeComplete);
          System.out.println(box);
        }
        else
        {
          if (issueName.isEmpty())
          {
            System.err.println("--issue-name is required unless --scope-complete is used");
            System.exit(1);
          }
          String box = output.discoverAndRender(issueName, targetBranch);
          System.out.println(box);
        }
      }
      catch (RuntimeException | AssertionError e)
      {
        Logger log = LoggerFactory.getLogger(GetIssueCompleteOutput.class);
        log.error("Unexpected error", e);
        System.out.println(block(scope,
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
      catch (Exception e)
      {
        System.err.println("Error: " + e.getMessage());
        System.exit(1);
      }
    }
  }

  /**
   * Builds the issue complete box with next task information.
   *
   * @param issueName    the completed issue name
   * @param nextIssue    the next issue name
   * @param nextGoal     the goal of the next issue
   * @param targetBranch the target branch that was merged to
   * @return the formatted box
   * @throws NullPointerException     if any parameter is null
   * @throws IllegalArgumentException if any parameter is blank
   */
  public String getIssueCompleteBox(String issueName, String nextIssue, String nextGoal,
    String targetBranch)
  {
    requireThat(issueName, "issueName").isNotBlank();
    requireThat(nextIssue, "nextIssue").isNotBlank();
    requireThat(nextGoal, "nextGoal").isNotBlank();
    requireThat(targetBranch, "targetBranch").isNotBlank();

    DisplayUtils display = scope.getDisplayUtils();
    String header = "✓ Issue Complete";

    List<String> content = List.of(
      "",
      issueName + " merged to " + targetBranch + ".",
      "");

    List<String> sep = List.of(
      "**Next:** " + nextIssue,
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
   * @param display  the display utilities
   * @param header   the header text
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
