/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.util.StatuslineCommand;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.testng.annotations.Test;

/**
 * Tests for StatuslineCommand.
 */
public final class StatuslineCommandTest
{
  /**
   * Executes the statusline command with the given JSON and directory, returning the output string.
   *
   * @param scope     the JVM scope
   * @param json      the JSON input
   * @param directory the directory to use for git operations
   * @return the statusline output
   * @throws IOException if an I/O error occurs
   */
  private static String executeWithDirectory(TestJvmScope scope, String json, Path directory) throws IOException
  {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(outputStream, true, StandardCharsets.UTF_8);
    StatuslineCommand cmd = new StatuslineCommand(scope);
    cmd.execute(inputStream, printStream, directory);
    return outputStream.toString(StandardCharsets.UTF_8);
  }

  /**
   * Verifies that the statusline output contains git info, model name, duration, full session ID, and usage
   * bar when valid JSON input is provided.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void validInputContainsAllComponents() throws IOException
  {
    Path gitRepo = TestUtils.createTempGitRepo("main");
    try (TestJvmScope scope = new TestJvmScope(gitRepo, gitRepo))
    {
      String json = """
        {
          "model": {"display_name": "claude-3-5-sonnet"},
          "session_id": "abcdef12-1234-5678-abcd-ef1234567890",
          "cost": {"total_duration_ms": 125000},
          "context_window": {"used_percentage": 45}
        }
        """;
      String output = executeWithDirectory(scope, json, gitRepo);
      requireThat(output, "output").contains("claude-3-5-sonnet");
      requireThat(output, "output").contains("abcdef12-1234-5678-abcd-ef1234567890");
      requireThat(output, "output").contains("00:02");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(gitRepo);
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
    Path gitRepo = TestUtils.createTempGitRepo("main");
    try (TestJvmScope scope = new TestJvmScope(gitRepo, gitRepo))
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 45000},
          "context_window": {"used_percentage": 0}
        }
        """;
      String output = executeWithDirectory(scope, json, gitRepo);
      requireThat(output, "output").contains("00:00");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(gitRepo);
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
    Path gitRepo = TestUtils.createTempGitRepo("main");
    try (TestJvmScope scope = new TestJvmScope(gitRepo, gitRepo))
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 3725000},
          "context_window": {"used_percentage": 0}
        }
        """;
      String output = executeWithDirectory(scope, json, gitRepo);
      // 3725000ms = 3725 seconds = 62 minutes 5 seconds → 1 hour 2 minutes → 01:02
      requireThat(output, "output").contains("01:02");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(gitRepo);
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
    Path gitRepo = TestUtils.createTempGitRepo("main");
    try (TestJvmScope scope = new TestJvmScope(gitRepo, gitRepo))
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": 85}
        }
        """;
      String output = executeWithDirectory(scope, json, gitRepo);
      // contextPct = (85 * 1000) / 835 = 101, clamped to 100 → pure red: \033[38;2;255;0;0m
      requireThat(output, "output").contains("\033[38;2;255;0;0m");
      requireThat(output, "output").contains("100%");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(gitRepo);
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
    Path gitRepo = TestUtils.createTempGitRepo("main");
    try (TestJvmScope scope = new TestJvmScope(gitRepo, gitRepo))
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": 65}
        }
        """;
      String output = executeWithDirectory(scope, json, gitRepo);
      // contextPct = (65 * 1000) / 835 = 77 → red=255, green=((100-77)*255)/50 = 117 → \033[38;2;255;117;0m
      requireThat(output, "output").contains("\033[38;2;255;117;0m");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(gitRepo);
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
    Path gitRepo = TestUtils.createTempGitRepo("main");
    try (TestJvmScope scope = new TestJvmScope(gitRepo, gitRepo))
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": 30}
        }
        """;
      String output = executeWithDirectory(scope, json, gitRepo);
      // contextPct = (30 * 1000) / 835 = 35 → red=(35*255)/50 = 178, green=255 → \033[38;2;178;255;0m
      requireThat(output, "output").contains("\033[38;2;178;255;0m");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(gitRepo);
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
    Path gitRepo = TestUtils.createTempGitRepo("main");
    try (TestJvmScope scope = new TestJvmScope(gitRepo, gitRepo))
    {
      String json = "{}";
      String output = executeWithDirectory(scope, json, gitRepo);
      requireThat(output, "output").contains("unknown");
      requireThat(output, "output").contains("00:00");
      // contextPct=0 → right-justified: "  0%"
      requireThat(output, "output").contains("  0%");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(gitRepo);
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
    Path gitRepo = TestUtils.createTempGitRepo("main");
    try (TestJvmScope scope = new TestJvmScope(gitRepo, gitRepo))
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": 150}
        }
        """;
      String output = executeWithDirectory(scope, json, gitRepo);
      requireThat(output, "output").contains("100%");
      requireThat(output, "output").doesNotContain("150%");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(gitRepo);
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
    Path gitRepo = TestUtils.createTempGitRepo("main");
    try (TestJvmScope scope = new TestJvmScope(gitRepo, gitRepo))
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": 100}
        }
        """;
      String output = executeWithDirectory(scope, json, gitRepo);
      // 20 filled blocks
      requireThat(output, "output").contains("████████████████████");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(gitRepo);
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
    Path gitRepo = TestUtils.createTempGitRepo("main");
    try (TestJvmScope scope = new TestJvmScope(gitRepo, gitRepo))
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "12345678-abcd-ef01-2345-678901234567",
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": 0}
        }
        """;
      String output = executeWithDirectory(scope, json, gitRepo);
      requireThat(output, "output").contains("12345678-abcd-ef01-2345-678901234567");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(gitRepo);
    }
  }

  /**
   * Verifies that a raw usage percentage of 50% is scaled to ~59% context and uses an orange-red RGB color.
   * At 50% raw, contextPct = (50 * 1000) / 835 = 59, which is above 50, so red=255.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void usageAt50PercentScalesAbove50() throws IOException
  {
    Path gitRepo = TestUtils.createTempGitRepo("main");
    try (TestJvmScope scope = new TestJvmScope(gitRepo, gitRepo))
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": 50}
        }
        """;
      String output = executeWithDirectory(scope, json, gitRepo);
      // contextPct = (50 * 1000) / 835 = 59 → red=255, green=((100-59)*255)/50 = 209 → \033[38;2;255;209;0m
      requireThat(output, "output").contains("\033[38;2;255;209;0m");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(gitRepo);
    }
  }

  /**
   * Verifies that a raw usage percentage of 80% is scaled to ~95% context and uses a near-red RGB color.
   * At 80% raw, contextPct = (80 * 1000) / 835 = 95, which produces a mostly-red color.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void usageAt80PercentScalesNearRed() throws IOException
  {
    Path gitRepo = TestUtils.createTempGitRepo("main");
    try (TestJvmScope scope = new TestJvmScope(gitRepo, gitRepo))
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": 80}
        }
        """;
      String output = executeWithDirectory(scope, json, gitRepo);
      // contextPct = (80 * 1000) / 835 = 95 → red=255, green=((100-95)*255)/50 = 25 → \033[38;2;255;25;0m
      requireThat(output, "output").contains("\033[38;2;255;25;0m");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(gitRepo);
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
    Path gitRepo = TestUtils.createTempGitRepo("main");
    try (TestJvmScope scope = new TestJvmScope(gitRepo, gitRepo))
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": 0}
        }
        """;
      String output = executeWithDirectory(scope, json, gitRepo);
      // All 20 segments should be empty
      requireThat(output, "output").contains("░░░░░░░░░░░░░░░░░░░░");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(gitRepo);
    }
  }

  /**
   * Verifies that a usage percentage of 50% raw (contextPct=59) produces 11 filled and 9 empty segments.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void usageBarAt50PercentRaw() throws IOException
  {
    Path gitRepo = TestUtils.createTempGitRepo("main");
    try (TestJvmScope scope = new TestJvmScope(gitRepo, gitRepo))
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": 50}
        }
        """;
      String output = executeWithDirectory(scope, json, gitRepo);
      // contextPct = (50 * 1000) / 835 = 59 → filled = (59 * 20) / 100 = 11
      // 11 filled followed by 9 empty segments
      requireThat(output, "output").contains("███████████░░░░░░░░░");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(gitRepo);
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
    Path gitRepo = TestUtils.createTempGitRepo("main");
    try (TestJvmScope scope = new TestJvmScope(gitRepo, gitRepo))
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": -5000},
          "context_window": {"used_percentage": 0}
        }
        """;
      String output = executeWithDirectory(scope, json, gitRepo);
      requireThat(output, "output").contains("00:00");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(gitRepo);
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
    Path gitRepo = TestUtils.createTempGitRepo("main");
    try (TestJvmScope scope = new TestJvmScope(gitRepo, gitRepo))
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": -10}
        }
        """;
      String output = executeWithDirectory(scope, json, gitRepo);
      // contextPct=0 → right-justified: "  0%"
      requireThat(output, "output").contains("  0%");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(gitRepo);
    }
  }

  /**
   * Verifies that all 5 statusline emojis are present in the output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void outputContainsAllFiveEmojis() throws IOException
  {
    Path gitRepo = TestUtils.createTempGitRepo("main");
    try (TestJvmScope scope = new TestJvmScope(gitRepo, gitRepo))
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": 50}
        }
        """;
      String output = executeWithDirectory(scope, json, gitRepo);
      requireThat(output, "output").contains("🌿");
      requireThat(output, "output").contains("🤖");
      requireThat(output, "output").contains("⏰");
      requireThat(output, "output").contains("🆔");
      requireThat(output, "output").contains("📊");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(gitRepo);
    }
  }

  /**
   * Verifies that the output contains all 4 component ANSI color escape sequences.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void outputContainsAllComponentColors() throws IOException
  {
    Path gitRepo = TestUtils.createTempGitRepo("main");
    try (TestJvmScope scope = new TestJvmScope(gitRepo, gitRepo))
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": 50}
        }
        """;
      String output = executeWithDirectory(scope, json, gitRepo);
      // WORKTREE_COLOR: Bright White
      requireThat(output, "output").contains("\033[38;2;255;255;255m");
      // MODEL_COLOR: Warm Gold
      requireThat(output, "output").contains("\033[38;2;220;150;9m");
      // TIME_COLOR: Coral
      requireThat(output, "output").contains("\033[38;2;255;127;80m");
      // SESSION_COLOR: Medium Purple
      requireThat(output, "output").contains("\033[38;2;147;112;219m");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(gitRepo);
    }
  }

  /**
   * Verifies the 83.5% scaling threshold boundary: used_percentage=84 yields contextPct=100 (all 20 bar
   * segments filled and "100%" displayed), while used_percentage=83 yields contextPct=99 (19 filled segments
   * and " 99%" displayed).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void scalingThresholdBoundaryAt835Percent() throws IOException
  {
    Path gitRepo = TestUtils.createTempGitRepo("main");
    try (TestJvmScope scope = new TestJvmScope(gitRepo, gitRepo))
    {
      // used_percentage=84: contextPct = (84 * 1000) / 835 = 100 (truncated), clamped to 100
      String jsonAt84 = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": 84}
        }
        """;
      String output84 = executeWithDirectory(scope, jsonAt84, gitRepo);
      // contextPct=100 → all 20 segments filled
      requireThat(output84, "output84").contains("████████████████████");
      requireThat(output84, "output84").contains("100%");

      // used_percentage=83: contextPct = (83 * 1000) / 835 = 99 (truncated)
      String jsonAt83 = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": 83}
        }
        """;
      String output83 = executeWithDirectory(scope, jsonAt83, gitRepo);
      // contextPct=99 → filled = (99 * 20) / 100 = 19, so 19 filled and 1 empty
      requireThat(output83, "output83").contains("███████████████████░");
      // contextPct=99 → right-justified: " 99%"
      requireThat(output83, "output83").contains(" 99%");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(gitRepo);
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
    Path gitRepo = TestUtils.createTempGitRepo("main");
    try (TestJvmScope scope = new TestJvmScope(gitRepo, gitRepo))
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "abc123",
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": 0}
        }
        """;
      String output = executeWithDirectory(scope, json, gitRepo);
      requireThat(output, "output").contains("abc123");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(gitRepo);
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
    Path gitRepo = TestUtils.createTempGitRepo("main");
    try (TestJvmScope scope = new TestJvmScope(gitRepo, gitRepo))
    {
      String json = """
        {
          "model": {"display_name": null},
          "session_id": null,
          "cost": {"total_duration_ms": null},
          "context_window": {"used_percentage": null}
        }
        """;
      String output = executeWithDirectory(scope, json, gitRepo);
      requireThat(output, "output").contains("unknown");
      requireThat(output, "output").contains("00:00");
      // contextPct=0 → right-justified: "  0%"
      requireThat(output, "output").contains("  0%");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(gitRepo);
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
    Path gitRepo = TestUtils.createTempGitRepo("main");
    try (TestJvmScope scope = new TestJvmScope(gitRepo, gitRepo))
    {
      String json = "not valid json at all !!!";
      String output = executeWithDirectory(scope, json, gitRepo);
      requireThat(output, "output").contains("unknown");
      requireThat(output, "output").contains("00:00");
      // contextPct=0 → right-justified: "  0%"
      requireThat(output, "output").contains("  0%");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(gitRepo);
    }
  }

  /**
   * Verifies that the git info shows the repository directory name when run inside a git repository.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void gitInfoShowsRepositoryDirectoryName() throws IOException
  {
    Path gitRepo = TestUtils.createTempGitRepo("main");
    try (TestJvmScope scope = new TestJvmScope(gitRepo, gitRepo))
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": 0}
        }
        """;
      String output = executeWithDirectory(scope, json, gitRepo);
      // Git info should be the basename of the temp repo directory
      String expectedDirName = gitRepo.getFileName().toString();
      requireThat(output, "output").contains(expectedDirName);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(gitRepo);
    }
  }

  /**
   * Verifies that the git info shows "N/A" when the directory is not inside a git repository.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void gitInfoShowsNaWhenNotInGitRepo() throws IOException
  {
    Path nonGitDir = java.nio.file.Files.createTempDirectory("statusline-nogit-");
    try (TestJvmScope scope = new TestJvmScope(nonGitDir, nonGitDir))
    {
      String json = """
        {
          "model": {"display_name": "claude"},
          "session_id": "00000000-0000-0000-0000-000000000000",
          "cost": {"total_duration_ms": 1000},
          "context_window": {"used_percentage": 0}
        }
        """;
      String output = executeWithDirectory(scope, json, nonGitDir);
      requireThat(output, "output").contains("N/A");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(nonGitDir);
    }
  }
}
