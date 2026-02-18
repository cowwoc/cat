/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.VerifyAudit;
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
   * Verifies that parse extracts acceptance criteria from PLAN.md.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void parseExtractsCriteria() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-audit-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      VerifyAudit audit = new VerifyAudit(scope);

      Path planFile = tempDir.resolve("PLAN.md");
      Files.writeString(planFile, """
        # Plan

        ## Acceptance Criteria
        - [ ] First criterion
        - [x] Second criterion checked
        - [ ] Third criterion

        ## Other Section
        Not criteria
        """);

      String result = audit.parse(planFile);
      JsonNode root = mapper.readTree(result);
      JsonNode criteria = root.path("criteria");

      requireThat(criteria.isArray(), "criteria.isArray").isTrue();
      requireThat(criteria.size(), "criteria.size").isEqualTo(3);
      requireThat(criteria.get(0).asString(), "criteria[0]").isEqualTo("First criterion");
      requireThat(criteria.get(1).asString(), "criteria[1]").isEqualTo("Second criterion checked");
      requireThat(criteria.get(2).asString(), "criteria[2]").isEqualTo("Third criterion");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that parse extracts file specs from Files to Modify section.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void parseExtractsFileModifications() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-audit-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      VerifyAudit audit = new VerifyAudit(scope);

      Path planFile = tempDir.resolve("PLAN.md");
      Files.writeString(planFile, """
        # Plan

        ## Files to Modify
        - plugin/skills/test.md - Update something
        - hooks/src/Main.java - Add feature

        ## Acceptance Criteria
        - [ ] Test criterion
        """);

      String result = audit.parse(planFile);
      JsonNode root = mapper.readTree(result);
      JsonNode fileSpecs = root.path("file_specs");
      JsonNode modify = fileSpecs.path("modify");

      requireThat(modify.isArray(), "modify.isArray").isTrue();
      requireThat(modify.size(), "modify.size").isEqualTo(2);
      requireThat(modify.get(0).asString(), "modify[0]").isEqualTo("plugin/skills/test.md");
      requireThat(modify.get(1).asString(), "modify[1]").isEqualTo("hooks/src/Main.java");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that parse extracts file specs from Execution Steps section.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void parseExtractsFileFromExecutionSteps() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-audit-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      VerifyAudit audit = new VerifyAudit(scope);

      Path planFile = tempDir.resolve("PLAN.md");
      Files.writeString(planFile, """
        # Plan

        ## Execution Steps
        1. Edit plugin/skills/verify.md to add feature
        2. Files: hooks/src/test/Test.java - create test
        3. Update the documentation

        ## Acceptance Criteria
        - [ ] Test criterion
        """);

      String result = audit.parse(planFile);
      JsonNode root = mapper.readTree(result);
      JsonNode fileSpecs = root.path("file_specs");
      JsonNode modify = fileSpecs.path("modify");

      requireThat(modify.isArray(), "modify.isArray").isTrue();
      requireThat(modify.size(), "modify.size").isGreaterThanOrEqualTo(2);

      boolean hasVerifyMd = false;
      boolean hasTestJava = false;
      for (JsonNode node : modify)
      {
        String file = node.asString();
        if (file.equals("plugin/skills/verify.md"))
          hasVerifyMd = true;
        if (file.equals("hooks/src/test/Test.java"))
          hasTestJava = true;
      }

      requireThat(hasVerifyMd, "hasVerifyMd").isTrue();
      requireThat(hasTestJava, "hasTestJava").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that parse groups criteria by shared file dependencies.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void parseGroupsCriteriaByFiles() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-audit-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      VerifyAudit audit = new VerifyAudit(scope);

      Path planFile = tempDir.resolve("PLAN.md");
      Files.writeString(planFile, """
        # Plan

        ## Files to Modify
        - plugin/skills/test.md - Update
        - hooks/src/Main.java - Update

        ## Acceptance Criteria
        - [ ] test.md should have new section
        - [ ] test.md formatting is correct
        - [ ] Main.java has new method
        """);

      String result = audit.parse(planFile);
      JsonNode root = mapper.readTree(result);
      JsonNode groups = root.path("groups");

      requireThat(groups.isArray(), "groups.isArray").isTrue();
      requireThat(groups.size(), "groups.size").isGreaterThanOrEqualTo(2);

      boolean foundTestMdGroup = false;
      boolean foundMainJavaGroup = false;

      for (JsonNode group : groups)
      {
        JsonNode files = group.path("files");
        JsonNode criteria = group.path("criteria");

        for (JsonNode file : files)
        {
          if (file.asString().equals("plugin/skills/test.md"))
          {
            foundTestMdGroup = true;
            requireThat(criteria.size(), "testMdCriteriaCount").isGreaterThanOrEqualTo(1);
          }
          if (file.asString().equals("hooks/src/Main.java"))
          {
            foundMainJavaGroup = true;
            requireThat(criteria.size(), "mainJavaCriteriaCount").isGreaterThanOrEqualTo(1);
          }
        }
      }

      requireThat(foundTestMdGroup, "foundTestMdGroup").isTrue();
      requireThat(foundMainJavaGroup, "foundMainJavaGroup").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that criteria without file references get grouped with all files.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void parseCriteriaWithoutFilesGetAllFiles() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-audit-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      VerifyAudit audit = new VerifyAudit(scope);

      Path planFile = tempDir.resolve("PLAN.md");
      Files.writeString(planFile, """
        # Plan

        ## Files to Modify
        - plugin/skills/test.md - Update
        - hooks/src/Main.java - Update

        ## Acceptance Criteria
        - [ ] General criterion with no file mention
        """);

      String result = audit.parse(planFile);
      JsonNode root = mapper.readTree(result);
      JsonNode groups = root.path("groups");

      requireThat(groups.isArray(), "groups.isArray").isTrue();
      requireThat(groups.size(), "groups.size").isEqualTo(1);

      JsonNode group = groups.get(0);
      JsonNode files = group.path("files");
      requireThat(files.size(), "files.size").isEqualTo(2);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that report renders all-Done results correctly.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void reportRendersAllDone() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-audit-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
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
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
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
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
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
   * Verifies that parse handles empty acceptance criteria section.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void parseHandlesEmptyCriteria() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-audit-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      VerifyAudit audit = new VerifyAudit(scope);

      Path planFile = tempDir.resolve("PLAN.md");
      Files.writeString(planFile, """
        # Plan

        ## Acceptance Criteria

        ## Files to Modify
        - test.md - Update
        """);

      String result = audit.parse(planFile);
      JsonNode root = mapper.readTree(result);
      JsonNode criteria = root.path("criteria");

      requireThat(criteria.isArray(), "criteria.isArray").isTrue();
      requireThat(criteria.size(), "criteria.size").isEqualTo(0);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that parse extracts files from Files to Delete section.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void parseExtractsDeleteFiles() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-audit-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      VerifyAudit audit = new VerifyAudit(scope);

      Path planFile = tempDir.resolve("PLAN.md");
      Files.writeString(planFile, """
        # Plan

        ## Files to Delete
        - plugin/skills/old-skill.md - Remove deprecated skill
        - hooks/src/OldClass.java - Remove old implementation

        ## Acceptance Criteria
        - [ ] Old files removed
        """);

      String result = audit.parse(planFile);
      JsonNode root = mapper.readTree(result);
      JsonNode fileSpecs = root.path("file_specs");
      JsonNode delete = fileSpecs.path("delete");

      requireThat(delete.isArray(), "delete.isArray").isTrue();
      requireThat(delete.size(), "delete.size").isEqualTo(2);
      requireThat(delete.get(0).asString(), "delete[0]").isEqualTo("plugin/skills/old-skill.md");
      requireThat(delete.get(1).asString(), "delete[1]").isEqualTo("hooks/src/OldClass.java");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that parse handles filename ambiguity by grouping similar names correctly.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void parseHandlesFilenameAmbiguity() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-audit-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      VerifyAudit audit = new VerifyAudit(scope);

      Path planFile = tempDir.resolve("PLAN.md");
      Files.writeString(planFile, """
        # Plan

        ## Files to Modify
        - plugin/skills/test.md - Update
        - plugin/commands/test.md - Update different test file

        ## Acceptance Criteria
        - [ ] test.md in skills has new section
        - [ ] test.md in commands has new content
        """);

      String result = audit.parse(planFile);
      JsonNode root = mapper.readTree(result);
      JsonNode fileSpecs = root.path("file_specs");
      JsonNode modify = fileSpecs.path("modify");

      requireThat(modify.isArray(), "modify.isArray").isTrue();
      requireThat(modify.size(), "modify.size").isEqualTo(2);
      requireThat(modify.get(0).asString(), "modify[0]").isEqualTo("plugin/skills/test.md");
      requireThat(modify.get(1).asString(), "modify[1]").isEqualTo("plugin/commands/test.md");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that prepare returns a complete JSON object for valid input with all required fields.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void prepareReturnsCompleteJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-audit-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      VerifyAudit audit = new VerifyAudit(scope);

      Path issueDir = tempDir.resolve("issue");
      Files.createDirectories(issueDir);
      Path worktreeDir = tempDir.resolve("worktree");
      Files.createDirectories(worktreeDir);

      Files.writeString(issueDir.resolve("PLAN.md"), """
        # Plan

        ## Files to Modify
        - plugin/skills/test.md - Update

        ## Acceptance Criteria
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

      requireThat(root.path("issue_id").asString(), "issue_id").isEqualTo("2.1-test-issue");
      requireThat(root.path("issue_path").asString(), "issue_path").isEqualTo(issueDir.toString());
      requireThat(root.path("worktree_path").asString(), "worktree_path").isEqualTo(worktreeDir.toString());
      requireThat(root.path("criteria_count").asInt(), "criteria_count").isEqualTo(1);
      requireThat(root.path("file_count").asInt(), "file_count").isEqualTo(1);
      requireThat(root.path("prompts").isArray(), "prompts.isArray").isTrue();
      requireThat(root.path("prompts").size(), "prompts.size").isEqualTo(1);
      requireThat(root.path("file_results").isObject(), "file_results.isObject").isTrue();
      requireThat(root.path("file_results").path("modify").isObject(), "file_results.modify.isObject").isTrue();
      requireThat(root.path("file_results").path("delete").isObject(), "file_results.delete.isObject").isTrue();
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
  @Test
  public void prepareRejectsMissingIssueId() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-audit-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      VerifyAudit audit = new VerifyAudit(scope);

      String json = """
        {
          "issue_path": "/tmp/issue",
          "worktree_path": "/tmp/worktree"
        }
        """;

      try
      {
        audit.prepare(json);
        requireThat(false, "shouldThrow").isEqualTo(true);
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("issue_id");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that prepare throws IllegalArgumentException when PLAN.md does not exist at issue_path.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void prepareRejectsMissingPlanMd() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-audit-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
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

      try
      {
        audit.prepare(json);
        requireThat(false, "shouldThrow").isEqualTo(true);
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("PLAN.md");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that prepare generates prompts for each group derived from PLAN.md.
   *
   * @throws IOException if file operations fail
   */
  @Test
  public void prepareGeneratesPromptsForGroups() throws IOException
  {
    Path tempDir = Files.createTempDirectory("verify-audit-test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      VerifyAudit audit = new VerifyAudit(scope);

      Path issueDir = tempDir.resolve("issue");
      Files.createDirectories(issueDir);
      Path worktreeDir = tempDir.resolve("worktree");
      Files.createDirectories(worktreeDir);

      Files.writeString(issueDir.resolve("PLAN.md"), """
        # Plan

        ## Files to Modify
        - plugin/skills/test.md - Update
        - hooks/src/Main.java - Add method

        ## Acceptance Criteria
        - [ ] test.md has new section
        - [ ] Main.java has new method
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

      JsonNode prompts = root.path("prompts");
      requireThat(prompts.isArray(), "prompts.isArray").isTrue();
      requireThat(prompts.size(), "prompts.size").isGreaterThanOrEqualTo(1);

      for (JsonNode promptEntry : prompts)
      {
        requireThat(promptEntry.has("group_index"), "hasGroupIndex").isTrue();
        requireThat(promptEntry.has("prompt"), "hasPrompt").isTrue();
        String promptText = promptEntry.path("prompt").asString();
        requireThat(promptText, "promptText").contains("Acceptance Criteri");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
