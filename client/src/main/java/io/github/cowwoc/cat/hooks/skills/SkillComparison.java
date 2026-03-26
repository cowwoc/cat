/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import io.github.cowwoc.cat.hooks.ClaudeTool;
import io.github.cowwoc.cat.hooks.util.SkillOutput;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Prepares a skill comparison prompt by reading two skill files and formatting a rubric-based
 * comparison request for the skill-comparison-agent subagent.
 * <p>
 * Accepts two or three arguments:
 * <ol>
 *   <li>Path to the first skill file (Skill A — typically the current version)</li>
 *   <li>Path to the second skill file (Skill B — typically the proposed revision)</li>
 *   <li>Goal statement (optional — if omitted, extracted from the first skill's purpose section)</li>
 * </ol>
 * Outputs a formatted comparison request for the agent to score and evaluate.
 */
public final class SkillComparison implements SkillOutput
{
  /**
   * Creates a SkillComparison instance.
   *
   * @param scope the ClaudeTool for accessing shared services
   * @throws NullPointerException if {@code scope} is null
   */
  public SkillComparison(ClaudeTool scope)
  {
    requireThat(scope, "scope").isNotNull();
  }

  /**
   * Generates a formatted skill comparison prompt.
   * <p>
   * Reads both skill files and produces a structured comparison request for the
   * skill-comparison-agent to score against the default rubric.
   *
   * @param args two or three arguments: [skill-a-path, skill-b-path] or
   *             [skill-a-path, skill-b-path, goal]
   * @return a formatted comparison prompt
   * @throws NullPointerException     if {@code args} is null
   * @throws IllegalArgumentException if fewer than 2 arguments are provided
   * @throws IOException              if either skill file cannot be read
   */
  @Override
  public String getOutput(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length < 2 || args.length > 3)
      throw new IllegalArgumentException(
        "SkillComparison requires 2 or 3 arguments: [skill-a-path, skill-b-path] or " +
        "[skill-a-path, skill-b-path, goal]. Got " + args.length + " argument(s).");

    String skillAPath = args[0];
    String skillBPath = args[1];
    String goal;
    if (args.length == 3)
      goal = args[2];
    else
      goal = null;

    // Read both skill files
    Path skillAFile = Path.of(skillAPath);
    if (!Files.exists(skillAFile))
      throw new IOException("Skill A file not found: " + skillAPath +
        ". Provide an absolute or relative path to the skill file.");

    Path skillBFile = Path.of(skillBPath);
    if (!Files.exists(skillBFile))
      throw new IOException("Skill B file not found: " + skillBPath +
        ". Provide an absolute or relative path to the skill file.");

    String skillAContent = Files.readString(skillAFile);
    String skillBContent = Files.readString(skillBFile);

    // Extract goal from Skill A if not provided
    if (goal == null || goal.isBlank())
      goal = extractGoal(skillAContent, skillAPath);

    return formatComparisonPrompt(skillAContent, skillBContent, goal, skillAPath, skillBPath);
  }

  /**
   * Extracts the goal from a skill's Purpose section.
   * <p>
   * Looks for a "## Purpose" heading and returns the text following it up to the next heading
   * or horizontal rule. Falls back to "(goal not specified)" if no Purpose section is found.
   *
   * @param content   the full skill file content
   * @param skillPath the file path (for error messages)
   * @return the extracted goal text, or a fallback message
   */
  public String extractGoal(String content, String skillPath)
  {
    int purposeIndex = content.indexOf("## Purpose");
    if (purposeIndex < 0)
    {
      // Try "# Purpose" as fallback
      purposeIndex = content.indexOf("# Purpose");
    }
    if (purposeIndex < 0)
      return "(goal not specified — add a ## Purpose section to " + skillPath + ")";

    // Extract text after the heading line
    int afterHeading = content.indexOf('\n', purposeIndex);
    if (afterHeading < 0)
      return "(goal not specified)";

    String afterContent = content.substring(afterHeading + 1).strip();

    // Take content up to the next heading (##) or horizontal rule (---)
    int nextSection = -1;
    int headingIndex = afterContent.indexOf("\n##");
    int ruleIndex = afterContent.indexOf("\n---");

    if (headingIndex >= 0)
      nextSection = headingIndex;
    if (ruleIndex >= 0 && (nextSection < 0 || ruleIndex < nextSection))
      nextSection = ruleIndex;

    String goalText;
    if (nextSection >= 0)
      goalText = afterContent.substring(0, nextSection).strip();
    else
      goalText = afterContent.strip();

    // Collapse whitespace
    return goalText.replaceAll("\\s+", " ").strip();
  }

  /**
   * Formats the comparison prompt from the two skill contents and goal.
   *
   * @param skillAContent the full content of Skill A
   * @param skillBContent the full content of Skill B
   * @param goal          the skill's goal statement
   * @param skillAPath    the path to Skill A (for labeling)
   * @param skillBPath    the path to Skill B (for labeling)
   * @return the formatted comparison prompt
   */
  private String formatComparisonPrompt(String skillAContent, String skillBContent, String goal,
    String skillAPath, String skillBPath)
  {
    return """
      SKILL COMPARISON REQUEST
      ========================

      Goal: %s

      Score both skill versions against the default rubric:
        - Trigger precision (0-10): Description routes correctly
        - Instruction clarity (0-10): Steps are unambiguous
        - Priming safety (0-10): Skill does not teach bypasses
        - Encapsulation (0-10): Orchestrator knows what, not how
        - Completeness (0-10): All necessary steps present

      Score each criterion independently for each skill version.
      Then determine the winner (higher total) and extract strengths/weaknesses.

      --- SKILL A (%s) ---

      %s

      --- SKILL B (%s) ---

      %s

      Produce a SKILL COMPARISON REPORT with rubric scores, winner, evidence, and recommendation.
      """.formatted(goal, skillAPath, skillAContent, skillBPath, skillBContent);
  }
}
