/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.ClaudeStatusline;
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
          "cost": {"total_duration_ms": 125000},
          "context_window": {"used_percentage": 45}
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
          "cost": {"total_duration_ms": 45000},
          "context_window": {"used_percentage": 0}
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
          "cost": {"total_duration_ms": 3725000},
          "context_window": {"used_percentage": 0}
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
   * Verifies that a usage percentage above 80% uses an RGB red color code in ANSI output.
   * After context scaling (85% raw * 1000/835 = 101, clamped to 100), the bar color is pure red RGB.
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
          "context_window": {"used_percentage": 85}
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        // contextPercent = (85 * 1000) / 835 = 101, clamped to 100 → pure red: \033[38;2;255;0;0m
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
   * Verifies that a usage percentage between 50% and 80% uses an RGB orange-red color code.
   * After context scaling (65% raw * 1000/835 = 77), the bar color is a warm orange-red.
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
          "context_window": {"used_percentage": 65}
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        // contextPercent = (65 * 1000) / 835 = 77 → red=255, green=((100-77)*255)/50 = 117 → \033[38;2;255;117;0m
        requireThat(output, "output").contains("\033[38;2;255;117;0m");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a usage percentage under 50% uses an RGB green-yellow color code.
   * After context scaling (30% raw * 1000/835 = 35), the bar color is a yellow-green.
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
          "context_window": {"used_percentage": 30}
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        // contextPercent = (30 * 1000) / 835 = 35 → red=(35*255)/50 = 178, green=255 → \033[38;2;178;255;0m
        requireThat(output, "output").contains("\033[38;2;178;255;0m");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that missing JSON fields use default values gracefully.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void emptyJsonObjectUsesDefaults() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = "{}";
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
   * Verifies that a usage percentage exceeding 100 is clamped to 100 after context scaling.
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
          "context_window": {"used_percentage": 150}
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
   * Verifies that the usage bar shows 20 filled segments when context usage is 100%.
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
          "context_window": {"used_percentage": 100}
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
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": 0}
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
   * Verifies that a raw usage percentage of 50% is scaled to ~59% context and uses an orange-red RGB color.
   * At 50% raw, contextPercent = (50 * 1000) / 835 = 59, which is above 50, so red=255.
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
          "context_window": {"used_percentage": 50}
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        // contextPercent = (50 * 1000) / 835 = 59 → red=255, green=((100-59)*255)/50 = 209 → \033[38;2;255;209;0m
        requireThat(output, "output").contains("\033[38;2;255;209;0m");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a raw usage percentage of 80% is scaled to ~95% context and uses a near-red RGB color.
   * At 80% raw, contextPercent = (80 * 1000) / 835 = 95, which produces a mostly-red color.
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
          "context_window": {"used_percentage": 80}
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        // contextPercent = (80 * 1000) / 835 = 95 → red=255, green=((100-95)*255)/50 = 25 → \033[38;2;255;25;0m
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
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": 0}
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
   * Verifies that a usage percentage of 50% raw (contextPercent=59) produces 11 filled and 9 empty segments.
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
          "context_window": {"used_percentage": 50}
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
        // contextPercent = (50 * 1000) / 835 = 59 → filled = (59 * 20) / 100 = 11
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
          "cost": {"total_duration_ms": -5000},
          "context_window": {"used_percentage": 0}
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
   * Verifies that a negative usage percentage is clamped to 0, resulting in "  0%" (right-justified) in the output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void negativeUsagePercentageClampedToZero() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": -10}
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, json))
      {
        String output = executeWithLockDir(scope, tempDir);
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
            "context_window": {"used_percentage": 50}
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
            "context_window": {"used_percentage": 50}
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
   * Verifies the 83.5% scaling threshold boundary: used_percentage=84 yields contextPercent=100 (all 20 bar
   * segments filled and "100%" displayed), while used_percentage=83 yields contextPercent=99 (19 filled segments
   * and " 99%" displayed).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void scalingThresholdBoundaryAt835Percent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      // used_percentage=84: contextPercent = (84 * 1000) / 835 = 100 (truncated), clamped to 100
      String jsonAt84 = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": 84}
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, jsonAt84))
      {
        String output84 = executeWithLockDir(scope, tempDir);
        // contextPercent=100 → all 20 segments filled
        requireThat(output84, "output84").contains("████████████████████");
        requireThat(output84, "output84").contains("100%");
      }

      // used_percentage=83: contextPercent = (83 * 1000) / 835 = 99 (truncated)
      String jsonAt83 = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": 83}
        }
        """;
      try (ClaudeStatusline scope = new TestClaudeStatusline(tempDir, tempDir, jsonAt83))
      {
        String output83 = executeWithLockDir(scope, tempDir);
        // contextPercent=99 → filled = (99 * 20) / 100 = 19, so 19 filled and 1 empty
        requireThat(output83, "output83").contains("███████████████████░");
        // contextPercent=99 → right-justified: " 99%"
        requireThat(output83, "output83").contains(" 99%");
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
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": 0}
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
   * Verifies that explicit null JSON field values produce the same defaults as missing fields.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void nullJsonFieldsUseDefaults() throws IOException
  {
    Path tempDir = Files.createTempDirectory("cat-");
    try
    {
      String json = """
        {
          "model": {"display_name": null},
          "session_id": null,
          "cost": {"total_duration_ms": null},
          "context_window": {"used_percentage": null}
        }
        """;
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
          "cost": {"total_duration_ms": 0},
          "context_window": {"used_percentage": 0}
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
            "cost": {"total_duration_ms": 0},
            "context_window": {"used_percentage": 0}
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
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": 0}
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
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": 0}
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
          "context_window": {"used_percentage": 45}
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
          "context_window": {"used_percentage": 75}
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
          "context_window": {"used_percentage": 45}
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
          "context_window": {"used_percentage": 45}
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
}
