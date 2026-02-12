package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.skills.GetAuditPlanOutput;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetAuditPlanOutput.
 */
public final class GetAuditPlanOutputTest
{
  /**
   * Verifies that all DONE criteria return COMPLETE status.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void allDoneReturnsComplete() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-audit-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetAuditPlanOutput generator = new GetAuditPlanOutput(scope);
      String json = """
        {
          "issue_id": "2.1-test-issue",
          "plan_path": "test/PLAN.md",
          "criteria_results": [
            {"criterion": "Skill exists", "status": "DONE", "evidence": "Found at path", "issues": []},
            {"criterion": "Tests pass", "status": "DONE", "evidence": "mvn test passed", "issues": []}
          ],
          "file_results": [
            {"file": "plugin/skills/test/SKILL.md", "status": "DONE", "evidence": "File created", "issues": []}
          ]
        }""";
      String result = generator.getOutput(json);
      requireThat(result, "result").contains("COMPLETE");
      requireThat(result, "result").contains("Done: 3");
      requireThat(result, "result").contains("Skill exists");
      requireThat(result, "result").contains("Tests pass");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that mixed statuses return PARTIAL.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void mixedStatusesReturnsPartial() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-audit-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetAuditPlanOutput generator = new GetAuditPlanOutput(scope);
      String json = """
        {
          "issue_id": "2.1-test-mixed",
          "plan_path": "test/PLAN.md",
          "criteria_results": [
            {"criterion": "Feature implemented", "status": "DONE", "evidence": "Code exists", "issues": []},
            {"criterion": "Tests pass", "status": "PARTIAL", "evidence": "2 of 3 tests pass",
              "issues": ["Test C fails"]},
            {"criterion": "Docs updated", "status": "MISSING", "evidence": "", "issues": ["README not updated"]}
          ],
          "file_results": []
        }""";
      String result = generator.getOutput(json);
      requireThat(result, "result").contains("PARTIAL");
      requireThat(result, "result").contains("Done: 1");
      requireThat(result, "result").contains("Partial: 1");
      requireThat(result, "result").contains("Missing: 1");
      requireThat(result, "result").contains("Actions Required");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that all MISSING criteria return INCOMPLETE.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void allMissingReturnsIncomplete() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-audit-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetAuditPlanOutput generator = new GetAuditPlanOutput(scope);
      String json = """
        {
          "issue_id": "2.1-test-incomplete",
          "plan_path": "test/PLAN.md",
          "criteria_results": [
            {"criterion": "Implementation exists", "status": "MISSING", "evidence": "", "issues": ["No code found"]},
            {"criterion": "Tests exist", "status": "MISSING", "evidence": "", "issues": ["No tests found"]}
          ],
          "file_results": []
        }""";
      String result = generator.getOutput(json);
      requireThat(result, "result").contains("INCOMPLETE");
      requireThat(result, "result").contains("Done: 0");
      requireThat(result, "result").contains("Missing: 2");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that empty input returns NO CHECKS status.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void emptyInputReturnsNoChecks() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-audit-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetAuditPlanOutput generator = new GetAuditPlanOutput(scope);
      String json = """
        {
          "issue_id": "2.1-test-empty",
          "plan_path": "test/PLAN.md",
          "criteria_results": [],
          "file_results": []
        }""";
      String result = generator.getOutput(json);
      requireThat(result, "result").contains("NO CHECKS");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that issues are displayed in the report.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void issuesAreDisplayed() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-audit-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetAuditPlanOutput generator = new GetAuditPlanOutput(scope);
      String json = """
        {
          "issue_id": "2.1-test-issues",
          "plan_path": "test/PLAN.md",
          "criteria_results": [
            {
              "criterion": "Tests pass",
              "status": "PARTIAL",
              "evidence": "Some tests fail",
              "issues": ["testFoo fails with NPE", "testBar fails with timeout"]
            }
          ],
          "file_results": []
        }""";
      String result = generator.getOutput(json);
      requireThat(result, "result").contains("testFoo fails with NPE");
      requireThat(result, "result").contains("testBar fails with timeout");
      requireThat(result, "result").contains("Issues:");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that file results are rendered correctly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void fileResultsAreRendered() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-audit-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetAuditPlanOutput generator = new GetAuditPlanOutput(scope);
      String json = """
        {
          "issue_id": "2.1-test-files",
          "plan_path": "test/PLAN.md",
          "criteria_results": [],
          "file_results": [
            {"file": "src/main/Foo.java", "status": "DONE", "evidence": "File created with correct content",
              "issues": []},
            {"file": "src/test/FooTest.java", "status": "MISSING", "evidence": "", "issues": ["File not found"]}
          ]
        }""";
      String result = generator.getOutput(json);
      requireThat(result, "result").contains("File Changes");
      requireThat(result, "result").contains("src/main/Foo.java");
      requireThat(result, "result").contains("src/test/FooTest.java");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that blank JSON is rejected.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void blankJsonRejected() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-audit-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetAuditPlanOutput generator = new GetAuditPlanOutput(scope);
      try
      {
        generator.getOutput("");
        requireThat(false, "shouldThrowException").isEqualTo(true);
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("json");
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that unrecognized status values are not counted.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void unrecognizedStatusNotCounted() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-audit-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetAuditPlanOutput generator = new GetAuditPlanOutput(scope);
      String json = """
        {
          "issue_id": "2.1-test-unknown",
          "plan_path": "test/PLAN.md",
          "criteria_results": [
            {"criterion": "Feature implemented", "status": "DONE", "evidence": "Code exists", "issues": []},
            {"criterion": "Unknown status", "status": "UNKNOWN", "evidence": "", "issues": []},
            {"criterion": "Docs updated", "status": "MISSING", "evidence": "", "issues": []}
          ],
          "file_results": []
        }""";
      String result = generator.getOutput(json);
      requireThat(result, "result").contains("Done: 1");
      requireThat(result, "result").contains("Missing: 1");
      requireThat(result, "result").contains("Partial: 0");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that invalid criteria_results array type returns error.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void invalidCriteriaArrayReturnsError() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-audit-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetAuditPlanOutput generator = new GetAuditPlanOutput(scope);
      String json = """
        {
          "issue_id": "2.1-test-invalid",
          "plan_path": "test/PLAN.md",
          "criteria_results": "not an array",
          "file_results": []
        }""";
      String result = generator.getOutput(json);
      requireThat(result, "result").contains("Error");
      requireThat(result, "result").contains("criteria_results");
      requireThat(result, "result").contains("array");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that only PARTIAL items return INCOMPLETE status.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void partialOnlyReturnsIncomplete() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-audit-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      GetAuditPlanOutput generator = new GetAuditPlanOutput(scope);
      String json = """
        {
          "issue_id": "2.1-test-partial-only",
          "plan_path": "test/PLAN.md",
          "criteria_results": [
            {"criterion": "Tests pass", "status": "PARTIAL", "evidence": "Some tests fail", "issues": []}
          ],
          "file_results": [
            {"file": "Foo.java", "status": "PARTIAL", "evidence": "Incomplete implementation", "issues": []}
          ]
        }""";
      String result = generator.getOutput(json);
      requireThat(result, "result").contains("INCOMPLETE");
      requireThat(result, "result").contains("Done: 0");
      requireThat(result, "result").contains("Partial: 2");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
