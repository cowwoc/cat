/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.skills.DescriptionOptimizer;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link DescriptionOptimizer}.
 * <p>
 * Each test is self-contained with no shared state.
 */
public final class DescriptionOptimizerTest
{
  /**
   * Minimal SKILL.md content for testing.
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
   * A valid eval set JSON with 10 entries to allow a 6/4 (60/40) split.
   */
  private static final String EVAL_SET_10 = """
    [
      {"query": "squash my last 3 commits", "should_trigger": true},
      {"query": "combine commits into one", "should_trigger": true},
      {"query": "merge two commits", "should_trigger": true},
      {"query": "collapse recent commits", "should_trigger": true},
      {"query": "squash before pushing", "should_trigger": true},
      {"query": "rebase onto main", "should_trigger": false},
      {"query": "push to remote", "should_trigger": false},
      {"query": "check git log", "should_trigger": false},
      {"query": "create a new branch", "should_trigger": false},
      {"query": "show git status", "should_trigger": false}
    ]
    """;

  /**
   * A valid eval set JSON with 5 entries for a smaller 3/2 split (60/40).
   */
  private static final String EVAL_SET_5 = """
    [
      {"query": "squash commits", "should_trigger": true},
      {"query": "combine into one commit", "should_trigger": true},
      {"query": "rebase onto main", "should_trigger": false},
      {"query": "push to remote", "should_trigger": false},
      {"query": "check git status", "should_trigger": false}
    ]
    """;

  /**
   * Verifies that the handler throws when no arguments are provided.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*requires 4 arguments.*")
  public void throwsOnNoArguments() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-desc-opt-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      DescriptionOptimizer handler = new DescriptionOptimizer(scope);
      handler.getOutput(new String[]{});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the handler throws when fewer than 4 arguments are provided.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*requires 4 arguments.*")
  public void throwsOnTooFewArguments() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-desc-opt-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      DescriptionOptimizer handler = new DescriptionOptimizer(scope);
      handler.getOutput(new String[]{"arg1", "arg2", "arg3"});
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
    Path tempDir = Files.createTempDirectory("test-desc-opt-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      DescriptionOptimizer handler = new DescriptionOptimizer(scope);
      handler.getOutput(new String[]{
        tempDir.resolve("nonexistent-SKILL.md").toString(),
        EVAL_SET_5,
        "claude-sonnet-4-5",
        "3"
      });
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the handler throws when max_iterations is not a positive integer.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*max_iterations must be a positive integer.*")
  public void throwsOnInvalidMaxIterations() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-desc-opt-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillFile = tempDir.resolve("SKILL.md");
      Files.writeString(skillFile, MINIMAL_SKILL_MD);

      DescriptionOptimizer handler = new DescriptionOptimizer(scope);
      handler.getOutput(new String[]{
        skillFile.toString(),
        EVAL_SET_5,
        "claude-sonnet-4-5",
        "0"
      });
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the handler throws when the eval set JSON is empty.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*eval_set must contain at least.*")
  public void throwsOnEmptyEvalSet() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-desc-opt-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillFile = tempDir.resolve("SKILL.md");
      Files.writeString(skillFile, MINIMAL_SKILL_MD);

      DescriptionOptimizer handler = new DescriptionOptimizer(scope);
      handler.getOutput(new String[]{
        skillFile.toString(),
        "[]",
        "claude-sonnet-4-5",
        "3"
      });
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the handler produces a valid optimization prompt for a well-formed input.
   */
  @Test
  public void producesOptimizationPromptWithTrainTestSplit() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-desc-opt-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillFile = tempDir.resolve("SKILL.md");
      Files.writeString(skillFile, MINIMAL_SKILL_MD);

      DescriptionOptimizer handler = new DescriptionOptimizer(scope);
      String output = handler.getOutput(new String[]{
        skillFile.toString(),
        EVAL_SET_10,
        "claude-sonnet-4-5",
        "5"
      });

      requireThat(output, "output").isNotNull();
      requireThat(output, "output").contains("DESCRIPTION OPTIMIZATION REQUEST");
      requireThat(output, "output").contains("train");
      requireThat(output, "output").contains("test");
      requireThat(output, "output").contains("max_iterations");
      requireThat(output, "output").contains("best_description");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the 60/40 train/test split is deterministic for the same eval set.
   * <p>
   * Calling the handler twice with identical inputs must produce the same split.
   */
  @Test
  public void splitIsDeterministic() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-desc-opt-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillFile = tempDir.resolve("SKILL.md");
      Files.writeString(skillFile, MINIMAL_SKILL_MD);

      DescriptionOptimizer handler = new DescriptionOptimizer(scope);
      String[] args = {
        skillFile.toString(),
        EVAL_SET_10,
        "claude-sonnet-4-5",
        "5"
      };

      String output1 = handler.getOutput(args);
      String output2 = handler.getOutput(args);

      requireThat(output1, "output1").isEqualTo(output2);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the train set contains 60% and the test set contains 40% of items.
   * <p>
   * With 10 eval items: train = 6, test = 4.
   * With 5 eval items: train = 3, test = 2.
   */
  @Test
  public void splitCountsAreCorrect() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-desc-opt-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillFile = tempDir.resolve("SKILL.md");
      Files.writeString(skillFile, MINIMAL_SKILL_MD);

      DescriptionOptimizer handler = new DescriptionOptimizer(scope);

      // 10-item eval set → 6 train, 4 test
      String output10 = handler.getOutput(new String[]{
        skillFile.toString(),
        EVAL_SET_10,
        "claude-sonnet-4-5",
        "3"
      });
      requireThat(output10, "output10").contains("\"train_size\" : 6");
      requireThat(output10, "output10").contains("\"test_size\" : 4");

      // 5-item eval set → 3 train, 2 test
      String output5 = handler.getOutput(new String[]{
        skillFile.toString(),
        EVAL_SET_5,
        "claude-sonnet-4-5",
        "3"
      });
      requireThat(output5, "output5").contains("\"train_size\" : 3");
      requireThat(output5, "output5").contains("\"test_size\" : 2");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the output includes the current description from the skill file.
   */
  @Test
  public void includesCurrentDescription() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-desc-opt-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillFile = tempDir.resolve("SKILL.md");
      Files.writeString(skillFile, MINIMAL_SKILL_MD);

      DescriptionOptimizer handler = new DescriptionOptimizer(scope);
      String output = handler.getOutput(new String[]{
        skillFile.toString(),
        EVAL_SET_5,
        "claude-sonnet-4-5",
        "3"
      });

      requireThat(output, "output").contains(
        "Use when user wants to squash commits");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the max_iterations value appears in the output.
   */
  @Test
  public void includesMaxIterations() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-desc-opt-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillFile = tempDir.resolve("SKILL.md");
      Files.writeString(skillFile, MINIMAL_SKILL_MD);

      DescriptionOptimizer handler = new DescriptionOptimizer(scope);
      String output = handler.getOutput(new String[]{
        skillFile.toString(),
        EVAL_SET_5,
        "claude-sonnet-4-5",
        "5"
      });

      requireThat(output, "output").contains("5");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the generated prompt includes early-stop instructions so the subagent can converge
   * in fewer than max_iterations when training score reaches 100% or stops improving.
   * <p>
   * The handler generates a prompt for an LLM subagent; convergence is driven by the prompt's
   * instructions. This test confirms those instructions are present.
   */
  @Test
  public void promptIncludesConvergenceInstructions() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-desc-opt-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillFile = tempDir.resolve("SKILL.md");
      Files.writeString(skillFile, MINIMAL_SKILL_MD);

      DescriptionOptimizer handler = new DescriptionOptimizer(scope);
      String output = handler.getOutput(new String[]{
        skillFile.toString(),
        EVAL_SET_10,
        "claude-sonnet-4-5",
        "10"
      });

      // The prompt must instruct the subagent to stop early when train score = 100%
      // or when there is no improvement, so convergence is possible before max_iterations
      requireThat(output, "output").contains("stop early");
      requireThat(output, "output").contains("100%");
      requireThat(output, "output").contains("no improvement");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that max_iterations is correctly embedded in the structured JSON output so the subagent
   * can run up to that many iterations when convergence is not reached.
   * <p>
   * Different max_iterations values must produce distinct JSON output; the JSON field
   * {@code "max_iterations"} must match the provided argument exactly.
   */
  @Test
  public void maxIterationsPassedThroughToJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-desc-opt-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillFile = tempDir.resolve("SKILL.md");
      Files.writeString(skillFile, MINIMAL_SKILL_MD);

      DescriptionOptimizer handler = new DescriptionOptimizer(scope);

      // max_iterations = 3 → JSON must contain "max_iterations" : 3
      String output3 = handler.getOutput(new String[]{
        skillFile.toString(),
        EVAL_SET_5,
        "claude-sonnet-4-5",
        "3"
      });
      requireThat(output3, "output3").contains("\"max_iterations\" : 3");

      // max_iterations = 7 → JSON must contain "max_iterations" : 7
      String output7 = handler.getOutput(new String[]{
        skillFile.toString(),
        EVAL_SET_5,
        "claude-sonnet-4-5",
        "7"
      });
      requireThat(output7, "output7").contains("\"max_iterations\" : 7");

      // The two outputs must differ (different max_iterations → different prompts)
      requireThat(output3, "output3").isNotEqualTo(output7);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an eval set with exactly 2 items (minimum boundary) is accepted.
   * <p>
   * With 2 items: train = max(1, floor(2 * 0.6)) = max(1, 1) = 1, test = 1.
   */
  @Test
  public void acceptsEvalSetWithExactlyTwoItems() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-desc-opt-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillFile = tempDir.resolve("SKILL.md");
      Files.writeString(skillFile, MINIMAL_SKILL_MD);

      DescriptionOptimizer handler = new DescriptionOptimizer(scope);
      String evalSet2 = """
        [
          {"query": "squash my last 3 commits", "should_trigger": true},
          {"query": "push to remote", "should_trigger": false}
        ]
        """;
      String output = handler.getOutput(new String[]{
        skillFile.toString(),
        evalSet2,
        "claude-sonnet-4-5",
        "3"
      });

      requireThat(output, "output").isNotNull();
      requireThat(output, "output").contains("\"train_size\" : 1");
      requireThat(output, "output").contains("\"test_size\" : 1");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an eval set item missing the 'query' field is rejected with a clear error.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*missing a non-empty 'query' field.*")
  public void throwsOnMissingQueryField() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-desc-opt-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillFile = tempDir.resolve("SKILL.md");
      Files.writeString(skillFile, MINIMAL_SKILL_MD);

      DescriptionOptimizer handler = new DescriptionOptimizer(scope);
      String badEvalSet = """
        [
          {"should_trigger": true},
          {"query": "push to remote", "should_trigger": false}
        ]
        """;
      handler.getOutput(new String[]{
        skillFile.toString(),
        badEvalSet,
        "claude-sonnet-4-5",
        "3"
      });
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an eval set item missing the 'should_trigger' field is rejected with a clear error.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*missing a 'should_trigger' field.*")
  public void throwsOnMissingShouldTriggerField() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-desc-opt-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      Path skillFile = tempDir.resolve("SKILL.md");
      Files.writeString(skillFile, MINIMAL_SKILL_MD);

      DescriptionOptimizer handler = new DescriptionOptimizer(scope);
      String badEvalSet = """
        [
          {"query": "squash my last 3 commits"},
          {"query": "push to remote", "should_trigger": false}
        ]
        """;
      handler.getOutput(new String[]{
        skillFile.toString(),
        badEvalSet,
        "claude-sonnet-4-5",
        "3"
      });
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
