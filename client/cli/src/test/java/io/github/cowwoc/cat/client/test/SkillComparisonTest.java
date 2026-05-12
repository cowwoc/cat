/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.tool.ClaudeTool;
import io.github.cowwoc.cat.claude.hook.skills.SkillComparison;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link SkillComparison}.
 * <p>
 * Each test is self-contained with no shared state.
 */
public final class SkillComparisonTest
{
  private static final String SKILL_A_CONTENT = """
    ---
    description: Use when user wants to squash commits - combines multiple commits into one
    user-invocable: false
    ---
    # Git Squash (Version A)

    ## Purpose

    Squash multiple commits into a single commit using git reset.

    ## Procedure

    ### Step 1: Reset
    Run git reset --soft HEAD~N.
    """;

  private static final String SKILL_B_CONTENT = """
    ---
    description: >
      MANDATORY: Use instead of git rebase -i or git reset --soft for squashing.
      Provides unified commit messages and automatic backup.
    user-invocable: false
    ---
    # Git Squash (Version B)

    ## Purpose

    Squash multiple commits into a single commit with backup and verification.

    ## Procedure

    ### Step 1: Backup
    Create backup branch before squashing.

    ### Step 2: Squash
    Use the squash script for safe squashing with unified messages.
    """;

  /**
   * Verifies that the handler throws when fewer than 2 arguments are provided.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*requires 2 or 3 arguments.*")
  public void throwsOnOneArgument() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-compare-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      SkillComparison handler = new SkillComparison(scope);
      handler.getOutput(new String[]{"only-one"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the handler throws when more than 3 arguments are provided.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*requires 2 or 3 arguments.*")
  public void throwsOnFourArguments() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-compare-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      SkillComparison handler = new SkillComparison(scope);
      handler.getOutput(new String[]{"a", "b", "goal", "extra"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the handler throws when Skill A file does not exist.
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*Skill A file not found.*")
  public void throwsWhenSkillANotFound() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-compare-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillBFile = tempDir.resolve("skill-b.md");
      Files.writeString(skillBFile, SKILL_B_CONTENT);

      SkillComparison handler = new SkillComparison(scope);
      handler.getOutput(new String[]{
        tempDir.resolve("nonexistent-a.md").toString(),
        skillBFile.toString()
      });
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the handler throws when Skill B file does not exist.
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*Skill B file not found.*")
  public void throwsWhenSkillBNotFound() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-compare-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillAFile = tempDir.resolve("skill-a.md");
      Files.writeString(skillAFile, SKILL_A_CONTENT);

      SkillComparison handler = new SkillComparison(scope);
      handler.getOutput(new String[]{
        skillAFile.toString(),
        tempDir.resolve("nonexistent-b.md").toString()
      });
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the handler produces a comparison prompt with both skill contents.
   */
  @Test
  public void producesComparisonPromptWithBothSkills() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-compare-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillAFile = tempDir.resolve("skill-a.md");
      Path skillBFile = tempDir.resolve("skill-b.md");
      Files.writeString(skillAFile, SKILL_A_CONTENT);
      Files.writeString(skillBFile, SKILL_B_CONTENT);

      SkillComparison handler = new SkillComparison(scope);
      String output = handler.getOutput(new String[]{skillAFile.toString(), skillBFile.toString()});

      requireThat(output, "output").isNotNull();
      requireThat(output, "output").contains("SKILL COMPARISON REQUEST");
      requireThat(output, "output").contains("SKILL A");
      requireThat(output, "output").contains("SKILL B");
      requireThat(output, "output").contains("Git Squash (Version A)");
      requireThat(output, "output").contains("Git Squash (Version B)");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an explicitly provided goal is included in the output.
   */
  @Test
  public void usesExplicitGoalWhenProvided() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-compare-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillAFile = tempDir.resolve("skill-a.md");
      Path skillBFile = tempDir.resolve("skill-b.md");
      Files.writeString(skillAFile, SKILL_A_CONTENT);
      Files.writeString(skillBFile, SKILL_B_CONTENT);

      String customGoal = "Combine N commits safely without losing history";
      SkillComparison handler = new SkillComparison(scope);
      String output = handler.getOutput(new String[]{
        skillAFile.toString(),
        skillBFile.toString(),
        customGoal
      });

      requireThat(output, "output").contains(customGoal);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the goal is extracted from Skill A's Purpose section when not provided.
   */
  @Test
  public void extractsGoalFromPurposeSectionWhenNotProvided() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-compare-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillAFile = tempDir.resolve("skill-a.md");
      Path skillBFile = tempDir.resolve("skill-b.md");
      Files.writeString(skillAFile, SKILL_A_CONTENT);
      Files.writeString(skillBFile, SKILL_B_CONTENT);

      SkillComparison handler = new SkillComparison(scope);
      String output = handler.getOutput(new String[]{skillAFile.toString(), skillBFile.toString()});

      // Goal extracted from Skill A's Purpose section
      requireThat(output, "output").contains("Squash multiple commits into a single commit");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that extractGoal returns fallback message when no Purpose section exists.
   */
  @Test
  public void extractGoalReturnsFallbackWhenNoPurposeSection() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-compare-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      SkillComparison handler = new SkillComparison(scope);
      String content = "---\ndescription: Some skill\n---\n# Skill\n\nNo purpose section here.";
      String goal = handler.extractGoal(content, "test.md");

      requireThat(goal, "goal").contains("goal not specified");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that extractGoal extracts text from ## Purpose section.
   */
  @Test
  public void extractGoalFromPurposeSection() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-compare-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      SkillComparison handler = new SkillComparison(scope);
      String content = """
        ---
        description: Some skill
        ---
        # Skill

        ## Purpose

        Produce correct output for all test cases.

        ## Procedure

        Steps here.
        """;
      String goal = handler.extractGoal(content, "test.md");

      requireThat(goal, "goal").isEqualTo("Produce correct output for all test cases.");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the comparison prompt includes the rubric criteria.
   */
  @Test
  public void promptIncludesRubricCriteria() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-compare-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillAFile = tempDir.resolve("skill-a.md");
      Path skillBFile = tempDir.resolve("skill-b.md");
      Files.writeString(skillAFile, SKILL_A_CONTENT);
      Files.writeString(skillBFile, SKILL_B_CONTENT);

      SkillComparison handler = new SkillComparison(scope);
      String output = handler.getOutput(new String[]{skillAFile.toString(), skillBFile.toString()});

      requireThat(output, "output").contains("Trigger precision");
      requireThat(output, "output").contains("Instruction clarity");
      requireThat(output, "output").contains("Priming safety");
      requireThat(output, "output").contains("Encapsulation");
      requireThat(output, "output").contains("Completeness");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
