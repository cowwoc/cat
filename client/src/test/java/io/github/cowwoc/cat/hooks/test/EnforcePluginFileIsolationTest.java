/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.FileWriteHandler;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.write.EnforcePluginFileIsolation;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for EnforcePluginFileIsolation hook.
 * <p>
 * Tests verify that plugin/ and client/ source files are properly blocked outside of issue worktrees
 * and allowed inside issue worktrees (identified by the git directory ending with
 * {@code worktrees/<branch-name>}).
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class EnforcePluginFileIsolationTest
{
  /**
   * Verifies that non-plugin files are always allowed.
   */
  @Test
  public void nonPluginFilesAreAllowed() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      EnforcePluginFileIsolation handler = new EnforcePluginFileIsolation();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", "/workspace/README.md");

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that plugin files in a regular repo (e.g., on main) are blocked.
   */
  @Test
  public void pluginFilesBlockedOnMain() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope())
    {
      EnforcePluginFileIsolation handler = new EnforcePluginFileIsolation();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", tempDir.resolve("plugin/hooks/test.py").toString());

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Cannot edit source files outside of an issue worktree");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that plugin files in a regular repo (e.g., on v2.1) are blocked.
   */
  @Test
  public void pluginFilesBlockedOnV21() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope())
    {
      EnforcePluginFileIsolation handler = new EnforcePluginFileIsolation();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", tempDir.resolve("plugin/skills/test.md").toString());

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Cannot edit source files outside of an issue worktree");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that plugin files in a regular repo (e.g., on version branch v1.0) are blocked.
   */
  @Test
  public void pluginFilesBlockedOnVersionBranch() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("v1.0");
    try (JvmScope scope = new TestJvmScope())
    {
      EnforcePluginFileIsolation handler = new EnforcePluginFileIsolation();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", tempDir.resolve("plugin/commands/test.md").toString());

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Cannot edit source files outside of an issue worktree");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that plugin files in an issue worktree are allowed.
   * <p>
   * An issue worktree has a git directory ending with {@code worktrees/<branch-name>}.
   */
  @Test
  public void pluginFilesAllowedOnTaskBranch() throws IOException
  {
    Path mainDir = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainDir, mainDir))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainDir, worktreesDir, "2.1-fix-bug");
      try
      {
        EnforcePluginFileIsolation handler = new EnforcePluginFileIsolation();
        JsonMapper mapper = scope.getJsonMapper();
        ObjectNode input = mapper.createObjectNode();
        input.put("file_path", worktreeDir.resolve("plugin/hooks/handler.py").toString());

        FileWriteHandler.Result result = handler.check(input, "test-session");

        requireThat(result.blocked(), "blocked").isFalse();
      }
      finally
      {
        TestUtils.runGit(mainDir, "worktree", "remove", "--force", worktreeDir.toString());
        TestUtils.deleteDirectoryRecursively(worktreeDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainDir);
    }
  }

  /**
   * Verifies that plugin files in a worktree directory are allowed.
   * <p>
   * Worktrees created by /cat:work have a git directory ending with {@code worktrees/<branch-name>}
   * and edits in them must be permitted.
   */
  @Test
  public void pluginFilesAllowedInWorktree() throws IOException
  {
    Path mainDir = TestUtils.createTempGitRepo("v2.1");
    try
    {
      try (JvmScope scope = new TestJvmScope(mainDir, mainDir))
      {
        Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
        Files.createDirectories(worktreesDir);

        Path worktreeDir = TestUtils.createWorktree(mainDir, worktreesDir, "2.1-test-task");
        try
        {
          EnforcePluginFileIsolation handler = new EnforcePluginFileIsolation();
          JsonMapper mapper = scope.getJsonMapper();
          ObjectNode input = mapper.createObjectNode();
          input.put("file_path", worktreeDir.resolve("plugin/hooks/test.py").toString());

          FileWriteHandler.Result result = handler.check(input, "test-session");

          requireThat(result.blocked(), "blocked").isFalse();
        }
        finally
        {
          TestUtils.runGit(mainDir, "worktree", "remove", "--force", worktreeDir.toString());
          TestUtils.deleteDirectoryRecursively(worktreeDir);
        }
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainDir);
    }
  }

  /**
   * Verifies that non-existent file paths trigger blocking when no issue worktree can be detected.
   * <p>
   * When the file and all its ancestors don't exist, findExistingAncestor returns
   * the original path, isIssueWorktree returns false, and the hook should block.
   */
  @Test
  public void nonExistentPluginFileIsBlocked() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      EnforcePluginFileIsolation handler = new EnforcePluginFileIsolation();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", "/nonexistent/path/plugin/test.py");

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Cannot edit source files outside of an issue worktree");
    }
  }

  /**
   * Verifies that empty file path is allowed.
   */
  @Test
  public void emptyFilePathIsAllowed() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      EnforcePluginFileIsolation handler = new EnforcePluginFileIsolation();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", "");

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that missing file_path field is allowed.
   */
  @Test
  public void missingFilePathIsAllowed() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      EnforcePluginFileIsolation handler = new EnforcePluginFileIsolation();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isFalse();
    }
  }

  /**
   * Verifies that client files in a regular repo (e.g., on main) are blocked.
   */
  @Test
  public void clientFilesBlockedOnMain() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope())
    {
      EnforcePluginFileIsolation handler = new EnforcePluginFileIsolation();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", tempDir.resolve("client/src/main/java/Test.java").toString());

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Cannot edit source files outside of an issue worktree");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that client files in a regular repo (e.g., on v2.1) are blocked.
   */
  @Test
  public void clientFilesBlockedOnV21() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope())
    {
      EnforcePluginFileIsolation handler = new EnforcePluginFileIsolation();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", tempDir.resolve("client/src/test/java/TestFoo.java").toString());

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Cannot edit source files outside of an issue worktree");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that client files in an issue worktree are allowed.
   * <p>
   * An issue worktree has a git directory ending with {@code worktrees/<branch-name>}.
   */
  @Test
  public void clientFilesAllowedOnTaskBranch() throws IOException
  {
    Path mainDir = TestUtils.createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope(mainDir, mainDir))
    {
      Path worktreesDir = scope.getCatWorkPath().resolve("worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainDir, worktreesDir, "2.1-fix-bug");
      try
      {
        EnforcePluginFileIsolation handler = new EnforcePluginFileIsolation();
        JsonMapper mapper = scope.getJsonMapper();
        ObjectNode input = mapper.createObjectNode();
        input.put("file_path", worktreeDir.resolve("client/src/main/java/Handler.java").toString());

        FileWriteHandler.Result result = handler.check(input, "test-session");

        requireThat(result.blocked(), "blocked").isFalse();
      }
      finally
      {
        TestUtils.runGit(mainDir, "worktree", "remove", "--force", worktreeDir.toString());
        TestUtils.deleteDirectoryRecursively(worktreeDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainDir);
    }
  }

  /**
   * Verifies that plugin files with 'plugin' as a subdirectory path component are detected and blocked
   * when not in an issue worktree.
   */
  @Test
  public void deepPluginPathIsDetected() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope())
    {
      EnforcePluginFileIsolation handler = new EnforcePluginFileIsolation();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", tempDir.resolve("some/nested/plugin/deep/file.py").toString());

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Cannot edit source files outside of an issue worktree");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }
}

