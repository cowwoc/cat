/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;
import io.github.cowwoc.cat.claude.hook.session.InjectSkillListing;
import io.github.cowwoc.cat.claude.hook.session.SessionStartHandler;
import org.testng.annotations.Test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for InjectSkillListing.
 */
public final class InjectSkillListingTest
{
  /**
   * Verifies that handle returns an empty result when the source is not "compact".
   */
  @Test
  public void nonCompactSourceReturnsEmpty() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"source\": \"login\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      InjectSkillListing handler = new InjectSkillListing();

      SessionStartHandler.Result result = handler.handle(scope);

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle returns the skill listing when source is "compact" and skills exist.
   */
  @Test
  public void compactSourceWithSkillsReturnsListing() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"source\": \"compact\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      // Create a skill in the user skills directory
      Path skillsDir = tempDir.resolve("skills/my-skill");
      Files.createDirectories(skillsDir);
      Files.writeString(skillsDir.resolve("SKILL.md"), """
        ---
        description: My test skill
        ---
        Skill body here.
        """);

      InjectSkillListing handler = new InjectSkillListing();

      SessionStartHandler.Result result = handler.handle(scope);

      requireThat(result.additionalContext(), "additionalContext").contains("my-skill");
      requireThat(result.additionalContext(), "additionalContext").contains("My test skill");
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that handle returns an empty result when source is "compact" but no skills are found.
   */
  @Test
  public void compactSourceWithNoSkillsReturnsEmpty() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (TestClaudeHook scope = new TestClaudeHook(
      "{\"source\": \"compact\", \"session_id\": \"test-session\"}", tempDir, tempDir, tempDir))
    {
      // No skills directory created - discovery will find nothing

      InjectSkillListing handler = new InjectSkillListing();

      SessionStartHandler.Result result = handler.handle(scope);

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
