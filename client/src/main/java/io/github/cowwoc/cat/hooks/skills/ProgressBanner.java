/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import io.github.cowwoc.cat.hooks.util.SkillOutput;
import java.io.IOException;

/**
 * Generates progress banners for CAT workflow phases.
 * <p>
 * Phase symbols:
 * - ○ Pending
 * - ● Complete
 * - ◉ Active
 * - ✗ Failed
 * <p>
 * Workflow phases: Preparing, Implementing, Confirming, Reviewing, Merging
 */
public final class ProgressBanner implements SkillOutput
{
  /**
   * Workflow phases for CAT issue processing.
   */
  public enum Phase
  {
    /**
     * Preparing phase - initial setup and planning.
     */
    PREPARING,

    /**
     * Implementing phase - implementation work.
     */
    IMPLEMENTING,

    /**
     * Confirming phase - acceptance criteria confirmation.
     */
    CONFIRMING,

    /**
     * Reviewing phase - code review and validation.
     */
    REVIEWING,

    /**
     * Merging phase - final integration.
     */
    MERGING
  }

  private static final String PENDING = "○";
  private static final String COMPLETE = "●";
  private static final String ACTIVE = "◉";
  private static final String CAT_EMOJI = "🐱";

  private final JvmScope scope;

  /**
   * Creates a new ProgressBanner instance.
   *
   * @param scope the JVM scope providing display utilities
   * @throws NullPointerException if {@code scope} is null
   */
  public ProgressBanner(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
  }

  /**
   * Generates a banner for a specific phase.
   *
   * @param issueId the issue ID (may be empty for generic banner)
   * @param phase the workflow phase
   * @return the banner text
   * @throws NullPointerException if {@code issueId} or {@code phase} are null
   */
  public String generateBanner(String issueId, Phase phase)
  {
    requireThat(issueId, "issueId").isNotNull();
    requireThat(phase, "phase").isNotNull();
    return buildBanner(issueId, phase);
  }

  /**
   * Returns the display symbol for a phase relative to the current active phase.
   *
   * @param phase the phase to get the symbol for
   * @param currentPhase the currently active phase
   * @return {@link #COMPLETE} if before current, {@link #ACTIVE} if current, {@link #PENDING} if after
   */
  private String phaseSymbol(Phase phase, Phase currentPhase)
  {
    if (phase.ordinal() < currentPhase.ordinal())
      return COMPLETE;
    if (phase == currentPhase)
      return ACTIVE;
    return PENDING;
  }

  /**
   * Generates all five phase banners.
   *
   * @param issueId the issue ID (may be empty for generic banner)
   * @return formatted output with all phase banners
   * @throws NullPointerException if {@code issueId} is null
   */
  public String generateAllPhases(String issueId)
  {
    requireThat(issueId, "issueId").isNotNull();

    StringBuilder output = new StringBuilder(512);

    output.append("**Preparing phase** (◉ ○ ○ ○ ○):\n").
      append("```\n").
      append(buildBanner(issueId, Phase.PREPARING)).
      append("\n```\n\n").
      append("**Implementing phase** (● ◉ ○ ○ ○):\n").
      append("```\n").
      append(buildBanner(issueId, Phase.IMPLEMENTING)).
      append("\n```\n\n").
      append("**Confirming phase** (● ● ◉ ○ ○):\n").
      append("```\n").
      append(buildBanner(issueId, Phase.CONFIRMING)).
      append("\n```\n\n").
      append("**Reviewing phase** (● ● ● ◉ ○):\n").
      append("```\n").
      append(buildBanner(issueId, Phase.REVIEWING)).
      append("\n```\n\n").
      append("**Merging phase** (● ● ● ● ◉):\n").
      append("```\n").
      append(buildBanner(issueId, Phase.MERGING)).
      append("\n```");

    return output.toString();
  }

  /**
   * Generates a generic preparing banner when no issue ID is available.
   *
   * @return the generic banner with explanatory text
   */
  public String generateGenericPreparingBanner()
  {
    StringBuilder output = new StringBuilder(256);
    output.append("```\n").
      append(buildBanner("", Phase.PREPARING)).
      append("\n```\n\n").
      append("Issue will be identified after preparation completes.");
    return output.toString();
  }

  /**
   * Generates skill output for preprocessor directives.
   *
   * @param args the arguments from the preprocessor directive
   * @return the generated output
   * @throws IOException if an I/O error occurs
   */
  @Override
  public String getOutput(String[] args) throws IOException
  {
    String issueId = "";
    String phaseStr = "";
    boolean allPhases = false;

    for (int i = 0; i < args.length; ++i)
    {
      if (args[i].equals("--phase") && i + 1 < args.length)
      {
        phaseStr = args[i + 1];
        ++i;
      }
      else if (args[i].equals("--all-phases"))
      {
        allPhases = true;
      }
      else if (args[i].equals("--project-dir") || args[i].equals("--session-id"))
      {
        ++i;
      }
      else if (!args[i].startsWith("--") && args[i].matches("^[0-9]+\\.[0-9]+-[a-zA-Z0-9_-]+$"))
      {
        issueId = args[i];
      }
    }

    if (phaseStr.isEmpty())
      allPhases = true;

    if (issueId.isEmpty())
    {
      return generateGenericPreparingBanner();
    }
    else if (allPhases)
    {
      return generateAllPhases(issueId);
    }
    else
    {
      Phase phase;
      switch (phaseStr.toLowerCase(java.util.Locale.ROOT))
      {
        case "preparing" -> phase = Phase.PREPARING;
        case "implementing" -> phase = Phase.IMPLEMENTING;
        case "confirming" -> phase = Phase.CONFIRMING;
        case "reviewing" -> phase = Phase.REVIEWING;
        case "merging" -> phase = Phase.MERGING;
        default -> throw new IllegalArgumentException(
          "Unknown phase '" + phaseStr + "'. Valid: preparing, implementing, confirming, reviewing, merging");
      }
      return generateBanner(issueId, phase);
    }
  }

  /**
   * Builds a banner showing progress for the specified phase.
   *
   * @param issueId the issue ID (may be empty)
   * @param currentPhase the currently active phase
   * @return the banner text (3 lines)
   */
  private String buildBanner(String issueId, Phase currentPhase)
  {
    String p1 = phaseSymbol(Phase.PREPARING, currentPhase);
    String p2 = phaseSymbol(Phase.IMPLEMENTING, currentPhase);
    String p3 = phaseSymbol(Phase.CONFIRMING, currentPhase);
    String p4 = phaseSymbol(Phase.REVIEWING, currentPhase);
    String p5 = phaseSymbol(Phase.MERGING, currentPhase);

    String phaseContent = "  " + p1 + " Preparing ────── " + p2 + " Implementing ────── " +
      p3 + " Confirming ────── " + p4 + " Reviewing ────── " + p5 + " Merging ";
    int phaseWidth = scope.getDisplayUtils().displayWidth(phaseContent);

    String headerContent;
    int headerWidth;
    if (issueId.isEmpty())
    {
      headerContent = DisplayUtils.BOX_HORIZONTAL + " " + CAT_EMOJI + " ";
      headerWidth = scope.getDisplayUtils().displayWidth(headerContent);
    }
    else
    {
      headerContent = DisplayUtils.BOX_HORIZONTAL + " " + CAT_EMOJI + " " + issueId + " ";
      headerWidth = scope.getDisplayUtils().displayWidth(headerContent);
    }

    int innerWidth = Math.max(headerWidth, phaseWidth);

    int topDashCount = innerWidth - headerWidth;
    if (topDashCount < 0)
      topDashCount = 0;
    String topDashes = DisplayUtils.BOX_HORIZONTAL.repeat(topDashCount);
    String topLine = DisplayUtils.BOX_TOP_LEFT + headerContent + topDashes + DisplayUtils.BOX_TOP_RIGHT;

    int phasePadding = innerWidth - phaseWidth;
    if (phasePadding < 0)
      phasePadding = 0;
    String middleLine = DisplayUtils.BOX_VERTICAL + phaseContent + " ".repeat(phasePadding) +
      DisplayUtils.BOX_VERTICAL;

    String bottomLine = DisplayUtils.BOX_BOTTOM_LEFT + DisplayUtils.BOX_HORIZONTAL.repeat(innerWidth) +
      DisplayUtils.BOX_BOTTOM_RIGHT;

    return topLine + "\n" + middleLine + "\n" + bottomLine;
  }

  /**
   * Main method for command-line execution.
   * <p>
   * Provides CLI entry point to replace the original get-progress-banner script.
   * Invoked as: java -cp hooks.jar io.github.cowwoc.cat.hooks.skills.ProgressBanner [args]
   *
   * @param args command-line arguments
   * @throws IOException if the operation fails
   */
  public static void main(String[] args) throws IOException
  {
    // Check for help flag
    for (String arg : args)
    {
      if (arg.equals("-h") || arg.equals("--help"))
      {
        System.out.println("""
          Usage: get-progress-banner [issue-id] [--phase <phase>] [--all-phases]

          Arguments:
            issue-id       Issue ID to display (positional, auto-discover if omitted)
            --phase        Phase to render (preparing|implementing|confirming|reviewing|merging)
            --all-phases   Generate all phase banners (default)
            --project-dir  Project directory for auto-discovery
            --session-id   Session ID for issue locking

          Examples:
            get-progress-banner 2.1-migrate-progress-banners
            get-progress-banner 2.1-migrate-progress-banners --phase preparing
            get-progress-banner --project-dir /workspace --session-id abc123""");
        return;
      }
    }

    try (JvmScope scope = new MainJvmScope())
    {
      ProgressBanner banner = new ProgressBanner(scope);
      String output = banner.getOutput(args);
      System.out.println(output);
    }
  }
}
