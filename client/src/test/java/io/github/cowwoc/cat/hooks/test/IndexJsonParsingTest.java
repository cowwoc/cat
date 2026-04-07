/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import org.testng.annotations.Test;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Regression tests for index.json parsing.
 * <p>
 * Verifies that index.json files with valid JSON parse correctly, and that files containing Markdown
 * content instead of JSON produce the expected {@link StreamReadException}.
 * <p>
 * Tests are self-contained and run in parallel — no shared state.
 */
public class IndexJsonParsingTest
{
  /**
   * Verifies that a valid index.json string parses without error and the status field is readable.
   *
   * @throws IOException if JSON parsing fails unexpectedly
   */
  @Test
  public void validJsonParsesWithoutError() throws IOException
  {
    String content = "{\"status\":\"open\",\"dependencies\":[],\"blocks\":[]}";
    Path tempDir = Files.createTempDirectory("test-index-json-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      JsonNode result = mapper.readTree(content);
      requireThat(result, "result").isNotNull();
      requireThat(result.get("status").asString(), "status").isEqualTo("open");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Markdown content (as incorrectly written by the /cat:add-agent bug) throws
   * {@link StreamReadException} when parsed as JSON.
   *
   * @throws IOException if JSON parsing fails with the expected exception
   */
  @Test(expectedExceptions = StreamReadException.class)
  public void markdownContentThrowsStreamReadException() throws IOException
  {
    String content = "# State\n\n- **Status:** open\n- **Progress:** 0%\n";
    Path tempDir = Files.createTempDirectory("test-index-json-");
    try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      mapper.readTree(content);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
