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
   * Verifies that a valid open index.json file is accepted.
   */
  @Test
  public void validOpenIndexIsAccepted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      String content = """
        {
          "status": "open"
        }
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/index.json");
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
   * Verifies that a valid in-progress index.json file is accepted.
   */
  @Test
  public void validInProgressIndexIsAccepted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      String content = """
        {
          "status": "in-progress",
          "dependencies": [],
          "blocks": ["2.1-other-issue"]
        }
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/index.json");
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
   * Verifies that a valid blocked index.json file is accepted.
   */
  @Test
  public void validBlockedIndexIsAccepted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      String content = """
        {
          "status": "blocked",
          "dependencies": ["2.1-other-issue"],
          "blocks": []
        }
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/index.json");
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
   * Verifies that a valid closed index.json file with resolution is accepted.
   */
  @Test
  public void validClosedIndexWithResolutionIsAccepted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      String content = """
        {
          "status": "closed",
          "resolution": "implemented"
        }
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/index.json");
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
   * Verifies that a closed index.json with duplicate resolution is accepted.
   */
  @Test
  public void closedIndexWithDuplicateResolutionIsAccepted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      String content = """
        {
          "status": "closed",
          "resolution": "duplicate 2.1-other-issue"
        }
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/index.json");
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
   * Verifies that index.json with parent field is accepted.
   */
  @Test
  public void indexWithParentIsAccepted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      String content = """
        {
          "status": "open",
          "parent": "v2.1-parent-issue"
        }
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/index.json");
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
   * Verifies that index.json with dependencies array is accepted.
   */
  @Test
  public void indexWithDependenciesIsAccepted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      String content = """
        {
          "status": "open",
          "dependencies": ["2.1-dep1", "2.1-dep2"]
        }
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/index.json");
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
   * Verifies that index.json with all optional fields is accepted.
   */
  @Test
  public void indexWithAllOptionalFieldsIsAccepted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      String content = """
        {
          "status": "closed",
          "resolution": "implemented",
          "target_branch": "v2.1",
          "dependencies": ["2.1-dep1"],
          "blocks": ["2.1-other"],
          "parent": "v2.1-parent-issue",
          "decomposedInto": ["2.1-sub1", "2.1-sub2"]
        }
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/index.json");
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
   * Verifies that missing status field is rejected.
   */
  @Test
  public void missingStatusFieldIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      String content = """
        {
          "dependencies": [],
          "blocks": []
        }
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/index.json");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Missing mandatory field 'status'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that non-standard fields are rejected.
   */
  @Test
  public void nonStandardFieldIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      String content = """
        {
          "status": "open",
          "custom_field": "some value"
        }
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/index.json");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Non-standard field 'custom_field'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that invalid status value is rejected.
   */
  @Test
  public void invalidStatusValueIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      String content = """
        {
          "status": "pending"
        }
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/index.json");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Invalid status value 'pending'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that closed status without resolution is rejected.
   */
  @Test
  public void closedStatusWithoutResolutionIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      String content = """
        {
          "status": "closed"
        }
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/index.json");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("resolution is required when status is 'closed'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that invalid resolution value is rejected.
   */
  @Test
  public void invalidResolutionValueIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      String content = """
        {
          "status": "closed",
          "resolution": "completed"
        }
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/index.json");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Invalid resolution value");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that invalid parent format is rejected.
   */
  @Test
  public void invalidParentFormatIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      String content = """
        {
          "status": "open",
          "parent": "Invalid_Parent_Name"
        }
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/index.json");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Invalid parent format");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that non-index.json files are allowed.
   */
  @Test
  public void nonIndexJsonFilesAreAllowed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      String content = "Any content here";

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/plan.md");
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
   * Verifies that version-level JSON files at the wrong path are allowed without validation.
   */
  @Test
  public void versionLevelJsonFileIsAllowed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      // Version-level path (missing issue directory segment) — pattern does not match
      String content = "{\"status\": \"open\"}";

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/index.json");
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
   * Verifies that empty content is allowed (no content to validate for Write-without-content).
   */
  @Test
  public void emptyContentIsAllowed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/index.json");
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
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      String content = """
        {
          "status": "closed",
          "resolution": "obsolete feature was removed in v3.0"
        }
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/index.json");
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
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      String content = """
        {
          "status": "closed",
          "resolution": "won't-fix out of scope for v2.1"
        }
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/index.json");
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
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      String content = """
        {
          "status": "closed",
          "resolution": "not-applicable requirements changed"
        }
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/index.json");
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
   * Verifies that invalid JSON content is rejected.
   */
  @Test
  public void invalidJsonContentIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      String content = "not valid json {{{";

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/index.json");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("invalid JSON");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that a JSON array root element is rejected.
   */
  @Test
  public void jsonArrayRootIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      String content = "[\"status\", \"open\"]";

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/index.json");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("root element must be a JSON object");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that multiple violations report the first error (missing status).
   */
  @Test
  public void multipleViolationsReportsFirstError() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      // Missing status, non-standard field — should report status first
      String content = """
        {
          "custom_field": "bad"
        }
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/index.json");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Missing mandatory field 'status'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that resolution field is allowed when status is open.
   */
  @Test
  public void resolutionFieldAllowedWhenStatusIsOpen() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      String content = """
        {
          "status": "open",
          "resolution": "implemented"
        }
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/index.json");
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
   * Verifies that the progress field (unsupported in new schema) is rejected.
   */
  @Test
  public void progressFieldIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      String content = """
        {
          "status": "open",
          "progress": 50
        }
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/index.json");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Non-standard field 'progress'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the target_branch optional field is accepted.
   */
  @Test
  public void targetBranchFieldIsAccepted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      String content = """
        {
          "status": "in-progress",
          "target_branch": "v2.1"
        }
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/index.json");
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
   * Verifies that the non-standard 'last_updated' field is rejected.
   */
  @Test
  public void lastUpdatedFieldIsRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      String content = """
        {
          "status": "open",
          "last_updated": "2026-02-12"
        }
        """;

      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", ".cat/issues/v2/v2.1/test-issue/index.json");
      toolInput.put("content", content);

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Non-standard field 'last_updated'");
      requireThat(result.reason(), "reason").contains("Only these fields are allowed:");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that an Edit tool operation on index.json is validated after applying the replacement.
   */
  @Test
  public void editOperationAppliesReplacementBeforeValidation() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      // Create a valid index.json file on disk
      Path issueDir = tempDir.resolve(".cat").resolve("issues").resolve("v2").
        resolve("v2.1").resolve("test-issue");
      Files.createDirectories(issueDir);
      Path indexFile = issueDir.resolve("index.json");
      Files.writeString(indexFile, "{\"status\": \"open\"}");

      JsonMapper mapper = scope.getJsonMapper();
      StateSchemaValidator validator = new StateSchemaValidator(scope);

      // Edit operation: replace "open" with "closed" (without resolution → should be blocked)
      ObjectNode toolInput = mapper.createObjectNode();
      toolInput.put("file_path", indexFile.toString());
      toolInput.put("old_string", "\"open\"");
      toolInput.put("new_string", "\"closed\"");

      FileWriteHandler.Result result = validator.check(toolInput, "session-123");

      // "closed" without "resolution" should be blocked
      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("resolution is required when status is 'closed'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
