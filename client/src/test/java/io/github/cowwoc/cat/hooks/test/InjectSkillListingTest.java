/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;
import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.session.InjectSkillListing;
import io.github.cowwoc.cat.hooks.session.SessionStartHandler;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      InjectSkillListing handler = new InjectSkillListing(scope);

      String hookJson = """
        {
          "source": "login",
          "session_id": "test-session"
        }""";
      InputStream stream = new ByteArrayInputStream(hookJson.getBytes(StandardCharsets.UTF_8));
      HookInput input = HookInput.readFrom(mapper, stream);

      SessionStartHandler.Result result = handler.handle(input);

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
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
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

      JsonMapper mapper = scope.getJsonMapper();
      InjectSkillListing handler = new InjectSkillListing(scope);

      String hookJson = """
        {
          "source": "compact",
          "session_id": "test-session"
        }""";
      InputStream stream = new ByteArrayInputStream(hookJson.getBytes(StandardCharsets.UTF_8));
      HookInput input = HookInput.readFrom(mapper, stream);

      SessionStartHandler.Result result = handler.handle(input);

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
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      // No skills directory created - discovery will find nothing

      JsonMapper mapper = scope.getJsonMapper();
      InjectSkillListing handler = new InjectSkillListing(scope);

      String hookJson = """
        {
          "source": "compact",
          "session_id": "test-session"
        }""";
      InputStream stream = new ByteArrayInputStream(hookJson.getBytes(StandardCharsets.UTF_8));
      HookInput input = HookInput.readFrom(mapper, stream);

      SessionStartHandler.Result result = handler.handle(input);

      requireThat(result.additionalContext(), "additionalContext").isEmpty();
      requireThat(result.stderr(), "stderr").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
