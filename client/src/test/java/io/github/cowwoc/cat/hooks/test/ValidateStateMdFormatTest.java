/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.FileWriteHandler;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.write.ValidateStateMdFormat;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for {@link ValidateStateMdFormat}.
 */
public final class ValidateStateMdFormatTest
{
  /**
   * Verifies that an Edit tool call (no content field) to a STATE.md file is allowed.
   * <p>
   * The Edit tool sends old_string/new_string, not content. Validator must not block edits that
   * lack the content field.
   */
  @Test
  public void editToolCallWithoutContentIsAllowed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      ValidateStateMdFormat validator = new ValidateStateMdFormat();

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("old_string", "- **Status:** open");
      toolInput.put("new_string", "- **Status:** closed\n- **Resolution:** implemented");

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a Write tool call with valid STATE.md content is allowed.
   */
  @Test
  public void writeWithValidContentIsAllowed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      ValidateStateMdFormat validator = new ValidateStateMdFormat();

      String content = """
        # State

        - **Status:** open
        - **Progress:** 0%
        - **Dependencies:** []
        - **Blocks:** []
        - **Last Updated:** 2026-02-23
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a Write tool call with content missing the Status line is blocked.
   */
  @Test
  public void writeWithMissingStatusLineIsBlocked() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      ValidateStateMdFormat validator = new ValidateStateMdFormat();

      String content = """
        # State

        - **Progress:** 0%
        - **Dependencies:** []
        - **Last Updated:** 2026-02-23
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Missing '- **Status:** value' line");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that non-STATE.md file paths are not validated.
   */
  @Test
  public void nonStateMdFilesAreNotValidated() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      ValidateStateMdFormat validator = new ValidateStateMdFormat();

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/test-issue/PLAN.md");
      toolInput.put("content", "No status line here");

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a Write tool call with content missing the Progress line is blocked.
   */
  @Test
  public void writeWithMissingProgressLineIsBlocked() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      ValidateStateMdFormat validator = new ValidateStateMdFormat();

      String content = """
        # State

        - **Status:** open
        - **Dependencies:** []
        - **Last Updated:** 2026-02-23
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Missing '- **Progress:**");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a Write tool call with content missing the Dependencies line is blocked.
   */
  @Test
  public void writeWithMissingDependenciesLineIsBlocked() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      ValidateStateMdFormat validator = new ValidateStateMdFormat();

      String content = """
        # State

        - **Status:** open
        - **Progress:** 0%
        - **Last Updated:** 2026-02-23
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Missing '- **Dependencies:**");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a Write tool call without a file_path field is allowed.
   */
  @Test
  public void writeWithNullFilePathIsAllowed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      ValidateStateMdFormat validator = new ValidateStateMdFormat();

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("content", "some content without a path");

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that empty content (no-op Write) is allowed without blocking.
   */
  @Test
  public void emptyContentIsAllowed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      ValidateStateMdFormat validator = new ValidateStateMdFormat();

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", "");

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
