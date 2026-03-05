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

/**
 * Tests for RecordLearning: ID generation, JSON append, counter validation/increment,
 * retrospective threshold detection, and commit location determination.
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
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);
      initializeIndex(scope, retroDir, 0, null);

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = buildPhase3Input(scope, "Test mistake", null);
      String result = cmd.execute(input, tempDir);
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
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);
      initializeIndex(scope, retroDir, 1, null);
      initializeMistakesFile(scope, retroDir, "2026-03", "M001");

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = buildPhase3Input(scope, "Second mistake", null);
      String result = cmd.execute(input, tempDir);
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
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);
      initializeIndex(scope, retroDir, 2, null);

      // Mistakes from two different months
      initializeMistakesFile(scope, retroDir, "2026-01", "M001");
      initializeMistakesFile(scope, retroDir, "2026-02", "M002");

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = buildPhase3Input(scope, "Third mistake", null);
      String result = cmd.execute(input, tempDir);
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
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);
      initializeIndex(scope, retroDir, 0, null);

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = buildPhase3Input(scope, "Test mistake description", null);
      cmd.execute(input, tempDir);

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
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);
      initializeIndex(scope, retroDir, 1, null);

      // Create a file for the current month with M001 already in it
      String yearMonth = ZonedDateTime.now(FIXED_CLOCK).format(YEAR_MONTH_FORMAT);
      initializeMistakesFile(scope, retroDir, yearMonth, "M001");

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = buildPhase3Input(scope, "Second mistake", null);
      cmd.execute(input, tempDir);

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
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);
      initializeIndex(scope, retroDir, 3, null);
      initializeMistakesFile(scope, retroDir, "2026-03", "M001", "M002", "M003");

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = buildPhase3Input(scope, "Fourth mistake", null);
      cmd.execute(input, tempDir);

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
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);
      // Counter says 1 but there are actually 2 existing mistakes
      initializeIndex(scope, retroDir, 1, null);
      initializeMistakesFile(scope, retroDir, "2026-03", "M001", "M002");

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = buildPhase3Input(scope, "Third mistake", null);
      cmd.execute(input, tempDir);

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
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);
      // threshold is 10 by default, we're at 4
      initializeIndex(scope, retroDir, 4, null);
      initializeMistakesFile(scope, retroDir, "2026-03", "M001", "M002", "M003", "M004");

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = buildPhase3Input(scope, "Fifth mistake", null);
      String result = cmd.execute(input, tempDir);
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
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);
      // threshold is 10, after adding M010 we hit it
      initializeIndex(scope, retroDir, 9, null);
      initializeMistakesFile(scope, retroDir, "2026-03",
        "M001", "M002", "M003", "M004", "M005", "M006", "M007", "M008", "M009");

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = buildPhase3Input(scope, "Tenth mistake", null);
      String result = cmd.execute(input, tempDir);
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
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);
      initializeIndex(scope, retroDir, 0, null);

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = buildPhase3Input(scope, "Test mistake", null);
      String result = cmd.execute(input, tempDir);
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
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);
      initializeIndex(scope, retroDir, 0, null);

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = buildPhase3Input(scope, "Test mistake", null);
      String result = cmd.execute(input, tempDir);
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
   * Verifies that the commit is made to the worktree repo when executed from a worktree.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void commitLocationIsWorktreeWhenInWorktree() throws IOException
  {
    Path mainRepo = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(mainRepo, pluginRoot))
    {
      // Create worktree
      Path worktreesDir = mainRepo.resolve("worktrees");
      Files.createDirectories(worktreesDir);
      Path worktree = TestUtils.createWorktree(mainRepo, worktreesDir, "feature-branch");

      // Create the cat-branch-point file that identifies this as a CAT worktree
      Path gitCommonDir = Path.of(TestUtils.runGitCommandWithOutput(worktree, "rev-parse", "--git-common-dir"));
      if (!gitCommonDir.isAbsolute())
        gitCommonDir = worktree.resolve(gitCommonDir);
      String worktreeName = worktree.getFileName().toString();
      Path catBranchPoint = gitCommonDir.resolve("worktrees").resolve(worktreeName).resolve("cat-branch-point");
      Files.writeString(catBranchPoint, "main");

      // No pre-existing retrospectives — RecordLearning will create them in commitDir (worktree)
      RecordLearning cmd = new RecordLearning(scope, mainRepo, FIXED_CLOCK);
      ObjectNode input = buildPhase3Input(scope, "Test mistake", null);
      // Execute from the worktree context — commit lands in the worktree
      String result = cmd.execute(input, worktree);
      JsonNode json = scope.getJsonMapper().readTree(result);

      requireThat(json.get("commit_hash"), "commit_hash").isNotNull();
      // The commit should be in the worktree
      String worktreeHash = TestUtils.runGitCommandWithOutput(worktree, "rev-parse", "--short", "HEAD");
      requireThat(json.get("commit_hash").asString(), "commit_hash").isEqualTo(worktreeHash);
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
      ObjectNode input = buildPhase3Input(scope, "First mistake in fresh repo", null);
      String result = cmd.execute(input, tempDir);
      JsonNode json = scope.getJsonMapper().readTree(result);

      Path indexFile = tempDir.resolve(".claude/cat/retrospectives/index.json");
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
      Path retroDir = tempDir.resolve(".claude/cat/retrospectives");
      Files.createDirectories(retroDir);
      initializeIndex(scope, retroDir, 1, null);
      initializeMistakesFile(scope, retroDir, "2026-03", "M001");

      RecordLearning cmd = new RecordLearning(scope, tempDir, FIXED_CLOCK);
      ObjectNode input = buildPhase3Input(scope, "Recurring mistake", "M001");
      cmd.execute(input, tempDir);

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
  // Helper methods
  // ============================================================================

  /**
   * Initializes the index.json file in the retrospectives directory.
   *
   * @param scope the JVM scope
   * @param retroDir the retrospectives directory
   * @param count the mistake_count_since_last value
   * @param lastRetrospective the last retrospective ISO timestamp, or null
   * @throws IOException if file writing fails
   */
  private void initializeIndex(JvmScope scope, Path retroDir, int count, String lastRetrospective)
    throws IOException
  {
    ObjectNode index = scope.getJsonMapper().createObjectNode();
    index.put("version", "2.0");
    ObjectNode config = scope.getJsonMapper().createObjectNode();
    config.put("mistake_count_threshold", 10);
    config.put("trigger_interval_days", 7);
    index.set("config", config);
    if (lastRetrospective != null)
      index.put("last_retrospective", lastRetrospective);
    else
      index.putNull("last_retrospective");
    index.put("mistake_count_since_last", count);
    ObjectNode files = scope.getJsonMapper().createObjectNode();
    files.set("mistakes", scope.getJsonMapper().createArrayNode());
    files.set("retrospectives", scope.getJsonMapper().createArrayNode());
    index.set("files", files);

    Files.writeString(retroDir.resolve("index.json"),
      scope.getJsonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(index));
  }

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
    ObjectNode data = scope.getJsonMapper().createObjectNode();
    data.put("period", yearMonth);
    ArrayNode mistakes = scope.getJsonMapper().createArrayNode();
    for (String id : ids)
    {
      ObjectNode m = scope.getJsonMapper().createObjectNode();
      m.put("id", id);
      m.put("timestamp", "2026-03-01T10:00:00Z");
      m.put("category", "protocol_violation");
      m.put("description", "Test mistake " + id);
      m.put("root_cause", "Test root cause");
      m.put("rca_method", "A");
      m.put("rca_method_name", "5-whys");
      m.put("prevention_type", "skill");
      m.put("prevention_path", "/workspace/test.md");
      m.set("pattern_keywords", scope.getJsonMapper().createArrayNode());
      m.put("prevention_implemented", true);
      m.put("prevention_verified", true);
      m.putNull("recurrence_of");
      m.put("correct_behavior", "Do the right thing");
      mistakes.add(m);
    }
    data.set("mistakes", mistakes);
    Files.writeString(retroDir.resolve("mistakes-" + yearMonth + ".json"),
      scope.getJsonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(data));
  }

  /**
   * Builds a Phase 3 input JSON object for testing.
   *
   * @param scope the JVM scope
   * @param description the mistake description
   * @param recurrenceOf the ID of the original mistake if recurrence, or null
   * @return an ObjectNode representing Phase 3 output
   */
  private ObjectNode buildPhase3Input(JvmScope scope, String description, String recurrenceOf)
  {
    ObjectNode input = scope.getJsonMapper().createObjectNode();
    input.put("category", "protocol_violation");
    input.put("description", description);
    input.put("root_cause", "Test root cause");
    input.put("rca_method", "A");
    input.put("rca_method_name", "5-whys");
    input.put("prevention_type", "skill");
    input.put("prevention_path", "/workspace/test.md");
    ArrayNode keywords = scope.getJsonMapper().createArrayNode();
    keywords.add("test");
    input.set("pattern_keywords", keywords);
    input.put("prevention_implemented", true);
    input.put("prevention_verified", true);
    if (recurrenceOf != null)
      input.put("recurrence_of", recurrenceOf);
    else
      input.putNull("recurrence_of");
    ObjectNode quality = scope.getJsonMapper().createObjectNode();
    quality.put("verification_type", "positive");
    quality.put("fragility", "low");
    quality.put("catches_variations", true);
    input.set("prevention_quality", quality);
    input.put("correct_behavior", "Do the right thing");
    return input;
  }
}
