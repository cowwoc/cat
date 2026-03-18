/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.IssueCreator;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for IssueCreator functionality.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class IssueCreatorTest
{
  /**
   * Verifies that execute rejects null JSON input.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*jsonInput.*")
  public void executeRejectsNullInput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      IssueCreator creator = new IssueCreator(scope);
      creator.execute(null);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that execute rejects empty JSON input.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*jsonInput.*")
  public void executeRejectsEmptyInput() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      IssueCreator creator = new IssueCreator(scope);
      creator.execute("");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that execute rejects malformed JSON input.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = {IOException.class, tools.jackson.core.exc.StreamReadException.class},
    expectedExceptionsMessageRegExp = ".*Unexpected character.*")
  public void executeRejectsMalformedJson() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      IssueCreator creator = new IssueCreator(scope);
      creator.execute("{invalid}");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that execute rejects JSON missing required field major.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*Missing required field: major.*")
  public void executeRejectsMissingMajor() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      IssueCreator creator = new IssueCreator(scope);
      String json = """
        {
          "minor": 1,
          "issueName": "test",
          "indexContent": "{}",
          "planContent": "plan"
        }""";
      creator.execute(json);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that execute rejects JSON missing required field minor.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*Missing required field: minor.*")
  public void executeRejectsMissingMinor() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      IssueCreator creator = new IssueCreator(scope);
      String json = """
        {
          "major": 2,
          "issueName": "test",
          "indexContent": "{}",
          "planContent": "plan"
        }""";
      creator.execute(json);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that execute rejects JSON missing required field issueName.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*Missing required field: issueName.*")
  public void executeRejectsMissingIssueName() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      IssueCreator creator = new IssueCreator(scope);
      String json = """
        {
          "major": 2,
          "minor": 1,
          "indexContent": "{}",
          "planContent": "plan"
        }""";
      creator.execute(json);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that execute rejects JSON missing required field indexContent.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*Missing required field: indexContent.*")
  public void executeRejectsMissingIndexContent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      IssueCreator creator = new IssueCreator(scope);
      String json = """
        {
          "major": 2,
          "minor": 1,
          "issueName": "test",
          "planContent": "plan"
        }""";
      creator.execute(json);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that execute rejects JSON missing required field planContent.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*Missing required field: planContent.*")
  public void executeRejectsMissingPlanContent() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      IssueCreator creator = new IssueCreator(scope);
      String json = """
        {
          "major": 2,
          "minor": 1,
          "issueName": "test",
          "indexContent": "{}"
        }""";
      creator.execute(json);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that execute returns JSON with success false when parent directory does not exist.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsErrorWhenParentMissing() throws IOException
  {
    Path tempDir = Files.createTempDirectory("test-");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      IssueCreator creator = new IssueCreator(scope);
      String json = """
        {
          "major": 999,
          "minor": 999,
          "issueName": "nonexistent-test",
          "indexContent": "{}",
          "planContent": "plan"
        }""";

      String result = creator.execute(json);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode resultNode = (ObjectNode) mapper.readTree(result);

      requireThat(resultNode.has("success"), "hasSuccess").isTrue();
      requireThat(resultNode.get("success").asBoolean(), "success").isFalse();
      requireThat(resultNode.has("error"), "hasError").isTrue();
      requireThat(resultNode.get("error").asString(), "error").contains("Parent version directory does not exist");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Sets up a git repository for testing.
   *
   * @return the temporary directory containing the git repository
   * @throws IOException if an I/O error occurs
   * @throws InterruptedException if git process is interrupted
   */
  private Path setupGitRepo() throws IOException, InterruptedException
  {
    Path tempDir = Files.createTempDirectory("issue-creator-test");

    ProcessBuilder pb = new ProcessBuilder("git", "init");
    pb.directory(tempDir.toFile());
    pb.start().waitFor();

    pb = new ProcessBuilder("git", "config", "user.email", "test@example.com");
    pb.directory(tempDir.toFile());
    pb.start().waitFor();

    pb = new ProcessBuilder("git", "config", "user.name", "Test User");
    pb.directory(tempDir.toFile());
    pb.start().waitFor();

    Path versionDir = tempDir.resolve(".cat/issues/v2/v2.1");
    Files.createDirectories(versionDir);
    Files.writeString(versionDir.resolve("index.json"), "{\"status\": \"open\"}\n");

    pb = new ProcessBuilder("git", "add", ".");
    pb.directory(tempDir.toFile());
    pb.start().waitFor();

    pb = new ProcessBuilder("git", "commit", "-m", "Initial commit");
    pb.directory(tempDir.toFile());
    pb.start().waitFor();

    return tempDir;
  }

  /**
   * Verifies that execute creates directories and files for valid input.
   *
   * @throws IOException if an I/O error occurs
   * @throws InterruptedException if git process is interrupted
   */
  @Test
  public void executeCreatesIssueStructure() throws IOException, InterruptedException
  {
    Path tempDir = setupGitRepo();
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      IssueCreator creator = new IssueCreator(scope);
      String json = """
        {
          "major": 2,
          "minor": 1,
          "issueName": "test-issue",
          "indexContent": "{\\"status\\": \\"open\\"}",
          "planContent": "# Plan\\nSteps here",
          "commitDescription": "Test issue creation"
        }""";

      String result = creator.execute(json, tempDir);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode resultNode = (ObjectNode) mapper.readTree(result);

      requireThat(resultNode.get("success").asBoolean(), "success").isTrue();

      Path issuePath = tempDir.resolve(".cat/issues/v2/v2.1/test-issue");
      requireThat(Files.exists(issuePath), "issuePathExists").isTrue();
      requireThat(Files.exists(issuePath.resolve("index.json")), "indexExists").isTrue();
      requireThat(Files.exists(issuePath.resolve("plan.md")), "planExists").isTrue();

      String indexContent = Files.readString(issuePath.resolve("index.json"));
      requireThat(indexContent, "indexContent").contains("open");

      String planContent = Files.readString(issuePath.resolve("plan.md"));
      requireThat(planContent, "planContent").contains("Steps here");

      // The version-level index.json tracks only status, not an issue list
      Path versionDir = tempDir.resolve(".cat/issues/v2/v2.1");
      String versionIndex = Files.readString(versionDir.resolve("index.json"));
      requireThat(versionIndex, "versionIndex").contains("\"status\"");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that execute accepts JSON without commitDescription field.
   *
   * @throws IOException if an I/O error occurs
   * @throws InterruptedException if git process is interrupted
   */
  @Test
  public void executeAcceptsOptionalCommitDescription() throws IOException, InterruptedException
  {
    Path tempDir = setupGitRepo();
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      IssueCreator creator = new IssueCreator(scope);
      String json = """
        {
          "major": 2,
          "minor": 1,
          "issueName": "test-optional",
          "indexContent": "{}",
          "planContent": "# Plan"
        }""";

      String result = creator.execute(json, tempDir);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode resultNode = (ObjectNode) mapper.readTree(result);

      requireThat(resultNode.get("success").asBoolean(), "success").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that execute creates multiple issues independently in the same version directory.
   * <p>
   * The version-level index.json tracks only status and is not modified when issues are added.
   *
   * @throws IOException if an I/O error occurs
   * @throws InterruptedException if git process is interrupted
   */
  @Test
  public void executeCreatesMultipleIssuesIndependently() throws IOException, InterruptedException
  {
    Path tempDir = setupGitRepo();
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path versionDir = tempDir.resolve(".cat/issues/v2/v2.1");

      IssueCreator creator = new IssueCreator(scope);
      String json1 = """
        {
          "major": 2,
          "minor": 1,
          "issueName": "first-issue",
          "indexContent": "{\\"status\\": \\"open\\"}",
          "planContent": "# Plan"
        }""";

      String result1 = creator.execute(json1, tempDir);
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode result1Node = (ObjectNode) mapper.readTree(result1);
      requireThat(result1Node.get("success").asBoolean(), "success1").isTrue();

      String json2 = """
        {
          "major": 2,
          "minor": 1,
          "issueName": "second-issue",
          "indexContent": "{\\"status\\": \\"open\\"}",
          "planContent": "# Plan"
        }""";

      String result2 = creator.execute(json2, tempDir);
      ObjectNode result2Node = (ObjectNode) mapper.readTree(result2);
      requireThat(result2Node.get("success").asBoolean(), "success2").isTrue();

      requireThat(Files.exists(versionDir.resolve("first-issue/index.json")), "firstIssueExists").isTrue();
      requireThat(Files.exists(versionDir.resolve("second-issue/index.json")), "secondIssueExists").isTrue();

      // The version-level index.json is unchanged — it tracks only status, not an issue list
      String versionIndex = Files.readString(versionDir.resolve("index.json"));
      requireThat(versionIndex, "versionIndex").contains("\"status\"");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that execute handles non-git directory gracefully.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class,
    expectedExceptionsMessageRegExp = ".*git command failed.*")
  public void executeHandlesNonGitDirectory() throws IOException
  {
    Path tempDir = Files.createTempDirectory("issue-creator-test-nongit");
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      Path versionDir = tempDir.resolve(".cat/issues/v2/v2.1");
      Files.createDirectories(versionDir);
      Files.writeString(versionDir.resolve("index.json"), "{\"status\": \"open\"}\n");

      IssueCreator creator = new IssueCreator(scope);
      String json = """
        {
          "major": 2,
          "minor": 1,
          "issueName": "test-issue",
          "indexContent": "{}",
          "planContent": "# Plan"
        }""";

      creator.execute(json, tempDir);
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that execute handles read-only version directory appropriately by throwing IOException.
   * <p>
   * When the version directory is read-only, creating subdirectories inside it should fail.
   *
   * @throws IOException if an I/O error occurs
   * @throws InterruptedException if git process is interrupted
   */
  @Test(expectedExceptions = IOException.class)
  public void executeHandlesReadOnlyDirectory() throws IOException, InterruptedException
  {
    Path tempDir = setupGitRepo();
    Path versionDir = tempDir.resolve(".cat/issues/v2/v2.1");

    boolean madeReadOnly = versionDir.toFile().setReadOnly();
    try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
    {
      IssueCreator creator = new IssueCreator(scope);
      String json = """
        {
          "major": 2,
          "minor": 1,
          "issueName": "test-readonly",
          "indexContent": "{\\"status\\": \\"open\\"}",
          "planContent": "# Plan"
        }""";

      creator.execute(json, tempDir);
    }
    finally
    {
      if (madeReadOnly)
        versionDir.toFile().setWritable(true);
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
