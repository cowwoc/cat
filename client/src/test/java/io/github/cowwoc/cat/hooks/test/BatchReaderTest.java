/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.util.BatchReader;
import io.github.cowwoc.cat.hooks.util.BatchReader.Config;
import io.github.cowwoc.cat.hooks.util.BatchReader.Result;
import io.github.cowwoc.cat.hooks.util.OperationStatus;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for BatchReader.
 * <p>
 * Tests verify batch file reading functionality, configuration validation, and JSON output.
 */
public class BatchReaderTest
{
  /**
   * Verifies that Config validates null pattern.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*pattern.*")
  public void configValidatesNullPattern()
  {
    new Config(null, 5, 100, "");
  }

  /**
   * Verifies that Config validates non-positive maxFiles.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*maxFiles.*")
  public void configValidatesNonPositiveMaxFiles()
  {
    new Config("pattern", 0, 100, "");

    new Config("pattern", -1, 100, "");
  }

  /**
   * Verifies that Config validates negative contextLines.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*contextLines.*")
  public void configValidatesNegativeContextLines()
  {
    new Config("pattern", 5, -1, "");
  }

  /**
   * Verifies that Config validates null fileType.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*fileType.*")
  public void configValidatesNullFileType()
  {
    new Config("pattern", 5, 100, null);
  }

  /**
   * Verifies that read returns error for non-existent pattern.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void readReturnsErrorForNonExistentPattern() throws IOException
  {
    Config config = new Config("NONEXISTENT_PATTERN_XYZ123_UNLIKELY_TO_MATCH", 5, 100, "");
    Result result = BatchReader.read(config);

    requireThat(result.status(), "status").isEqualTo(OperationStatus.ERROR);
    requireThat(result.filesFound(), "filesFound").isEqualTo(0);
    requireThat(result.filesRead(), "filesRead").isEqualTo(0);
  }

  /**
   * Verifies that toJson produces valid JSON.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void toJsonProducesValidJson() throws IOException
  {
    Result result = new Result(
      OperationStatus.SUCCESS,
      "Test message",
      1L,
      "test-pattern",
      3,
      3,
      "file contents",
      "/working/dir",
      "2024-01-01T00:00:00Z");

    JsonMapper mapper = JsonMapper.builder().build();
    String json = result.toJson(mapper);

    JsonNode root = mapper.readTree(json);
    requireThat(root.get("status").asString(), "status").isEqualTo("success");
    requireThat(root.get("message").asString(), "message").isEqualTo("Test message");
    requireThat(root.get("duration_seconds").asLong(), "duration_seconds").isEqualTo(1L);
    requireThat(root.get("pattern").asString(), "pattern").isEqualTo("test-pattern");
    requireThat(root.get("files_found").asInt(), "files_found").isEqualTo(3);
    requireThat(root.get("files_read").asInt(), "files_read").isEqualTo(3);
    requireThat(root.get("working_directory").asString(), "working_directory").isEqualTo("/working/dir");
    requireThat(root.get("timestamp").asString(), "timestamp").isEqualTo("2024-01-01T00:00:00Z");
  }

  /**
   * Verifies that Result validates null status.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*status.*")
  public void resultValidatesNullStatus()
  {
    new Result(null, "msg", 0L, "pattern", 0, 0, "", "/dir", "2024-01-01T00:00:00Z");
  }

  /**
   * Verifies that Result validates null message.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*message.*")
  public void resultValidatesNullMessage()
  {
    new Result(OperationStatus.SUCCESS, null, 0L, "pattern", 0, 0, "", "/dir", "2024-01-01T00:00:00Z");
  }

  /**
   * Verifies that Result validates negative durationSeconds.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*durationSeconds.*")
  public void resultValidatesNegativeDurationSeconds()
  {
    new Result(OperationStatus.SUCCESS, "msg", -1L, "pattern", 0, 0, "", "/dir", "2024-01-01T00:00:00Z");
  }

  /**
   * Verifies that Result validates null pattern.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*pattern.*")
  public void resultValidatesNullPattern()
  {
    new Result(OperationStatus.SUCCESS, "msg", 0L, null, 0, 0, "", "/dir", "2024-01-01T00:00:00Z");
  }

  /**
   * Verifies that Result validates negative filesFound.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*filesFound.*")
  public void resultValidatesNegativeFilesFound()
  {
    new Result(OperationStatus.SUCCESS, "msg", 0L, "pattern", -1, 0, "", "/dir", "2024-01-01T00:00:00Z");
  }

  /**
   * Verifies that Result validates negative filesRead.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*filesRead.*")
  public void resultValidatesNegativeFilesRead()
  {
    new Result(OperationStatus.SUCCESS, "msg", 0L, "pattern", 0, -1, "", "/dir", "2024-01-01T00:00:00Z");
  }

  /**
   * Verifies that Result validates null outputContent.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*outputContent.*")
  public void resultValidatesNullOutputContent()
  {
    new Result(OperationStatus.SUCCESS, "msg", 0L, "pattern", 0, 0, null, "/dir", "2024-01-01T00:00:00Z");
  }

  /**
   * Verifies that Result validates null workingDirectory.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*workingDirectory.*")
  public void resultValidatesNullWorkingDirectory()
  {
    new Result(OperationStatus.SUCCESS, "msg", 0L, "pattern", 0, 0, "", null, "2024-01-01T00:00:00Z");
  }

  /**
   * Verifies that Result validates null timestamp.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*timestamp.*")
  public void resultValidatesNullTimestamp()
  {
    new Result(OperationStatus.SUCCESS, "msg", 0L, "pattern", 0, 0, "", "/dir", null);
  }

  /**
   * Verifies that Config allows zero contextLines for reading entire files.
   */
  @Test
  public void configAllowsZeroContextLines()
  {
    Config config = new Config("pattern", 5, 0, "");
    requireThat(config.contextLines(), "contextLines").isEqualTo(0);
  }

  /**
   * Verifies that Config allows empty fileType for no filtering.
   */
  @Test
  public void configAllowsEmptyFileType()
  {
    Config config = new Config("pattern", 5, 100, "");
    requireThat(config.fileType(), "fileType").isEmpty();
  }

  /**
   * Verifies that read finds and reads actual files with known content.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void readFindsAndReadsActualFiles() throws IOException
  {
    Config config = new Config("package io.github.cowwoc.cat.hooks.util", 10, 0, "java");
    Result result = BatchReader.read(config);

    requireThat(result.status(), "status").isEqualTo(OperationStatus.SUCCESS);
    requireThat(result.filesFound(), "filesFound").isPositive();
    requireThat(result.filesRead(), "filesRead").isPositive();
    requireThat(result.outputContent(), "outputContent").contains("package io.github.cowwoc.cat.hooks.util");
    requireThat(result.outputContent(), "outputContent").contains("FILE:");
  }

  /**
   * Verifies that read truncates output when contextLines is specified.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void readTruncatesWithContextLines() throws IOException
  {
    Config config = new Config("package io.github.cowwoc.cat.hooks.util", 1, 5, "java");
    Result result = BatchReader.read(config);

    requireThat(result.status(), "status").isEqualTo(OperationStatus.SUCCESS);
    requireThat(result.outputContent(), "outputContent").contains("truncated");
    requireThat(result.outputContent(), "outputContent").contains("showing 5");
  }

  /**
   * Verifies that parseIntFlag returns the parsed integer for a valid value.
   */
  @Test
  public void parseIntFlagReturnsIntegerForValidValue()
  {
    int result = BatchReader.parseIntFlag("--max-files", "42");
    requireThat(result, "result").isEqualTo(42);
  }

  /**
   * Verifies that parseIntFlag throws IllegalArgumentException for non-integer input.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*--max-files.*")
  public void parseIntFlagThrowsForNonIntegerValue()
  {
    BatchReader.parseIntFlag("--max-files", "not-a-number");
  }
}
