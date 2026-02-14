/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

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
  @Test(expectedExceptions = NullPointerException.class)
  public void executeRejectsNullInput() throws IOException
  {
    IssueCreator creator = new IssueCreator();
    creator.execute(null);
  }

  /**
   * Verifies that execute rejects empty JSON input.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void executeRejectsEmptyInput() throws IOException
  {
    IssueCreator creator = new IssueCreator();
    creator.execute("");
  }

  /**
   * Verifies that execute rejects malformed JSON input.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = {IOException.class, tools.jackson.core.exc.StreamReadException.class})
  public void executeRejectsMalformedJson() throws IOException
  {
    IssueCreator creator = new IssueCreator();
    creator.execute("{invalid}");
  }

  /**
   * Verifies that execute rejects JSON missing required field major.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class)
  public void executeRejectsMissingMajor() throws IOException
  {
    IssueCreator creator = new IssueCreator();
    String json = """
      {
        "minor": 1,
        "issue_name": "test",
        "state_content": "state",
        "plan_content": "plan"
      }""";
    creator.execute(json);
  }

  /**
   * Verifies that execute rejects JSON missing required field minor.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class)
  public void executeRejectsMissingMinor() throws IOException
  {
    IssueCreator creator = new IssueCreator();
    String json = """
      {
        "major": 2,
        "issue_name": "test",
        "state_content": "state",
        "plan_content": "plan"
      }""";
    creator.execute(json);
  }

  /**
   * Verifies that execute rejects JSON missing required field issue_name.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class)
  public void executeRejectsMissingIssueName() throws IOException
  {
    IssueCreator creator = new IssueCreator();
    String json = """
      {
        "major": 2,
        "minor": 1,
        "state_content": "state",
        "plan_content": "plan"
      }""";
    creator.execute(json);
  }

  /**
   * Verifies that execute rejects JSON missing required field state_content.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class)
  public void executeRejectsMissingStateContent() throws IOException
  {
    IssueCreator creator = new IssueCreator();
    String json = """
      {
        "major": 2,
        "minor": 1,
        "issue_name": "test",
        "plan_content": "plan"
      }""";
    creator.execute(json);
  }

  /**
   * Verifies that execute rejects JSON missing required field plan_content.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class)
  public void executeRejectsMissingPlanContent() throws IOException
  {
    IssueCreator creator = new IssueCreator();
    String json = """
      {
        "major": 2,
        "minor": 1,
        "issue_name": "test",
        "state_content": "state"
      }""";
    creator.execute(json);
  }

  /**
   * Verifies that execute returns JSON with success false when parent directory does not exist.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void executeReturnsErrorWhenParentMissing() throws IOException
  {
    IssueCreator creator = new IssueCreator();
    String json = """
      {
        "major": 999,
        "minor": 999,
        "issue_name": "nonexistent-test",
        "state_content": "state",
        "plan_content": "plan"
      }""";

    String result = creator.execute(json);
    JsonMapper mapper = JsonMapper.builder().build();
    ObjectNode resultNode = (ObjectNode) mapper.readTree(result);

    requireThat(resultNode.has("success"), "hasSuccess").isTrue();
    requireThat(resultNode.get("success").asBoolean(), "success").isFalse();
    requireThat(resultNode.has("error"), "hasError").isTrue();
    requireThat(resultNode.get("error").asString(), "error").contains("Parent version directory does not exist");
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

    Path versionDir = tempDir.resolve(".claude/cat/issues/v2/v2.1");
    Files.createDirectories(versionDir);
    Files.writeString(versionDir.resolve("STATE.md"), "# Version 2.1\n");

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
    try
    {
      Path versionDir = tempDir.resolve(".claude/cat/issues/v2/v2.1");

      IssueCreator creator = new IssueCreator();
      String json = """
        {
          "major": 2,
          "minor": 1,
          "issue_name": "test-issue",
          "state_content": "# State\\nstatus: pending",
          "plan_content": "# Plan\\nSteps here",
          "commit_description": "Test issue creation"
        }""";

      String result = creator.execute(json, tempDir);
      JsonMapper mapper = JsonMapper.builder().build();
      ObjectNode resultNode = (ObjectNode) mapper.readTree(result);

      requireThat(resultNode.get("success").asBoolean(), "success").isTrue();

      Path issuePath = tempDir.resolve(".claude/cat/issues/v2/v2.1/test-issue");
      requireThat(Files.exists(issuePath), "issuePathExists").isTrue();
      requireThat(Files.exists(issuePath.resolve("STATE.md")), "stateExists").isTrue();
      requireThat(Files.exists(issuePath.resolve("PLAN.md")), "planExists").isTrue();

      String stateContent = Files.readString(issuePath.resolve("STATE.md"));
      requireThat(stateContent, "stateContent").contains("status: pending");

      String planContent = Files.readString(issuePath.resolve("PLAN.md"));
      requireThat(planContent, "planContent").contains("Steps here");

      String parentState = Files.readString(versionDir.resolve("STATE.md"));
      requireThat(parentState, "parentState").contains("## Issues Pending");
      requireThat(parentState, "parentState").contains("- test-issue");
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that execute accepts JSON without commit_description field.
   *
   * @throws IOException if an I/O error occurs
   * @throws InterruptedException if git process is interrupted
   */
  @Test
  public void executeAcceptsOptionalCommitDescription() throws IOException, InterruptedException
  {
    Path tempDir = setupGitRepo();
    try
    {
      IssueCreator creator = new IssueCreator();
      String json = """
        {
          "major": 2,
          "minor": 1,
          "issue_name": "test-optional",
          "state_content": "# State",
          "plan_content": "# Plan"
        }""";

      String result = creator.execute(json, tempDir);
      JsonMapper mapper = JsonMapper.builder().build();
      ObjectNode resultNode = (ObjectNode) mapper.readTree(result);

      requireThat(resultNode.get("success").asBoolean(), "success").isTrue();
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that execute adds new issue after existing pending issues.
   *
   * @throws IOException if an I/O error occurs
   * @throws InterruptedException if git process is interrupted
   */
  @Test
  public void executeAppendsToExistingPendingIssues() throws IOException, InterruptedException
  {
    Path tempDir = setupGitRepo();
    try
    {
      Path versionDir = tempDir.resolve(".claude/cat/issues/v2/v2.1");
      String existingState = """
        # Version 2.1

        ## Issues Pending
        - existing-issue
        """;
      Files.writeString(versionDir.resolve("STATE.md"), existingState);

      ProcessBuilder pb = new ProcessBuilder("git", "add", ".");
      pb.directory(tempDir.toFile());
      pb.start().waitFor();

      pb = new ProcessBuilder("git", "commit", "-m", "Add existing issue");
      pb.directory(tempDir.toFile());
      pb.start().waitFor();

      IssueCreator creator = new IssueCreator();
      String json = """
        {
          "major": 2,
          "minor": 1,
          "issue_name": "new-issue",
          "state_content": "# State",
          "plan_content": "# Plan"
        }""";

      String result = creator.execute(json, tempDir);
      JsonMapper mapper = JsonMapper.builder().build();
      ObjectNode resultNode = (ObjectNode) mapper.readTree(result);

      requireThat(resultNode.get("success").asBoolean(), "success").isTrue();

      String parentState = Files.readString(versionDir.resolve("STATE.md"));
      requireThat(parentState, "parentState").contains("- existing-issue");
      requireThat(parentState, "parentState").contains("- new-issue");
      int existingPos = parentState.indexOf("- existing-issue");
      int newPos = parentState.indexOf("- new-issue");
      requireThat(newPos > existingPos, "newIssueAfterExisting").isTrue();
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that execute handles non-git directory gracefully.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = IOException.class)
  public void executeHandlesNonGitDirectory() throws IOException
  {
    Path tempDir = Files.createTempDirectory("issue-creator-test-nongit");
    try
    {
      Path versionDir = tempDir.resolve(".claude/cat/issues/v2/v2.1");
      Files.createDirectories(versionDir);
      Files.writeString(versionDir.resolve("STATE.md"), "# Version 2.1\n");

      IssueCreator creator = new IssueCreator();
      String json = """
        {
          "major": 2,
          "minor": 1,
          "issue_name": "test-issue",
          "state_content": "# State",
          "plan_content": "# Plan"
        }""";

      creator.execute(json, tempDir);
    }
    finally
    {
      deleteRecursively(tempDir);
    }
  }

  /**
   * Verifies that execute handles read-only directory appropriately.
   *
   * @throws IOException if an I/O error occurs
   * @throws InterruptedException if git process is interrupted
   */
  @Test(expectedExceptions = IOException.class)
  public void executeHandlesReadOnlyDirectory() throws IOException, InterruptedException
  {
    Path tempDir = setupGitRepo();
    Path versionDir = tempDir.resolve(".claude/cat/issues/v2/v2.1");

    boolean madeReadOnly = versionDir.toFile().setReadOnly();
    try
    {
      IssueCreator creator = new IssueCreator();
      String json = """
        {
          "major": 2,
          "minor": 1,
          "issue_name": "test-readonly",
          "state_content": "# State",
          "plan_content": "# Plan"
        }""";

      creator.execute(json, tempDir);
    }
    finally
    {
      if (madeReadOnly)
        versionDir.toFile().setWritable(true);
      deleteRecursively(tempDir);
    }
  }

  /**
   * Recursively deletes a directory.
   *
   * @param path the directory to delete
   * @throws IOException if deletion fails
   */
  private void deleteRecursively(Path path) throws IOException
  {
    if (Files.isDirectory(path))
    {
      try (java.util.stream.Stream<Path> stream = Files.list(path))
      {
        for (Path child : stream.toList())
          deleteRecursively(child);
      }
    }
    Files.deleteIfExists(path);
  }
}
