/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.RecordLearning;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Tests for RecordLearning: ID generation, JSON append, counter validation/increment,
 * retrospective threshold detection, and commit location determination.
 * <p>
 * Tests that do not exercise commit location use {@code executeInDir()} directly.
 * Tests that exercise lock-based commit location use {@code execute()} with a session ID
 * after creating the required lock file structure.
 */
public final class RecordLearningTest
{
  private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
  private static final Clock FIXED_CLOCK = Clock.fixed(
    Instant.parse("2026-03-05T10:00:00Z"), ZoneOffset.UTC);

  // ============================================================================
  // ID Generation
  // ============================================================================

  /**
   * Verifies that the first mistake ID is M001 when no mistakes exist.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void firstMistakeIdIsM001() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      Path retroDir = tempDir.resolve(".cat/retrospectives");
      Files.createDirectories(retroDir);
      RecordLearningTestUtils.initializeIndex(scope, retroDir, 0, null);

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = RecordLearningTestUtils.buildPhase3Input(scope, "Test mistake", null, null);
      String result = cmd.executeInDir(input, tempDir);
      JsonNode json = scope.getJsonMapper().readTree(result);

      requireThat(json.get("learning_id").asString(), "learning_id").isEqualTo("M001");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that the next mistake ID is M002 when M001 exists.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void nextMistakeIdIsIncrementalFromExisting() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      Path retroDir = tempDir.resolve(".cat/retrospectives");
      Files.createDirectories(retroDir);
      RecordLearningTestUtils.initializeIndex(scope, retroDir, 1, null);
      initializeMistakesFile(scope, retroDir, "2026-03", "M001");

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = RecordLearningTestUtils.buildPhase3Input(scope, "Second mistake", null, null);
      String result = cmd.executeInDir(input, tempDir);
      JsonNode json = scope.getJsonMapper().readTree(result);

      requireThat(json.get("learning_id").asString(), "learning_id").isEqualTo("M002");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that ID generation spans across multiple month files correctly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void idGenerationSpansMultipleMonthFiles() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      Path retroDir = tempDir.resolve(".cat/retrospectives");
      Files.createDirectories(retroDir);
      RecordLearningTestUtils.initializeIndex(scope, retroDir, 2, null);

      // Mistakes from two different months
      initializeMistakesFile(scope, retroDir, "2026-01", "M001");
      initializeMistakesFile(scope, retroDir, "2026-02", "M002");

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = RecordLearningTestUtils.buildPhase3Input(scope, "Third mistake", null, null);
      String result = cmd.executeInDir(input, tempDir);
      JsonNode json = scope.getJsonMapper().readTree(result);

      requireThat(json.get("learning_id").asString(), "learning_id").isEqualTo("M003");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  // ============================================================================
  // JSON Append
  // ============================================================================

  /**
   * Verifies that the mistake entry is appended to mistakes-YYYY-MM.json.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void mistakeEntryIsAppendedToCurrentMonthFile() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      Path retroDir = tempDir.resolve(".cat/retrospectives");
      Files.createDirectories(retroDir);
      RecordLearningTestUtils.initializeIndex(scope, retroDir, 0, null);

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = RecordLearningTestUtils.buildPhase3Input(scope, "Test mistake description", null, null);
      cmd.executeInDir(input, tempDir);

      // Check that the current month file exists and has the entry
      String yearMonth = ZonedDateTime.now(FIXED_CLOCK).format(YEAR_MONTH_FORMAT);
      Path mistakesFile = retroDir.resolve("mistakes-" + yearMonth + ".json");
      requireThat(Files.exists(mistakesFile), "mistakesFile.exists()").isTrue();

      JsonNode mistakesData = scope.getJsonMapper().readTree(Files.readString(mistakesFile));
      JsonNode mistakes = mistakesData.get("mistakes");
      requireThat(mistakes.isArray(), "mistakes.isArray()").isTrue();
      requireThat(mistakes.size(), "mistakes.size()").isEqualTo(1);

      JsonNode entry = mistakes.get(0);
      requireThat(entry.get("id").asString(), "entry.id").isEqualTo("M001");
      requireThat(entry.get("description").asString(), "entry.description").
        isEqualTo("Test mistake description");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that the mistake entry is added to existing mistakes file without overwriting.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void mistakeEntryAppendsToExistingFile() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      Path retroDir = tempDir.resolve(".cat/retrospectives");
      Files.createDirectories(retroDir);
      RecordLearningTestUtils.initializeIndex(scope, retroDir, 1, null);

      // Create a file for the current month with M001 already in it
      String yearMonth = ZonedDateTime.now(FIXED_CLOCK).format(YEAR_MONTH_FORMAT);
      initializeMistakesFile(scope, retroDir, yearMonth, "M001");

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = RecordLearningTestUtils.buildPhase3Input(scope, "Second mistake", null, null);
      cmd.executeInDir(input, tempDir);

      Path mistakesFile = retroDir.resolve("mistakes-" + yearMonth + ".json");
      JsonNode mistakesData = scope.getJsonMapper().readTree(Files.readString(mistakesFile));
      JsonNode mistakes = mistakesData.get("mistakes");

      requireThat(mistakes.size(), "mistakes.size()").isEqualTo(2);
      requireThat(mistakes.get(0).get("id").asString(), "mistakes[0].id").isEqualTo("M001");
      requireThat(mistakes.get(1).get("id").asString(), "mistakes[1].id").isEqualTo("M002");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  // ============================================================================
  // Counter Validation and Increment
  // ============================================================================

  /**
   * Verifies that the counter is incremented by 1 after recording a mistake.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void counterIsIncrementedAfterRecording() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      Path retroDir = tempDir.resolve(".cat/retrospectives");
      Files.createDirectories(retroDir);
      RecordLearningTestUtils.initializeIndex(scope, retroDir, 3, null);
      initializeMistakesFile(scope, retroDir, "2026-03", "M001", "M002", "M003");

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = RecordLearningTestUtils.buildPhase3Input(scope, "Fourth mistake", null, null);
      cmd.executeInDir(input, tempDir);

      // Read updated index
      Path indexFile = retroDir.resolve("index.json");
      JsonNode index = scope.getJsonMapper().readTree(Files.readString(indexFile));
      requireThat(index.get("mistake_count_since_last").asInt(), "mistake_count_since_last").
        isEqualTo(4);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that a mismatched counter is fixed to match actual count before incrementing.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void mismatchedCounterIsFixedBeforeIncrement() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      Path retroDir = tempDir.resolve(".cat/retrospectives");
      Files.createDirectories(retroDir);
      // Counter says 1 but there are actually 2 existing mistakes
      RecordLearningTestUtils.initializeIndex(scope, retroDir, 1, null);
      initializeMistakesFile(scope, retroDir, "2026-03", "M001", "M002");

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = RecordLearningTestUtils.buildPhase3Input(scope, "Third mistake", null, null);
      cmd.executeInDir(input, tempDir);

      // After fixing mismatch (counter becomes actual=2+1new=3), not 1+1=2
      Path indexFile = retroDir.resolve("index.json");
      JsonNode index = scope.getJsonMapper().readTree(Files.readString(indexFile));
      requireThat(index.get("mistake_count_since_last").asInt(), "mistake_count_since_last").
        isEqualTo(3);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  // ============================================================================
  // Retrospective Threshold Detection
  // ============================================================================

  /**
   * Verifies that retrospective is not triggered when count is below threshold.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void retrospectiveNotTriggeredBelowThreshold() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      Path retroDir = tempDir.resolve(".cat/retrospectives");
      Files.createDirectories(retroDir);
      // threshold is 10 by default, we're at 4
      RecordLearningTestUtils.initializeIndex(scope, retroDir, 4, null);
      initializeMistakesFile(scope, retroDir, "2026-03", "M001", "M002", "M003", "M004");

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = RecordLearningTestUtils.buildPhase3Input(scope, "Fifth mistake", null, null);
      String result = cmd.executeInDir(input, tempDir);
      JsonNode json = scope.getJsonMapper().readTree(result);

      requireThat(json.get("retrospective_trigger").asBoolean(), "retrospective_trigger").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that retrospective is triggered when count reaches the threshold.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void retrospectiveTriggeredAtThreshold() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      Path retroDir = tempDir.resolve(".cat/retrospectives");
      Files.createDirectories(retroDir);
      // threshold is 10, after adding M010 we hit it
      RecordLearningTestUtils.initializeIndex(scope, retroDir, 9, null);
      initializeMistakesFile(scope, retroDir, "2026-03",
        "M001", "M002", "M003", "M004", "M005", "M006", "M007", "M008", "M009");

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = RecordLearningTestUtils.buildPhase3Input(scope, "Tenth mistake", null, null);
      String result = cmd.executeInDir(input, tempDir);
      JsonNode json = scope.getJsonMapper().readTree(result);

      requireThat(json.get("retrospective_trigger").asBoolean(), "retrospective_trigger").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that counter_status is included in the output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void outputIncludesCounterStatus() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      Path retroDir = tempDir.resolve(".cat/retrospectives");
      Files.createDirectories(retroDir);
      RecordLearningTestUtils.initializeIndex(scope, retroDir, 0, null);

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = RecordLearningTestUtils.buildPhase3Input(scope, "Test mistake", null, null);
      String result = cmd.executeInDir(input, tempDir);
      JsonNode json = scope.getJsonMapper().readTree(result);

      JsonNode counterStatus = json.get("counter_status");
      requireThat(counterStatus, "counter_status").isNotNull();
      requireThat(counterStatus.get("count").asInt(), "count").isEqualTo(1);
      requireThat(counterStatus.get("threshold").asInt(), "threshold").isEqualTo(10);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  // ============================================================================
  // Commit Location Determination
  // ============================================================================

  /**
   * Verifies that commit is made to the main repo when not in a worktree.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void commitLocationIsMainRepoWhenNotInWorktree() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      Path retroDir = tempDir.resolve(".cat/retrospectives");
      Files.createDirectories(retroDir);
      RecordLearningTestUtils.initializeIndex(scope, retroDir, 0, null);

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = RecordLearningTestUtils.buildPhase3Input(scope, "Test mistake", null, null);
      String result = cmd.executeInDir(input, tempDir);
      JsonNode json = scope.getJsonMapper().readTree(result);

      requireThat(json.get("commit_hash"), "commit_hash").isNotNull();
      // The commit must be in the main repo
      String commitHash = TestUtils.runGitCommandWithOutput(tempDir, "rev-parse", "--short", "HEAD");
      requireThat(json.get("commit_hash").asString(), "commit_hash").isEqualTo(commitHash);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that the commit is made to the worktree repo when the session lock points to a worktree.
   * <p>
   * Creates a lock file associating the test session ID with an issue ID, creates the corresponding
   * worktree under the project CAT directory, then verifies that {@code execute()} commits into
   * the worktree rather than the main repo.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void commitLocationIsWorktreeWhenLockPointsToWorktree() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    // Use a unique session ID to avoid the WorktreeLock static cache returning stale results
    // from other tests that used the default "test-session" ID.
    String sessionId = UUID.randomUUID().toString();
    try (JvmScope scope = new TestJvmScope(mainRepo, pluginRoot))
    {
      // Create the worktree at the location WorktreeContext expects:
      // {projectCatDir}/worktrees/{issueId}
      String issueId = "2.1-test-feature";

      Path projectCatDir = scope.getCatWorkPath();
      Path worktreesDir = projectCatDir.resolve("worktrees");
      Files.createDirectories(worktreesDir);

      // Create the git worktree in the CAT worktrees directory
      Path worktree = TestUtils.createWorktree(mainRepo, worktreesDir, issueId);

      // Create a lock file mapping the test session to the issue
      Path lockDir = projectCatDir.resolve("locks");
      Files.createDirectories(lockDir);
      String lockContent = """
        {"session_id": "%s", "worktrees": {}, "created_at": 1000000, "created_iso": "2026-01-01T00:00:00Z"}
        """.formatted(sessionId);
      Files.writeString(lockDir.resolve(issueId + ".lock"), lockContent);

      // No pre-existing retrospectives — RecordLearning will create them in the worktree
      RecordLearning cmd = new RecordLearning(scope, mainRepo, FIXED_CLOCK);
      ObjectNode input = RecordLearningTestUtils.buildPhase3Input(scope, "Test mistake", null, null);

      // execute() uses lock-based detection: session -> issue -> worktree path
      String result = cmd.execute(input, sessionId);
      JsonNode json = scope.getJsonMapper().readTree(result);

      requireThat(json.get("commit_hash"), "commit_hash").isNotNull();
      // The commit should be in the worktree, not the main repo
      String worktreeHash = TestUtils.runGitCommandWithOutput(worktree, "rev-parse", "--short", "HEAD");
      requireThat(json.get("commit_hash").asString(), "commit_hash").isEqualTo(worktreeHash);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that the commit is made to the main repo when the session has no active worktree lock.
   * <p>
   * When no lock file maps the session to a worktree, {@code execute()} falls back to committing
   * in the project directory.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void commitLocationIsMainRepoWhenNoLockExists() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    // Use a unique session ID to avoid the WorktreeLock static cache returning stale results
    String sessionId = UUID.randomUUID().toString();
    try (JvmScope scope = new TestJvmScope(mainRepo, pluginRoot))
    {
      Path retroDir = mainRepo.resolve(".cat/retrospectives");
      Files.createDirectories(retroDir);
      RecordLearningTestUtils.initializeIndex(scope, retroDir, 0, null);

      // No lock file — session has no active worktree
      RecordLearning cmd = new RecordLearning(scope, mainRepo, FIXED_CLOCK);
      ObjectNode input = RecordLearningTestUtils.buildPhase3Input(scope, "Test mistake", null, null);
      String result = cmd.execute(input, sessionId);
      JsonNode json = scope.getJsonMapper().readTree(result);

      requireThat(json.get("commit_hash"), "commit_hash").isNotNull();
      // The commit must be in the main repo
      String commitHash = TestUtils.runGitCommandWithOutput(mainRepo, "rev-parse", "--short", "HEAD");
      requireThat(json.get("commit_hash").asString(), "commit_hash").isEqualTo(commitHash);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainRepo);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  // ============================================================================
  // Index file initialization (new retrospectives directory)
  // ============================================================================

  /**
   * Verifies that index.json is created when it does not exist.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void indexFileCreatedWhenAbsent() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      // Do NOT create the retrospectives directory or index.json
      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = RecordLearningTestUtils.buildPhase3Input(scope, "First mistake in fresh repo", null, null);
      String result = cmd.executeInDir(input, tempDir);
      JsonNode json = scope.getJsonMapper().readTree(result);

      Path indexFile = tempDir.resolve(".cat/retrospectives/index.json");
      requireThat(Files.exists(indexFile), "indexFile.exists()").isTrue();
      requireThat(json.get("learning_id").asString(), "learning_id").isEqualTo("M001");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  // ============================================================================
  // Recurrence tracking
  // ============================================================================

  /**
   * Verifies that recurrence_of field is set when provided.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void recurrenceOfIsSetWhenProvided() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      Path retroDir = tempDir.resolve(".cat/retrospectives");
      Files.createDirectories(retroDir);
      RecordLearningTestUtils.initializeIndex(scope, retroDir, 1, null);
      initializeMistakesFile(scope, retroDir, "2026-03", "M001");

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = RecordLearningTestUtils.buildPhase3Input(scope, "Recurring mistake", "M001", null);
      cmd.executeInDir(input, tempDir);

      String yearMonth = ZonedDateTime.now(FIXED_CLOCK).format(YEAR_MONTH_FORMAT);
      Path mistakesFile = retroDir.resolve("mistakes-" + yearMonth + ".json");
      JsonNode mistakesData = scope.getJsonMapper().readTree(Files.readString(mistakesFile));
      JsonNode mistakes = mistakesData.get("mistakes");

      // Find M002 entry
      JsonNode m002 = null;
      for (JsonNode m : mistakes)
      {
        if ("M002".equals(m.get("id").asString()))
        {
          m002 = m;
          break;
        }
      }
      requireThat(m002, "m002").isNotNull();
      requireThat(m002.get("recurrence_of").asString(), "recurrence_of").isEqualTo("M001");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  // ============================================================================
  // Cause Signature Tests
  // ============================================================================

  /**
   * Verifies that cause_signature is stored in the mistake entry when provided.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void causeSignatureIsStoredWhenProvided() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      Path retroDir = tempDir.resolve(".cat/retrospectives");
      Files.createDirectories(retroDir);
      RecordLearningTestUtils.initializeIndex(scope, retroDir, 0, null);

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = RecordLearningTestUtils.buildPhase3Input(scope, "Compliance failure", null,
        "compliance_failure:hook_absent:file_operations");
      cmd.executeInDir(input, tempDir);

      String yearMonth = ZonedDateTime.now(FIXED_CLOCK).format(YEAR_MONTH_FORMAT);
      Path mistakesFile = retroDir.resolve("mistakes-" + yearMonth + ".json");
      JsonNode mistakesData = scope.getJsonMapper().readTree(Files.readString(mistakesFile));
      JsonNode entry = mistakesData.get("mistakes").get(0);

      requireThat(entry.get("cause_signature").asString(), "cause_signature").
        isEqualTo("compliance_failure:hook_absent:file_operations");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that cause_signature is null when not provided.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void causeSignatureIsNullWhenAbsent() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      Path retroDir = tempDir.resolve(".cat/retrospectives");
      Files.createDirectories(retroDir);
      RecordLearningTestUtils.initializeIndex(scope, retroDir, 0, null);

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = RecordLearningTestUtils.buildPhase3Input(scope, "Test mistake", null, null);
      cmd.executeInDir(input, tempDir);

      String yearMonth = ZonedDateTime.now(FIXED_CLOCK).format(YEAR_MONTH_FORMAT);
      Path mistakesFile = retroDir.resolve("mistakes-" + yearMonth + ".json");
      JsonNode mistakesData = scope.getJsonMapper().readTree(Files.readString(mistakesFile));
      JsonNode entry = mistakesData.get("mistakes").get(0);

      requireThat(entry.get("cause_signature").isNull(), "cause_signature.isNull()").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that all valid cause_signature formats are accepted and stored correctly.
   * <p>
   * The system stores cause_signature as-is when it follows the format
   * {@code <cause_type>:<barrier_type>:<context>}. Format validation is enforced at the skill/agent
   * level; the Java layer stores whatever the agent provides.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void causeSignatureEnumValidation() throws IOException
  {
    String[] validSignatures = {
      "compliance_failure:hook_absent:file_operations",
      "compliance_failure:doc_ignored:plugin_rules",
      "knowledge_gap:doc_missing:skill_execution",
      "knowledge_gap:skill_incomplete:subagent_delegation",
      "context_degradation:process_gap:issue_workflow",
      "architectural_conflict:hook_absent:plugin_rules",
      "environment_mismatch:validation_absent:git_operations",
      "compliance_failure:hook_bypassed:git_operations",
      "tool_limitation:config_wrong:pre_tool_use",
    };

    for (String signature : validSignatures)
    {
      Path tempDir = TestUtils.createTempGitRepo("main");
      Path pluginRoot = Files.createTempDirectory("plugin-root-");
      try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
      {
        Path retroDir = tempDir.resolve(".cat/retrospectives");
        Files.createDirectories(retroDir);
        RecordLearningTestUtils.initializeIndex(scope, retroDir, 0, null);

        RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
        ObjectNode input = RecordLearningTestUtils.buildPhase3Input(scope,
          "Test mistake with signature " + signature, null, signature);
        cmd.executeInDir(input, tempDir);

        String yearMonth = ZonedDateTime.now(FIXED_CLOCK).format(YEAR_MONTH_FORMAT);
        Path mistakesFile = retroDir.resolve("mistakes-" + yearMonth + ".json");
        JsonNode mistakesData = scope.getJsonMapper().readTree(Files.readString(mistakesFile));
        JsonNode entry = mistakesData.get("mistakes").get(0);

        requireThat(entry.get("cause_signature").asString(), "cause_signature[" + signature + "]").
          isEqualTo(signature);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
  }

  /**
   * Verifies that a null cause_signature does not trigger incorrect auto-linking when a
   * signature-bearing entry already exists.
   * <p>
   * An entry without a cause_signature must never be linked to an existing entry that has one.
   * Backward compatibility: pre-existing entries without cause_signature do not break the workflow.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void backwardCompatibilityMissingCauseSignature() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      Path retroDir = tempDir.resolve(".cat/retrospectives");
      Files.createDirectories(retroDir);

      // Set up pre-existing entries: M001 with no cause_signature, M002 with a signature
      RecordLearningTestUtils.initializeIndex(scope, retroDir, 2, null);
      initializeMistakesFileWithSignatures(scope, retroDir, "2026-03",
        new String[]{"M001", null, null},
        new String[]{"M002", null, "compliance_failure:hook_absent:file_operations"});

      // Record new entry without cause_signature
      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = RecordLearningTestUtils.buildPhase3Input(scope, "New mistake without signature", null, null);
      cmd.executeInDir(input, tempDir);

      String yearMonth = ZonedDateTime.now(FIXED_CLOCK).format(YEAR_MONTH_FORMAT);
      Path mistakesFile = retroDir.resolve("mistakes-" + yearMonth + ".json");
      JsonNode mistakesData = scope.getJsonMapper().readTree(Files.readString(mistakesFile));
      JsonNode mistakes = mistakesData.get("mistakes");

      // Find M003 entry
      JsonNode m003 = null;
      for (JsonNode m : mistakes)
      {
        if ("M003".equals(m.get("id").asString()))
        {
          m003 = m;
          break;
        }
      }
      requireThat(m003, "m003").isNotNull();

      // M003 should have null cause_signature — never incorrectly linked
      requireThat(m003.get("cause_signature").isNull(), "m003.cause_signature.isNull()").isTrue();

      // M003 recurrence_of should be null — not auto-linked to M001 or M002
      requireThat(m003.get("recurrence_of").isNull(), "m003.recurrence_of.isNull()").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that cause_signature is stored correctly when no vocabulary match is possible.
   * <p>
   * When a cause is ambiguous or unclassifiable, the agent may record null or an approximate value.
   * The system must not reject or crash on unusual signature values — it stores what is provided.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void ambiguousCauseSignatureSelection() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      Path retroDir = tempDir.resolve(".cat/retrospectives");
      Files.createDirectories(retroDir);
      RecordLearningTestUtils.initializeIndex(scope, retroDir, 0, null);

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);

      // Case 1: null signature (unclassifiable) — must not crash
      ObjectNode input1 = RecordLearningTestUtils.buildPhase3Input(scope,
        "Ambiguous mistake with no clear cause", null, null);
      String result1 = cmd.executeInDir(input1, tempDir);
      JsonNode json1 = scope.getJsonMapper().readTree(result1);
      requireThat(json1.get("learning_id").asString(), "learning_id").isEqualTo("M001");

      // Verify null signature was stored without error
      String yearMonth = ZonedDateTime.now(FIXED_CLOCK).format(YEAR_MONTH_FORMAT);
      Path mistakesFile = retroDir.resolve("mistakes-" + yearMonth + ".json");
      JsonNode mistakesData = scope.getJsonMapper().readTree(Files.readString(mistakesFile));
      JsonNode entry1 = mistakesData.get("mistakes").get(0);
      requireThat(entry1.get("cause_signature").isNull(), "entry1.cause_signature.isNull()").isTrue();

      // Case 2: closest-match signature for an ambiguous case — must be stored as-is
      // (e.g., mistake where both compliance_failure and knowledge_gap apply; agent picks closest)
      ObjectNode input2 = RecordLearningTestUtils.buildPhase3Input(scope,
        "Ambiguous: both compliance and knowledge gap apply",
        null, "compliance_failure:doc_ignored:plugin_rules");
      String result2 = cmd.executeInDir(input2, tempDir);
      JsonNode json2 = scope.getJsonMapper().readTree(result2);
      requireThat(json2.get("learning_id").asString(), "learning_id").isEqualTo("M002");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that cause_signature is propagated through the full workflow, end-to-end,
   * and that entries with matching signatures can be detected by scanning the mistakes file.
   * <p>
   * This test simulates the analyze phase workflow: record entry with signature, then simulate
   * a second mistake with the same signature and verify the match is detectable.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void causeSignatureRecurrenceDetection() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      Path retroDir = tempDir.resolve(".cat/retrospectives");
      Files.createDirectories(retroDir);
      RecordLearningTestUtils.initializeIndex(scope, retroDir, 0, null);

      String signature = "compliance_failure:hook_absent:file_operations";
      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);

      // Record M001 with a specific cause_signature
      ObjectNode input1 = RecordLearningTestUtils.buildPhase3Input(scope, "First compliance failure", null, signature);
      cmd.executeInDir(input1, tempDir);

      // Record M002 with the same signature, explicitly linking to M001 (recurrence_of)
      ObjectNode input2 = RecordLearningTestUtils.buildPhase3Input(scope,
        "Same compliance failure recurs", "M001", signature);
      cmd.executeInDir(input2, tempDir);

      String yearMonth = ZonedDateTime.now(FIXED_CLOCK).format(YEAR_MONTH_FORMAT);
      Path mistakesFile = retroDir.resolve("mistakes-" + yearMonth + ".json");
      JsonNode mistakesData = scope.getJsonMapper().readTree(Files.readString(mistakesFile));
      JsonNode mistakes = mistakesData.get("mistakes");

      requireThat(mistakes.size(), "mistakes.size()").isEqualTo(2);

      // M001: has signature, no recurrence_of
      JsonNode m001 = mistakes.get(0);
      requireThat(m001.get("id").asString(), "m001.id").isEqualTo("M001");
      requireThat(m001.get("cause_signature").asString(), "m001.cause_signature").isEqualTo(signature);
      requireThat(m001.get("recurrence_of").isNull(), "m001.recurrence_of.isNull()").isTrue();

      // M002: same signature, recurrence_of=M001
      JsonNode m002 = mistakes.get(1);
      requireThat(m002.get("id").asString(), "m002.id").isEqualTo("M002");
      requireThat(m002.get("cause_signature").asString(), "m002.cause_signature").isEqualTo(signature);
      requireThat(m002.get("recurrence_of").asString(), "m002.recurrence_of").isEqualTo("M001");

      // Simulate analyze phase: scan for matching signatures in existing entries
      // Verify: given the candidate signature, we can find M001 as a matching earlier entry
      String matchingEntryId = null;
      for (JsonNode m : mistakes)
      {
        JsonNode sigNode = m.get("cause_signature");
        // The first matching entry is the one that triggered recurrence detection
        if (sigNode != null && !sigNode.isNull() && signature.equals(sigNode.asString()) &&
          "M001".equals(m.get("id").asString()))
        {
          matchingEntryId = m.get("id").asString();
          break;
        }
      }
      requireThat(matchingEntryId, "matchingEntryId").isEqualTo("M001");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that signature-based recurrence detection works when entries are in different month files.
   * <p>
   * Simulates: M001 recorded in a prior month, M002 recorded now with the same signature.
   * The system stores the new entry with recurrence_of linking back to M001.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void signatureDrivenRecurrenceDetection() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      Path retroDir = tempDir.resolve(".cat/retrospectives");
      Files.createDirectories(retroDir);

      // Pre-populate M001 in a prior month file with a specific cause_signature
      RecordLearningTestUtils.initializeIndex(scope, retroDir, 1, null);
      initializeMistakesFileWithSignatures(scope, retroDir, "2026-02",
        new String[]{"M001", null, "compliance_failure:doc_ignored:plugin_rules"});

      // Record M002 in current month with the same signature, explicitly linking to M001
      // (The analyze phase agent would detect the match and set recurrence_of before calling RecordLearning)
      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = RecordLearningTestUtils.buildPhase3Input(scope, "Same compliance failure in different month",
        "M001", "compliance_failure:doc_ignored:plugin_rules");
      cmd.executeInDir(input, tempDir);

      // Verify M002 was recorded with both the signature and recurrence_of linkage
      String yearMonth = ZonedDateTime.now(FIXED_CLOCK).format(YEAR_MONTH_FORMAT);
      Path mistakesFile = retroDir.resolve("mistakes-" + yearMonth + ".json");
      JsonNode mistakesData = scope.getJsonMapper().readTree(Files.readString(mistakesFile));
      JsonNode mistakes = mistakesData.get("mistakes");

      requireThat(mistakes.size(), "mistakes.size()").isEqualTo(1);
      JsonNode m002 = mistakes.get(0);
      requireThat(m002.get("id").asString(), "m002.id").isEqualTo("M002");
      requireThat(m002.get("cause_signature").asString(), "m002.cause_signature").
        isEqualTo("compliance_failure:doc_ignored:plugin_rules");
      requireThat(m002.get("recurrence_of").asString(), "m002.recurrence_of").isEqualTo("M001");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  // ============================================================================
  // Helper methods
  // ============================================================================

  /**
   * Initializes a mistakes-YYYY-MM.json file with the given mistake IDs.
   *
   * @param scope the JVM scope
   * @param retroDir the retrospectives directory
   * @param yearMonth the year-month period (e.g. "2026-03")
   * @param ids the mistake IDs to include
   * @throws IOException if file writing fails
   */
  private void initializeMistakesFile(JvmScope scope, Path retroDir, String yearMonth, String... ids)
    throws IOException
  {
    // Build entries with no recurrence_of and no cause_signature
    String[][] entries = new String[ids.length][3];
    for (int i = 0; i < ids.length; ++i)
      entries[i] = new String[]{ids[i], null, null};
    initializeMistakesFileWithSignatures(scope, retroDir, yearMonth, entries);
  }

  /**
   * Initializes a mistakes-YYYY-MM.json file with the given mistake IDs, recurrence links, and signatures.
   *
   * @param scope the JVM scope
   * @param retroDir the retrospectives directory
   * @param yearMonth the year-month period (e.g. "2026-03")
   * @param entries arrays of [id, recurrenceOf, causeSignature] (null values are stored as JSON null)
   * @throws IOException if file writing fails
   */
  private void initializeMistakesFileWithSignatures(JvmScope scope, Path retroDir, String yearMonth,
    String[]... entries) throws IOException
  {
    ObjectNode data = scope.getJsonMapper().createObjectNode();
    data.put("period", yearMonth);
    ArrayNode mistakes = scope.getJsonMapper().createArrayNode();
    for (String[] entry : entries)
    {
      String id = entry[0];
      String recurrenceOf = null;
      if (entry.length > 1)
        recurrenceOf = entry[1];
      String causeSignature = null;
      if (entry.length > 2)
        causeSignature = entry[2];

      ObjectNode m = scope.getJsonMapper().createObjectNode();
      m.put("id", id);
      m.put("timestamp", "2026-03-01T10:00:00Z");
      m.put("category", "protocol_violation");
      m.put("description", "Test mistake " + id);
      m.put("root_cause", "Test root cause");
      if (causeSignature != null)
        m.put("cause_signature", causeSignature);
      else
        m.putNull("cause_signature");
      m.put("prevention_type", "skill");
      m.put("prevention_path", "/workspace/test.md");
      m.set("pattern_keywords", scope.getJsonMapper().createArrayNode());
      m.put("prevention_implemented", true);
      m.put("prevention_verified", true);
      if (recurrenceOf != null)
        m.put("recurrence_of", recurrenceOf);
      else
        m.putNull("recurrence_of");
      m.put("correct_behavior", "Do the right thing");
      mistakes.add(m);
    }
    data.set("mistakes", mistakes);
    Files.writeString(retroDir.resolve("mistakes-" + yearMonth + ".json"),
      scope.getJsonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(data));
  }
}
