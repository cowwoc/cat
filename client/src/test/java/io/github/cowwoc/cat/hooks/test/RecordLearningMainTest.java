/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.RecordLearning;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for RecordLearning.run() CLI error path handling.
 * <p>
 * Verifies that when the CLI encounters errors (empty stdin, invalid JSON),
 * it produces valid HookOutput JSON with "decision": "block" on stdout instead of throwing exceptions
 * or producing malformed output.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained with no shared state.
 */
public class RecordLearningMainTest
{
  /**
   * Verifies the happy path: when valid Phase 3 JSON is provided and the project is properly set up,
   * the result JSON contains {@code learning_id}, {@code counter_status}, and {@code retrospective_trigger}.
   * <p>
   * This test exercises {@code executeInDir()} directly to isolate the business logic from environment
   * variable reads in {@code run()}, ensuring the core record-learning workflow succeeds end-to-end
   * without side effects on the host repository.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void happyPathProducesLearningIdAndCounterStatus() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      // Set up minimal retrospectives directory
      Path retroDir = tempDir.resolve(".cat").resolve("retrospectives");
      Files.createDirectories(retroDir);
      RecordLearningTestUtils.initializeIndex(scope, retroDir, 0, null);

      Clock fixedClock = Clock.fixed(Instant.parse("2026-03-05T10:00:00Z"), ZoneOffset.UTC);
      ObjectNode phase3Input = buildPhase3Input(scope, "test mistake description");
      RecordLearning cmd = new RecordLearning(scope, tempDir, fixedClock);
      String result = cmd.executeInDir(phase3Input, tempDir);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode json = mapper.readTree(result);

      requireThat(json.get("learning_id").asString(), "learning_id").isEqualTo("M001");
      JsonNode counterStatus = json.get("counter_status");
      requireThat(counterStatus, "counter_status").isNotNull();
      requireThat(counterStatus.get("count").asInt(), "count").isEqualTo(1);
      requireThat(counterStatus.get("threshold").asInt(), "threshold").isGreaterThan(0);
      requireThat(json.get("retrospective_trigger").asBoolean(), "retrospective_trigger").isFalse();
      requireThat(json.get("commit_hash").asString(), "commit_hash").isNotBlank();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that empty stdin produces a block response on stdout.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void emptyStdinProducesBlockResponse() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      InputStream emptyInput = new ByteArrayInputStream(new byte[0]);
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

      RecordLearning.run(scope, emptyInput, out, tempDir::toString);

      String output = buffer.toString(StandardCharsets.UTF_8).strip();
      requireThat(output, "output").isNotBlank();

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode json = mapper.readTree(output);

      requireThat(json.get("decision").asString(), "decision").isEqualTo("block");
      requireThat(json.get("reason").asString(), "reason").isNotBlank();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that invalid JSON on stdin produces a block response on stdout.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void invalidJsonOnStdinProducesBlockResponse() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      InputStream invalidJson = new ByteArrayInputStream(
        "not valid json at all".getBytes(StandardCharsets.UTF_8));
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

      RecordLearning.run(scope, invalidJson, out, tempDir::toString);

      String output = buffer.toString(StandardCharsets.UTF_8).strip();
      requireThat(output, "output").isNotBlank();

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode json = mapper.readTree(output);

      requireThat(json.get("decision").asString(), "decision").isEqualTo("block");
      requireThat(json.get("reason").asString(), "reason").isNotBlank();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that when CLAUDE_PROJECT_DIR is not set (supplier throws AssertionError),
   * run() produces a block response on stdout with a clear error message
   * instead of falling back to git root detection.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void missingClaudeProjectDirProducesBlockResponse() throws IOException
  {
    Path tempDir = Files.createTempDirectory("record-learning-main-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      InputStream emptyInput = new ByteArrayInputStream(new byte[0]);
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

      // Inject a supplier that throws AssertionError to simulate missing CLAUDE_PROJECT_DIR
      Supplier<String> failingProvider = () ->
      {
        throw new AssertionError("CLAUDE_PROJECT_DIR not set");
      };

      RecordLearning.run(scope, emptyInput, out, failingProvider);

      String output = buffer.toString(StandardCharsets.UTF_8).strip();
      requireThat(output, "output").isNotBlank();

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode json = mapper.readTree(output);

      requireThat(json.get("decision").asString(), "decision").isEqualTo("block");
      requireThat(json.get("reason").asString(), "reason").contains("CLAUDE_PROJECT_DIR");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() throws NullPointerException for null scope.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*scope.*")
  public void nullScopeThrowsException()
  {
    RecordLearning.run(null, new ByteArrayInputStream(new byte[0]),
      new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
      () -> "");
  }

  /**
   * Verifies that run() throws NullPointerException for null input stream.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*in.*")
  public void nullInThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("record-learning-main-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      RecordLearning.run(scope, null,
        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
        () -> "");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that run() throws NullPointerException for null output stream.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*out.*")
  public void nullOutThrowsException() throws IOException
  {
    Path tempDir = Files.createTempDirectory("record-learning-main-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      RecordLearning.run(scope, new ByteArrayInputStream(new byte[0]), null,
        () -> "");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that when the mistakes file contains malformed JSON (e.g., a string value with a literal
   * newline), the IOException message names the file path and instructs the user to inspect and repair it.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void corruptedMistakesFileExceptionNamesFilePath() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      Clock fixedClock = Clock.fixed(Instant.parse("2026-03-05T10:00:00Z"), ZoneOffset.UTC);
      // Create the retrospectives directory with a corrupted mistakes file containing a literal newline
      // in a string value, which makes it invalid JSON.
      Path retroDir = tempDir.resolve(".cat").resolve("retrospectives");
      Files.createDirectories(retroDir);
      String yearMonth = ZonedDateTime.now(fixedClock).format(DateTimeFormatter.ofPattern("yyyy-MM"));
      Path mistakesFile = retroDir.resolve("mistakes-" + yearMonth + ".json");
      // Write JSON with a literal newline inside a string value — this is invalid JSON
      Files.writeString(mistakesFile,
        "{\"period\":\"" + yearMonth + "\",\"mistakes\":[{\"id\":\"M001\",\"description\":\"bad\nvalue\"}]}");

      // Valid Phase 3 JSON input
      ObjectNode phase3Input = buildPhase3Input(scope, "a test mistake");
      RecordLearning cmd = new RecordLearning(scope, tempDir, fixedClock);
      try
      {
        cmd.executeInDir(phase3Input, tempDir);
        throw new AssertionError("Expected IOException was not thrown");
      }
      catch (IOException e)
      {
        String message = e.getMessage();
        requireThat(message, "message").contains(mistakesFile.toString());
        requireThat(message, "message").contains("malformed JSON");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that prior-period mistakes files are included when scanning for the maximum existing ID,
   * so the next assigned ID is unique across all periods.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void priorPeriodMistakesFilesAreIncludedInMaxIdScan() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      Clock fixedClock = Clock.fixed(Instant.parse("2026-03-05T10:00:00Z"), ZoneOffset.UTC);
      Path retroDir = tempDir.resolve(".cat").resolve("retrospectives");
      Files.createDirectories(retroDir);

      // Write a prior-period file with M001-M003
      Files.writeString(retroDir.resolve("mistakes-2026-01.json"), """
        {
          "period": "2026-01",
          "mistakes": [
            {"id": "M001", "timestamp": "2026-01-10T10:00:00Z"},
            {"id": "M002", "timestamp": "2026-01-15T10:00:00Z"},
            {"id": "M003", "timestamp": "2026-01-20T10:00:00Z"}
          ]
        }
        """);

      // Write another prior-period file with M004-M006
      Files.writeString(retroDir.resolve("mistakes-2026-02.json"), """
        {
          "period": "2026-02",
          "mistakes": [
            {"id": "M004", "timestamp": "2026-02-10T10:00:00Z"},
            {"id": "M005", "timestamp": "2026-02-15T10:00:00Z"},
            {"id": "M006", "timestamp": "2026-02-20T10:00:00Z"}
          ]
        }
        """);

      RecordLearningTestUtils.initializeIndex(scope, retroDir, 6, null);

      ObjectNode phase3Input = buildPhase3Input(scope, "test mistake after prior periods");
      RecordLearning cmd = new RecordLearning(scope, tempDir, fixedClock);
      String result = cmd.executeInDir(phase3Input, tempDir);

      JsonNode json = scope.getJsonMapper().readTree(result);
      requireThat(json.get("learning_id").asString(), "learning_id").isEqualTo("M007");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that when a prior-period mistakes file contains malformed JSON, the IOException message
   * includes the path of the corrupted file so the user can identify and fix it.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void corruptedOldMistakesFileExceptionNamesFilePath() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      Clock fixedClock = Clock.fixed(Instant.parse("2026-03-05T10:00:00Z"), ZoneOffset.UTC);
      Path retroDir = tempDir.resolve(".cat").resolve("retrospectives");
      Files.createDirectories(retroDir);

      // Write a well-formed current-month file with no entries
      String currentYearMonth = ZonedDateTime.now(fixedClock).format(DateTimeFormatter.ofPattern("yyyy-MM"));
      Files.writeString(retroDir.resolve("mistakes-" + currentYearMonth + ".json"),
        "{\"period\":\"" + currentYearMonth + "\",\"mistakes\":[]}");

      // Write a corrupted prior-period file (literal newline inside a string value — invalid JSON)
      Path corruptedFile = retroDir.resolve("mistakes-2026-01.json");
      Files.writeString(corruptedFile,
        "{\"period\":\"2026-01\",\"mistakes\":[{\"id\":\"M001\",\"description\":\"bad\nvalue\"}]}");

      RecordLearningTestUtils.initializeIndex(scope, retroDir, 0, null);

      String phase3Json = """
        {
          "category": "test",
          "description": "a test mistake",
          "root_cause": "testing",
          "prevention_type": "rule",
          "prevention_path": ".claude/rules/test.md",
          "pattern_keywords": [],
          "prevention_implemented": true,
          "prevention_verified": true,
          "correct_behavior": "do the right thing"
        }
        """;
      ObjectNode phase3Input = (ObjectNode) scope.getJsonMapper().readTree(phase3Json);
      RecordLearning cmd = new RecordLearning(scope, tempDir, fixedClock);

      try
      {
        cmd.executeInDir(phase3Input, tempDir);
        throw new AssertionError("Expected IOException for corrupted old-period mistakes file");
      }
      catch (IOException e)
      {
        String message = e.getMessage();
        requireThat(message, "message").contains(corruptedFile.toString());
        requireThat(message, "message").contains("malformed JSON");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Builds a minimal Phase 3 input JSON object for testing.
   *
   * @param scope the JVM scope
   * @param description the mistake description
   * @return an ObjectNode representing Phase 3 output
   */
  private ObjectNode buildPhase3Input(JvmScope scope, String description)
  {
    return RecordLearningTestUtils.buildPhase3Input(scope, description, null, null);
  }
}
