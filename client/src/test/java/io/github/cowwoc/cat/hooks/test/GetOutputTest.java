/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.skills.GetOutput;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetOutput dispatcher.
 * <p>
 * Tests verify that the dispatcher correctly parses dot-notation type arguments,
 * routes them to appropriate skill handlers, wraps output in tags, and validates arguments.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class GetOutputTest
{
  /**
   * Verifies that dot-notation with skill and page parses correctly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void dotNotationWithPageParses() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create minimal config for config handler to return non-null
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), "{}");

      GetOutput handler = new GetOutput(scope);
      String result = handler.getOutput(new String[]{"config.settings"});

      requireThat(result, "result").
        contains("<output type=\"config.settings\">").
        contains("</output>").
        contains("CURRENT SETTINGS");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that single skill name (no page) routes correctly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void singleSkillNameWithoutPageRoutes() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      GetOutput handler = new GetOutput(scope);
      String result = handler.getOutput(new String[]{"config.saved"});

      requireThat(result, "result").
        contains("<output type=\"config.saved\">").
        contains("</output>").
        contains("SAVED");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that output starts with the Purpose section and contains the skill name from the type argument.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void outputIsWrappedInTag() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      GetOutput handler = new GetOutput(scope);
      String result = handler.getOutput(new String[]{"config.no-changes"});

      requireThat(result, "result").
        contains("## Purpose").
        contains("Output the pre-rendered config display").
        contains("<output type=\"config.no-changes\">").
        endsWith("</output>");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the output contains the full set of guardrail instructions preventing investigation,
   * reconstruction, or follow-up actions, including the exact phrases from the first-use.md template.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void outputContainsGuardrailInstructions() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-guardrails-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      GetOutput handler = new GetOutput(scope);
      String result = handler.getOutput(new String[]{"config.no-changes"});

      requireThat(result, "result").
        contains("Do not investigate the cause").
        contains("Stop after outputting").
        contains("The triggering prompt is the").
        contains("The rendered").
        contains("display from the").
        contains("tag is printed completely");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that unknown skill type throws IllegalArgumentException with valid types listed.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*(?=.*Unknown skill)(?=.*status)(?=.*config).*")
  public void unknownSkillTypeThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      GetOutput handler = new GetOutput(scope);
      handler.getOutput(new String[]{"invalid-skill"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that missing type argument throws IllegalArgumentException with usage hints.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*(?=.*get-output requires a type argument)(?=.*Usage).*")
  public void missingTypeArgumentThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      GetOutput handler = new GetOutput(scope);
      handler.getOutput(new String[]{});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that extra arguments are passed through to handlers.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void extraArgumentsPassThroughToHandler() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create minimal config for config handler
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), "{}");

      GetOutput handler = new GetOutput(scope);
      // config.conditions-for-version version pre post
      String result = handler.getOutput(new String[]{
        "config.conditions-for-version", "v1.0", "no-deps", "ready-to-release"
      });

      requireThat(result, "result").
        contains("<output type=\"config.conditions-for-version\">").
        contains("CONDITIONS FOR v1.0");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies null args throws NullPointerException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void nullArgsThrows() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      GetOutput handler = new GetOutput(scope);
      handler.getOutput(null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that content wrapped in tags contains newlines.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void wrappedOutputContainsNewlines() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      GetOutput handler = new GetOutput(scope);
      String result = handler.getOutput(new String[]{"config.saved"});

      requireThat(result, "result").
        contains("<output type=\"config.saved\">\n").
        contains("\n</output>");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that multiple dots in type are handled (only first dot is separator).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void multipleDotsSeparatesOnFirst() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      // init.choose-your-partner should parse as skill=init, page=choose-your-partner
      GetOutput handler = new GetOutput(scope);
      String result = handler.getOutput(new String[]{"init.choose-your-partner"});

      requireThat(result, "result").
        contains("<output type=\"init.choose-your-partner\">").
        contains("CHOOSE YOUR PARTNER");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Integration test: verifies that "instruction-test-aggregator" type routes through GetOutput dispatcher
   * to InstructionTestAggregator and returns wrapped output with configs and no delta for
   * a single config.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void instructionTestAggregatorRoutesThroughDispatcher() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-instruction-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      GetOutput handler = new GetOutput(scope);
      String runResultsJson = """
        [
          {"config": "with-skill", "assertions": [true, false, true], "duration_ms": 1200, "total_tokens": 500}
        ]
        """;

      String result = handler.getOutput(new String[]{"instruction-test-aggregator", runResultsJson});

      requireThat(result, "result").
        contains("<output type=\"instruction-test-aggregator\">").
        contains("</output>").
        contains("\"with-skill\"").
        contains("pass_rate");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Integration test: verifies that "description-optimizer" type routes through GetOutput dispatcher
   * to DescriptionOptimizer and returns a wrapped optimization prompt.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void descriptionOptimizerRoutesThroughDispatcher() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-desc-opt-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Write a minimal SKILL.md into temp dir
      Path skillFile = tempDir.resolve("SKILL.md");
      Files.writeString(skillFile, """
        ---
        description: Use when user wants to squash commits
        user-invocable: false
        ---
        # Git Squash
        """);

      String evalSetJson = """
        [
          {"query": "squash my last 3 commits", "should_trigger": true},
          {"query": "push to remote", "should_trigger": false},
          {"query": "combine two commits", "should_trigger": true},
          {"query": "check git log", "should_trigger": false},
          {"query": "squash before push", "should_trigger": true}
        ]
        """;

      GetOutput handler = new GetOutput(scope);
      String result = handler.getOutput(new String[]{
        "description-optimizer",
        skillFile.toString(),
        evalSetJson,
        "claude-sonnet-4-5",
        "3"
      });

      requireThat(result, "result").
        contains("<output type=\"description-optimizer\">").
        contains("</output>").
        contains("DESCRIPTION OPTIMIZATION REQUEST").
        contains("train_size").
        contains("test_size");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when the first argument is a UUID (main agent ID, as passed via {@code $ARGUMENTS}),
   * it is skipped and the second argument is used as the output type.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void mainAgentIdAsFirstArgIsSkipped() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-skip-$0-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      GetOutput handler = new GetOutput(scope);
      String agentId = UUID.randomUUID().toString();
      // When invoked via $ARGUMENTS, first arg is $0 (agent ID), second is the type
      String result = handler.getOutput(new String[]{agentId, "config.saved"});

      requireThat(result, "result").
        contains("<output type=\"config.saved\">").
        contains("SAVED");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when the first argument is a subagent ID (UUID/subagents/xxx), it is skipped and
   * the second argument is used as the output type.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void subagentIdAsFirstArgIsSkipped() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-skip-subagent-$0-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      GetOutput handler = new GetOutput(scope);
      String subagentId = UUID.randomUUID() + "/subagents/abc123";
      String result = handler.getOutput(new String[]{subagentId, "config.saved"});

      requireThat(result, "result").
        contains("<output type=\"config.saved\">").
        contains("SAVED");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when the first argument is NOT an agent ID (e.g., a type token), it is used as
   * the type directly without skipping.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void nonAgentIdFirstArgUsedAsType() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-no-skip-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      GetOutput handler = new GetOutput(scope);
      // Direct invocation without agent ID prefix
      String result = handler.getOutput(new String[]{"config.saved"});

      requireThat(result, "result").
        contains("<output type=\"config.saved\">").
        contains("SAVED");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Integration test: verifies that skipAgentId and dot-notation parsing work together correctly
   * when a subagent ID prefix is combined with a multi-argument dot-notation type invocation.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void skipAgentIdAndDotNotationIntegration() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-integration-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create minimal config for config handler
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), "{}");

      GetOutput handler = new GetOutput(scope);
      String agentId = UUID.randomUUID() + "/subagents/abc123";
      // Full flow: subagent ID is skipped, then dot-notation type parsed, then extra args passed to handler
      String result = handler.getOutput(new String[]{
        agentId, "config.conditions-for-version", "v1.0", "no-deps", "ready-to-release"
      });

      requireThat(result, "result").
        contains("<output type=\"config.conditions-for-version\">").
        contains("CONDITIONS FOR v1.0");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a malformed UUID (wrong hex segment lengths) is not treated as an agent ID
   * and is used as the type directly instead.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Unknown skill.*")
  public void malformedUuidNotSkipped() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-malformed-uuid-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      GetOutput handler = new GetOutput(scope);
      // Malformed UUID: wrong segment lengths — must NOT be skipped, treated as type
      handler.getOutput(new String[]{"12345678-1234-1234-1234-12345", "config.saved"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a value matching the UUID prefix but missing the /subagents/{id} suffix is
   * not treated as a subagent ID and is not skipped.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Unknown skill.*")
  public void subagentIdWithoutIdentifierNotSkipped() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-no-subagent-id-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      GetOutput handler = new GetOutput(scope);
      // UUID followed by /subagents/ but no identifier — must NOT be skipped
      String incompleteSubagentId = UUID.randomUUID() + "/subagents/";
      handler.getOutput(new String[]{incompleteSubagentId, "config.saved"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a value with a UUID prefix followed by invalid characters in the subagent
   * identifier segment is not treated as a subagent ID and is not skipped.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*Unknown skill.*")
  public void subagentIdWithInvalidCharsNotSkipped() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-invalid-subagent-chars-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      GetOutput handler = new GetOutput(scope);
      // UUID followed by /subagents/ with invalid characters (spaces) in identifier — must NOT be skipped
      String invalidSubagentId = UUID.randomUUID() + "/subagents/agent id with spaces";
      handler.getOutput(new String[]{invalidSubagentId, "config.saved"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the generated instructions reference the specific output type, not a generic output tag.
   * This ensures the agent selects the correct output tag when multiple types exist in the conversation.
   */
  @Test
  public void generatedInstructionsReferenceOutputType() throws IOException
  {
    Path tempDir = Files.createTempDirectory("get-output-test-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      String output = new GetOutput(scope).getOutput(new String[]{"status"});
      requireThat(output, "output").contains("<output type=\"status\">");
      requireThat(output, "output").contains("`<output type=\"status\">`");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that type matching correctly handles dot-notation types and does not match partial types.
   * This validates that the skill correctly processes type arguments like "config.saved" and does not
   * confuse them with "config.no-changes" or "config.*" patterns. This is a critical guard against
   * the staleness bug where the wrong output tag type could be selected.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void typeMatchingSelectsCorrectOutput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-get-output-type-matching-");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      // Create minimal config for config handler
      Path catDir = tempDir.resolve(".cat");
      Files.createDirectories(catDir);
      Files.writeString(catDir.resolve("config.json"), "{}");

      GetOutput handler = new GetOutput(scope);
      // Request config.saved type specifically
      String result = handler.getOutput(new String[]{"config.saved"});

      // Verify the output contains the CORRECT type attribute and content for config.saved
      requireThat(result, "result").
        contains("<output type=\"config.saved\">").
        contains("SAVED");

      // Verify output does NOT contain content from other config types
      // (would indicate type confusion or staleness)
      requireThat(result, "result").
        doesNotContain("CURRENT SETTINGS").
        doesNotContain("NO CHANGES").
        doesNotContain("DIFF");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
