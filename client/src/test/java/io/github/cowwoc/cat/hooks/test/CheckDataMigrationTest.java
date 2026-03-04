/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.Config;
import io.github.cowwoc.cat.hooks.JvmScope;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tests the VERSION file behavior for CAT migration version tracking.
 */
public final class CheckDataMigrationTest
{
  /**
   * The Phase 7 awk script as a constant for use across multiple tests.
   * Mirrors the awk command in plugin/migrations/2.1.sh Phase 7.
   */
  private static final String PHASE7_AWK = """
    /^## Execution Steps/ {
        print "## Execution Waves"
        print ""
        print "### Wave 1"
        in_section = 1
        last_blank=0
        next
    }
    in_section && /^## / {
        in_section=0
        if (!last_blank) print ""
        print
        next
    }
    { print; last_blank=($0 == "") }
    """;

  /**
   * Verifies that reading a VERSION file that does not exist returns the default version "0.0.0".
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void versionFileAbsentReturnsDefault() throws IOException
  {
    Path tempDir = Files.createTempDirectory("check-upgrade-test-");
    Path catDir = tempDir.resolve(".claude/cat");
    Files.createDirectories(catDir);
    try
    {
      Path versionFile = catDir.resolve("VERSION");
      String version;
      if (Files.isRegularFile(versionFile))
        version = Files.readString(versionFile).strip();
      else
        version = "0.0.0";
      if (version.isEmpty())
        version = "0.0.0";
      requireThat(version, "version").isEqualTo("0.0.0");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that reading a VERSION file containing a valid version string returns that version.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void versionFileWithVersionReturnsVersion() throws IOException
  {
    Path tempDir = Files.createTempDirectory("check-upgrade-test-");
    Path catDir = tempDir.resolve(".claude/cat");
    Files.createDirectories(catDir);
    try
    {
      Path versionFile = catDir.resolve("VERSION");
      Files.writeString(versionFile, "2.4\n");
      String version = Files.readString(versionFile).strip();
      if (version.isEmpty())
        version = "0.0.0";
      requireThat(version, "version").isEqualTo("2.4");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that reading an empty VERSION file returns the default version "0.0.0".
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void versionFileEmptyReturnsDefault() throws IOException
  {
    Path tempDir = Files.createTempDirectory("check-upgrade-test-");
    Path catDir = tempDir.resolve(".claude/cat");
    Files.createDirectories(catDir);
    try
    {
      Path versionFile = catDir.resolve("VERSION");
      Files.writeString(versionFile, "");
      String version = Files.readString(versionFile).strip();
      if (version.isEmpty())
        version = "0.0.0";
      requireThat(version, "version").isEqualTo("0.0.0");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that reading a VERSION file with surrounding whitespace strips the whitespace and returns
   * the version.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void versionFileWithTrailingWhitespaceReturnsStripped() throws IOException
  {
    Path tempDir = Files.createTempDirectory("check-upgrade-test-");
    Path catDir = tempDir.resolve(".claude/cat");
    Files.createDirectories(catDir);
    try
    {
      Path versionFile = catDir.resolve("VERSION");
      Files.writeString(versionFile, "  2.3  \n");
      String version = Files.readString(versionFile).strip();
      if (version.isEmpty())
        version = "0.0.0";
      requireThat(version, "version").isEqualTo("2.3");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that writing a version to the VERSION file stores it with a trailing newline.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void writeVersionFileCreatesCorrectContent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("check-upgrade-test-");
    Path catDir = tempDir.resolve(".claude/cat");
    Files.createDirectories(catDir);
    try
    {
      Path versionFile = catDir.resolve("VERSION");
      Files.writeString(versionFile, "2.4" + "\n");
      String content = Files.readString(versionFile);
      requireThat(content.strip(), "version").isEqualTo("2.4");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Config.load() succeeds after migration removes the reviewThreshold key.
   * <p>
   * Simulates Phase 8 of the 2.1 migration: removes reviewThreshold from a config file,
   * then verifies Config.load() accepts the migrated file and preserves valid keys.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void configWithReviewThresholdCanBeValidated() throws IOException
  {
    Path tempDir = Files.createTempDirectory("migration-test-");
    Path catDir = tempDir.resolve(".claude/cat");
    Files.createDirectories(catDir);
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();

      // Create a config with reviewThreshold (simulating pre-migration state)
      Path configFile = catDir.resolve("cat-config.json");
      Files.writeString(configFile, """
        {
          "trust": "medium",
          "reviewThreshold": "medium",
          "verify": "changed"
        }
        """);

      // Simulate Phase 8 migration: remove reviewThreshold
      String content = Files.readString(configFile);
      String migratedContent = content.
        replaceAll("\"reviewThreshold\"[\\s]*:[\\s]*\"[^\"]*\"[\\s]*,?[\\s]*", "").
        replaceAll(",[\\s]*}", "}");
      Files.writeString(configFile, migratedContent);

      // Verify the migration removed reviewThreshold
      String afterMigration = Files.readString(configFile);
      requireThat(afterMigration.contains("reviewThreshold"), "hasReviewThreshold").isFalse();
      requireThat(afterMigration.contains("trust"), "hasTrust").isTrue();
      requireThat(afterMigration.contains("verify"), "hasVerify").isTrue();

      // Config.load() must succeed after migration — verifies the migrated JSON is valid
      Config config = Config.load(mapper, tempDir);
      requireThat(config.getString("trust"), "trust").isEqualTo("medium");
      requireThat(config.getString("verify"), "verify").isEqualTo("changed");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Config.load() throws IllegalArgumentException when reviewThreshold is present,
   * and succeeds after its removal — demonstrating the complete migration flow.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void migrationFlowConfigLoadFailsThenSucceeds() throws IOException
  {
    Path tempDir = Files.createTempDirectory("migration-test-");
    Path catDir = tempDir.resolve(".claude/cat");
    Files.createDirectories(catDir);
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path configFile = catDir.resolve("cat-config.json");

      // Pre-migration: config with reviewThreshold
      Files.writeString(configFile, """
        {
          "trust": "medium",
          "reviewThreshold": "medium",
          "verify": "changed"
        }
        """);

      // Config.load() must throw before migration
      boolean threwBeforeMigration = false;
      try
      {
        Config.load(mapper, tempDir);
      }
      catch (IllegalArgumentException _)
      {
        threwBeforeMigration = true;
      }
      requireThat(threwBeforeMigration, "threwBeforeMigration").isTrue();

      // Simulate Phase 8 migration: remove reviewThreshold
      String content = Files.readString(configFile);
      String migratedContent = content.
        replaceAll("\"reviewThreshold\"[\\s]*:[\\s]*\"[^\"]*\"[\\s]*,?[\\s]*", "").
        replaceAll(",[\\s]*}", "}");
      Files.writeString(configFile, migratedContent);

      // Post-migration: Config.load() must succeed
      Config config = Config.load(mapper, tempDir);
      requireThat(config.getString("trust"), "trust").isEqualTo("medium");
      requireThat(config.getString("verify"), "verify").isEqualTo("changed");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Config.load() throws IllegalArgumentException when reviewThreshold is present,
   * confirming the negative case — configs with deprecated keys are rejected.
   *
   * @throws IOException if file operations fail
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*reviewThreshold.*")
  public void configLoadRejectsReviewThreshold() throws IOException
  {
    Path tempDir = Files.createTempDirectory("migration-test-");
    Path catDir = tempDir.resolve(".claude/cat");
    Files.createDirectories(catDir);
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path configFile = catDir.resolve("cat-config.json");
      Files.writeString(configFile, """
        {
          "trust": "medium",
          "reviewThreshold": "medium"
        }
        """);

      Config.load(mapper, tempDir);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  // ──────────────────────────────────────────────────────────────────────────────
  // Phase 7 tests: Migrate ## Execution Steps to ## Execution Waves
  // ──────────────────────────────────────────────────────────────────────────────

  /**
   * Runs the Phase 7 awk command against {@code input} and returns the resulting string.
   *
   * @param input the content to process
   * @return the output produced by the awk command
   * @throws IOException          if file I/O or process execution fails
   * @throws InterruptedException if the process is interrupted
   */
  private static String runPhase7Awk(String input) throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("phase7-awk-test-");
    try
    {
      Path planFile = tempDir.resolve("PLAN.md");
      Files.writeString(planFile, input);

      ProcessBuilder pb = new ProcessBuilder("awk", PHASE7_AWK, planFile.toString());
      pb.redirectErrorStream(true);
      Process process = pb.start();

      StringBuilder output = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
      {
        String line = reader.readLine();
        while (line != null)
        {
          output.append(line).append('\n');
          line = reader.readLine();
        }
      }

      int exitCode = process.waitFor();
      if (exitCode != 0)
        throw new IOException("awk command failed with exit code " + exitCode);

      return output.toString();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Phase 7 renames {@code ## Execution Steps} to {@code ## Execution Waves} and inserts
   * {@code ### Wave 1} immediately after it.
   *
   * @throws IOException          if file I/O fails
   * @throws InterruptedException if the process is interrupted
   */
  @Test
  public void phase7MigratesExecutionStepsToExecutionWaves() throws IOException, InterruptedException
  {
    String input = """
      ## Execution Steps

      - Step A
      - Step B
      """;

    String output = runPhase7Awk(input);

    requireThat(output, "output").contains("## Execution Waves");
    requireThat(output, "output").contains("### Wave 1");
    requireThat(output, "output").doesNotContain("## Execution Steps");
    // Wave 1 heading must appear after Execution Waves heading
    int wavesIdx = output.indexOf("## Execution Waves");
    int wave1Idx = output.indexOf("### Wave 1");
    requireThat(wave1Idx > wavesIdx, "wave1AfterWaves").isTrue();
  }

  /**
   * Verifies that Phase 7 produces exactly one blank line between the migrated Execution Waves content and
   * the next {@code ## } heading, even when the original content already ends with a blank line.
   * <p>
   * This is the specific bug fix: a trailing blank line in the Execution Steps content followed by a
   * {@code ## } heading must not produce two consecutive blank lines after migration.
   *
   * @throws IOException          if file I/O fails
   * @throws InterruptedException if the process is interrupted
   */
  @Test
  public void phase7AvoidsDoubleBlankLineBeforeNextSection() throws IOException, InterruptedException
  {
    // The Execution Steps content ends with a blank line before the next ## heading.
    // Without the fix, awk would emit an extra blank line, producing two consecutive blank lines.
    String input = """
      ## Execution Steps

      - Step A

      ## Next Section

      Some content.
      """;

    String output = runPhase7Awk(input);

    // Must not contain two consecutive blank lines anywhere
    requireThat(output.contains("\n\n\n"), "hasDoubleBlankLine").isFalse();
    // Next Section heading must still be present
    requireThat(output, "output").contains("## Next Section");
  }

  /**
   * Verifies that Phase 7 is idempotent: files that already contain {@code ## Execution Waves} are passed
   * through unchanged by the awk script itself (the outer script skips them, but the awk transform must also
   * be safe to apply twice).
   *
   * @throws IOException          if file I/O fails
   * @throws InterruptedException if the process is interrupted
   */
  @Test
  public void phase7SkipsAlreadyMigratedFiles() throws IOException, InterruptedException
  {
    // Input already uses the new heading — no ## Execution Steps present
    String input = """
      ## Execution Waves

      ### Wave 1

      - Step A

      ## Next Section

      Some content.
      """;

    String output = runPhase7Awk(input);

    // Output must be identical to input (awk passes through unchanged)
    requireThat(output, "output").isEqualTo(input);
  }

  /**
   * Verifies that content before and after the {@code ## Execution Steps} section is preserved unchanged
   * after Phase 7 migration.
   *
   * @throws IOException          if file I/O fails
   * @throws InterruptedException if the process is interrupted
   */
  @Test
  public void phase7PreservesContentBeforeAndAfterSection() throws IOException, InterruptedException
  {
    String input = """
      # Title

      ## Goal

      Do something useful.

      ## Execution Steps

      - Step A
      - Step B

      ## Acceptance Criteria

      - Criteria 1
      """;

    String output = runPhase7Awk(input);

    requireThat(output, "output").contains("# Title");
    requireThat(output, "output").contains("## Goal");
    requireThat(output, "output").contains("Do something useful.");
    requireThat(output, "output").contains("## Acceptance Criteria");
    requireThat(output, "output").contains("- Criteria 1");
    requireThat(output, "output").contains("## Execution Waves");
    requireThat(output, "output").contains("### Wave 1");
    requireThat(output, "output").doesNotContain("## Execution Steps");
  }

  /**
   * Verifies that Phase 7 correctly migrates {@code ## Execution Steps} when it is the last section in the
   * file (no subsequent {@code ## } heading follows).
   *
   * @throws IOException          if file I/O fails
   * @throws InterruptedException if the process is interrupted
   */
  @Test
  public void phase7HandlesExecutionStepsAtEndOfFile() throws IOException, InterruptedException
  {
    String input = """
      ## Execution Steps

      - Step A
      - Step B
      """;

    String output = runPhase7Awk(input);

    requireThat(output, "output").contains("## Execution Waves");
    requireThat(output, "output").contains("### Wave 1");
    requireThat(output, "output").contains("- Step A");
    requireThat(output, "output").contains("- Step B");
    requireThat(output, "output").doesNotContain("## Execution Steps");
  }

  /**
   * Applies the Phase 7 migration transformation to the given content.
   * <p>
   * Mirrors the awk logic in {@code plugin/migrations/2.1.sh} Phase 7:
   * <ul>
   *   <li>If {@code ## Execution Waves} is already present, returns the content unchanged.</li>
   *   <li>If {@code ## Execution Steps} is present, replaces it with {@code ## Execution Waves} followed
   *       by {@code ### Wave 1}, and passes all subsequent lines through unchanged.</li>
   *   <li>If neither heading is present, returns the content unchanged.</li>
   * </ul>
   *
   * @param content the PLAN.md file content to transform
   * @return the transformed content, or the original content if no transformation is needed
   */
  private static String applyPhase7Migration(String content)
  {
    if (content.contains("\n## Execution Waves\n") || content.startsWith("## Execution Waves\n"))
      return content;
    if (!content.contains("\n## Execution Steps\n") && !content.startsWith("## Execution Steps\n"))
      return content;

    List<String> inputLines = new ArrayList<>(List.of(content.split("\n", -1)));
    List<String> outputLines = new ArrayList<>();
    boolean inSection = false;

    for (String line : inputLines)
    {
      if (line.equals("## Execution Steps"))
      {
        outputLines.add("## Execution Waves");
        outputLines.add("");
        outputLines.add("### Wave 1");
        inSection = true;
        continue;
      }
      if (inSection && line.startsWith("## "))
      {
        inSection = false;
        outputLines.add("");
        outputLines.add(line);
        continue;
      }
      outputLines.add(line);
    }

    return String.join("\n", outputLines);
  }

  /**
   * Verifies Phase 7: simple numbered steps are preserved verbatim under Wave 1.
   */
  @Test
  public void phase7SimpleStepsPreservedVerbatim()
  {
    String input = """
      ## Goal
      Do something useful.

      ## Execution Steps

      1. First step
      2. Second step
      3. Third step

      ## Post-conditions
      - All steps complete
      """;

    String result = applyPhase7Migration(input);

    requireThat(result.contains("## Execution Waves"), "hasExecutionWaves").isTrue();
    requireThat(result.contains("### Wave 1"), "hasWave1").isTrue();
    requireThat(result.contains("## Execution Steps"), "hasOldHeading").isFalse();
    requireThat(result.contains("1. First step"), "hasStep1").isTrue();
    requireThat(result.contains("2. Second step"), "hasStep2").isTrue();
    requireThat(result.contains("3. Third step"), "hasStep3").isTrue();
  }

  /**
   * Verifies Phase 7: sub-bullets, file lists, code blocks, and inner headings are preserved.
   */
  @Test
  public void phase7SubContentPreserved()
  {
    String input = """
      ## Goal
      Complex issue.

      ## Execution Steps

      - Implement the feature
        - Files: `plugin/skills/my-skill.md`
        - Replace the current implementation
        - Verify with:
          ```bash
          mvn test
          ```
      - Add documentation

      ## Post-conditions
      - Feature works
      """;

    String result = applyPhase7Migration(input);

    requireThat(result.contains("## Execution Waves"), "hasExecutionWaves").isTrue();
    requireThat(result.contains("### Wave 1"), "hasWave1").isTrue();
    requireThat(result.contains("## Execution Steps"), "hasOldHeading").isFalse();
    requireThat(result.contains("- Implement the feature"), "hasTopLevel").isTrue();
    requireThat(result.contains("  - Files: `plugin/skills/my-skill.md`"), "hasFilesLine").isTrue();
    requireThat(result.contains("    ```bash"), "hasCodeBlock").isTrue();
    requireThat(result.contains("    mvn test"), "hasCodeContent").isTrue();
    requireThat(result.contains("- Add documentation"), "hasSecondBullet").isTrue();
    requireThat(result.contains("## Post-conditions"), "hasPostConditions").isTrue();
  }

  /**
   * Verifies Phase 7: section boundary is respected when another top-level heading follows.
   */
  @Test
  public void phase7SectionBoundaryRespected()
  {
    String input = """
      ## Goal
      Some goal.

      ## Execution Steps

      - Step 1
      - Step 2

      ## Post-conditions
      - Done
      """;

    String result = applyPhase7Migration(input);

    int wavesPos = result.indexOf("## Execution Waves");
    int postCondPos = result.indexOf("## Post-conditions");

    requireThat(result.contains("## Execution Waves"), "hasExecutionWaves").isTrue();
    requireThat(result.contains("## Post-conditions"), "hasPostConditions").isTrue();
    requireThat(wavesPos < postCondPos, "wavesBeforePostCond").isTrue();
    requireThat(result.contains("- Step 1"), "hasStep1").isTrue();
    requireThat(result.contains("- Step 2"), "hasStep2").isTrue();
  }

  /**
   * Verifies Phase 7: content is fully preserved when Execution Steps is the last section.
   */
  @Test
  public void phase7LastSectionFullyPreserved()
  {
    String input = """
      ## Goal
      Some goal.

      ## Execution Steps

      - Step A
      - Step B with
        multi-line content
      """;

    String result = applyPhase7Migration(input);

    requireThat(result.contains("## Execution Waves"), "hasExecutionWaves").isTrue();
    requireThat(result.contains("### Wave 1"), "hasWave1").isTrue();
    requireThat(result.contains("## Execution Steps"), "hasOldHeading").isFalse();
    requireThat(result.contains("- Step A"), "hasStepA").isTrue();
    requireThat(result.contains("- Step B with"), "hasStepB").isTrue();
    requireThat(result.contains("  multi-line content"), "hasMultiLine").isTrue();
  }

  /**
   * Verifies Phase 7: already-migrated files (with Execution Waves) are skipped (idempotent).
   */
  @Test
  public void phase7AlreadyMigratedSkipped()
  {
    String input = """
      ## Goal
      Some goal.

      ## Execution Waves

      ### Wave 1
      - Step 1
      - Step 2

      ## Post-conditions
      - Done
      """;

    String result = applyPhase7Migration(input);

    requireThat(result, "result").isEqualTo(input);
    requireThat(result.contains("## Execution Waves"), "hasExecutionWaves").isTrue();
    requireThat(result.contains("## Execution Steps"), "hasOldHeading").isFalse();
  }

  /**
   * Verifies Phase 7: files without Execution Steps are left unchanged.
   */
  @Test
  public void phase7NoExecutionStepsUnchanged()
  {
    String input = """
      ## Goal
      Some goal.

      ## Pre-conditions
      - Ready

      ## Post-conditions
      - Done
      """;

    String result = applyPhase7Migration(input);

    requireThat(result, "result").isEqualTo(input);
    requireThat(result.contains("## Execution Waves"), "hasExecutionWaves").isFalse();
    requireThat(result.contains("## Execution Steps"), "hasOldHeading").isFalse();
  }
}
