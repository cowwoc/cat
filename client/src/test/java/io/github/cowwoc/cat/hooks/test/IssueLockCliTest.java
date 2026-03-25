/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.util.IssueLock;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for IssueLock CLI entry point (run() method).
 * <p>
 * Tests verify argument parsing, output format, and exit behavior for all subcommands.
 * Uses injectable streams and scopes to avoid System.exit() calls during tests.
 * All output (success and error) is written to stdout using ClaudeHook format.
 */
public class IssueLockCliTest
{
  /**
   * Verifies that acquire with valid arguments writes acquired status to stdout.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acquireWithValidArgsWritesAcquiredToStdout() throws IOException
  {
    Path tempDir = TestUtils.createTempCatProject("issue-lock-cli-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);

        String sessionId = UUID.randomUUID().toString();
        IssueLock.run(scope, new String[]{"acquire", "test-issue", sessionId, "/some/worktree"}, out);

        String output = outBytes.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").contains("\"status\"");
        requireThat(output, "output").contains("\"acquired\"");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that acquire with missing session ID writes a block error response to stdout.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acquireWithMissingSessionIdWritesBlockErrorToStdout() throws IOException
  {
    Path tempDir = TestUtils.createTempCatProject("issue-lock-cli-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);

        IssueLock.run(scope, new String[]{"acquire", "test-issue"}, out);

        String output = outBytes.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").contains("\"decision\"");
        requireThat(output, "output").contains("\"block\"");
        requireThat(output, "output").contains("Usage");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that acquire with an invalid UUID session ID writes a block error response to stdout.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acquireWithInvalidUuidSessionIdWritesBlockErrorToStdout() throws IOException
  {
    Path tempDir = TestUtils.createTempCatProject("issue-lock-cli-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);

        IssueLock.run(scope, new String[]{"acquire", "test-issue", "not-a-uuid", "/some/worktree"}, out);

        String output = outBytes.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").contains("\"decision\"");
        requireThat(output, "output").contains("\"block\"");
        requireThat(output, "output").contains("UUID");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that release with valid arguments writes released status to stdout.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void releaseWithValidArgsWritesReleasedToStdout() throws IOException
  {
    Path tempDir = TestUtils.createTempCatProject("issue-lock-cli-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);

        String sessionId = UUID.randomUUID().toString();
        IssueLock lock = new IssueLock(scope);
        lock.acquire("test-issue", sessionId, "/path/to/worktree");

        IssueLock.run(scope, new String[]{"release", "test-issue", sessionId}, out);

        String output = outBytes.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").contains("\"status\"");
        requireThat(output, "output").contains("\"released\"");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that check with a valid issue returns JSON with "locked" field.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void checkWithValidIssueReturnsLockedFalseJson() throws IOException
  {
    Path tempDir = TestUtils.createTempCatProject("issue-lock-cli-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);

        IssueLock.run(scope, new String[]{"check", "unlocked-issue"}, out);

        String output = outBytes.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").contains("\"locked\"");
        requireThat(output, "output").contains("false");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that list returns a JSON array.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void listCommandReturnsJsonArray() throws IOException
  {
    Path tempDir = TestUtils.createTempCatProject("issue-lock-cli-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);

        IssueLock.run(scope, new String[]{"list"}, out);

        String output = outBytes.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").contains("[");
        requireThat(output, "output").contains("]");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that an unknown command writes a block error response to stdout.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void unknownCommandWritesBlockErrorToStdout() throws IOException
  {
    Path tempDir = TestUtils.createTempCatProject("issue-lock-cli-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);

        IssueLock.run(scope, new String[]{"invalid-command"}, out);

        String output = outBytes.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").contains("\"decision\"");
        requireThat(output, "output").contains("\"block\"");
        requireThat(output, "output").contains("Unknown command");
        requireThat(output, "output").contains("invalid-command");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that force-release with valid arguments writes released status to stdout.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void forceReleaseWithValidIssueWritesReleasedToStdout() throws IOException
  {
    Path tempDir = TestUtils.createTempCatProject("issue-lock-cli-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);

        String sessionId = UUID.randomUUID().toString();
        IssueLock lock = new IssueLock(scope);
        lock.acquire("test-issue", sessionId, "/path/to/worktree");

        IssueLock.run(scope, new String[]{"force-release", "test-issue"}, out);

        String output = outBytes.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").contains("\"released\"");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that no-args invocation writes a block error response to stdout.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void noArgsWritesBlockErrorToStdout() throws IOException
  {
    Path tempDir = TestUtils.createTempCatProject("issue-lock-cli-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);

        IssueLock.run(scope, new String[]{}, out);

        String output = outBytes.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").contains("\"decision\"");
        requireThat(output, "output").contains("\"block\"");
        requireThat(output, "output").contains("Usage");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that acquire with a blank worktree argument writes a block error response to stdout.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acquireWithBlankWorktreeArgWritesBlockErrorToStdout() throws IOException
  {
    Path tempDir = TestUtils.createTempCatProject("issue-lock-cli-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);

        String sessionId = UUID.randomUUID().toString();
        IssueLock.run(scope, new String[]{"acquire", "test-issue", sessionId, ""}, out);

        String output = outBytes.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").contains("\"decision\"");
        requireThat(output, "output").contains("\"block\"");
        requireThat(output, "output").contains("worktree");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that acquire with a worktree argument produces JSON with status=acquired and populates
   * the worktrees map in the lock file on disk.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acquireWithWorktreeArgPopulatesWorktreesMap() throws IOException
  {
    Path tempDir = TestUtils.createTempCatProject("issue-lock-cli-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      try
      {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);

        String sessionId = UUID.randomUUID().toString();
        String worktreePath = "/some/path";
        IssueLock.run(scope, new String[]{"acquire", "test-issue", sessionId, worktreePath}, out);

        String output = outBytes.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").contains("\"status\"");
        requireThat(output, "output").contains("\"acquired\"");

        // Verify the lock file on disk has the worktree path in the worktrees map
        Path lockFile = scope.getCatWorkPath().resolve("locks").resolve("test-issue.lock");
        String content = Files.readString(lockFile);
        @SuppressWarnings("unchecked")
        Map<String, Object> lockData = scope.getJsonMapper().readValue(content, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, String> worktrees = (Map<String, String>) lockData.get("worktrees");

        requireThat(worktrees, "worktrees").isNotNull();
        requireThat(worktrees.get(worktreePath), "worktreeValue").isEqualTo(sessionId);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }

  /**
   * Verifies that acquiring a lock when the session already holds one for a different issue outputs
   * JSON with {@code "status": "error"} and the conflicting issue ID in the message.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void acquireConflictErrorOutputsCorrectJson() throws IOException
  {
    Path tempDir = TestUtils.createTempCatProject("issue-lock-cli-test");
    try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))
    {
      try
      {
        String sessionId = UUID.randomUUID().toString();

        // Acquire a lock for issue-a first
        IssueLock lock = new IssueLock(scope);
        lock.acquire("issue-a", sessionId, "/worktree-a");

        // Now attempt to acquire a lock for issue-b via the CLI entry point
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        IssueLock.run(scope, new String[]{"acquire", "issue-b", sessionId, "/worktree-b"}, out);

        String output = outBytes.toString(StandardCharsets.UTF_8);
        requireThat(output, "output").contains("\"status\"");
        requireThat(output, "output").contains("\"error\"");
        // Verify the conflicting issue ID appears specifically within the message field
        JsonNode parsed = scope.getJsonMapper().readTree(output);
        String message = parsed.get("message").asString();
        requireThat(message, "message").contains("issue-a");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
      }
    }
  }
}
