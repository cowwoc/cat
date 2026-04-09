/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.JvmScope;
import io.github.cowwoc.cat.claude.hook.util.VerifyDeferPlanGeneration;
import org.testng.annotations.Test;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Integration tests for {@link VerifyDeferPlanGeneration}.
 */
public final class VerifyDeferPlanGenerationTest
{
  /**
   * Creates the add-agent skill file with the given content.
   *
   * @param projectRoot the project root directory
   * @param content     the file content
   * @throws IOException if the file cannot be written
   */
  private static void writeAddSkill(Path projectRoot, String content) throws IOException
  {
    Path file = projectRoot.resolve("plugin/skills/add-agent/first-use.md");
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  /**
   * Creates the work-implement-agent skill file with the given content.
   *
   * @param projectRoot the project root directory
   * @param content     the file content
   * @throws IOException if the file cannot be written
   */
  private static void writeWorkImplementSkill(Path projectRoot, String content) throws IOException
  {
    Path file = projectRoot.resolve("plugin/skills/work-implement-agent/first-use.md");
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  /**
   * Filters output lines to only PASS/FAIL result lines.
   *
   * @param output the full output string
   * @return the list of PASS/FAIL lines
   */
  private static List<String> filterResults(String output)
  {
    return output.lines().
      filter(line -> line.startsWith("PASS:") || line.startsWith("FAIL:")).
      toList();
  }

  /**
   * Verifies that all 4 checks pass when both skill files contain the expected content.
   */
  @Test
  public void allChecksPass() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-defer-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      writeAddSkill(tempDir, "Some content with planTempFile=$(mktemp but no plan builder ref");
      writeWorkImplementSkill(tempDir, "Content with hasSteps and cat:plan-builder-agent invocation");

      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, false, UTF_8);
      int exitCode = VerifyDeferPlanGeneration.run(scope, new String[]{tempDir.toString()}, out);
      String output = buffer.toString(UTF_8);

      requireThat(exitCode, "exitCode").isEqualTo(0);
      requireThat(output, "output").contains("0 failed");

      List<String> results = filterResults(output);
      requireThat(results.size(), "resultCount").isEqualTo(4);

      for (String result : results)
        requireThat(result, "result").startsWith("PASS:");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that check 1 fails when add-agent/first-use.md contains cat:plan-builder-agent.
   */
  @Test
  public void check1FailsWhenPlanBuilderPresent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-defer-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      writeAddSkill(tempDir,
        "Content with planTempFile=$(mktemp and cat:plan-builder-agent present");
      writeWorkImplementSkill(tempDir, "Content with hasSteps and cat:plan-builder-agent invocation");

      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, false, UTF_8);
      int exitCode = VerifyDeferPlanGeneration.run(scope, new String[]{tempDir.toString()}, out);
      String output = buffer.toString(UTF_8);

      requireThat(exitCode, "exitCode").isEqualTo(1);

      List<String> results = filterResults(output);
      requireThat(results.size(), "resultCount").isEqualTo(4);
      requireThat(results.get(0), "check1").startsWith("FAIL:");
      requireThat(results.get(1), "check2").startsWith("PASS:");
      requireThat(results.get(2), "check3").startsWith("PASS:");
      requireThat(results.get(3), "check4").startsWith("PASS:");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that check 2 fails when add-agent/first-use.md omits planTempFile=$(mktemp.
   */
  @Test
  public void check2FailsWhenLightweightPlanMissing() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-defer-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      writeAddSkill(tempDir, "Content without the lightweight plan block");
      writeWorkImplementSkill(tempDir, "Content with hasSteps and cat:plan-builder-agent invocation");

      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, false, UTF_8);
      int exitCode = VerifyDeferPlanGeneration.run(scope, new String[]{tempDir.toString()}, out);
      String output = buffer.toString(UTF_8);

      requireThat(exitCode, "exitCode").isEqualTo(1);

      List<String> results = filterResults(output);
      requireThat(results.size(), "resultCount").isEqualTo(4);
      requireThat(results.get(0), "check1").startsWith("PASS:");
      requireThat(results.get(1), "check2").startsWith("FAIL:");
      requireThat(results.get(2), "check3").startsWith("PASS:");
      requireThat(results.get(3), "check4").startsWith("PASS:");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that check 3 fails when work-implement-agent/first-use.md omits hasSteps.
   */
  @Test
  public void check3FailsWhenHasStepsMissing() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-defer-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      writeAddSkill(tempDir, "Content with planTempFile=$(mktemp but no plan builder ref");
      writeWorkImplementSkill(tempDir, "Content with cat:plan-builder-agent but no steps check");

      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, false, UTF_8);
      int exitCode = VerifyDeferPlanGeneration.run(scope, new String[]{tempDir.toString()}, out);
      String output = buffer.toString(UTF_8);

      requireThat(exitCode, "exitCode").isEqualTo(1);

      List<String> results = filterResults(output);
      requireThat(results.size(), "resultCount").isEqualTo(4);
      requireThat(results.get(0), "check1").startsWith("PASS:");
      requireThat(results.get(1), "check2").startsWith("PASS:");
      requireThat(results.get(2), "check3").startsWith("FAIL:");
      requireThat(results.get(3), "check4").startsWith("PASS:");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that check 4 fails when work-implement-agent/first-use.md omits cat:plan-builder-agent.
   */
  @Test
  public void check4FailsWhenPlanBuilderMissing() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-defer-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      writeAddSkill(tempDir, "Content with planTempFile=$(mktemp but no plan builder ref");
      writeWorkImplementSkill(tempDir, "Content with hasSteps but no plan builder agent");

      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, false, UTF_8);
      int exitCode = VerifyDeferPlanGeneration.run(scope, new String[]{tempDir.toString()}, out);
      String output = buffer.toString(UTF_8);

      requireThat(exitCode, "exitCode").isEqualTo(1);

      List<String> results = filterResults(output);
      requireThat(results.size(), "resultCount").isEqualTo(4);
      requireThat(results.get(0), "check1").startsWith("PASS:");
      requireThat(results.get(1), "check2").startsWith("PASS:");
      requireThat(results.get(2), "check3").startsWith("PASS:");
      requireThat(results.get(3), "check4").startsWith("FAIL:");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that checks 1 and 2 fail with "File not found" when add-agent/first-use.md is missing.
   */
  @Test
  public void missingAddSkillFile() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-defer-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      writeWorkImplementSkill(tempDir, "Content with hasSteps and cat:plan-builder-agent invocation");

      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, false, UTF_8);
      int exitCode = VerifyDeferPlanGeneration.run(scope, new String[]{tempDir.toString()}, out);
      String output = buffer.toString(UTF_8);

      requireThat(exitCode, "exitCode").isEqualTo(1);
      requireThat(output, "output").contains("File not found");

      List<String> results = filterResults(output);
      requireThat(results.size(), "resultCount").isEqualTo(4);
      requireThat(results.get(0), "check1").startsWith("FAIL:");
      requireThat(results.get(1), "check2").startsWith("FAIL:");
      requireThat(results.get(2), "check3").startsWith("PASS:");
      requireThat(results.get(3), "check4").startsWith("PASS:");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that checks 3 and 4 fail with "File not found" when work-implement-agent/first-use.md is
   * missing.
   */
  @Test
  public void missingWorkImplementSkillFile() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-defer-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      writeAddSkill(tempDir, "Content with planTempFile=$(mktemp but no plan builder ref");

      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, false, UTF_8);
      int exitCode = VerifyDeferPlanGeneration.run(scope, new String[]{tempDir.toString()}, out);
      String output = buffer.toString(UTF_8);

      requireThat(exitCode, "exitCode").isEqualTo(1);
      requireThat(output, "output").contains("File not found");

      List<String> results = filterResults(output);
      requireThat(results.size(), "resultCount").isEqualTo(4);
      requireThat(results.get(0), "check1").startsWith("PASS:");
      requireThat(results.get(1), "check2").startsWith("PASS:");
      requireThat(results.get(2), "check3").startsWith("FAIL:");
      requireThat(results.get(3), "check4").startsWith("FAIL:");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that all 4 checks fail when both skill files are missing.
   */
  @Test
  public void bothSkillFilesMissing() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-defer-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, false, UTF_8);
      int exitCode = VerifyDeferPlanGeneration.run(scope, new String[]{tempDir.toString()}, out);
      String output = buffer.toString(UTF_8);

      requireThat(exitCode, "exitCode").isEqualTo(1);
      requireThat(output, "output").contains("4 failed");
      requireThat(output, "output").contains("File not found");

      List<String> results = filterResults(output);
      requireThat(results.size(), "resultCount").isEqualTo(4);

      for (String result : results)
        requireThat(result, "result").startsWith("FAIL:");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() falls back to scope.getProjectPath() when no args are provided.
   */
  @Test
  public void fallbackToScopeProjectPath() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-defer-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      writeAddSkill(tempDir, "Some content with planTempFile=$(mktemp but no plan builder ref");
      writeWorkImplementSkill(tempDir, "Content with hasSteps and cat:plan-builder-agent invocation");

      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, false, UTF_8);
      int exitCode = VerifyDeferPlanGeneration.run(scope, new String[]{}, out);
      String output = buffer.toString(UTF_8);

      requireThat(exitCode, "exitCode").isEqualTo(0);
      requireThat(output, "output").contains("0 failed");

      long passCount = output.lines().filter(line -> line.startsWith("PASS:")).count();
      requireThat(passCount, "passCount").isEqualTo(4L);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
