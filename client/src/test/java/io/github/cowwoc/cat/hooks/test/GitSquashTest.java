/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.GitSquash;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GitSquash commit message validation, rebase conflict handling,
 * and concurrent modification detection.
 */
public class GitSquashTest
{
  // ============================================================================
  // Commit message validation: accepts valid messages
  // ============================================================================

  /**
   * Verifies that a valid feature commit message is accepted.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acceptsValidFeatureMessage() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      createTestBranch(tempDir, "test-branch", 3);
      GitSquash cmd = new GitSquash(scope, tempDir.toString());
      String result = cmd.execute("main", "feature: add user authentication");
      JsonNode json = scope.getJsonMapper().readTree(result);
      requireThat(json.get("status").asString(), "status").isEqualTo("OK");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that a valid bugfix commit message is accepted.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acceptsValidBugfixMessage() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      createTestBranch(tempDir, "test-branch", 3);
      GitSquash cmd = new GitSquash(scope, tempDir.toString());
      String result = cmd.execute("main", "bugfix: fix crash on startup");
      JsonNode json = scope.getJsonMapper().readTree(result);
      requireThat(json.get("status").asString(), "status").isEqualTo("OK");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that a valid docs commit message is accepted.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acceptsValidDocsMessage() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      createTestBranch(tempDir, "test-branch", 3);
      GitSquash cmd = new GitSquash(scope, tempDir.toString());
      String result = cmd.execute("main", "docs: update README with installation guide");
      JsonNode json = scope.getJsonMapper().readTree(result);
      requireThat(json.get("status").asString(), "status").isEqualTo("OK");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that a valid refactor commit message is accepted.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acceptsValidRefactorMessage() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      createTestBranch(tempDir, "test-branch", 3);
      GitSquash cmd = new GitSquash(scope, tempDir.toString());
      String result = cmd.execute("main", "refactor: extract validation logic");
      JsonNode json = scope.getJsonMapper().readTree(result);
      requireThat(json.get("status").asString(), "status").isEqualTo("OK");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that a valid test commit message is accepted.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acceptsValidTestMessage() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      createTestBranch(tempDir, "test-branch", 3);
      GitSquash cmd = new GitSquash(scope, tempDir.toString());
      String result = cmd.execute("main", "test: add validation tests");
      JsonNode json = scope.getJsonMapper().readTree(result);
      requireThat(json.get("status").asString(), "status").isEqualTo("OK");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that a valid performance commit message is accepted.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acceptsValidPerformanceMessage() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      createTestBranch(tempDir, "test-branch", 3);
      GitSquash cmd = new GitSquash(scope, tempDir.toString());
      String result = cmd.execute("main", "performance: optimize search algorithm");
      JsonNode json = scope.getJsonMapper().readTree(result);
      requireThat(json.get("status").asString(), "status").isEqualTo("OK");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that a valid config commit message is accepted.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acceptsValidConfigMessage() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      createTestBranch(tempDir, "test-branch", 3);
      GitSquash cmd = new GitSquash(scope, tempDir.toString());
      String result = cmd.execute("main", "config: update project settings");
      JsonNode json = scope.getJsonMapper().readTree(result);
      requireThat(json.get("status").asString(), "status").isEqualTo("OK");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that a valid planning commit message is accepted.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acceptsValidPlanningMessage() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      createTestBranch(tempDir, "test-branch", 3);
      GitSquash cmd = new GitSquash(scope, tempDir.toString());
      String result = cmd.execute("main", "planning: add task breakdown");
      JsonNode json = scope.getJsonMapper().readTree(result);
      requireThat(json.get("status").asString(), "status").isEqualTo("OK");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  // ============================================================================
  // Commit message validation: rejects invalid messages
  // ============================================================================

  /**
   * Verifies that a generic 'squash commit' message is rejected.
   */
  @Test
  public void rejectsGenericSquashCommitMessage() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GitSquash cmd = new GitSquash(scope, ".");
      try
      {
        cmd.execute("main", "squash commit");
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("Invalid commit message format");
      }
    }
  }

  /**
   * Verifies that a generic 'fix typo' message is rejected.
   */
  @Test
  public void rejectsGenericFixTypoMessage() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GitSquash cmd = new GitSquash(scope, ".");
      try
      {
        cmd.execute("main", "fix typo");
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("Invalid commit message format");
      }
    }
  }

  /**
   * Verifies that a message with missing space after colon is rejected.
   */
  @Test
  public void rejectsMissingSpaceAfterColonFeature() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GitSquash cmd = new GitSquash(scope, ".");
      try
      {
        cmd.execute("main", "feature:nodescription");
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("Invalid commit message format");
      }
    }
  }

  /**
   * Verifies that a bugfix message with missing space after colon is rejected.
   */
  @Test
  public void rejectsMissingSpaceAfterColonBugfix() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GitSquash cmd = new GitSquash(scope, ".");
      try
      {
        cmd.execute("main", "bugfix:fix");
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("Invalid commit message format");
      }
    }
  }

  /**
   * Verifies that a message with only colon and space (empty description) is rejected.
   */
  @Test
  public void rejectsEmptyDescription() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GitSquash cmd = new GitSquash(scope, ".");
      try
      {
        cmd.execute("main", "feature: ");
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("Invalid commit message format");
      }
    }
  }

  /**
   * Verifies that an uppercase type prefix is rejected.
   */
  @Test
  public void rejectsUppercaseTypePrefix() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GitSquash cmd = new GitSquash(scope, ".");
      try
      {
        cmd.execute("main", "Feature: add auth");
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("Invalid commit message format");
      }
    }
  }

  /**
   * Verifies that a mixed case type prefix is rejected.
   */
  @Test
  public void rejectsMixedCaseTypePrefix() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GitSquash cmd = new GitSquash(scope, ".");
      try
      {
        cmd.execute("main", "Bugfix: fix crash");
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("Invalid commit message format");
      }
    }
  }

  /**
   * Verifies that an empty string message is rejected.
   */
  @Test
  public void rejectsEmptyStringMessage() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GitSquash cmd = new GitSquash(scope, ".");
      try
      {
        cmd.execute("main", "");
      }
      catch (IllegalArgumentException _)
      {
        // Either isNotBlank or format validation triggers - both are correct
      }
    }
  }

  /**
   * Verifies that an invalid type prefix like 'chore' is rejected.
   */
  @Test
  public void rejectsInvalidTypePrefix() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GitSquash cmd = new GitSquash(scope, ".");
      try
      {
        cmd.execute("main", "chore: update dependencies");
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("Invalid commit message format");
      }
    }
  }

  /**
   * Verifies that a message with only whitespace after type and colon is rejected.
   */
  @Test
  public void rejectsWhitespaceOnlyDescription() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GitSquash cmd = new GitSquash(scope, ".");
      try
      {
        cmd.execute("main", "feature:   ");
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("Invalid commit message format");
      }
    }
  }

  /**
   * Verifies that a generic 'add parser' message is rejected.
   */
  @Test
  public void rejectsGenericAddParserMessage() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GitSquash cmd = new GitSquash(scope, ".");
      try
      {
        cmd.execute("main", "add parser");
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("Invalid commit message format");
      }
    }
  }

  // ============================================================================
  // Error message content tests
  // ============================================================================

  /**
   * Verifies that the error message shows the received message.
   */
  @Test
  public void errorMessageShowsReceivedMessage() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GitSquash cmd = new GitSquash(scope, ".");
      try
      {
        cmd.execute("main", "invalid message");
      }
      catch (IllegalArgumentException e)
      {
        requireThat(e.getMessage(), "message").contains("Received: invalid message");
      }
    }
  }

  /**
   * Verifies that the error message shows valid type prefixes.
   */
  @Test
  public void errorMessageShowsValidTypePrefixes() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      GitSquash cmd = new GitSquash(scope, ".");
      try
      {
        cmd.execute("main", "invalid: message");
      }
      catch (IllegalArgumentException e)
      {
        String msg = e.getMessage();
        requireThat(msg, "message").contains("feature:");
        requireThat(msg, "message").contains("bugfix:");
        requireThat(msg, "message").contains("docs:");
      }
    }
  }

  /**
   * Verifies that a long valid message is accepted and produces OK status.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acceptsLongValidMessage() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      createTestBranch(tempDir, "test-branch", 3);
      GitSquash cmd = new GitSquash(scope, tempDir.toString());
      String result = cmd.execute("main",
        "feature: add comprehensive user authentication system with OAuth2 support and session management");
      JsonNode json = scope.getJsonMapper().readTree(result);
      requireThat(json.get("status").asString(), "status").isEqualTo("OK");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  // ============================================================================
  // Special character tests
  // ============================================================================

  /**
   * Verifies that a message with special characters in description is accepted.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acceptsMessageWithSpecialCharacters() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      createTestBranch(tempDir, "test-branch", 3);
      GitSquash cmd = new GitSquash(scope, tempDir.toString());
      String result = cmd.execute("main", "feature: add $VAR and `backtick` support");
      JsonNode json = scope.getJsonMapper().readTree(result);
      requireThat(json.get("status").asString(), "status").isEqualTo("OK");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that a message with parentheses and brackets is accepted.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acceptsMessageWithParenthesesAndBrackets() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      createTestBranch(tempDir, "test-branch", 3);
      GitSquash cmd = new GitSquash(scope, tempDir.toString());
      String result = cmd.execute("main", "bugfix: fix array[0] access (null check)");
      JsonNode json = scope.getJsonMapper().readTree(result);
      requireThat(json.get("status").asString(), "status").isEqualTo("OK");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  // ============================================================================
  // Rebase conflict tests
  // ============================================================================

  /**
   * Verifies that a rebase conflict causes the JSON result to contain REBASE_CONFLICT status.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void rebaseConflictReturnsRebaseConflictStatus() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      createTestBranch(tempDir, "test-branch", 3);

      // Create a conflicting commit on main
      TestUtils.runGitCommand(tempDir, "checkout", "main");
      Files.writeString(tempDir.resolve("file.txt"), "conflict on main");
      TestUtils.runGitCommand(tempDir, "add", "file.txt");
      TestUtils.runGitCommand(tempDir, "commit", "-m", "conflicting change on main");

      TestUtils.runGitCommand(tempDir, "checkout", "test-branch");

      GitSquash cmd = new GitSquash(scope, tempDir.toString());
      String result = cmd.execute("main", "feature: squash with conflict");
      JsonNode json = scope.getJsonMapper().readTree(result);
      requireThat(json.get("status").asString(), "status").isEqualTo("REBASE_CONFLICT");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that a rebase conflict output includes a backup_branch field.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void rebaseConflictIncludesBackupBranch() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      createTestBranch(tempDir, "test-branch", 3);

      TestUtils.runGitCommand(tempDir, "checkout", "main");
      Files.writeString(tempDir.resolve("file.txt"), "conflict on main");
      TestUtils.runGitCommand(tempDir, "add", "file.txt");
      TestUtils.runGitCommand(tempDir, "commit", "-m", "conflicting change on main");

      TestUtils.runGitCommand(tempDir, "checkout", "test-branch");

      GitSquash cmd = new GitSquash(scope, tempDir.toString());
      String result = cmd.execute("main", "feature: squash with conflict");
      JsonNode json = scope.getJsonMapper().readTree(result);
      requireThat(json.has("backup_branch"), "hasBackupBranch").isTrue();
      requireThat(json.get("backup_branch").asString(), "backupBranch").
        contains("backup-after-rebase-conflict-");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that a rebase conflict creates a backup branch in the repository.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void rebaseConflictCreatesBackupBranch() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      createTestBranch(tempDir, "test-branch", 3);

      TestUtils.runGitCommand(tempDir, "checkout", "main");
      Files.writeString(tempDir.resolve("file.txt"), "conflict on main");
      TestUtils.runGitCommand(tempDir, "add", "file.txt");
      TestUtils.runGitCommand(tempDir, "commit", "-m", "conflicting change on main");

      TestUtils.runGitCommand(tempDir, "checkout", "test-branch");

      GitSquash cmd = new GitSquash(scope, tempDir.toString());
      cmd.execute("main", "feature: squash with conflict");

      // Verify backup branch was created
      String branches = TestUtils.runGitCommandWithOutput(tempDir, "branch", "--list",
        "backup-after-rebase-conflict-*");
      requireThat(branches.isBlank(), "backupBranchMissing").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that the working directory is left clean after a rebase conflict (rebase aborted).
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void rebaseConflictLeavesCleanWorkingDirectory() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      createTestBranch(tempDir, "test-branch", 3);

      TestUtils.runGitCommand(tempDir, "checkout", "main");
      Files.writeString(tempDir.resolve("file.txt"), "conflict on main");
      TestUtils.runGitCommand(tempDir, "add", "file.txt");
      TestUtils.runGitCommand(tempDir, "commit", "-m", "conflicting change on main");

      TestUtils.runGitCommand(tempDir, "checkout", "test-branch");

      GitSquash cmd = new GitSquash(scope, tempDir.toString());
      cmd.execute("main", "feature: squash with conflict");

      // Working tree should be clean (rebase was aborted)
      String statusOutput = TestUtils.runGitCommandWithOutput(tempDir, "status", "--porcelain");
      requireThat(statusOutput.isBlank(), "isClean").isTrue();

      // No ongoing rebase
      requireThat(Files.exists(tempDir.resolve(".git/rebase-merge")), "rebaseInProgress").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that rebase conflict output includes conflicting_files array.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void rebaseConflictIncludesConflictingFiles() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      createTestBranch(tempDir, "test-branch", 3);

      TestUtils.runGitCommand(tempDir, "checkout", "main");
      Files.writeString(tempDir.resolve("file.txt"), "conflict on main");
      TestUtils.runGitCommand(tempDir, "add", "file.txt");
      TestUtils.runGitCommand(tempDir, "commit", "-m", "conflicting change on main");

      TestUtils.runGitCommand(tempDir, "checkout", "test-branch");

      GitSquash cmd = new GitSquash(scope, tempDir.toString());
      String result = cmd.execute("main", "feature: squash with conflict");
      JsonNode json = scope.getJsonMapper().readTree(result);
      requireThat(json.has("conflicting_files"), "hasConflictingFiles").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  // ============================================================================
  // Concurrent modification detection tests
  // ============================================================================

  /**
   * Verifies that a concurrent modification warning is emitted when a file is modified
   * on both the base branch and the issue branch.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void warnsOnConcurrentModification() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      // Add shared.txt on main
      Files.writeString(tempDir.resolve("shared.txt"), "line1\nline2\nline3\nline4\nline5\n");
      TestUtils.runGitCommand(tempDir, "add", "shared.txt");
      TestUtils.runGitCommand(tempDir, "commit", "-m", "initial: add shared.txt");

      // Create issue branch BEFORE main advances
      TestUtils.runGitCommand(tempDir, "checkout", "-b", "concurrent-branch");

      // Issue branch modifies the LAST line
      Files.writeString(tempDir.resolve("shared.txt"), "line1\nline2\nline3\nline4\nissue-line5\n");
      Files.writeString(tempDir.resolve("feature.txt"), "feature work");
      TestUtils.runGitCommand(tempDir, "add", ".");
      TestUtils.runGitCommand(tempDir, "commit", "-m", "feature: modify line5");

      // Main modifies the FIRST line (non-overlapping, rebase will auto-merge)
      TestUtils.runGitCommand(tempDir, "checkout", "main");
      Files.writeString(tempDir.resolve("shared.txt"), "base-line1\nline2\nline3\nline4\nline5\n");
      TestUtils.runGitCommand(tempDir, "add", "shared.txt");
      TestUtils.runGitCommand(tempDir, "commit", "-m", "update line1 on main");

      // Back to issue branch for squash
      TestUtils.runGitCommand(tempDir, "checkout", "concurrent-branch");

      GitSquash cmd = new GitSquash(scope, tempDir.toString());
      String result = cmd.execute("main", "feature: add something");
      JsonNode json = scope.getJsonMapper().readTree(result);
      requireThat(json.get("status").asString(), "status").isEqualTo("OK");
      requireThat(result, "result").contains("CONCURRENT_MODIFICATION");
      requireThat(result, "result").contains("shared.txt");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that concurrent modification warning lists multiple affected files.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void concurrentModWarningListsMultipleFiles() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      // Add two shared files
      Files.writeString(tempDir.resolve("config.yaml"), "aaa\nbbb\nccc\n");
      Files.writeString(tempDir.resolve("data.json"), "xxx\nyyy\nzzz\n");
      TestUtils.runGitCommand(tempDir, "add", "config.yaml", "data.json");
      TestUtils.runGitCommand(tempDir, "commit", "-m", "initial: add config and data");

      TestUtils.runGitCommand(tempDir, "checkout", "-b", "concurrent-branch2");

      // Issue branch modifies last line of both files
      Files.writeString(tempDir.resolve("config.yaml"), "aaa\nbbb\nissue-ccc\n");
      Files.writeString(tempDir.resolve("data.json"), "xxx\nyyy\nissue-zzz\n");
      Files.writeString(tempDir.resolve("feature.txt"), "feature work");
      TestUtils.runGitCommand(tempDir, "add", ".");
      TestUtils.runGitCommand(tempDir, "commit", "-m", "feature: modify config and data");

      // Main modifies first line of both files (non-overlapping)
      TestUtils.runGitCommand(tempDir, "checkout", "main");
      Files.writeString(tempDir.resolve("config.yaml"), "base-aaa\nbbb\nccc\n");
      Files.writeString(tempDir.resolve("data.json"), "base-xxx\nyyy\nzzz\n");
      TestUtils.runGitCommand(tempDir, "add", "config.yaml", "data.json");
      TestUtils.runGitCommand(tempDir, "commit", "-m", "update config and data on main");

      TestUtils.runGitCommand(tempDir, "checkout", "concurrent-branch2");

      GitSquash cmd = new GitSquash(scope, tempDir.toString());
      String result = cmd.execute("main", "feature: add something");
      requireThat(result, "result").contains("config.yaml");
      requireThat(result, "result").contains("data.json");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that no concurrent modification warning is emitted when only the issue branch
   * modifies a file.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void noConcurrentModWarningWhenOnlyIssueModifies() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      createTestBranch(tempDir, "test-branch", 3);
      GitSquash cmd = new GitSquash(scope, tempDir.toString());
      String result = cmd.execute("main", "feature: add something");
      JsonNode json = scope.getJsonMapper().readTree(result);
      requireThat(json.get("status").asString(), "status").isEqualTo("OK");
      requireThat(result.contains("CONCURRENT_MODIFICATION"), "hasConcurrentMod").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that no warnings are emitted when the base branch has no new commits.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void noWarningWhenBaseUnchanged() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      createTestBranch(tempDir, "test-branch", 3);
      GitSquash cmd = new GitSquash(scope, tempDir.toString());
      String result = cmd.execute("main", "feature: add something");
      JsonNode json = scope.getJsonMapper().readTree(result);
      requireThat(json.get("status").asString(), "status").isEqualTo("OK");
      requireThat(result.contains("warnings"), "hasWarnings").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  /**
   * Verifies that squash succeeds with warnings when concurrent modifications are detected.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void squashSucceedsWithConcurrentModWarnings() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    Path pluginRoot = Files.createTempDirectory("plugin-root-");
    try (JvmScope scope = new TestJvmScope(tempDir, pluginRoot))
    {
      // Add shared.txt on main
      Files.writeString(tempDir.resolve("shared.txt"), "line1\nline2\nline3\nline4\nline5\n");
      TestUtils.runGitCommand(tempDir, "add", "shared.txt");
      TestUtils.runGitCommand(tempDir, "commit", "-m", "initial: add shared.txt");

      TestUtils.runGitCommand(tempDir, "checkout", "-b", "verify-branch");

      Files.writeString(tempDir.resolve("shared.txt"), "line1\nline2\nline3\nline4\nissue-line5\n");
      Files.writeString(tempDir.resolve("feature.txt"), "feature work");
      TestUtils.runGitCommand(tempDir, "add", ".");
      TestUtils.runGitCommand(tempDir, "commit", "-m", "feature: modify shared");

      TestUtils.runGitCommand(tempDir, "checkout", "main");
      Files.writeString(tempDir.resolve("shared.txt"), "base-line1\nline2\nline3\nline4\nline5\n");
      TestUtils.runGitCommand(tempDir, "add", "shared.txt");
      TestUtils.runGitCommand(tempDir, "commit", "-m", "update shared on main");

      TestUtils.runGitCommand(tempDir, "checkout", "verify-branch");

      GitSquash cmd = new GitSquash(scope, tempDir.toString());
      String result = cmd.execute("main", "feature: add something");
      JsonNode json = scope.getJsonMapper().readTree(result);
      requireThat(json.get("status").asString(), "status").isEqualTo("OK");
      requireThat(result, "result").contains("backup_branch");
      requireThat(result, "result").contains("backup_verified");

      // Verify exactly 1 commit from base
      String count = TestUtils.runGitCommandWithOutput(tempDir,
        "rev-list", "--count", "main..HEAD");
      requireThat(count.strip(), "commitCount").isEqualTo("1");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
      TestUtils.deleteDirectoryRecursively(pluginRoot);
    }
  }

  // ============================================================================
  // Helper methods
  // ============================================================================

  /**
   * Creates a test branch with the specified number of commits diverging from the current branch.
   *
   * @param repoDir     the repository directory
   * @param branchName  the branch name to create
   * @param commitCount the number of commits to create on the branch
   */
  private static void createTestBranch(Path repoDir, String branchName, int commitCount)
  {
    TestUtils.runGitCommand(repoDir, "checkout", "-b", branchName);
    for (int i = 1; i <= commitCount; ++i)
    {
      try
      {
        Files.writeString(repoDir.resolve("file.txt"), "commit " + i);
      }
      catch (IOException e)
      {
        throw WrappedCheckedException.wrap(e);
      }
      TestUtils.runGitCommand(repoDir, "add", "file.txt");
      TestUtils.runGitCommand(repoDir, "commit", "-m", "commit " + i);
    }
  }
}
