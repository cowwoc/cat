/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.ClaudeTool;
import io.github.cowwoc.cat.hooks.skills.SkillValidator;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link SkillValidator}.
 * <p>
 * Each test is self-contained with no shared state.
 */
public final class SkillValidatorTest
{
  /**
   * The minimal valid SKILL.md content for testing.
   */
  private static final String MINIMAL_SKILL_MD = """
    ---
    description: Use when user wants to squash commits - combines multiple commits into one
    user-invocable: false
    ---
    # Git Squash

    ## Purpose

    Squash multiple commits into a single commit.
    """;

  /**
   * Verifies that the handler throws when no arguments are provided.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*requires 2 arguments.*")
  public void throwsOnNoArguments() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-validator-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      SkillValidator handler = new SkillValidator(scope);
      handler.getOutput(new String[]{});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the handler throws when only one argument is provided.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*requires 2 arguments.*")
  public void throwsOnOneArgument() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-validator-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      SkillValidator handler = new SkillValidator(scope);
      handler.getOutput(new String[]{"only-one"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the handler throws when the skill file does not exist.
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*Skill file not found.*")
  public void throwsWhenSkillFileNotFound() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-validator-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      SkillValidator handler = new SkillValidator(scope);
      handler.getOutput(new String[]{
        tempDir.resolve("nonexistent-SKILL.md").toString(),
        "{\"should_trigger\":[],\"should_not_trigger\":[]}"
      });
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the handler produces a validation prompt containing both prompt lists.
   */
  @Test
  public void producesValidationPromptWithBothLists() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-validator-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillFile = tempDir.resolve("SKILL.md");
      Files.writeString(skillFile, MINIMAL_SKILL_MD);

      String testPromptsJson = """
        {
          "should_trigger": ["squash my last 3 commits", "combine commits into one"],
          "should_not_trigger": ["rebase onto main", "check git log"]
        }
        """;

      SkillValidator handler = new SkillValidator(scope);
      String output = handler.getOutput(new String[]{skillFile.toString(), testPromptsJson});

      requireThat(output, "output").isNotNull();
      requireThat(output, "output").contains("squash my last 3 commits");
      requireThat(output, "output").contains("combine commits into one");
      requireThat(output, "output").contains("rebase onto main");
      requireThat(output, "output").contains("check git log");
      requireThat(output, "output").contains("Should-Trigger");
      requireThat(output, "output").contains("Should-Not-Trigger");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the description is extracted from the SKILL.md frontmatter.
   */
  @Test
  public void extractsDescriptionFromFrontmatter() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-validator-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillFile = tempDir.resolve("SKILL.md");
      Files.writeString(skillFile, MINIMAL_SKILL_MD);

      String testPromptsJson = "{\"should_trigger\":[],\"should_not_trigger\":[]}";
      SkillValidator handler = new SkillValidator(scope);
      String output = handler.getOutput(new String[]{skillFile.toString(), testPromptsJson});

      requireThat(output, "output").contains("Use when user wants to squash commits");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies extractDescription on a skill with block scalar description format.
   */
  @Test
  public void extractsBlockScalarDescription() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-validator-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      SkillValidator handler = new SkillValidator(scope);
      String content = """
        ---
        description: >
          Use when user says work on issue.
          Handles resuming paused work.
        model: sonnet
        ---
        # Work
        """;
      String description = handler.extractDescription(content, "test-SKILL.md");

      requireThat(description, "description").contains("Use when user says work on issue");
      requireThat(description, "description").contains("Handles resuming paused work");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that extractJsonArray parses should_trigger correctly.
   */
  @Test
  public void parsesJsonArrayCorrectly() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-validator-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      SkillValidator handler = new SkillValidator(scope);
      String json = """
        {
          "should_trigger": ["squash commits", "combine into one"],
          "should_not_trigger": ["push to remote"]
        }
        """;

      List<String> triggers = handler.extractJsonArray(json, "should_trigger");
      List<String> nonTriggers = handler.extractJsonArray(json, "should_not_trigger");

      requireThat(triggers.size(), "triggers.size()").isEqualTo(2);
      requireThat(triggers.get(0), "triggers[0]").isEqualTo("squash commits");
      requireThat(triggers.get(1), "triggers[1]").isEqualTo("combine into one");
      requireThat(nonTriggers.size(), "nonTriggers.size()").isEqualTo(1);
      requireThat(nonTriggers.get(0), "nonTriggers[0]").isEqualTo("push to remote");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that missing frontmatter throws a descriptive error.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*No YAML frontmatter.*")
  public void throwsOnMissingFrontmatter() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-validator-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      SkillValidator handler = new SkillValidator(scope);
      handler.extractDescription("# No frontmatter here\n\nJust content.", "test.md");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that missing description field throws a descriptive error.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*No 'description:' field.*")
  public void throwsOnMissingDescriptionField() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-validator-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      SkillValidator handler = new SkillValidator(scope);
      handler.extractDescription("---\nmodel: sonnet\n---\n# No description field", "test.md");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
