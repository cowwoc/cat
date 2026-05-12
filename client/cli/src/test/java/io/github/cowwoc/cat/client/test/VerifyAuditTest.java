/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.tool.ClaudeTool;
import io.github.cowwoc.cat.claude.hook.skills.VerifyAudit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tests for VerifyAudit.
 */
public final class VerifyAuditTest
{
  /**
   * Verifies that report renders all-Done results correctly.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void reportRendersAllDone() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-audit-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      VerifyAudit audit = new VerifyAudit(scope);

      String inputJson = """
        {
          "criteria_results": [
            {
              "criterion": "First criterion",
              "status": "Done",
              "evidence": [{"type": "file_exists", "detail": "test.md exists"}],
              "notes": "All good"
            },
            {
              "criterion": "Second criterion",
              "status": "Done",
              "evidence": [],
              "notes": ""
            }
          ],
          "file_results": {
            "modify": {
              "test.md": "exists_and_modified"
            },
            "delete": {}
          }
        }
        """;

      String result = audit.report("2.1-test-issue", inputJson);

      requireThat(result, "result").contains("AUDIT REPORT: 2.1-test-issue");
      requireThat(result, "result").contains("Total:        2");
      requireThat(result, "result").contains("✓ Done:       2");
      requireThat(result, "result").contains("◐ Partial:    0");
      requireThat(result, "result").contains("✗ Missing:    0");
      requireThat(result, "result").contains("\"assessment\" : \"COMPLETE\"");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that report renders mixed Done/Partial/Missing results.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void reportRendersMixedResults() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-audit-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      VerifyAudit audit = new VerifyAudit(scope);

      String inputJson = """
        {
          "criteria_results": [
            {
              "criterion": "Done criterion",
              "status": "Done",
              "evidence": [],
              "notes": ""
            },
            {
              "criterion": "Partial criterion",
              "status": "Partial",
              "evidence": [],
              "notes": "Some work done"
            },
            {
              "criterion": "Missing criterion",
              "status": "Missing",
              "evidence": [],
              "notes": "Not implemented"
            }
          ],
          "file_results": {
            "modify": {
              "test.md": "exists_and_modified"
            },
            "delete": {}
          }
        }
        """;

      String result = audit.report("2.1-test-issue", inputJson);

      requireThat(result, "result").contains("Total:        3");
      requireThat(result, "result").contains("✓ Done:       1");
      requireThat(result, "result").contains("◐ Partial:    1");
      requireThat(result, "result").contains("✗ Missing:    1");
      requireThat(result, "result").contains("\"assessment\" : \"INCOMPLETE\"");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that assessment is COMPLETE when all done, PARTIAL when some partial, INCOMPLETE when any missing.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void reportAssessmentLogic() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-audit-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      VerifyAudit audit = new VerifyAudit(scope);

      String allDone = """
        {
          "criteria_results": [
            {"criterion": "C1", "status": "Done", "evidence": [], "notes": ""}
          ],
          "file_results": {"modify": {}, "delete": {}}
        }
        """;
      String result1 = audit.report("test", allDone);
      requireThat(result1, "allDone").contains("\"assessment\" : \"COMPLETE\"");

      String somePartial = """
        {
          "criteria_results": [
            {"criterion": "C1", "status": "Done", "evidence": [], "notes": ""},
            {"criterion": "C2", "status": "Partial", "evidence": [], "notes": ""}
          ],
          "file_results": {"modify": {}, "delete": {}}
        }
        """;
      String result2 = audit.report("test", somePartial);
      requireThat(result2, "somePartial").contains("\"assessment\" : \"PARTIAL\"");

      String someMissing = """
        {
          "criteria_results": [
            {"criterion": "C1", "status": "Done", "evidence": [], "notes": ""},
            {"criterion": "C2", "status": "Missing", "evidence": [], "notes": ""}
          ],
          "file_results": {"modify": {}, "delete": {}}
        }
        """;
      String result3 = audit.report("test", someMissing);
      requireThat(result3, "someMissing").contains("\"assessment\" : \"INCOMPLETE\"");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that prepare returns a JSON object with issue identifiers and file_results only.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void prepareReturnsIssueIdentifiersAndFileResults() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-audit-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      VerifyAudit audit = new VerifyAudit(scope);

      Path issueDir = tempDir.resolve("issue");
      Files.createDirectories(issueDir);
      Path worktreeDir = tempDir.resolve("worktree");
      Files.createDirectories(worktreeDir);

      Files.writeString(issueDir.resolve("plan.md"), """
        # Plan

        ## Files to Modify
        - plugin/skills/test.md - Update

        ## Post-conditions
        - [ ] test.md has new section
        """);

      String argumentsJson = """
        {
          "issue_id": "2.1-test-issue",
          "issue_path": "%s",
          "worktree_path": "%s"
        }
        """.formatted(issueDir.toString(), worktreeDir.toString());

      String result = audit.prepare(argumentsJson);
      JsonNode root = mapper.readTree(result);

      requireThat(root.path("issue_id").asString(), "issueId").isEqualTo("2.1-test-issue");
      requireThat(root.path("issue_path").asString(), "issuePath").isEqualTo(issueDir.toString());
      requireThat(root.path("worktree_path").asString(), "worktreePath").isEqualTo(worktreeDir.toString());
      requireThat(root.path("file_results").isObject(), "file_results.isObject").isTrue();
      requireThat(root.path("file_results").path("modify").isObject(), "file_results.modify.isObject").isTrue();
      requireThat(root.path("file_results").path("delete").isObject(), "file_results.delete.isObject").isTrue();

      // criteria_count, file_count, and prompts must NOT be present in the new schema
      requireThat(root.has("criteria_count"), "has_criteria_count").isFalse();
      requireThat(root.has("file_count"), "has_file_count").isFalse();
      requireThat(root.has("prompts"), "has_prompts").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that prepare throws IllegalArgumentException when issue_id is missing.
   *
   * @throws IOException if JSON parsing fails
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*issue_id.*")
  public void prepareRejectsMissingIssueId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-audit-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      VerifyAudit audit = new VerifyAudit(scope);

      String json = """
        {
          "issue_path": "/tmp/issue",
          "worktree_path": "/tmp/worktree"
        }
        """;

      audit.prepare(json);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that prepare throws IllegalArgumentException when plan.md does not exist at issuePath.
   *
   * @throws IOException if file operations fail
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*plan\\.md.*")
  public void prepareRejectsMissingPlanMd() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-audit-test-");
    try (ClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      VerifyAudit audit = new VerifyAudit(scope);

      Path issueDir = tempDir.resolve("issue-no-plan");
      Files.createDirectories(issueDir);

      String json = """
        {
          "issue_id": "2.1-test-issue",
          "issue_path": "%s",
          "worktree_path": "/tmp/worktree"
        }
        """.formatted(issueDir.toString());

      audit.prepare(json);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
