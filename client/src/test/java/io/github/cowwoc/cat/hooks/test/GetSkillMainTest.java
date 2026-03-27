/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.ClaudeTool;
import io.github.cowwoc.cat.hooks.util.GetSkill;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link GetSkill#run(io.github.cowwoc.cat.hooks.ClaudeTool, String[], PrintStream)} CLI entry
 * point.
 */
public class GetSkillMainTest
{
  /**
   * Verifies that run() throws NullPointerException for null args.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*args.*")
  public void nullArgsThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-skill-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      GetSkill.run(scope, null,
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() throws NullPointerException for null output stream.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*out.*")
  public void nullOutThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-skill-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      GetSkill.run(scope, new String[]{"some-skill"}, null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() throws ArrayIndexOutOfBoundsException for empty args.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = ArrayIndexOutOfBoundsException.class)
  public void emptyArgsThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-skill-main-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      GetSkill.run(scope, new String[]{}, out);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() loads a skill and produces output for a valid skill name with a
   * minimal skill directory structure.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void validSkillProducesOutput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-skill-main-test-");
    try
    {
      // Create a minimal plugin/skills/test-skill/ directory structure
      Path pluginRoot = tempDir.resolve("plugin-root");
      Path skillDir = pluginRoot.resolve("skills").resolve("test-skill");
      Files.createDirectories(skillDir);
      Files.writeString(skillDir.resolve("SKILL.md"), """
        ---
        description: A test skill
        ---
        Test skill content.
        """);
      Files.writeString(skillDir.resolve("first-use.md"), "First use instructions for test skill.");

      // Create a session directory with a skills-loaded subdirectory
      Path configDir = tempDir.resolve("config");
      Files.createDirectories(configDir);

      try (TestClaudeTool scope = new TestClaudeTool(tempDir, pluginRoot))
      {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

        GetSkill.run(scope, new String[]{"cat:test-skill", "00000000-0000-0000-0000-000000000001"}, out);

        String output = buffer.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").isNotBlank();
        requireThat(output, "output").contains("First use instructions");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
