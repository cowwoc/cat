package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

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
        - **Last Updated:** 2026-02-12
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
        - **Last Updated:** 2026-02-12
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
        - **Last Updated:** 2026-02-12
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
        - **Last Updated:** 2026-02-12
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
        - **Last Updated:** 2026-02-12
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
        - **Last Updated:** 2026-02-12
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
        - **Last Updated:** 2026-02-12
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/test-issue/STATE.md");
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
        - **Last Updated:** 2026-02-12
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/test-issue/STATE.md");
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
        - **Last Updated:** 2026-02-12
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/test-issue/STATE.md");
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
        - **Last Updated:** 2026-02-12
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/test-issue/STATE.md");
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
   * Verifies that missing Last Updated key is rejected.
   */
  @Test
  public void missingLastUpdatedKeyIsRejected() throws IOException
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
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Missing mandatory key 'Last Updated'");
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
        - **Last Updated:** 2026-02-12
        - **CustomField:** some value
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/test-issue/STATE.md");
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
        - **Last Updated:** 2026-02-12
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/test-issue/STATE.md");
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
        - **Last Updated:** 2026-02-12
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/test-issue/STATE.md");
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
        - **Last Updated:** 2026-02-12
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/test-issue/STATE.md");
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
        - **Last Updated:** 2026-02-12
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/test-issue/STATE.md");
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
        - **Last Updated:** 2026-02-12
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/test-issue/STATE.md");
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
   * Verifies that invalid Last Updated format is rejected.
   */
  @Test
  public void invalidLastUpdatedFormatIsRejected() throws IOException
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
        - **Last Updated:** 02/12/2026
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/test-issue/STATE.md");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Invalid 'Last Updated' format");
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
        - **Last Updated:** 2026-02-12
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/test-issue/STATE.md");
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
        - **Last Updated:** 2026-02-12
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/test-issue/STATE.md");
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
        - **Last Updated:** 2026-02-12
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/test-issue/STATE.md");
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
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/test-issue/PLAN.md");
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
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/STATE.md");
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
        - **Last Updated:** 2026-02-12
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
        - **Last Updated:** 2026-02-12
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
        - **Last Updated:** 2026-02-12
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
        - **Last Updated:** 2026-02-12
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/test-issue/STATE.md");
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
        - **Last Updated:** 2026-02-12
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".claude/cat/issues/v2/v2.1/test-issue/STATE.md");
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
        - **Last Updated:** 2026-02-12
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
}
