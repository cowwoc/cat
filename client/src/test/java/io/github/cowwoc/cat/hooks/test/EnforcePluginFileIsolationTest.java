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
import java.nio.file.Paths;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for EnforcePluginFileIsolation hook.
 * <p>
 * Tests verify that plugin/ and client/ source files are properly blocked outside of issue worktrees
 * and allowed inside issue worktrees (identified by the presence of a cat-base file).
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
   * Verifies that plugin files in a repo without cat-base (e.g., on main) are blocked.
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
   * Verifies that plugin files in a repo without cat-base (e.g., on v2.1) are blocked.
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
   * Verifies that plugin files in a repo without cat-base (e.g., on version branch v1.0) are blocked.
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
   * Verifies that plugin files in an issue worktree (with cat-base) are allowed.
   */
  @Test
  public void pluginFilesAllowedOnTaskBranch() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("v2.1-fix-bug");
    try
    {
      createCatBaseFile(tempDir);
      try (JvmScope scope = new TestJvmScope())
      {
        EnforcePluginFileIsolation handler = new EnforcePluginFileIsolation();
        JsonMapper mapper = scope.getJsonMapper();
        ObjectNode input = mapper.createObjectNode();
        input.put("file_path", tempDir.resolve("plugin/hooks/handler.py").toString());

        FileWriteHandler.Result result = handler.check(input, "test-session");

        requireThat(result.blocked(), "blocked").isFalse();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that plugin files in a worktree directory with cat-base are allowed.
   * <p>
   * This tests the core use case: worktrees created by /cat:work have a cat-base file
   * in their git directory and edits in them must be permitted.
   */
  @Test
  public void pluginFilesAllowedInWorktree() throws IOException
  {
    Path mainDir = TestUtils.createTempGitRepo("v2.1");
    try
    {
      Path worktreesDir = mainDir.resolve(".claude/cat/worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = TestUtils.createWorktree(mainDir, worktreesDir, "2.1-test-task");
      try
      {
        createCatBaseFile(worktreeDir);
        try (JvmScope scope = new TestJvmScope())
        {
          EnforcePluginFileIsolation handler = new EnforcePluginFileIsolation();
          JsonMapper mapper = scope.getJsonMapper();
          ObjectNode input = mapper.createObjectNode();
          input.put("file_path", worktreeDir.resolve("plugin/hooks/test.py").toString());

          FileWriteHandler.Result result = handler.check(input, "test-session");

          requireThat(result.blocked(), "blocked").isFalse();
        }
      }
      finally
      {
        TestUtils.runGitCommand(mainDir, "worktree", "remove", "--force", worktreeDir.toString());
        TestUtils.deleteDirectoryRecursively(worktreeDir);
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
   * Verifies that client files in a repo without cat-base (e.g., on main) are blocked.
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
   * Verifies that client files in a repo without cat-base (e.g., on v2.1) are blocked.
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
   * Verifies that client files in an issue worktree (with cat-base) are allowed.
   */
  @Test
  public void clientFilesAllowedOnTaskBranch() throws IOException
  {
    Path tempDir = TestUtils.createTempGitRepo("v2.1-fix-bug");
    try
    {
      createCatBaseFile(tempDir);
      try (JvmScope scope = new TestJvmScope())
      {
        EnforcePluginFileIsolation handler = new EnforcePluginFileIsolation();
        JsonMapper mapper = scope.getJsonMapper();
        ObjectNode input = mapper.createObjectNode();
        input.put("file_path", tempDir.resolve("client/src/main/java/Handler.java").toString());

        FileWriteHandler.Result result = handler.check(input, "test-session");

        requireThat(result.blocked(), "blocked").isFalse();
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
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

  /**
   * Creates a {@code cat-base} file in the git directory of the given repository.
   * <p>
   * This simulates an issue worktree created by {@code /cat:work}. The file content is
   * the base branch name (e.g., "v2.1").
   *
   * @param repoDir the repository directory
   * @throws IOException if the git command fails or file creation fails
   */
  private void createCatBaseFile(Path repoDir) throws IOException
  {
    String gitDirPath = TestUtils.runGitCommandWithOutput(repoDir, "rev-parse", "--git-dir");
    Path gitDir;
    if (Paths.get(gitDirPath).isAbsolute())
      gitDir = Paths.get(gitDirPath);
    else
      gitDir = repoDir.resolve(gitDirPath);
    Files.writeString(gitDir.resolve("cat-base"), "v2.1");
  }
}
