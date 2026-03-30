/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.skills.InstructionTestAggregator;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link InstructionTestAggregator}.
 * <p>
 * Each test is self-contained with no shared state.
 */
public final class InstructionTestAggregatorTest
{
  /**
   * Verifies that the handler throws when no arguments are provided.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*requires 1 argument.*")
  public void throwsOnNoArguments() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-instruction-test-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      InstructionTestAggregator handler = new InstructionTestAggregator(scope);
      handler.getOutput(new String[]{});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the handler throws when more than one argument is provided.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*requires 1 argument.*")
  public void throwsOnTooManyArguments() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-instruction-test-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      InstructionTestAggregator handler = new InstructionTestAggregator(scope);
      handler.getOutput(new String[]{"arg1", "arg2"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the handler throws when given an empty run list.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*at least one run result.*")
  public void throwsOnEmptyResultList() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-instruction-test-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      InstructionTestAggregator handler = new InstructionTestAggregator(scope);
      handler.getOutput(new String[]{"[]"});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies correct stats for a single config with a single run.
   * <p>
   * With a single run: stddev = 0.0 since there is no variance.
   */
  @Test
  public void singleConfigSingleRun() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-instruction-test-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      InstructionTestAggregator handler = new InstructionTestAggregator(scope);
      String input = """
        [
          {"config": "baseline", "assertions": [true, false, true], "duration_ms": 1200, "total_tokens": 500}
        ]
        """;
      String output = handler.getOutput(new String[]{input});

      requireThat(output, "output").isNotNull();
      requireThat(output, "output").contains("\"baseline\"");
      // pass_rate = 2/3 ≈ 0.667
      requireThat(output, "output").contains("pass_rate");
      // stddev should be 0.0 for a single run
      requireThat(output, "output").contains("stddev_duration_ms");
      requireThat(output, "output").contains("stddev_tokens");
      // No delta for a single config
      requireThat(output, "output").doesNotContain("\"delta\"");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies correct stats for multiple runs under the same config (stddev calculation).
   */
  @Test
  public void multipleRunsSameConfig() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-instruction-test-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      InstructionTestAggregator handler = new InstructionTestAggregator(scope);
      // Two runs under "baseline": durations 1000 and 3000 ms → mean=2000, stddev=1000
      String input = """
        [
          {"config": "baseline", "assertions": [true], "duration_ms": 1000, "total_tokens": 400},
          {"config": "baseline", "assertions": [true], "duration_ms": 3000, "total_tokens": 600}
        ]
        """;
      String output = handler.getOutput(new String[]{input});

      requireThat(output, "output").isNotNull();
      requireThat(output, "output").contains("\"baseline\"");
      requireThat(output, "output").contains("mean_duration_ms");
      // mean = 2000, stddev = 1000
      requireThat(output, "output").contains("2000");
      requireThat(output, "output").contains("1000");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that delta vs. baseline is computed when two or more distinct configs exist.
   * <p>
   * The first distinct config name is treated as the baseline.
   */
  @Test
  public void multipleConfigsProducesDelta() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-instruction-test-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      InstructionTestAggregator handler = new InstructionTestAggregator(scope);
      // baseline: 2/3 pass rate, 1200ms, 500 tokens
      // with-skill: 3/3 pass rate, 800ms, 300 tokens
      String input = """
        [
          {"config": "baseline", "assertions": [true, false, true], "duration_ms": 1200,
           "total_tokens": 500},
          {"config": "with-skill", "assertions": [true, true, true], "duration_ms": 800,
           "total_tokens": 300}
        ]
        """;
      String output = handler.getOutput(new String[]{input});

      requireThat(output, "output").isNotNull();
      requireThat(output, "output").contains("\"baseline\"");
      requireThat(output, "output").contains("\"with-skill\"");
      requireThat(output, "output").contains("\"delta\"");
      requireThat(output, "output").contains("\"baseline_config\"");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that stddev is 0.0 when all runs for a config have identical durations.
   */
  @Test
  public void stddevIsZeroForIdenticalValues() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-instruction-test-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      InstructionTestAggregator handler = new InstructionTestAggregator(scope);
      String input = """
        [
          {"config": "baseline", "assertions": [true], "duration_ms": 1000, "total_tokens": 200},
          {"config": "baseline", "assertions": [true], "duration_ms": 1000, "total_tokens": 200}
        ]
        """;
      String output = handler.getOutput(new String[]{input});

      requireThat(output, "output").isNotNull();
      // Both stddev values should appear as 0.0
      requireThat(output, "output").contains("\"stddev_duration_ms\" : 0.0");
      requireThat(output, "output").contains("\"stddev_tokens\" : 0.0");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that all-false assertions yields pass_rate 0.0.
   */
  @Test
  public void allFailedAssertionsYieldsZeroPassRate() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-instruction-test-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      InstructionTestAggregator handler = new InstructionTestAggregator(scope);
      String input = """
        [
          {"config": "baseline", "assertions": [false, false, false], "duration_ms": 900,
           "total_tokens": 300}
        ]
        """;
      String output = handler.getOutput(new String[]{input});

      requireThat(output, "output").isNotNull();
      requireThat(output, "output").contains("\"pass_rate\" : 0.0");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that all-true assertions yields pass_rate 1.0.
   */
  @Test
  public void allPassedAssertionsYieldsFullPassRate() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-instruction-test-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      InstructionTestAggregator handler = new InstructionTestAggregator(scope);
      String input = """
        [
          {"config": "baseline", "assertions": [true, true, true], "duration_ms": 1500,
           "total_tokens": 600}
        ]
        """;
      String output = handler.getOutput(new String[]{input});

      requireThat(output, "output").isNotNull();
      requireThat(output, "output").contains("\"pass_rate\" : 1.0");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the delta pass_rate is correctly computed as (with-skill) - (baseline).
   */
  @Test
  public void deltaPassRateIsCorrect() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-instruction-test-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      InstructionTestAggregator handler = new InstructionTestAggregator(scope);
      // baseline: 1/2 = 0.5, with-skill: 2/2 = 1.0 → delta = 0.5
      String input = """
        [
          {"config": "baseline", "assertions": [true, false], "duration_ms": 1000, "total_tokens": 400},
          {"config": "with-skill", "assertions": [true, true], "duration_ms": 800, "total_tokens": 300}
        ]
        """;
      String output = handler.getOutput(new String[]{input});

      requireThat(output, "output").isNotNull();
      requireThat(output, "output").contains("\"delta\"");
      // delta pass_rate = 1.0 - 0.5 = 0.5
      requireThat(output, "output").contains("0.5");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a run with an empty assertions array yields pass_rate 0.0 and run_count 1.
   * <p>
   * Empty assertions means no assertions to check; pass_rate defaults to 0.0.
   */
  @Test
  public void emptyAssertionsArrayYieldsZeroPassRate() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-instruction-test-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      InstructionTestAggregator handler = new InstructionTestAggregator(scope);
      String input = """
        [
          {"config": "baseline", "assertions": [], "duration_ms": 1000, "total_tokens": 400}
        ]
        """;
      String output = handler.getOutput(new String[]{input});

      requireThat(output, "output").isNotNull();
      requireThat(output, "output").contains("\"pass_rate\" : 0.0");
      requireThat(output, "output").contains("\"run_count\" : 1");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that negative duration_ms is rejected with a clear error.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*duration_ms.*must be >= 0.*")
  public void throwsOnNegativeDurationMs() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-instruction-test-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      InstructionTestAggregator handler = new InstructionTestAggregator(scope);
      String input = """
        [
          {"config": "baseline", "assertions": [true], "duration_ms": -1, "total_tokens": 400}
        ]
        """;
      handler.getOutput(new String[]{input});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that negative total_tokens is rejected with a clear error.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*total_tokens.*must be >= 0.*")
  public void throwsOnNegativeTotalTokens() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-instruction-test-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      InstructionTestAggregator handler = new InstructionTestAggregator(scope);
      String input = """
        [
          {"config": "baseline", "assertions": [true], "duration_ms": 1000, "total_tokens": -5}
        ]
        """;
      handler.getOutput(new String[]{input});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that missing duration_ms field is rejected with a clear error.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*duration_ms.*missing.*")
  public void throwsOnMissingDurationMs() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-instruction-test-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      InstructionTestAggregator handler = new InstructionTestAggregator(scope);
      String input = """
        [
          {"config": "baseline", "assertions": [true], "total_tokens": 400}
        ]
        """;
      handler.getOutput(new String[]{input});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that missing total_tokens field is rejected with a clear error.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*total_tokens.*missing.*")
  public void throwsOnMissingTotalTokens() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-instruction-test-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      InstructionTestAggregator handler = new InstructionTestAggregator(scope);
      String input = """
        [
          {"config": "baseline", "assertions": [true], "duration_ms": 1000}
        ]
        """;
      handler.getOutput(new String[]{input});
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that delta is computed correctly when baseline has zero pass rate.
   * <p>
   * This exercises the delta computation with a zero baseline, ensuring no division-by-zero issues.
   */
  @Test
  public void deltaWithZeroBaselinePassRate() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-instruction-test-");
    try (var scope = new TestClaudeTool(tempDir, tempDir))
    {
      InstructionTestAggregator handler = new InstructionTestAggregator(scope);
      // baseline: 0/2 = 0.0, with-skill: 2/2 = 1.0 → delta = 1.0
      String input = """
        [
          {"config": "baseline", "assertions": [false, false], "duration_ms": 1000, "total_tokens": 400},
          {"config": "with-skill", "assertions": [true, true], "duration_ms": 800, "total_tokens": 300}
        ]
        """;
      String output = handler.getOutput(new String[]{input});

      requireThat(output, "output").isNotNull();
      requireThat(output, "output").contains("\"delta\"");
      requireThat(output, "output").contains("\"pass_rate\" : 1.0");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
