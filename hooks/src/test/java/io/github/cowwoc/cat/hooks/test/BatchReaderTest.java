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
  @Test
  public void configValidatesNullPattern()
  {
    try
    {
      new Config(null, 5, 100, "");
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("pattern");
    }
  }

  /**
   * Verifies that Config validates non-positive maxFiles.
   */
  @Test
  public void configValidatesNonPositiveMaxFiles()
  {
    try
    {
      new Config("pattern", 0, 100, "");
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("maxFiles");
    }

    try
    {
      new Config("pattern", -1, 100, "");
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("maxFiles");
    }
  }

  /**
   * Verifies that Config validates negative contextLines.
   */
  @Test
  public void configValidatesNegativeContextLines()
  {
    try
    {
      new Config("pattern", 5, -1, "");
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("contextLines");
    }
  }

  /**
   * Verifies that Config validates null fileType.
   */
  @Test
  public void configValidatesNullFileType()
  {
    try
    {
      new Config("pattern", 5, 100, null);
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("fileType");
    }
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

    String json = result.toJson();

    JsonMapper mapper = JsonMapper.builder().build();
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
  @Test
  public void resultValidatesNullStatus()
  {
    try
    {
      new Result(null, "msg", 0L, "pattern", 0, 0, "", "/dir", "2024-01-01T00:00:00Z");
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("status");
    }
  }

  /**
   * Verifies that Result validates null message.
   */
  @Test
  public void resultValidatesNullMessage()
  {
    try
    {
      new Result(OperationStatus.SUCCESS, null, 0L, "pattern", 0, 0, "", "/dir", "2024-01-01T00:00:00Z");
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("message");
    }
  }

  /**
   * Verifies that Result validates negative durationSeconds.
   */
  @Test
  public void resultValidatesNegativeDurationSeconds()
  {
    try
    {
      new Result(OperationStatus.SUCCESS, "msg", -1L, "pattern", 0, 0, "", "/dir", "2024-01-01T00:00:00Z");
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("durationSeconds");
    }
  }

  /**
   * Verifies that Result validates null pattern.
   */
  @Test
  public void resultValidatesNullPattern()
  {
    try
    {
      new Result(OperationStatus.SUCCESS, "msg", 0L, null, 0, 0, "", "/dir", "2024-01-01T00:00:00Z");
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("pattern");
    }
  }

  /**
   * Verifies that Result validates negative filesFound.
   */
  @Test
  public void resultValidatesNegativeFilesFound()
  {
    try
    {
      new Result(OperationStatus.SUCCESS, "msg", 0L, "pattern", -1, 0, "", "/dir", "2024-01-01T00:00:00Z");
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("filesFound");
    }
  }

  /**
   * Verifies that Result validates negative filesRead.
   */
  @Test
  public void resultValidatesNegativeFilesRead()
  {
    try
    {
      new Result(OperationStatus.SUCCESS, "msg", 0L, "pattern", 0, -1, "", "/dir", "2024-01-01T00:00:00Z");
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("filesRead");
    }
  }

  /**
   * Verifies that Result validates null outputContent.
   */
  @Test
  public void resultValidatesNullOutputContent()
  {
    try
    {
      new Result(OperationStatus.SUCCESS, "msg", 0L, "pattern", 0, 0, null, "/dir", "2024-01-01T00:00:00Z");
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("outputContent");
    }
  }

  /**
   * Verifies that Result validates null workingDirectory.
   */
  @Test
  public void resultValidatesNullWorkingDirectory()
  {
    try
    {
      new Result(OperationStatus.SUCCESS, "msg", 0L, "pattern", 0, 0, "", null, "2024-01-01T00:00:00Z");
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("workingDirectory");
    }
  }

  /**
   * Verifies that Result validates null timestamp.
   */
  @Test
  public void resultValidatesNullTimestamp()
  {
    try
    {
      new Result(OperationStatus.SUCCESS, "msg", 0L, "pattern", 0, 0, "", "/dir", null);
      requireThat(false, "shouldThrow").isTrue();
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("timestamp");
    }
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

    if (result.status() == OperationStatus.SUCCESS)
    {
      requireThat(result.outputContent(), "outputContent").contains("truncated");
      requireThat(result.outputContent(), "outputContent").contains("showing 5");
    }
  }
}
