/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.DescriptionTester;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link DescriptionTester}.
 * <p>
 * Each test is self-contained with no shared state.
 */
public final class DescriptionTesterTest
{
  /**
   * The minimal valid SKILL.md content for testing.
   */
  private static final String MINIMAL_SKILL_MD = """
    ---
    description: Use when user wants to record a mistake - documents failures with root cause analysis
    user-invocable: false
    ---
    # Learn

    ## Purpose

    Record mistakes and perform root cause analysis.
    """;

  /**
   * Verifies that the handler throws when no arguments are provided.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*requires 1 argument.*")
  public void throwsOnNoArguments() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-desc-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DescriptionTester handler = new DescriptionTester(scope);
      handler.getOutput(new String[]{});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the handler throws when too many arguments are provided.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*requires 1 argument.*")
  public void throwsOnTooManyArguments() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-desc-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DescriptionTester handler = new DescriptionTester(scope);
      handler.getOutput(new String[]{"arg1", "arg2"});
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
    Path tempDir = Files.createTempDirectory("test-desc-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DescriptionTester handler = new DescriptionTester(scope);
      handler.getOutput(new String[]{tempDir.resolve("nonexistent.md").toString()});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the handler produces a calibration prompt containing the description.
   */
  @Test
  public void producesCalibrationPromptWithDescription() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-desc-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path skillFile = tempDir.resolve("SKILL.md");
      Files.writeString(skillFile, MINIMAL_SKILL_MD);

      DescriptionTester handler = new DescriptionTester(scope);
      String output = handler.getOutput(new String[]{skillFile.toString()});

      requireThat(output, "output").isNotNull();
      requireThat(output, "output").contains("Description under test");
      requireThat(output, "output").contains("Use when user wants to record a mistake");
      requireThat(output, "output").contains("Core triggers");
      requireThat(output, "output").contains("Adjacent non-triggers");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that extractDescription handles single-line description format.
   */
  @Test
  public void extractsSingleLineDescription() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-desc-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DescriptionTester handler = new DescriptionTester(scope);
      String content = """
        ---
        description: Use when user asks for help - shows available commands
        user-invocable: true
        ---
        # Help
        """;

      String description = handler.extractDescription(content, "SKILL.md");
      requireThat(description, "description").
        isEqualTo("Use when user asks for help - shows available commands");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that extractDescription handles block scalar (>) description format.
   */
  @Test
  public void extractsBlockScalarDescription() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-desc-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DescriptionTester handler = new DescriptionTester(scope);
      String content = """
        ---
        description: >
          Use when user says record mistake or document failure.
          Trigger words: "record this mistake", "learn from this".
        model: sonnet
        ---
        # Learn
        """;

      String description = handler.extractDescription(content, "SKILL.md");
      requireThat(description, "description").contains("Use when user says record mistake");
      requireThat(description, "description").contains("Trigger words");
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
    Path tempDir = Files.createTempDirectory("test-desc-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DescriptionTester handler = new DescriptionTester(scope);
      handler.extractDescription("# No frontmatter\nJust content.", "test.md");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a missing description field throws a descriptive error.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*No 'description:' field.*")
  public void throwsOnMissingDescriptionField() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-desc-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      DescriptionTester handler = new DescriptionTester(scope);
      handler.extractDescription("---\nmodel: haiku\nuser-invocable: true\n---\n# Skill", "test.md");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the calibration prompt mentions all four query categories.
   */
  @Test
  public void promptMentionsAllFourQueryCategories() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-desc-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path skillFile = tempDir.resolve("SKILL.md");
      Files.writeString(skillFile, MINIMAL_SKILL_MD);

      DescriptionTester handler = new DescriptionTester(scope);
      String output = handler.getOutput(new String[]{skillFile.toString()});

      requireThat(output, "output").contains("Core triggers");
      requireThat(output, "output").contains("Synonym triggers");
      requireThat(output, "output").contains("Boundary cases");
      requireThat(output, "output").contains("Adjacent non-triggers");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
