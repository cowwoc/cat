/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.skills.GetAddOutput;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GetAddOutput planning data functionality.
 * <p>
 * Validates output JSON generation: planning_valid false when no issues dir,
 * version listing with status/summary/issues, and filtering of closed versions.
 */
public class GetAddOutputPlanningDataTest
{
  // ==================== planning_valid ====================

  /**
   * Verifies that planning_valid is false when the issues directory does not exist.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void planningValidFalseWhenNoPlanningStructure() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      requireThat(root.get("planning_valid").asBoolean(), "planning_valid").isFalse();
      requireThat(root.get("error_message").asString(), "error_message").contains("Run /cat:init");
      requireThat(root.get("versions").size(), "versions.size").isEqualTo(0);
      requireThat(root.get("branch_strategy").asString(), "branch_strategy").isEmpty();
      requireThat(root.get("branch_pattern").asString(), "branch_pattern").isEmpty();
    }
  }

  /**
   * Verifies that error message uses a relative path instead of an absolute path.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void errorMessageUsesRelativePath() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      requireThat(root.get("planning_valid").asBoolean(), "planning_valid").isFalse();
      String errorMessage = root.get("error_message").asString();
      requireThat(errorMessage, "error_message").contains(".cat/issues");
      requireThat(errorMessage, "error_message").doesNotContain(projectPath.toString());
    }
  }

  // ==================== version inclusion ====================

  /**
   * Verifies that a non-closed version is included in the output with correct fields.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void inProgressVersionIsIncluded() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();
      Path issuesDir = projectPath.resolve(".cat/issues");
      Path versionDir = issuesDir.resolve("v2/v2.1");
      Files.createDirectories(versionDir);

      Files.writeString(versionDir.resolve("STATE.md"),
        "# State\n\n- **Status:** in-progress\n");
      Files.writeString(versionDir.resolve("PLAN.md"),
        "# Plan\n\n## Goal\n\nTest version goal summary.\n");
      Files.createDirectories(versionDir.resolve("my-issue"));

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      requireThat(root.get("planning_valid").asBoolean(), "planning_valid").isTrue();
      requireThat(root.get("error_message").asString(), "error_message").isEmpty();
      requireThat(root.get("branch_strategy").asString(), "branch_strategy").isEqualTo("feature");
      requireThat(root.get("branch_pattern").asString(), "branch_pattern").
        isEqualTo("v{version}/{issue-name}");

      JsonNode versions = root.get("versions");
      requireThat(versions.size(), "versions.size").isEqualTo(1);

      JsonNode version = versions.get(0);
      requireThat(version.get("version").asString(), "version").isEqualTo("2.1");
      requireThat(version.get("status").asString(), "status").isEqualTo("in-progress");
      requireThat(version.get("summary").asString(), "summary").
        isEqualTo("Test version goal summary.");
      requireThat(version.get("issue_count").asInt(), "issue_count").isEqualTo(1);

      JsonNode existingIssues = version.get("existing_issues");
      requireThat(existingIssues.size(), "existing_issues.size").isEqualTo(1);
      requireThat(existingIssues.get(0).asString(), "existing_issues[0]").isEqualTo("my-issue");
    }
  }

  /**
   * Verifies that versions with status "closed" are excluded from the output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void closedVersionIsExcluded() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();
      Path issuesDir = projectPath.resolve(".cat/issues");
      Path versionDir = issuesDir.resolve("v1/v1.0");
      Files.createDirectories(versionDir);
      Files.writeString(versionDir.resolve("STATE.md"), "# State\n\n- **Status:** closed\n");

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      requireThat(root.get("planning_valid").asBoolean(), "planning_valid").isTrue();
      requireThat(root.get("versions").size(), "versions.size").isEqualTo(0);
    }
  }

  /**
   * Verifies that a version with a missing STATE.md is treated as closed and excluded.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void missingStateMdTreatedAsClosed() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();
      Path issuesDir = projectPath.resolve(".cat/issues");
      Path versionDir = issuesDir.resolve("v2/v2.0");
      Files.createDirectories(versionDir);
      // No STATE.md written

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      requireThat(root.get("planning_valid").asBoolean(), "planning_valid").isTrue();
      requireThat(root.get("versions").size(), "versions.size").isEqualTo(0);
    }
  }

  // ==================== multi-version scenarios ====================

  /**
   * Verifies that mixed open, in-progress, and closed versions are handled correctly:
   * open and in-progress versions are included, closed versions are excluded.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void mixedVersionStatusesHandledCorrectly() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();
      Path issuesDir = projectPath.resolve(".cat/issues");

      Path v1Dir = issuesDir.resolve("v1/v1.0");
      Files.createDirectories(v1Dir);
      Files.writeString(v1Dir.resolve("STATE.md"), "# State\n\n- **Status:** closed\n");

      Path v2Dir = issuesDir.resolve("v2/v2.0");
      Files.createDirectories(v2Dir);
      Files.writeString(v2Dir.resolve("STATE.md"), "# State\n\n- **Status:** open\n");

      Path v2p1Dir = issuesDir.resolve("v2/v2.1");
      Files.createDirectories(v2p1Dir);
      Files.writeString(v2p1Dir.resolve("STATE.md"), "# State\n\n- **Status:** in-progress\n");

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode versions = mapper.readTree(result).get("versions");

      requireThat(versions.size(), "versions.size").isEqualTo(2);
      requireThat(versions.get(0).get("status").asString(), "versions[0].status").
        isEqualTo("open");
      requireThat(versions.get(1).get("status").asString(), "versions[1].status").
        isEqualTo("in-progress");
    }
  }

  /**
   * Verifies that versions are sorted lexicographically so v1.10 comes before v1.9.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void versionsSortedLexicographically() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();
      Path issuesDir = projectPath.resolve(".cat/issues");

      Path v1p10Dir = issuesDir.resolve("v1/v1.10");
      Files.createDirectories(v1p10Dir);
      Files.writeString(v1p10Dir.resolve("STATE.md"), "# State\n\n- **Status:** open\n");

      Path v1p9Dir = issuesDir.resolve("v1/v1.9");
      Files.createDirectories(v1p9Dir);
      Files.writeString(v1p9Dir.resolve("STATE.md"), "# State\n\n- **Status:** open\n");

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode versions = mapper.readTree(result).get("versions");

      requireThat(versions.size(), "versions.size").isEqualTo(2);
      // Path.sorted() is lexicographic: v1.10 < v1.9 lexicographically
      requireThat(versions.get(0).get("version").asString(), "versions[0].version").
        isEqualTo("1.10");
      requireThat(versions.get(1).get("version").asString(), "versions[1].version").
        isEqualTo("1.9");
    }
  }

  // ==================== issue directory filtering ====================

  /**
   * Verifies that a version with no issue directories returns an empty existing_issues list.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void emptyIssueListWhenNoIssueDirs() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();
      Path issuesDir = projectPath.resolve(".cat/issues");
      Path versionDir = issuesDir.resolve("v2/v2.1");
      Files.createDirectories(versionDir);
      Files.writeString(versionDir.resolve("STATE.md"), "# State\n\n- **Status:** open\n");

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode version = mapper.readTree(result).get("versions").get(0);

      requireThat(version.get("existing_issues").size(), "existing_issues.size").isEqualTo(0);
      requireThat(version.get("issue_count").asInt(), "issue_count").isEqualTo(0);
    }
  }

  /**
   * Verifies that regular files (non-directories) under the version dir are not listed as issues.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void regularFilesNotListedAsIssues() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();
      Path issuesDir = projectPath.resolve(".cat/issues");
      Path versionDir = issuesDir.resolve("v2/v2.1");
      Files.createDirectories(versionDir);
      Files.writeString(versionDir.resolve("STATE.md"), "# State\n\n- **Status:** open\n");
      Files.writeString(versionDir.resolve("PLAN.md"), "# Plan\n\n## Goal\n\nGoal.\n");
      Files.writeString(versionDir.resolve("CHANGELOG.md"), "# Changelog\n");
      Files.writeString(versionDir.resolve("notes.txt"), "some notes");
      Files.createDirectories(versionDir.resolve("real-issue"));

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode version = mapper.readTree(result).get("versions").get(0);
      JsonNode existingIssues = version.get("existing_issues");

      requireThat(existingIssues.size(), "existing_issues.size").isEqualTo(1);
      requireThat(existingIssues.get(0).asString(), "existing_issues[0]").isEqualTo("real-issue");
    }
  }

  /**
   * Verifies that issue names with hyphens and digits are listed and sorted correctly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void issueNamesWithSpecialCharactersListed() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();
      Path issuesDir = projectPath.resolve(".cat/issues");
      Path versionDir = issuesDir.resolve("v2/v2.1");
      Files.createDirectories(versionDir);
      Files.writeString(versionDir.resolve("STATE.md"), "# State\n\n- **Status:** open\n");
      Files.createDirectories(versionDir.resolve("fix-bug-123"));
      Files.createDirectories(versionDir.resolve("add-feature-abc"));

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode existingIssues =
        mapper.readTree(result).get("versions").get(0).get("existing_issues");

      requireThat(existingIssues.size(), "existing_issues.size").isEqualTo(2);
      requireThat(existingIssues.get(0).asString(), "existing_issues[0]").
        isEqualTo("add-feature-abc");
      requireThat(existingIssues.get(1).asString(), "existing_issues[1]").isEqualTo("fix-bug-123");
    }
  }

  /**
   * Verifies that issue_count equals existing_issues.size() invariant.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void issueCountEqualsExistingIssuesSize() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();
      Path issuesDir = projectPath.resolve(".cat/issues");
      Path versionDir = issuesDir.resolve("v2/v2.1");
      Files.createDirectories(versionDir);
      Files.writeString(versionDir.resolve("STATE.md"), "# State\n\n- **Status:** open\n");
      Files.createDirectories(versionDir.resolve("issue-a"));
      Files.createDirectories(versionDir.resolve("issue-b"));
      Files.createDirectories(versionDir.resolve("issue-c"));

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode version = mapper.readTree(result).get("versions").get(0);

      int issueCount = version.get("issue_count").asInt();
      int existingIssuesSize = version.get("existing_issues").size();
      requireThat(issueCount, "issue_count").isEqualTo(existingIssuesSize);
      requireThat(issueCount, "issue_count").isEqualTo(3);
    }
  }

  /**
   * Verifies that issue_count is zero when the version has no issue directories.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void issueCountZeroWhenNoIssues() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();
      Path issuesDir = projectPath.resolve(".cat/issues");
      Path versionDir = issuesDir.resolve("v2/v2.1");
      Files.createDirectories(versionDir);
      Files.writeString(versionDir.resolve("STATE.md"), "# State\n\n- **Status:** open\n");

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode version = mapper.readTree(result).get("versions").get(0);

      requireThat(version.get("issue_count").asInt(), "issue_count").isEqualTo(0);
      requireThat(version.get("existing_issues").size(), "existing_issues.size").isEqualTo(0);
    }
  }

  // ==================== parseGoalSummary edge cases ====================

  /**
   * Verifies that an empty string is returned when PLAN.md has no Goal section.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void summaryEmptyWhenNoGoalSection() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();
      Path issuesDir = projectPath.resolve(".cat/issues");
      Path versionDir = issuesDir.resolve("v2/v2.1");
      Files.createDirectories(versionDir);
      Files.writeString(versionDir.resolve("STATE.md"), "# State\n\n- **Status:** open\n");
      Files.writeString(versionDir.resolve("PLAN.md"), "# Plan\n\nNo goal section here.\n");

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode version = mapper.readTree(result).get("versions").get(0);
      requireThat(version.get("summary").asString(), "summary").isEmpty();
    }
  }

  /**
   * Verifies that an empty string is returned when Goal section is at EOF with no content.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void summaryEmptyWhenGoalAtEof() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();
      Path issuesDir = projectPath.resolve(".cat/issues");
      Path versionDir = issuesDir.resolve("v2/v2.1");
      Files.createDirectories(versionDir);
      Files.writeString(versionDir.resolve("STATE.md"), "# State\n\n- **Status:** open\n");
      Files.writeString(versionDir.resolve("PLAN.md"), "# Plan\n\n## Goal");

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode version = mapper.readTree(result).get("versions").get(0);
      requireThat(version.get("summary").asString(), "summary").isEmpty();
    }
  }

  /**
   * Verifies that the first non-blank line is returned when multiple blank lines follow Goal.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void summarySkipsMultipleBlankLinesAfterGoal() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();
      Path issuesDir = projectPath.resolve(".cat/issues");
      Path versionDir = issuesDir.resolve("v2/v2.1");
      Files.createDirectories(versionDir);
      Files.writeString(versionDir.resolve("STATE.md"), "# State\n\n- **Status:** open\n");
      Files.writeString(versionDir.resolve("PLAN.md"),
        "# Plan\n\n## Goal\n\n\n\nActual goal summary.\n");

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode version = mapper.readTree(result).get("versions").get(0);
      requireThat(version.get("summary").asString(), "summary").isEqualTo("Actual goal summary.");
    }
  }

  /**
   * Verifies that special characters in the Goal section are preserved correctly.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void summaryPreservesSpecialCharacters() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();
      Path issuesDir = projectPath.resolve(".cat/issues");
      Path versionDir = issuesDir.resolve("v2/v2.1");
      Files.createDirectories(versionDir);
      Files.writeString(versionDir.resolve("STATE.md"), "# State\n\n- **Status:** open\n");
      Files.writeString(versionDir.resolve("PLAN.md"),
        "# Plan\n\n## Goal\n\nFix bug #123: handle 'null' & <empty> values.\n");

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode version = mapper.readTree(result).get("versions").get(0);
      requireThat(version.get("summary").asString(), "summary").
        isEqualTo("Fix bug #123: handle 'null' & <empty> values.");
    }
  }

  // ==================== parseStatus edge cases ====================

  /**
   * Verifies that "open" is returned as the default when the Status field is missing.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void statusDefaultsToOpenWhenMissing() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();
      Path issuesDir = projectPath.resolve(".cat/issues");
      Path versionDir = issuesDir.resolve("v2/v2.1");
      Files.createDirectories(versionDir);
      Files.writeString(versionDir.resolve("STATE.md"), "# State\n\nNo status field here.\n");

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode versions = mapper.readTree(result).get("versions");
      requireThat(versions.size(), "versions.size").isEqualTo(1);
      requireThat(versions.get(0).get("status").asString(), "status").isEqualTo("open");
    }
  }

  /**
   * Verifies that status value is trimmed of surrounding whitespace.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void statusValueIsTrimmed() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();
      Path issuesDir = projectPath.resolve(".cat/issues");
      Path versionDir = issuesDir.resolve("v2/v2.1");
      Files.createDirectories(versionDir);
      Files.writeString(versionDir.resolve("STATE.md"),
        "# State\n\n- **Status:**   in-progress   \n");

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode version = mapper.readTree(result).get("versions").get(0);
      requireThat(version.get("status").asString(), "status").isEqualTo("in-progress");
    }
  }

  /**
   * Verifies that the first Status field value is used when multiple Status fields are present.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void firstStatusFieldUsedWhenMultiplePresent() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();
      Path issuesDir = projectPath.resolve(".cat/issues");
      Path versionDir = issuesDir.resolve("v2/v2.1");
      Files.createDirectories(versionDir);
      Files.writeString(versionDir.resolve("STATE.md"),
        "# State\n\n- **Status:** open\n\n- **Status:** in-progress\n");

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode versions = mapper.readTree(result).get("versions");
      requireThat(versions.size(), "versions.size").isEqualTo(1);
      requireThat(versions.get(0).get("status").asString(), "status").isEqualTo("open");
    }
  }

  // ==================== summary truncation boundary ====================

  /**
   * Verifies that a summary of exactly 120 characters is returned without truncation.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void summaryAt120CharactersNotTruncated() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();
      Path issuesDir = projectPath.resolve(".cat/issues");
      Path versionDir = issuesDir.resolve("v3/v3.0");
      Files.createDirectories(versionDir);
      Files.writeString(versionDir.resolve("STATE.md"), "# State\n\n- **Status:** open\n");
      String exactSummary = "A".repeat(120);
      Files.writeString(versionDir.resolve("PLAN.md"),
        "# Plan\n\n## Goal\n\n" + exactSummary + "\n");

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      String summary = mapper.readTree(result).get("versions").get(0).get("summary").asString();
      requireThat(summary.length(), "summary.length").isEqualTo(120);
      requireThat(summary, "summary").isEqualTo(exactSummary);
    }
  }

  /**
   * Verifies that a summary longer than 120 characters is truncated to exactly 120.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void summaryTruncatedAt120Characters() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();
      Path issuesDir = projectPath.resolve(".cat/issues");
      Path versionDir = issuesDir.resolve("v3/v3.0");
      Files.createDirectories(versionDir);
      Files.writeString(versionDir.resolve("STATE.md"), "# State\n\n- **Status:** open\n");
      String longSummary = "A".repeat(200);
      Files.writeString(versionDir.resolve("PLAN.md"),
        "# Plan\n\n## Goal\n\n" + longSummary + "\n");

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode versions = mapper.readTree(result).get("versions");
      requireThat(versions.size(), "versions.size").isEqualTo(1);
      String summary = versions.get(0).get("summary").asString();
      requireThat(summary.length(), "summary.length").isEqualTo(120);
    }
  }

  /**
   * Verifies that a summary of 119 characters is returned without truncation.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void summaryAt119CharactersNotTruncated() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();
      Path issuesDir = projectPath.resolve(".cat/issues");
      Path versionDir = issuesDir.resolve("v3/v3.0");
      Files.createDirectories(versionDir);
      Files.writeString(versionDir.resolve("STATE.md"), "# State\n\n- **Status:** open\n");
      String shortSummary = "A".repeat(119);
      Files.writeString(versionDir.resolve("PLAN.md"),
        "# Plan\n\n## Goal\n\n" + shortSummary + "\n");

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      String summary = mapper.readTree(result).get("versions").get(0).get("summary").asString();
      requireThat(summary.length(), "summary.length").isEqualTo(119);
      requireThat(summary, "summary").isEqualTo(shortSummary);
    }
  }

  // ==================== version directory path filter ====================

  /**
   * Verifies that a directory with an invalid minor version format is not included.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void invalidMinorVersionFormatExcluded() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();
      Path issuesDir = projectPath.resolve(".cat/issues");
      Path invalidDir = issuesDir.resolve("v2/invalid-dir");
      Files.createDirectories(invalidDir);
      Files.writeString(invalidDir.resolve("STATE.md"), "# State\n\n- **Status:** open\n");

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      requireThat(mapper.readTree(result).get("versions").size(), "versions.size").isEqualTo(0);
    }
  }

  /**
   * Verifies that a single-digit minor version (e.g., v2.1) is included in the output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void singleDigitMinorVersionIncluded() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();
      Path issuesDir = projectPath.resolve(".cat/issues");
      Path versionDir = issuesDir.resolve("v2/v2.1");
      Files.createDirectories(versionDir);
      Files.writeString(versionDir.resolve("STATE.md"), "# State\n\n- **Status:** open\n");

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode versions = mapper.readTree(result).get("versions");
      requireThat(versions.size(), "versions.size").isEqualTo(1);
      requireThat(versions.get(0).get("version").asString(), "version").isEqualTo("2.1");
    }
  }

  /**
   * Verifies that a directory at major-level depth is not treated as a version.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void majorDirAtVersionLevelExcluded() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();
      Path issuesDir = projectPath.resolve(".cat/issues");
      // v2 directly under issuesDir is depth 1, not depth 2 - excluded by filter
      Path majorDir = issuesDir.resolve("v2");
      Files.createDirectories(majorDir);
      Files.writeString(majorDir.resolve("STATE.md"), "# State\n\n- **Status:** open\n");

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      requireThat(mapper.readTree(result).get("versions").size(), "versions.size").isEqualTo(0);
    }
  }

  // ==================== JSON structure validation ====================

  /**
   * Verifies that all required top-level fields are present in the successful output.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void allRequiredTopLevelFieldsPresent() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();
      Path issuesDir = projectPath.resolve(".cat/issues");
      Files.createDirectories(issuesDir);

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode root = mapper.readTree(result);

      requireThat(root.has("planning_valid"), "planning_valid.present").isTrue();
      requireThat(root.has("error_message"), "error_message.present").isTrue();
      requireThat(root.has("branch_strategy"), "branch_strategy.present").isTrue();
      requireThat(root.has("branch_pattern"), "branch_pattern.present").isTrue();
      requireThat(root.has("versions"), "versions.present").isTrue();
    }
  }

  /**
   * Verifies that each version node contains all required fields.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  @SuppressWarnings("try")
  public void versionNodeHasAllRequiredFields() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      Path projectPath = scope.getProjectPath();
      Path issuesDir = projectPath.resolve(".cat/issues");
      Path versionDir = issuesDir.resolve("v2/v2.1");
      Files.createDirectories(versionDir);
      Files.writeString(versionDir.resolve("STATE.md"), "# State\n\n- **Status:** open\n");

      GetAddOutput handler = new GetAddOutput(scope);
      String result = handler.getOutput(new String[0]);

      JsonMapper mapper = scope.getJsonMapper();
      JsonNode version = mapper.readTree(result).get("versions").get(0);

      requireThat(version.has("version"), "version.present").isTrue();
      requireThat(version.has("status"), "status.present").isTrue();
      requireThat(version.has("summary"), "summary.present").isTrue();
      requireThat(version.has("existing_issues"), "existing_issues.present").isTrue();
      requireThat(version.has("issue_count"), "issue_count.present").isTrue();
    }
  }

  // ==================== null args ====================

  /**
   * Verifies that null args throws NullPointerException.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test(expectedExceptions = NullPointerException.class)
  @SuppressWarnings("try")
  public void nullArgsThrowsNullPointerException() throws IOException
  {
    try (TestJvmScope scope = new TestJvmScope())
    {
      GetAddOutput handler = new GetAddOutput(scope);
      handler.getOutput(null);
    }
  }
}
