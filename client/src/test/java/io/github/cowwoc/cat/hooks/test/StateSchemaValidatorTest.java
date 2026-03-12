/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.github.cowwoc.cat.hooks.FileWriteHandler;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.write.StateSchemaValidator;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


/**
 * Tests for {@link StateSchemaValidator}.
 */
public final class StateSchemaValidatorTest
{
  /**
   * Verifies that a valid open STATE.md file is accepted.
   */
  @Test
  public void validOpenStateIsAccepted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** open
        - **Progress:** 0%
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
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
   * Verifies that a valid in-progress STATE.md file is accepted.
   */
  @Test
  public void validInProgressStateIsAccepted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** in-progress
        - **Progress:** 50%
        - **Dependencies:** []
        - **Blocks:** [v2.1-other-issue]
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
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
   * Verifies that a valid blocked STATE.md file is accepted.
   */
  @Test
  public void validBlockedStateIsAccepted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** blocked
        - **Progress:** 25%
        - **Dependencies:** [v2.1-other-issue]
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
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
   * Verifies that a valid closed STATE.md file with resolution is accepted.
   */
  @Test
  public void validClosedStateWithResolutionIsAccepted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** closed
        - **Progress:** 100%
        - **Resolution:** implemented
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
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
   * Verifies that a closed STATE.md with duplicate resolution is accepted.
   */
  @Test
  public void closedStateWithDuplicateResolutionIsAccepted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** closed
        - **Progress:** 100%
        - **Resolution:** duplicate (v2.1-other-issue)
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
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
   * Verifies that STATE.md with parent field is accepted.
   */
  @Test
  public void stateWithParentIsAccepted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** open
        - **Progress:** 25%
        - **Parent:** v2.1-parent-issue
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
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
   * Verifies that STATE.md with dependencies is accepted.
   */
  @Test
  public void stateWithDependenciesIsAccepted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** open
        - **Progress:** 0%
        - **Dependencies:** [v2.1-dep1, v2.1-dep2]
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
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
   * Verifies that missing Status key is rejected.
   */
  @Test
  public void missingStatusKeyIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Progress:** 0%
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Missing mandatory key 'Status'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that missing Progress key is rejected.
   */
  @Test
  public void missingProgressKeyIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** open
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Missing mandatory key 'Progress'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that missing Dependencies key is rejected.
   */
  @Test
  public void missingDependenciesKeyIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** open
        - **Progress:** 0%
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Missing mandatory key 'Dependencies'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that missing Blocks key is rejected.
   */
  @Test
  public void missingBlocksKeyIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** open
        - **Progress:** 0%
        - **Dependencies:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Missing mandatory key 'Blocks'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that non-standard keys are rejected.
   */
  @Test
  public void nonStandardKeyIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** open
        - **Progress:** 0%
        - **Dependencies:** []
        - **Blocks:** []
        - **CustomField:** some value
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Non-standard key 'CustomField'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that invalid Status value is rejected.
   */
  @Test
  public void invalidStatusValueIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** pending
        - **Progress:** 0%
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Invalid Status value 'pending'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that invalid Progress format is rejected.
   */
  @Test
  public void invalidProgressFormatIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** open
        - **Progress:** 50 percent
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Invalid Progress format");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Progress value over 100 is rejected.
   */
  @Test
  public void progressOver100IsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** open
        - **Progress:** 150%
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Progress value out of range");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that invalid Dependencies format is rejected.
   */
  @Test
  public void invalidDependenciesFormatIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** open
        - **Progress:** 0%
        - **Dependencies:** none
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Invalid Dependencies format");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that invalid Blocks format is rejected.
   */
  @Test
  public void invalidBlocksFormatIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** open
        - **Progress:** 0%
        - **Dependencies:** []
        - **Blocks:** none
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Invalid Blocks format");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that closed status without Resolution is rejected.
   */
  @Test
  public void closedStatusWithoutResolutionIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** closed
        - **Progress:** 100%
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Resolution is required when Status is 'closed'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that invalid Resolution value is rejected.
   */
  @Test
  public void invalidResolutionValueIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** closed
        - **Progress:** 100%
        - **Resolution:** completed
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Invalid Resolution value");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that invalid Parent format is rejected.
   */
  @Test
  public void invalidParentFormatIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** open
        - **Progress:** 0%
        - **Parent:** Invalid_Parent_Name
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Invalid Parent format");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that non-STATE.md files are allowed.
   */
  @Test
  public void nonStateMdFilesAreAllowed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = "Any content here";

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/PLAN.md");
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
   * Verifies that version-level STATE.md files are allowed without validation.
   */
  @Test
  public void versionStateMdFilesAreAllowed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = "Any content here - version STATE.md has different format";

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/STATE.md");
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
   * Verifies that empty STATE.md content is allowed.
   */
  @Test
  public void emptyStateMdContentIsAllowed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
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

  /**
   * Verifies that obsolete resolution with explanation is accepted.
   */
  @Test
  public void obsoleteResolutionWithExplanationIsAccepted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** closed
        - **Progress:** 100%
        - **Resolution:** obsolete (feature was removed in v3.0)
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
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
   * Verifies that won't-fix resolution with explanation is accepted.
   */
  @Test
  public void wontFixResolutionWithExplanationIsAccepted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** closed
        - **Progress:** 100%
        - **Resolution:** won't-fix (out of scope for v2.1)
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
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
   * Verifies that not-applicable resolution with explanation is accepted.
   */
  @Test
  public void notApplicableResolutionWithExplanationIsAccepted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** closed
        - **Progress:** 100%
        - **Resolution:** not-applicable (requirements changed)
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
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
   * Verifies that negative Progress value is rejected.
   */
  @Test
  public void negativeProgressIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** open
        - **Progress:** -5%
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Invalid Progress format");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that multiple violations report the first error.
   */
  @Test
  public void multipleViolationsReportsFirstError() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Progress:** 150%
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Missing mandatory key 'Status'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that Resolution field is allowed when Status is open.
   */
  @Test
  public void resolutionFieldAllowedWhenStatusIsOpen() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** open
        - **Progress:** 50%
        - **Resolution:** implemented
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
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
   * Verifies that a STATE.md file containing the non-standard 'Last Updated' field is rejected.
   */
  @Test
  public void lastUpdatedFieldIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** open
        - **Progress:** 0%
        - **Dependencies:** []
        - **Blocks:** []
        - **Last Updated:** 2026-02-12
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Non-standard key 'Last Updated'");
      requireThat(result.reason(), "reason").contains("Only these keys are allowed:");
      requireThat(result.reason(), "reason").contains("Mandatory: Blocks, Dependencies, Progress, Status");
      requireThat(result.reason(), "reason").contains("Optional: Parent, Resolution, Target Branch");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the 'Target Branch' field (written by WorkPrepare) is accepted.
   */
  @Test
  public void targetBranchFieldIsAccepted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** in-progress
        - **Progress:** 50%
        - **Dependencies:** []
        - **Blocks:** []
        - **Target Branch:** v2.1
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
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
   * Verifies that the 'Final commit' field (an unsupported agent-generated field) is rejected.
   */
  @Test
  public void finalCommitFieldIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** closed
        - **Progress:** 100%
        - **Resolution:** implemented
        - **Dependencies:** []
        - **Blocks:** []
        - **Final commit:** abc123
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Non-standard key 'Final commit'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a STATE.md file containing the non-standard 'Completed' field is rejected.
   */
  @Test
  public void completedFieldIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** closed
        - **Progress:** 100%
        - **Resolution:** implemented
        - **Dependencies:** []
        - **Blocks:** []
        - **Completed:** 2026-02-12
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Non-standard key 'Completed'");
      requireThat(result.reason(), "reason").contains("Only these keys are allowed:");
      requireThat(result.reason(), "reason").contains("Mandatory: Blocks, Dependencies, Progress, Status");
      requireThat(result.reason(), "reason").contains("Optional: Parent, Resolution, Target Branch");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that field keys with leading whitespace are accepted (whitespace is stripped).
   */
  @Test
  public void testFieldKeysWithLeadingWhitespace() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **  Status:** open
        - **Progress:** 0%
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
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
   * Verifies that field keys with trailing whitespace are accepted (whitespace is stripped).
   */
  @Test
  public void testFieldKeysWithTrailingWhitespace() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status  :** open
        - **Progress:** 0%
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
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
   * Verifies that field keys with internal whitespace are rejected.
   */
  @Test
  public void testFieldKeysWithInternalWhitespaceInvalid() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Final Commit:** abc123
        - **Status:** open
        - **Progress:** 0%
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Non-standard key 'Final Commit'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that field keys with mixed whitespace (tabs and spaces) are accepted (whitespace is stripped).
   */
  @Test
  public void testFieldKeysWithMixedWhitespace() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = "# State\n\n- **\tStatus\t:** open\n- **Progress:** 0%\n- **Dependencies:** []\n" +
        "- **Blocks:** []";

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
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
   * Verifies that lowercase 'status' field is case-sensitive and rejected.
   */
  @Test
  public void testStatusFieldCaseInsensitivity() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **status:** open
        - **Progress:** 0%
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Missing mandatory key 'Status'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that lowercase 'progress' field is case-sensitive and rejected.
   */
  @Test
  public void testProgressFieldCaseInsensitivity() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** open
        - **progress:** 0%
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Missing mandatory key 'Progress'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that lowercase 'dependencies' field is case-sensitive and rejected.
   */
  @Test
  public void testDependenciesFieldCaseInsensitivity() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** open
        - **Progress:** 0%
        - **dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Missing mandatory key 'Dependencies'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that lowercase 'blocks' field is case-sensitive and rejected.
   */
  @Test
  public void testBlocksFieldCaseInsensitivity() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** open
        - **Progress:** 0%
        - **Dependencies:** []
        - **blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Missing mandatory key 'Blocks'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that lowercase 'resolution' field is case-sensitive and rejected as non-standard.
   */
  @Test
  public void testResolutionFieldCaseInsensitivity() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** closed
        - **Progress:** 100%
        - **resolution:** implemented
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Non-standard key 'resolution'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that lowercase 'parent' field is case-sensitive and rejected.
   */
  @Test
  public void testParentFieldCaseInsensitivity() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** open
        - **Progress:** 0%
        - **parent:** v2.1-parent-issue
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Non-standard key 'parent'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that non-standard 'Last Updated' combined with missing mandatory 'Status' reports the mandatory key first.
   */
  @Test
  public void testNonStandardKeyWithMissingMandatory() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Progress:** 0%
        - **Dependencies:** []
        - **Blocks:** []
        - **Last Updated:** 2026-02-12
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Missing mandatory key 'Status'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that non-standard 'Last Updated' combined with non-standard 'CustomField' reports the first non-standard
   * key found.
   */
  @Test
  public void testMultipleNonStandardKeys() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** open
        - **Progress:** 0%
        - **Dependencies:** []
        - **Blocks:** []
        - **Last Updated:** 2026-02-12
        - **CustomField:** value
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Non-standard key");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that multiple non-standard keys report the first one encountered.
   */
  @Test
  public void testMultipleNonStandardKeysReportsFirst() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** open
        - **Progress:** 0%
        - **Dependencies:** []
        - **Blocks:** []
        - **CustomField1:** value1
        - **CustomField2:** value2
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      String reason = result.reason();
      // Should contain one of the custom fields mentioned
      boolean hasCustomField = reason.contains("CustomField");
      requireThat(hasCustomField, "shouldContainCustomField").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that non-standard key combined with invalid Progress value reports non-standard key first.
   */
  @Test
  public void testNonStandardKeyWithInvalidValue() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** open
        - **Progress:** invalid
        - **Dependencies:** []
        - **Blocks:** []
        - **CustomField:** value
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Non-standard key 'CustomField'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that special characters in field keys (pipes, brackets, parentheses) are parsed but rejected.
   */
  @Test
  public void testSpecialCharactersAreParsedButRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** open
        - **Progress:** 0%
        - **Dependencies:** []
        - **Blocks:** []
        - **Field|Name:** value
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Non-standard key");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a STATE.md file containing the non-standard 'Closed' field is rejected.
   */
  @Test
  public void closedFieldIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** closed
        - **Progress:** 100%
        - **Resolution:** implemented
        - **Dependencies:** []
        - **Blocks:** []
        - **Closed:** 2026-02-12
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Non-standard key 'Closed'");
      requireThat(result.reason(), "reason").contains("Mandatory: Blocks, Dependencies, Progress, Status");
      requireThat(result.reason(), "reason").contains("Optional: Parent, Resolution, Target Branch");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that uppercase 'CLOSED' status correctly triggers the Resolution requirement.
   */
  @Test
  public void testUppercaseClosedStatusRequiresResolution() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** CLOSED
        - **Progress:** 100%
        - **Dependencies:** []
        - **Blocks:** []
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Resolution is required when Status is 'closed'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a STATE.md file containing the non-standard 'Started' field is rejected.
   */
  @Test
  public void startedFieldIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** in-progress
        - **Progress:** 50%
        - **Dependencies:** []
        - **Blocks:** []
        - **Started:** 2026-02-12
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Non-standard key 'Started'");
      requireThat(result.reason(), "reason").contains("Only these keys are allowed:");
      requireThat(result.reason(), "reason").contains("Mandatory: Blocks, Dependencies, Progress, Status");
      requireThat(result.reason(), "reason").contains("Optional: Parent, Resolution, Target Branch");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a STATE.md file containing the non-standard 'Tokens Used' field is rejected.
   */
  @Test
  public void tokensUsedFieldIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String content = """
        # State

        - **Status:** open
        - **Progress:** 0%
        - **Dependencies:** []
        - **Blocks:** []
        - **Tokens Used:** 12345
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Non-standard key 'Tokens Used'");
      requireThat(result.reason(), "reason").contains("Only these keys are allowed:");
      requireThat(result.reason(), "reason").contains("Mandatory: Blocks, Dependencies, Progress, Status");
      requireThat(result.reason(), "reason").contains("Optional: Parent, Resolution, Target Branch");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an Edit call adding a non-standard field to STATE.md is blocked.
   */
  @Test
  public void editWithNonStandardFieldIsBlocked() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String diskContent = """
        # State

        - **Status:** open
        - **Progress:** 0%
        - **Dependencies:** []
        - **Blocks:** []
        """;

      // Write a valid STATE.md file to disk at the path expected by the Edit toolInput
      Path issueDir = tempDir.resolve(".cat/issues/v2/v2.1/test-issue");
      Files.createDirectories(issueDir);
      Path stateMd = issueDir.resolve("STATE.md");
      Files.writeString(stateMd, diskContent, UTF_8);

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", stateMd.toString());
      toolInput.put("old_string", "- **Blocks:** []");
      toolInput.put("new_string", "- **Blocks:** []\n- **Stakeholder Review:** xyz");

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Stakeholder Review");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an Edit call making a valid change to STATE.md is allowed.
   */
  @Test
  public void editWithValidChangeIsAllowed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String diskContent = """
        # State

        - **Status:** open
        - **Progress:** 0%
        - **Dependencies:** []
        - **Blocks:** []
        """;

      Path issueDir = tempDir.resolve(".cat/issues/v2/v2.1/test-issue");
      Files.createDirectories(issueDir);
      Path stateMd = issueDir.resolve("STATE.md");
      Files.writeString(stateMd, diskContent, UTF_8);

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", stateMd.toString());
      toolInput.put("old_string", "- **Status:** open\n- **Progress:** 0%");
      toolInput.put("new_string", "- **Status:** closed\n- **Progress:** 100%\n- **Resolution:** implemented");

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an Edit call with a non-existent file fails open (is allowed).
   */
  @Test
  public void editWithMissingFileReturnsError() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", tempDir.resolve(".cat/issues/v2/v2.1/nonexistent/STATE.md").toString());
      toolInput.put("old_string", "- **Blocks:** []");
      toolInput.put("new_string", "- **Blocks:** []\n- **Stakeholder Review:** xyz");

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      // File does not exist, so applyEdit() throws IOException. This should be reported to the
      // user as a blocked edit with the error details, not silently allowed.
      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Edit validation failed");
      requireThat(result.reason(), "reason").contains("NoSuchFileException");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an Edit replacing only the first occurrence is validated correctly when oldString appears
   * multiple times in STATE.md.
   * <p>
   * When "[]" appears in both "- **Dependencies:** []" and "- **Blocks:** []", the Edit tool replaces only
   * the first match. Replacing "[]" with "[dep-a]" must yield "- **Dependencies:** [dep-a]" while leaving
   * "- **Blocks:** []" untouched — and the resulting content must be valid.
   */
  @Test
  public void editWithMultipleOccurrencesOfOldStringReplacesFirstOnly() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      // "[]" appears twice: once in Dependencies and once in Blocks
      String diskContent = """
        # State

        - **Status:** open
        - **Progress:** 0%
        - **Dependencies:** []
        - **Blocks:** []
        """;

      Path issueDir = tempDir.resolve(".cat/issues/v2/v2.1/test-issue");
      Files.createDirectories(issueDir);
      Path stateMd = issueDir.resolve("STATE.md");
      Files.writeString(stateMd, diskContent, UTF_8);

      // Replace first occurrence of "[]" with "[dep-a]"; Blocks still has "[]"
      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", stateMd.toString());
      toolInput.put("old_string", "[]");
      toolInput.put("new_string", "[dep-a]");

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      // Result must be allowed — the reconstructed content is a valid open STATE.md
      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an Edit call whose oldString is not present in the file is blocked (fail-fast).
   * <p>
   * When oldString is missing from the file, the validator cannot reconstruct the post-edit content.
   * Rather than silently allowing the edit, it blocks with a clear error message.
   */
  @Test
  public void editWithOldStringNotFoundInFileIsBlocked() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String diskContent = """
        # State

        - **Status:** open
        - **Progress:** 0%
        - **Dependencies:** []
        - **Blocks:** []
        """;

      Path issueDir = tempDir.resolve(".cat/issues/v2/v2.1/test-issue");
      Files.createDirectories(issueDir);
      Path stateMd = issueDir.resolve("STATE.md");
      Files.writeString(stateMd, diskContent, UTF_8);

      // oldString does not exist in the file
      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", stateMd.toString());
      toolInput.put("old_string", "- **Status:** in_progress");
      toolInput.put("new_string", "- **Status:** closed\n- **Progress:** 100%\n- **Resolution:** implemented");

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      // Validator should block — oldString not found means we can't validate the post-edit content
      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("old_string not found");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an Edit that changes Status to an invalid value is blocked.
   */
  @Test
  public void editResultingInInvalidStatusIsBlocked() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator();

      String diskContent = """
        # State

        - **Status:** open
        - **Progress:** 0%
        - **Dependencies:** []
        - **Blocks:** []
        """;

      Path issueDir = tempDir.resolve(".cat/issues/v2/v2.1/test-issue");
      Files.createDirectories(issueDir);
      Path stateMd = issueDir.resolve("STATE.md");
      Files.writeString(stateMd, diskContent, UTF_8);

      // Replace Status with an invalid value
      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", stateMd.toString());
      toolInput.put("old_string", "- **Status:** open");
      toolInput.put("new_string", "- **Status:** pending");

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Status");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
