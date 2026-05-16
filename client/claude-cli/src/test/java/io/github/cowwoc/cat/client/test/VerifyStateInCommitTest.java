/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.AskHandler;
import io.github.cowwoc.cat.claude.hook.ask.VerifyStateInCommit;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for {@link VerifyStateInCommit}.
 * <p>
 * Verifies that the approval gate is blocked when the issue's {@code index.json} is not closed,
 * and allowed when it is closed. Also verifies that non-approval-gate questions are not blocked.
 */
public final class VerifyStateInCommitTest
{
  /**
   * Verifies that non-approval questions (no "approve" in text) are allowed without any checks.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void allowsNonApprovalQuestions() throws IOException
  {
    Path tempDir = Files.createTempDirectory("vsic-test-");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      VerifyStateInCommit handler = new VerifyStateInCommit(scope);
      JsonNode toolInput = mapper.readTree("""
        {"question": "What should I do next?"}""");

      AskHandler.Result result = handler.check(toolInput, "test-session");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the approval gate is blocked when the current branch cannot be determined.
   * <p>
   * When the project path is not a git repo (or {@code getCurrentBranch} fails with an
   * {@code IOException}), the handler blocks the gate with an error message rather than
   * silently allowing it.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void blocksApprovalGateWhenBranchCannotBeDetermined() throws IOException
  {
    // tempDir is not a git repo, so getCurrentBranch will throw IOException -> block
    Path tempDir = Files.createTempDirectory("vsic-test-");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      VerifyStateInCommit handler = new VerifyStateInCommit(scope);
      JsonNode toolInput = mapper.readTree("""
        {"question": "Do you approve these changes?", "options": ["Approve and merge", "Reject"]}""");

      AskHandler.Result result = handler.check(toolInput, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("BLOCKED");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that approval questions on a branch that does not follow the CAT naming convention are allowed.
   * <p>
   * When the branch name cannot be parsed as a CAT issue branch, the handler assumes it is
   * not a managed issue worktree and allows the operation.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void allowsApprovalGateWhenBranchNotCatConvention() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      JsonMapper mapper = scope.getJsonMapper();
      VerifyStateInCommit handler = new VerifyStateInCommit(scope);
      JsonNode toolInput = mapper.readTree("""
        {"question": "Do you approve these changes?", "options": ["Approve and merge", "Reject"]}""");

      AskHandler.Result result = handler.check(toolInput, "test-session");

      // "main" does not match the CAT issue branch pattern -> allow
      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the approval gate is blocked when the issue's index.json has "open" status.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void blocksApprovalGateWhenIndexJsonNotClosed() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("2.1-my-test-issue");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      // Create index.json with "open" status at the expected path for branch "2.1-my-test-issue"
      // Expected: .cat/issues/v2/v2.1/my-test-issue/index.json
      Path issueDir = tempDir.resolve(".cat").resolve("issues").resolve("v2").
        resolve("v2.1").resolve("my-test-issue");
      Files.createDirectories(issueDir);
      Files.writeString(issueDir.resolve("index.json"), "{\"status\":\"open\"}");

      JsonMapper mapper = scope.getJsonMapper();
      VerifyStateInCommit handler = new VerifyStateInCommit(scope);
      JsonNode toolInput = mapper.readTree("""
        {"question": "Do you approve these changes?", "options": ["Approve and merge", "Reject"]}""");

      AskHandler.Result result = handler.check(toolInput, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("BLOCKED");
      requireThat(result.reason(), "reason").contains("index.json");
      requireThat(result.reason(), "reason").contains("closed");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the approval gate is allowed when the issue's index.json has "closed" status.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void allowsApprovalGateWhenIndexJsonClosed() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("2.1-my-feature");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      // Create index.json with "closed" status at the expected path for branch "2.1-my-feature"
      Path issueDir = tempDir.resolve(".cat").resolve("issues").resolve("v2").
        resolve("v2.1").resolve("my-feature");
      Files.createDirectories(issueDir);
      Files.writeString(issueDir.resolve("index.json"), "{\"status\":\"closed\"}");

      JsonMapper mapper = scope.getJsonMapper();
      VerifyStateInCommit handler = new VerifyStateInCommit(scope);
      JsonNode toolInput = mapper.readTree("""
        {"question": "Do you approve these changes?", "options": ["Approve and merge", "Reject"]}""");

      AskHandler.Result result = handler.check(toolInput, "test-session");

      requireThat(result.blocked(), "blocked").isFalse();
      requireThat(result.reason(), "reason").isEmpty();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the approval gate is blocked when index.json is absent (does not exist).
   * <p>
   * An absent index.json is treated as "not closed" to prevent presenting an approval gate
   * for issues where index.json was never committed.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void blocksApprovalGateWhenIndexJsonAbsent() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("2.1-missing-index");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      // Do NOT create index.json — it is intentionally absent
      JsonMapper mapper = scope.getJsonMapper();
      VerifyStateInCommit handler = new VerifyStateInCommit(scope);
      JsonNode toolInput = mapper.readTree("""
        {"question": "Do you approve?", "options": ["Approve and merge", "Reject"]}""");

      AskHandler.Result result = handler.check(toolInput, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("BLOCKED");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the approval gate is blocked when index.json has "in-progress" status.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void blocksApprovalGateWhenIndexJsonInProgress() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("2.1-in-progress-issue");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      Path issueDir = tempDir.resolve(".cat").resolve("issues").resolve("v2").
        resolve("v2.1").resolve("in-progress-issue");
      Files.createDirectories(issueDir);
      Files.writeString(issueDir.resolve("index.json"), "{\"status\":\"in-progress\"}");

      JsonMapper mapper = scope.getJsonMapper();
      VerifyStateInCommit handler = new VerifyStateInCommit(scope);
      JsonNode toolInput = mapper.readTree("""
        {"question": "Do you approve?", "options": ["Approve and merge", "Reject"]}""");

      AskHandler.Result result = handler.check(toolInput, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the approval gate is blocked with a parse error when index.json contains malformed JSON.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void blocksApprovalGateWhenIndexJsonMalformed() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("2.1-malformed-issue");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      Path issueDir = tempDir.resolve(".cat").resolve("issues").resolve("v2").
        resolve("v2.1").resolve("malformed-issue");
      Files.createDirectories(issueDir);
      Files.writeString(issueDir.resolve("index.json"), "not-valid-json{{{");

      JsonMapper mapper = scope.getJsonMapper();
      VerifyStateInCommit handler = new VerifyStateInCommit(scope);
      JsonNode toolInput = mapper.readTree("""
        {"question": "Do you approve?", "options": ["Approve and merge", "Reject"]}""");

      AskHandler.Result result = handler.check(toolInput, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("BLOCKED");
      requireThat(result.reason(), "reason").contains("malformed");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that the block message includes the specific index.json path derived from the branch name.
   *
   * @throws IOException if test setup fails
   */
  @Test
  public void blockMessageContainsIndexJsonPath() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("2.1-path-check-issue");
    try (TestClaudeHook scope = new TestClaudeHook(tempDir, tempDir, tempDir))
    {
      // Do NOT create index.json so it is absent
      JsonMapper mapper = scope.getJsonMapper();
      VerifyStateInCommit handler = new VerifyStateInCommit(scope);
      JsonNode toolInput = mapper.readTree("""
        {"question": "Do you approve?", "options": ["Approve and merge", "Reject"]}""");

      AskHandler.Result result = handler.check(toolInput, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").
        contains(".cat/issues/v2/v2.1/path-check-issue/index.json");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}
