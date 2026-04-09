/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.skills;

import io.github.cowwoc.cat.claude.tool.ClaudeTool;
import io.github.cowwoc.cat.claude.hook.util.SkillOutput;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Prepares a description calibration prompt by reading the skill's description frontmatter and
 * formatting a calibration request for the description-tester-agent subagent.
 * <p>
 * Accepts one argument: the path to the skill's SKILL.md file.
 * <p>
 * Outputs a formatted calibration request that asks the subagent to generate test queries
 * from the description and evaluate trigger precision.
 */
public final class DescriptionTester implements SkillOutput
{
  /**
   * Creates a DescriptionTester instance.
   *
   * @param scope the ClaudeTool for accessing shared services
   * @throws NullPointerException if {@code scope} is null
   */
  public DescriptionTester(ClaudeTool scope)
  {
    requireThat(scope, "scope").isNotNull();
  }

  /**
   * Generates a formatted description calibration prompt.
   * <p>
   * Reads the skill description from the provided SKILL.md path and produces a prompt
   * requesting the description-tester-agent to generate and evaluate calibration queries.
   *
   * @param args one argument: [skill-path]
   * @return a formatted calibration prompt
   * @throws NullPointerException     if {@code args} is null
   * @throws IllegalArgumentException if the wrong number of arguments is provided
   * @throws IOException              if the skill file cannot be read
   */
  @Override
  public String getOutput(String[] args) throws IOException
  {
    requireThat(args, "args").isNotNull();
    if (args.length != 1)
      throw new IllegalArgumentException(
        "DescriptionTester requires 1 argument: [skill-path]. " +
        "Got " + args.length + " argument(s).");

    String skillPath = args[0];

    // Read skill description from frontmatter
    Path skillFile = Path.of(skillPath);
    if (!Files.exists(skillFile))
      throw new IOException("Skill file not found: " + skillPath +
        ". Provide an absolute or relative path to a SKILL.md file.");

    String skillContent = Files.readString(skillFile);
    String description = SkillFrontmatter.extractDescription(skillContent, skillPath);

    return formatCalibrationPrompt(description);
  }

  /**
   * Formats the calibration prompt from the extracted description.
   *
   * @param description the skill's description frontmatter text
   * @return the formatted calibration prompt
   */
  private String formatCalibrationPrompt(String description)
  {
    return """
      DESCRIPTION CALIBRATION REQUEST
      ===============================

      Description under test:
        %s

      Generate calibration queries in these four categories:
        1. Core triggers (2-3): Direct phrasings of the skill's primary use case
        2. Synonym triggers (1-2): Alternative phrasings that should also activate the skill
        3. Boundary cases (1-2): Edge cases near the boundary of the skill's scope
        4. Adjacent non-triggers (2-3): Phrases from adjacent domains that should NOT activate the skill

      For each query, evaluate:
        - Whether the description would route the query to this skill (YES/NO)
        - Whether that routing decision is correct given the intent (MATCH/NO-MATCH)
        - Overall verdict: CORRECT or MISCALIBRATED

      Produce a DESCRIPTION CALIBRATION REPORT with overall status and specific recommendations.
      """.formatted(description);
  }
}
