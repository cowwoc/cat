package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.FileWriteHandler;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.write.EnforcePluginFileIsolation;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for EnforcePluginFileIsolation hook.
 * <p>
 * Tests verify that plugin files are properly blocked on protected branches
 * and allowed on task branches and in worktrees.
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
   * Verifies that plugin files on protected branch 'main' are blocked.
   */
  @Test
  public void pluginFilesBlockedOnMain() throws IOException
  {
    Path tempDir = createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope())
    {
      EnforcePluginFileIsolation handler = new EnforcePluginFileIsolation();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", tempDir.resolve("plugin/hooks/test.py").toString());

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Cannot edit plugin files on protected branch 'main'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that plugin files on protected branch 'v2.1' are blocked.
   */
  @Test
  public void pluginFilesBlockedOnV21() throws IOException
  {
    Path tempDir = createTempGitRepo("v2.1");
    try (JvmScope scope = new TestJvmScope())
    {
      EnforcePluginFileIsolation handler = new EnforcePluginFileIsolation();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", tempDir.resolve("plugin/skills/test.md").toString());

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Cannot edit plugin files on protected branch 'v2.1'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that plugin files on version branch 'v1.0' are blocked.
   */
  @Test
  public void pluginFilesBlockedOnVersionBranch() throws IOException
  {
    Path tempDir = createTempGitRepo("v1.0");
    try (JvmScope scope = new TestJvmScope())
    {
      EnforcePluginFileIsolation handler = new EnforcePluginFileIsolation();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", tempDir.resolve("plugin/commands/test.md").toString());

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Cannot edit plugin files on protected branch 'v1.0'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that plugin files on task branch are allowed.
   */
  @Test
  public void pluginFilesAllowedOnTaskBranch() throws IOException
  {
    Path tempDir = createTempGitRepo("v2.1-fix-bug");
    try (JvmScope scope = new TestJvmScope())
    {
      EnforcePluginFileIsolation handler = new EnforcePluginFileIsolation();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", tempDir.resolve("plugin/hooks/handler.py").toString());

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isFalse();
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Verifies that plugin files in a worktree directory on task branch are allowed.
   * <p>
   * This tests the bug fix: before the fix, the hook would check the parent workspace
   * branch (v2.1) instead of the worktree's own branch (2.1-task-name), incorrectly blocking.
   */
  @Test
  public void pluginFilesAllowedInWorktree() throws IOException
  {
    Path mainDir = createTempGitRepo("v2.1");
    try
    {
      Path worktreesDir = mainDir.resolve(".claude/cat/worktrees");
      Files.createDirectories(worktreesDir);

      Path worktreeDir = createWorktree(mainDir, worktreesDir, "2.1-test-task");
      try (JvmScope scope = new TestJvmScope())
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
        runGitCommand(mainDir, "worktree", "remove", "--force", worktreeDir.toString());
        TestUtils.deleteDirectoryRecursively(worktreeDir);
      }
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(mainDir);
    }
  }

  /**
   * Verifies that non-existent file paths trigger branch detection failure.
   * <p>
   * When the file and all its ancestors don't exist, findExistingAncestor returns
   * the original path, getCurrentBranch returns empty, and the hook should block.
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
      requireThat(result.reason(), "reason").contains("Cannot determine branch");
      requireThat(result.reason(), "reason").contains("Branch Detection Failed");
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
   * Verifies that plugin files with 'plugin' as a subdirectory path component are detected.
   */
  @Test
  public void deepPluginPathIsDetected() throws IOException
  {
    Path tempDir = createTempGitRepo("main");
    try (JvmScope scope = new TestJvmScope())
    {
      EnforcePluginFileIsolation handler = new EnforcePluginFileIsolation();
      JsonMapper mapper = scope.getJsonMapper();
      ObjectNode input = mapper.createObjectNode();
      input.put("file_path", tempDir.resolve("some/nested/plugin/deep/file.py").toString());

      FileWriteHandler.Result result = handler.check(input, "test-session");

      requireThat(result.blocked(), "blocked").isTrue();
      requireThat(result.reason(), "reason").contains("Cannot edit plugin files on protected branch 'main'");
    }
    finally
    {
      TestUtils.deleteDirectoryRecursively(tempDir);
    }
  }

  /**
   * Creates a temporary git repository with the specified branch.
   *
   * @param branchName the branch name to create
   * @return the path to the created git repository
   * @throws IOException if repository creation fails
   */
  private Path createTempGitRepo(String branchName) throws IOException
  {
    Path tempDir = Files.createTempDirectory("git-test-");

    runGitCommand(tempDir, "init");
    runGitCommand(tempDir, "config", "user.email", "test@example.com");
    runGitCommand(tempDir, "config", "user.name", "Test User");

    Files.writeString(tempDir.resolve("README.md"), "test");
    runGitCommand(tempDir, "add", "README.md");
    runGitCommand(tempDir, "commit", "-m", "Initial commit");

    if (!branchName.equals("master") && !branchName.equals("main"))
    {
      runGitCommand(tempDir, "checkout", "-b", branchName);
    }
    if (branchName.equals("main") && !getCurrentBranch(tempDir).equals("main"))
    {
      runGitCommand(tempDir, "branch", "-m", "main");
    }

    return tempDir;
  }

  /**
   * Creates a git worktree in the specified directory.
   *
   * @param mainRepo the main repository path
   * @param worktreesDir the worktrees parent directory
   * @param branchName the branch name for the worktree
   * @return the path to the created worktree
   * @throws IOException if worktree creation fails
   */
  private Path createWorktree(Path mainRepo, Path worktreesDir, String branchName) throws IOException
  {
    Path worktreePath = worktreesDir.resolve(branchName);
    runGitCommand(mainRepo, "worktree", "add", "-b", branchName, worktreePath.toString());
    return worktreePath;
  }

  /**
   * Runs a git command in the specified directory.
   *
   * @param directory the directory to run the command in
   * @param args the git command arguments
   */
  private void runGitCommand(Path directory, String... args)
  {
    try
    {
      String[] command = new String[args.length + 3];
      command[0] = "git";
      command[1] = "-C";
      command[2] = directory.toString();
      System.arraycopy(args, 0, command, 3, args.length);

      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(true);
      Process process = pb.start();

      try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
      {
        String line = reader.readLine();
        while (line != null)
        {
          line = reader.readLine();
        }
      }

      int exitCode = process.waitFor();
      if (exitCode != 0)
        throw new IOException("Git command failed with exit code " + exitCode);
    }
    catch (IOException | InterruptedException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Gets the current branch name for a directory.
   *
   * @param directory the directory to check
   * @return the branch name
   */
  private String getCurrentBranch(Path directory)
  {
    try
    {
      ProcessBuilder pb = new ProcessBuilder("git", "-C", directory.toString(), "branch", "--show-current");
      pb.redirectErrorStream(true);
      Process process = pb.start();

      String branch;
      try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
      {
        branch = reader.readLine();
      }

      process.waitFor();
      if (branch != null)
        return branch.strip();
      return "";
    }
    catch (IOException | InterruptedException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }
}
