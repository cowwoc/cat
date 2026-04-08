/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.ClaudeStatusline;
import io.github.cowwoc.cat.hooks.SharedSecrets;
import io.github.cowwoc.cat.hooks.util.StatuslineCommand;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.annotations.Test;

/**
 * Tests for StatuslineCommand.
 */
public final class StatuslineCommandTest
{
  /**
   * Executes the statusline command with the given scope and locks directory, returning the output
   * string.
   *
   * @param scope   the test scope (with JSON already parsed via the 3-arg constructor)
   * @param lockDir the locks directory containing {@code .lock} files
   * @return the statusline output
   * @throws IOException if an I/O error occurs
   */
  private static String executeWithLockDir(ClaudeStatusline scope, Path lockDir) throws IOException
  {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(outputStream, true, StandardCharsets.UTF_8);
    StatuslineCommand cmd = new StatuslineCommand(scope);
    cmd.execute(printStream, lockDir);
    return outputStream.toString(StandardCharsets.UTF_8);
  }

  /**
   * Verifies that the statusline output contains model name, duration, full session ID, and usage
   * bar when valid JSON input is provided.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void validInputContainsAllComponents() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude-3-5-sonnet"},
          "session_id": "abcdef12-1234-5678-abcd-ef1234567890",
          "cost": {"total_duration_ms": 125000}
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        requireThat(output, "output").contains("claude-3-5-sonnet");
        requireThat(output, "output").contains("abcdef12-1234-5678-abcd-ef1234567890");
        requireThat(output, "output").contains("00:02");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that durations under 60 seconds are formatted as 00:00 in HH:MM format.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void durationUnderOneMinuteFormattedAsHHMM() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 45000}
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        requireThat(output, "output").contains("00:00");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that durations over 60 minutes are formatted with hours in HH:MM format.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void durationOverOneHourFormattedWithHours() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 3725000}
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        // 3725000ms = 3725 seconds = 62 minutes 5 seconds → 1 hour 2 minutes → 01:02
        requireThat(output, "output").contains("01:02");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that 200,000 used tokens (full context, 200k model) uses an RGB red color code and shows 100%.
   * <p>
   * With 200k model: usableContext = 165,500. effectiveUsed = 200,000 - 34,500 = 165,500. 165,500 * 100 /
   * 165,500 = 100%.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void usageAbove80ContainsRgbRedColor() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {
            "current_usage": {"input_tokens": 200000},
            "context_window_size": 200000
          }
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        // contextPercent=100 → pure red: \033[38;2;255;0;0m
        requireThat(output, "output").contains("\033[38;2;255;0;0m");
        requireThat(output, "output").contains("100%");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that 161,935 used tokens (77% of usable context, 200k model) uses an RGB orange-red color code.
   * <p>
   * With 200k model: usableContext = 165,500. effectiveUsed = 161,935 - 34,500 = 127,435.
   * 127,435 * 100 / 165,500 = 77%. red=255, green=((100-77)*255)/50 = 117 → orange-red.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void usageBetween50And80ContainsRgbOrangeColor() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {
            "current_usage": {"input_tokens": 161935},
            "context_window_size": 200000
          }
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        // contextPercent=77 → red=255, green=((100-77)*255)/50 = 117 → \033[38;2;255;117;0m
        requireThat(output, "output").contains("\033[38;2;255;117;0m");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that 92,425 used tokens (35% of usable context, 200k model) uses an RGB green-yellow color code.
   * <p>
   * With 200k model: usableContext = 165,500. effectiveUsed = 92,425 - 34,500 = 57,925.
   * 57,925 * 100 / 165,500 = 35%. red=(35*255)/50 = 178, green=255 → yellow-green.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void usageBelow50ContainsRgbGreenColor() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {
            "current_usage": {"input_tokens": 92425},
            "context_window_size": 200000
          }
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        // contextPercent=35 → red=(35*255)/50 = 178, green=255 → \033[38;2;178;255;0m
        requireThat(output, "output").contains("\033[38;2;178;255;0m");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an empty JSON object throws an {@code IllegalArgumentException} for the first missing
   * required field.
   * <p>
   * All of {@code model}, {@code session_id}, and {@code cost} are required to display correct information.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = "(?s).*model.*missing.*")
  public void missingRequiredFieldsThrowsIllegalArgumentException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      new TestClaudeStatusline(tempDir, tempDir, "{}").close();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that used tokens exceeding the total context window are clamped to 100%.
   * <p>
   * 300,000 used tokens against a 200k model exceeds the total context; the result is clamped to 100%.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void usageExceeding100ClampedTo100() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {
            "current_usage": {"input_tokens": 300000},
            "context_window_size": 200000
          }
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        requireThat(output, "output").contains("100%");
        requireThat(output, "output").doesNotContain("150%");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the usage bar shows 20 filled segments when context usage is at full context (200k model).
   * <p>
   * 200,000 used tokens with a 200k model yields contextPercent=100, filling all 20 bar segments.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void usageBarFullAt100Percent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {
            "current_usage": {"input_tokens": 200000},
            "context_window_size": 200000
          }
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        // 20 filled blocks
        requireThat(output, "output").contains("████████████████████");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the full session ID is displayed without truncation.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void sessionIdDisplayedInFull() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "12345678-abcd-ef01-2345-678901234567",
          "cost": {"total_duration_ms": 1000}
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        requireThat(output, "output").contains("12345678-abcd-ef01-2345-678901234567");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that 132,145 used tokens (59% of usable context, 200k model) uses an orange-red RGB color.
   * <p>
   * With 200k model: usableContext = 165,500. effectiveUsed = 132,145 - 34,500 = 97,645.
   * 97,645 * 100 / 165,500 = 59%. red=255, green=((100-59)*255)/50 = 209 → orange-red.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void usageAt50PercentScalesAbove50() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {
            "current_usage": {"input_tokens": 132145},
            "context_window_size": 200000
          }
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        // contextPercent=59 → red=255, green=((100-59)*255)/50 = 209 → \033[38;2;255;209;0m
        requireThat(output, "output").contains("\033[38;2;255;209;0m");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that 191,725 used tokens (95% of usable context, 200k model) uses a near-red RGB color.
   * <p>
   * With 200k model: usableContext = 165,500. effectiveUsed = 191,725 - 34,500 = 157,225.
   * 157,225 * 100 / 165,500 = 95%. red=255, green=((100-95)*255)/50 = 25 → near-red.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void usageAt80PercentScalesNearRed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {
            "current_usage": {"input_tokens": 191725},
            "context_window_size": 200000
          }
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        // contextPercent=95 → red=255, green=((100-95)*255)/50 = 25 → \033[38;2;255;25;0m
        requireThat(output, "output").contains("\033[38;2;255;25;0m");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a usage percentage of 0% produces an all-empty usage bar (20 empty segments).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void usageBarAllEmptyAt0Percent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000}
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        // All 20 segments should be empty
        requireThat(output, "output").contains("░░░░░░░░░░░░░░░░░░░░");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that 132,145 used tokens (59% of usable context, 200k model) produces 11 filled and 9 empty bar
   * segments.
   * <p>
   * With 200k model: usableContext = 165,500. effectiveUsed = 132,145 - 34,500 = 97,645.
   * 97,645 * 100 / 165,500 = 59%. filled = (59 * 20) / 100 = 11.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void usageBarAt50PercentRaw() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {
            "current_usage": {"input_tokens": 132145},
            "context_window_size": 200000
          }
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        // contextPercent=59 → filled = (59 * 20) / 100 = 11
        // 11 filled followed by 9 empty segments
        requireThat(output, "output").contains("███████████░░░░░░░░░");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a negative duration is clamped to 0, resulting in "00:00" in the output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void negativeDurationClampedToZero() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": -5000}
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        requireThat(output, "output").contains("00:00");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that negative input tokens throw an {@code IllegalArgumentException}.
   * <p>
   * Negative token counts are invalid and must be rejected rather than silently clamped to zero,
   * which would produce an incorrect context percentage.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = "(?s).*input_tokens.*negative.*")
  public void negativeInputTokensThrowsIllegalArgumentException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {
            "current_usage": {"input_tokens": -10},
            "context_window_size": 200000
          }
        }
        """;
      new TestClaudeStatusline(tempDir, tempDir, json).close();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that all 5 statusline emojis are present in the output when an active issue lock is present.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void outputContainsAllFiveEmojisWhenActiveIssuePresent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      Path lockDir = Files.createTempDirectory("cat-locks-");
      try
      {
        Files.writeString(lockDir.resolve("2.1-my-issue.lock"),
          """
          {"session_id": "00000000-0000-0000-0000-000000000000", "created_at": 1000000}
          """);
        String json = """
          {
            "model": {"display_name": "claude"},
            "session_id": "00000000-0000-0000-0000-000000000000",
            "cost": {"total_duration_ms": 1000},
            "context_window": {
              "current_usage": {"input_tokens": 100000},
              "context_window_size": 200000
            }
          }
          """;
        try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
        {
          String output = executeWithLockDir(scope, lockDir);
          requireThat(output, "output").contains("🌿");
          requireThat(output, "output").contains("🤖");
          requireThat(output, "output").contains("⏰");
          requireThat(output, "output").contains("🆔");
          requireThat(output, "output").contains("📊");
        }
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(lockDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the output contains all 4 component ANSI color escape sequences when an active issue is present.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void outputContainsAllComponentColors() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      Path lockDir = Files.createTempDirectory("cat-locks-");
      try
      {
        Files.writeString(lockDir.resolve("2.1-my-issue.lock"),
          """
          {"session_id": "00000000-0000-0000-0000-000000000000", "created_at": 1000000}
          """);
        String json = """
          {
            "model": {"display_name": "claude"},
            "session_id": "00000000-0000-0000-0000-000000000000",
            "cost": {"total_duration_ms": 1000},
            "context_window": {
              "current_usage": {"input_tokens": 100000},
              "context_window_size": 200000
            }
          }
          """;
        try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
        {
          String output = executeWithLockDir(scope, lockDir);
          // WORKTREE_COLOR: Bright White
          requireThat(output, "output").contains("\033[38;2;255;255;255m");
          // MODEL_COLOR: Warm Gold
          requireThat(output, "output").contains("\033[38;2;220;150;9m");
          // TIME_COLOR: Coral
          requireThat(output, "output").contains("\033[38;2;255;127;80m");
          // SESSION_COLOR: Medium Purple
          requireThat(output, "output").contains("\033[38;2;147;112;219m");
        }
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(lockDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies the context scaling boundary: full context (200,000 tokens) yields 100% (all 20 bar segments
   * filled), while one token below full context yields 99% (19 filled segments and " 99%" displayed).
   * <p>
   * With 200k model: usableContext = 165,500. At 200,000 tokens: effectiveUsed = 165,500 → 100%.
   * At 199,834 tokens: effectiveUsed = 165,334 → 165,334 * 100 / 165,500 = 99%.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void scalingThresholdBoundaryAt835Percent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      // Full context: 200,000 tokens → contextPercent=100
      String jsonFull = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {
            "current_usage": {"input_tokens": 200000},
            "context_window_size": 200000
          }
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, jsonFull))
      {
        String outputFull = executeWithLockDir(scope, tempDir);
        // contextPercent=100 → all 20 segments filled
        requireThat(outputFull, "outputFull").contains("████████████████████");
        requireThat(outputFull, "outputFull").contains("100%");
      }

      // 199,834 tokens: effectiveUsed = 165,334 → 165,334 * 100 / 165,500 = 99
      String jsonNearFull = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {
            "current_usage": {"input_tokens": 199834},
            "context_window_size": 200000
          }
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, jsonNearFull))
      {
        String outputNearFull = executeWithLockDir(scope, tempDir);
        // contextPercent=99 → filled = (99 * 20) / 100 = 19, so 19 filled and 1 empty
        requireThat(outputNearFull, "outputNearFull").contains("███████████████████░");
        // contextPercent=99 → right-justified: " 99%"
        requireThat(outputNearFull, "outputNearFull").contains(" 99%");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a session ID shorter than 8 characters is passed through unchanged.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void shortSessionIdPassesThroughUnchanged() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "abc123",
          "cost": {"total_duration_ms": 1000}
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        requireThat(output, "output").contains("abc123");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that explicit null values for required JSON fields throw an {@code IllegalArgumentException}.
   * <p>
   * Null values for required fields are treated the same as missing fields — both indicate that the
   * statusline cannot display correct information.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = "(?s).*display_name.*missing.*")
  public void nullRequiredFieldsThrowsIllegalArgumentException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": null},
          "session_id": null,
          "cost": {"total_duration_ms": null},
          "context_window": null
        }
        """;
      new TestClaudeStatusline(tempDir, tempDir, json).close();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that invalid (malformed) JSON input causes graceful degradation to all-default output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void malformedJsonUsesDefaults() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = "not valid json at all !!!";
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        requireThat(output, "output").contains("unknown");
        requireThat(output, "output").contains("00:00");
        // contextPercent=0 → right-justified: "  0%"
        requireThat(output, "output").contains("  0%");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that {@code getActiveIssue} returns the issue ID when a lock file matches the session.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getActiveIssueReturnsIssueIdWhenLockMatchesSession() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir))
    {
      StatuslineCommand cmd = new StatuslineCommand(scope);
      Path lockDir = Files.createTempDirectory("cat-locks-");
      try
      {
        Files.writeString(lockDir.resolve("2.1-test-issue.lock"),
          """
          {"session_id": "aaaaaaaa-1111-2222-3333-444444444444", "created_at": 1000000}
          """);
        String result = cmd.getActiveIssue("aaaaaaaa-1111-2222-3333-444444444444", lockDir);
        requireThat(result, "result").isEqualTo("2.1-test-issue");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(lockDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that {@code getActiveIssue} returns an empty string when no lock file matches the session.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getActiveIssueReturnsEmptyWhenNoMatchingLock() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir))
    {
      StatuslineCommand cmd = new StatuslineCommand(scope);
      Path lockDir = Files.createTempDirectory("cat-locks-");
      try
      {
        Files.writeString(lockDir.resolve("2.1-other-issue.lock"),
          """
          {"session_id": "bbbbbbbb-2222-3333-4444-555555555555", "created_at": 1000000}
          """);
        String result = cmd.getActiveIssue("aaaaaaaa-1111-2222-3333-444444444444", lockDir);
        requireThat(result, "result").isEmpty();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(lockDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that {@code getActiveIssue} returns an empty string when the locks directory does not exist.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getActiveIssueReturnsEmptyWhenLocksDirectoryAbsent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir))
    {
      StatuslineCommand cmd = new StatuslineCommand(scope);
      // Pass a non-existent directory — should return "" gracefully
      Path nonExistentLockDir = tempDir.resolve("locks");
      String result = cmd.getActiveIssue("aaaaaaaa-1111-2222-3333-444444444444", nonExistentLockDir);
      requireThat(result, "result").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the statusline omits the first element (worktree emoji and issue ID) when no active issue lock
   * matches the session.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeOmitsFirstElementWhenNoActiveIssue() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude-3-5-sonnet"},
          "session_id": "aaaaaaaa-1111-2222-3333-444444444444",
          "cost": {"total_duration_ms": 0}
        }""";
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String result = executeWithLockDir(scope, tempDir);
        // No lock → no worktree element → output does not contain 🌿
        requireThat(result, "result").doesNotContain("🌿");
        // Model element still present
        requireThat(result, "result").contains("claude-3-5-sonnet");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the statusline includes the issue ID prefixed with the worktree emoji when an active issue
   * lock matches the session.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeIncludesIssueIdWhenActiveIssueFound() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      Path lockDir = Files.createTempDirectory("cat-locks-");
      try
      {
        Files.writeString(lockDir.resolve("2.1-my-issue.lock"),
          """
          {"session_id": "aaaaaaaa-1111-2222-3333-444444444444", "created_at": 1000000}
          """);
        String json = """
          {
            "model": {"display_name": "claude-3-5-sonnet"},
            "session_id": "aaaaaaaa-1111-2222-3333-444444444444",
            "cost": {"total_duration_ms": 0}
          }""";
        try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
        {
          String result = executeWithLockDir(scope, lockDir);
          requireThat(result, "result").contains("🌿");
          requireThat(result, "result").contains("2.1-my-issue");
        }
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(lockDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that {@code getActiveIssue} sanitizes ANSI injection characters from the issue ID derived from
   * lock filenames, including BEL, BS, TAB, CR, FF, and DEL.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getActiveIssueSanitizesAnsiInjectionInIssueId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir))
    {
      StatuslineCommand cmd = new StatuslineCommand(scope);
      Path lockDir = Files.createTempDirectory("cat-locks-");
      try
      {
        // Lock filename can't contain ESC in real filesystems, but test sanitization path
        // by injecting via a lock file whose content has an injected session_id
        Files.writeString(lockDir.resolve("2-1-evil\u001b[0m.lock"),
          """
          {"session_id": "aaaaaaaa-1111-2222-3333-444444444444", "created_at": 1000000}
          """);
        String result = cmd.getActiveIssue("aaaaaaaa-1111-2222-3333-444444444444", lockDir);
        // ESC character must be stripped by removeControlCharacters
        requireThat(result, "result").doesNotContain("\u001b");
        // Other control characters also removed: BEL, BS, TAB, CR, FF, DEL
        requireThat(result, "result").doesNotContain("\u0007");
        requireThat(result, "result").doesNotContain("\u0008");
        requireThat(result, "result").doesNotContain("\u0009");
        requireThat(result, "result").doesNotContain("\r");
        requireThat(result, "result").doesNotContain("\f");
        requireThat(result, "result").doesNotContain("\u007f");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(lockDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that {@code getActiveIssue} returns an error indicator string when the lock file contains
   * malformed JSON, so the error is visible in the statusline.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getActiveIssueReturnsErrorWhenLockFileHasMalformedJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir))
    {
      StatuslineCommand cmd = new StatuslineCommand(scope);
      Path lockDir = Files.createTempDirectory("cat-locks-");
      try
      {
        Files.writeString(lockDir.resolve("2.1-broken.lock"), "{broken}");
        String result = cmd.getActiveIssue("aaaaaaaa-1111-2222-3333-444444444444", lockDir);
        requireThat(result, "result").startsWith("⚠ CAT:");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(lockDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that {@code getActiveIssue} returns an empty string when the lock file contains valid JSON but
   * no {@code session_id} field.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getActiveIssueReturnsEmptyWhenLockFileHasNoSessionIdField() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir))
    {
      StatuslineCommand cmd = new StatuslineCommand(scope);
      Path lockDir = Files.createTempDirectory("cat-locks-");
      try
      {
        Files.writeString(lockDir.resolve("2.1-no-session.lock"),
          """
          {"created_at": 1000000}
          """);
        String result = cmd.getActiveIssue("aaaaaaaa-1111-2222-3333-444444444444", lockDir);
        requireThat(result, "result").isEmpty();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(lockDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Executes the statusline command with the given scope, locks directory, and terminal width, returning
   * the output string.
   *
   * @param scope         the test scope (with JSON already parsed via the 3-arg constructor)
   * @param lockDir       the locks directory containing {@code .lock} files
   * @param terminalWidth the terminal width in columns (0 for unlimited)
   * @return the statusline output
   * @throws IOException if an I/O error occurs
   */
  private static String executeWithTerminalWidth(ClaudeStatusline scope, Path lockDir, int terminalWidth)
    throws IOException
  {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(outputStream, true, StandardCharsets.UTF_8);
    StatuslineCommand cmd = new StatuslineCommand(scope);
    cmd.execute(printStream, lockDir, terminalWidth);
    return outputStream.toString(StandardCharsets.UTF_8);
  }

  /**
   * Verifies that when all components fit within the terminal width, the output is a single line.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void allComponentsFitProducesSingleLine() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "m"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000}
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        // Use a wide terminal so components always fit (500 cols >> any realistic statusline)
        String output = executeWithTerminalWidth(scope, tempDir, 500);
        // Single-line: no newline between components (only trailing newline from println)
        String stripped = output.strip();
        requireThat(stripped, "stripped").doesNotContain("\n");
        // All component emojis still present
        requireThat(output, "output").contains("🤖");
        requireThat(output, "output").contains("⏰");
        requireThat(output, "output").contains("🆔");
        requireThat(output, "output").contains("📊");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when the combined component width exactly matches the terminal width, the output is a
   * single line (no wrapping at exact fit).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void exactFitProducesSingleLine() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "m"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000}
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        // Build the single-line output with a wide terminal first, then measure its plain width
        String singleLine = executeWithTerminalWidth(scope, tempDir, 500).strip();
        int plainWidth = StatuslineCommand.plainWidth(singleLine);

        // Now execute with exactly that width — should still produce a single line
        String output = executeWithTerminalWidth(scope, tempDir, plainWidth);
        String stripped = output.strip();
        requireThat(stripped, "stripped").doesNotContain("\n");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when combined component width exceeds the terminal width, each component is rendered on
   * its own line.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void overflowProducesOneComponentPerLine() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude-3-5-sonnet"},
          "session_id": "abcdef12-1234-5678-abcd-ef1234567890",
          "cost": {"total_duration_ms": 125000},
          "context_window": {
            "current_usage": {"input_tokens": 108975},
            "context_window_size": 200000
          }
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        // Use a very narrow terminal (40 cols) to force wrapping
        String output = executeWithTerminalWidth(scope, tempDir, 40);
        // Each component should be on its own line — output has multiple newlines
        long newlineCount = output.chars().filter(c -> c == '\n').count();
        requireThat((int) newlineCount, "newlineCount").isGreaterThanOrEqualTo(3);
        // Component emojis all appear (content preserved)
        requireThat(output, "output").contains("🤖");
        requireThat(output, "output").contains("⏰");
        requireThat(output, "output").contains("🆔");
        requireThat(output, "output").contains("📊");
        // Component data preserved
        requireThat(output, "output").contains("claude-3-5-sonnet");
        requireThat(output, "output").contains("abcdef12-1234-5678-abcd-ef1234567890");
        // 125000ms = 125s = 2 minutes → "00:02" in HH:MM format
        requireThat(output, "output").contains("00:02");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that no component content is truncated or dropped when wrapping to per-component lines.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void wrappedOutputPreservesAllComponentContent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "test-model-name"},
          "session_id": "12345678-abcd-ef01-2345-678901234567",
          "cost": {"total_duration_ms": 3661000},
          "context_window": {
            "current_usage": {"input_tokens": 158625},
            "context_window_size": 200000
          }
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        // Force wrap by using narrow terminal
        String output = executeWithTerminalWidth(scope, tempDir, 1);
        // Model name preserved
        requireThat(output, "output").contains("test-model-name");
        // Full session ID preserved (not truncated)
        requireThat(output, "output").contains("12345678-abcd-ef01-2345-678901234567");
        // Duration preserved
        requireThat(output, "output").contains("01:01");
        // Usage bar present (20 segments)
        int filledCount = (int) output.chars().filter(c -> c == '█').count();
        int emptyCount = (int) output.chars().filter(c -> c == '░').count();
        requireThat(filledCount + emptyCount, "barSegments").isEqualTo(20);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that {@code plainWidth} strips ANSI escape sequences before measuring width, and counts
   * emoji as width 2.
   */
  @Test
  public void plainWidthStripsAnsiAndCountsEmojiAsWidth2()
  {
    // Plain ASCII: each character is width 1
    requireThat(StatuslineCommand.plainWidth("hello"), "helloWidth").isEqualTo(5);

    // ANSI color codes must not contribute to width
    String ansiWrapped = "\033[38;2;255;0;0m" + "hi" + "\033[0m";
    requireThat(StatuslineCommand.plainWidth(ansiWrapped), "ansiHiWidth").isEqualTo(2);

    // Emoji must count as width 2
    requireThat(StatuslineCommand.plainWidth("🤖"), "robotWidth").isEqualTo(2);
    requireThat(StatuslineCommand.plainWidth("⏰"), "clockWidth").isEqualTo(2);

    // Mixed: ANSI + emoji + ASCII
    String mixed = "\033[38;2;220;150;9m" + "🤖 hi" + "\033[0m";
    // emoji(2) + space(1) + h(1) + i(1) = 5
    requireThat(StatuslineCommand.plainWidth(mixed), "mixedWidth").isEqualTo(5);

    // Empty string
    requireThat(StatuslineCommand.plainWidth(""), "emptyWidth").isEqualTo(0);
  }

  /**
   * Verifies that when {@code terminalWidth} is 0 (unlimited), the output is always a single line
   * regardless of component total width.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void terminalWidthZeroAlwaysProducesSingleLine() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude-opus-4-5-long-model-name"},
          "session_id": "abcdef12-1234-5678-abcd-ef1234567890",
          "cost": {"total_duration_ms": 125000},
          "context_window": {
            "current_usage": {"input_tokens": 108975},
            "context_window_size": 200000
          }
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        // terminalWidth=0 means unlimited — never wrap
        String output = executeWithTerminalWidth(scope, tempDir, 0);
        String stripped = output.strip();
        requireThat(stripped, "stripped").doesNotContain("\n");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that {@code run()} reads {@code displayWidth} from {@code .cat/config.json} and applies
   * it as the terminal width when deciding whether to wrap component output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void runUsesDisplayWidthFromConfig() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      // Write a config.json with a very narrow displayWidth to force wrapping
      Path catDir = Files.createDirectories(tempDir.resolve(".cat"));
      Files.writeString(catDir.resolve("config.json"), "{\"displayWidth\": 40}");

      String json = """
        {
          "model": {"display_name": "claude-opus-4-5"},
          "session_id": "abcdef12-1234-5678-abcd-ef1234567890",
          "cost": {"total_duration_ms": 125000},
          "context_window": {
            "current_usage": {"input_tokens": 108975},
            "context_window_size": 200000
          }
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(outputStream, true, StandardCharsets.UTF_8);
        StatuslineCommand.run(scope, new String[0], printStream);
        String output = outputStream.toString(StandardCharsets.UTF_8);
        // 40 cols is too narrow for all 4 components on one line; expect multiple newlines
        long newlineCount = output.chars().filter(c -> c == '\n').count();
        requireThat((int) newlineCount, "newlineCount").isGreaterThanOrEqualTo(2);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that {@code getActiveIssue} returns an error indicator string when the lock file is empty
   * (0 bytes), so the parse error is visible in the statusline.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void getActiveIssueReturnsErrorWhenLockFileIsEmpty() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir))
    {
      StatuslineCommand cmd = new StatuslineCommand(scope);
      Path lockDir = Files.createTempDirectory("cat-locks-");
      try
      {
        Files.writeString(lockDir.resolve("2.1-empty.lock"), "");
        String result = cmd.getActiveIssue("aaaaaaaa-1111-2222-3333-444444444444", lockDir);
        requireThat(result, "result").startsWith("⚠ CAT:");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(lockDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that 0 used tokens on a 200k model yields 0% (below-overhead tokens are clamped to 0).
   * <p>
   * OVERHEAD_TOKENS = 34,500. usableContext = 200,000 - 34,500 = 165,500.
   * effectiveUsed = 0 - 34,500 = -34,500 → clamped to 0%.
   */
  @Test
  public void scale200kZeroTokensYields0()
  {
    int result = SharedSecrets.scaleContextPercent(0, 200_000);
    requireThat(result, "result").isEqualTo(0);
  }

  /**
   * Verifies that exactly overhead tokens on a 200k model yields 0%.
   * <p>
   * OVERHEAD_TOKENS = 34,500. effectiveUsed = 34,500 - 34,500 = 0 → 0%.
   */
  @Test
  public void scale200kOverheadTokensYields0()
  {
    int result = SharedSecrets.scaleContextPercent(34_500, 200_000);
    requireThat(result, "result").isEqualTo(0);
  }

  /**
   * Verifies that full context (200,000 tokens) on a 200k model yields 100%.
   * <p>
   * usableContext = 165,500. effectiveUsed = 200,000 - 34,500 = 165,500.
   * 165,500 * 100 / 165,500 = 100%.
   */
  @Test
  public void scale200kFullContextYields100()
  {
    int result = SharedSecrets.scaleContextPercent(200_000, 200_000);
    requireThat(result, "result").isEqualTo(100);
  }

  /**
   * Verifies that the midpoint of usable context on a 200k model yields 50%.
   * <p>
   * Midpoint = 34,500 + 165,500 / 2 = 117,250 (using integer arithmetic: 34,500 + 82,750 = 117,250).
   * effectiveUsed = 117,250 - 34,500 = 82,750. 82,750 * 100 / 165,500 = 50%.
   */
  @Test
  public void scale200kMidpointYields50()
  {
    int result = SharedSecrets.scaleContextPercent(117_250, 200_000);
    requireThat(result, "result").isEqualTo(50);
  }

  /**
   * Verifies that 0 used tokens on a 1M model yields 0% (below-overhead tokens are clamped to 0).
   * <p>
   * OVERHEAD_TOKENS = 34,500. usableContext = 1,000,000 - 34,500 = 965,500.
   * effectiveUsed = 0 - 34,500 = -34,500 → clamped to 0%.
   */
  @Test
  public void scale1MZeroTokensYields0()
  {
    int result = SharedSecrets.scaleContextPercent(0, 1_000_000);
    requireThat(result, "result").isEqualTo(0);
  }

  /**
   * Verifies that exactly overhead tokens on a 1M model yields 0%.
   * <p>
   * OVERHEAD_TOKENS = 34,500. effectiveUsed = 34,500 - 34,500 = 0 → 0%.
   */
  @Test
  public void scale1MOverheadTokensYields0()
  {
    int result = SharedSecrets.scaleContextPercent(34_500, 1_000_000);
    requireThat(result, "result").isEqualTo(0);
  }

  /**
   * Verifies that full context (1,000,000 tokens) on a 1M model yields 100%.
   * <p>
   * usableContext = 965,500. effectiveUsed = 1,000,000 - 34,500 = 965,500.
   * 965,500 * 100 / 965,500 = 100%.
   */
  @Test
  public void scale1MFullContextYields100()
  {
    int result = SharedSecrets.scaleContextPercent(1_000_000, 1_000_000);
    requireThat(result, "result").isEqualTo(100);
  }

  /**
   * Verifies that the midpoint of usable context on a 1M model yields 50%.
   * <p>
   * Midpoint = 34,500 + 965,500 / 2 = 34,500 + 482,750 = 517,250.
   * effectiveUsed = 517,250 - 34,500 = 482,750. 482,750 * 100 / 965,500 = 50%.
   */
  @Test
  public void scale1MMidpointYields50()
  {
    int result = SharedSecrets.scaleContextPercent(517_250, 1_000_000);
    requireThat(result, "result").isEqualTo(50);
  }

  /**
   * Verifies that tokens below overhead are clamped to 0% (not negative) for a 200k model.
   * <p>
   * 10,000 tokens is well below OVERHEAD_TOKENS (34,500). effectiveUsed = -24,500 → clamped to 0%.
   */
  @Test
  public void scale200kBelowOverheadClampsTo0()
  {
    int result = SharedSecrets.scaleContextPercent(10_000, 200_000);
    requireThat(result, "result").isEqualTo(0);
  }

  /**
   * Verifies that tokens exceeding the total context are clamped to 100% for a 200k model.
   * <p>
   * 300,000 tokens exceeds 200,000 total context. effectiveUsed = 265,500 → 265,500 * 100 / 165,500 > 100
   * → clamped to 100%.
   */
  @Test
  public void scale200kAboveFullContextClampedTo100()
  {
    int result = SharedSecrets.scaleContextPercent(300_000, 200_000);
    requireThat(result, "result").isEqualTo(100);
  }

  /**
   * Verifies that a 1M context window size is used when {@code context_window_size} is 1,000,000.
   * <p>
   * At 517,250 tokens on a 1M context window: usableContext = 965,500.
   * effectiveUsed = 517,250 - 34,500 = 482,750. 482,750 * 100 / 965,500 = 50%.
   * Fills 10 of 20 bar segments.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void oneMillionContextWindowSizeUsesLargerContextWindow() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude-3-7-sonnet"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {
            "current_usage": {"input_tokens": 517250},
            "context_window_size": 1000000
          }
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        // contextPercent=50 → filled = (50 * 20) / 100 = 10
        requireThat(output, "output").contains("██████████░░░░░░░░░░");
        requireThat(output, "output").contains(" 50%");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a 200,000-token context window size is used when {@code context_window_size} is 200,000.
   * <p>
   * At 117,250 tokens on a 200k context window: usableContext = 165,500.
   * effectiveUsed = 117,250 - 34,500 = 82,750. 82,750 * 100 / 165,500 = 50%.
   * Fills 10 of 20 bar segments.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void standardContextWindowSizeUses200kScale() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude-3-5-sonnet"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {
            "current_usage": {"input_tokens": 117250},
            "context_window_size": 200000
          }
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        // contextPercent=50 → filled = (50 * 20) / 100 = 10
        requireThat(output, "output").contains("██████████░░░░░░░░░░");
        requireThat(output, "output").contains(" 50%");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when {@code context_window} is absent (no context data yet), the statusline shows 0%
   * context usage without throwing.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void nullCurrentUsageBeforeFirstApiCallShowsZeroPercent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      // No context_window at all — before the first API call, Claude Code omits this field
      String json = """
        {
          "model": {"display_name": "claude-3-5-sonnet"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000}
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        requireThat(output, "output").contains("  0%");
        requireThat(output, "output").contains("░░░░░░░░░░░░░░░░░░░░");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a null current_usage with a valid context_window_size shows 0% usage.
   * <p>
   * When current_usage is absent, there is no token consumption to report, so the output must show 0%.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void nullCurrentUsageShowsZeroPercent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {
            "context_window_size": 200000
          }
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        requireThat(output, "output").contains("0%");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a missing context_window_size throws IllegalArgumentException with a message indicating the
   * field is missing or non-positive.
   * <p>
   * Without context_window_size, the percentage calculation has no denominator and must fail fast.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = "(?s).*missing or non-positive.*")
  public void missingContextWindowSizeThrowsIllegalArgumentException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {
            "current_usage": {"input_tokens": 100000}
          }
        }
        """;
      new TestClaudeStatusline(tempDir, tempDir, json).close();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a context_window_size of zero throws IllegalArgumentException with a message indicating the
   * field is missing or non-positive.
   * <p>
   * Zero is not a valid denominator for percentage calculation.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = "(?s).*missing or non-positive.*")
  public void zeroContextWindowSizeThrowsIllegalArgumentException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {
            "current_usage": {"input_tokens": 100000},
            "context_window_size": 0
          }
        }
        """;
      new TestClaudeStatusline(tempDir, tempDir, json).close();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a negative context_window_size throws IllegalArgumentException with a message indicating the
   * field is missing or non-positive.
   * <p>
   * Negative values are not valid for a context window size.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = "(?s).*missing or non-positive.*")
  public void negativeContextWindowSizeThrowsIllegalArgumentException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {
            "current_usage": {"input_tokens": 100000},
            "context_window_size": -1
          }
        }
        """;
      new TestClaudeStatusline(tempDir, tempDir, json).close();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a current_usage object missing input_tokens throws IllegalArgumentException with a message
   * indicating the field is missing.
   * <p>
   * Without input_tokens there is no token count to use as a numerator, so the constructor must fail fast.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = "(?s).*missing.*")
  public void missingInputTokensThrowsIllegalArgumentException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {
            "current_usage": {},
            "context_window_size": 200000
          }
        }
        """;
      new TestClaudeStatusline(tempDir, tempDir, json).close();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
