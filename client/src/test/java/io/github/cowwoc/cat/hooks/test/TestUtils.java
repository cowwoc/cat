/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.HookInput;
import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.util.FileUtils;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Shared test utilities for directory, file, and git operations.
 */
public final class TestUtils
{
  /**
   * Private constructor to prevent instantiation.
   */
  private TestUtils()
  {
    // Utility class
  }

  /**
   * Creates a HookInput with a dummy session ID for tests that need a valid input but don't depend on the
   * session ID value.
   * <p>
   * The dummy session ID ({@code "00000000-0000-0000-0000-000000000001"}) is intentionally distinct from the
   * session ID returned by {@code TestJvmScope.getClaudeSessionId()}, so that handlers which skip the current
   * session (e.g., {@link io.github.cowwoc.cat.hooks.session.SessionEndHandler}) treat it as an
   * external (non-current) session.
   *
   * @param scope the JVM scope
   * @return a HookInput with a hard-coded session ID that differs from any {@code TestJvmScope} session ID
   */
  static HookInput dummyInput(JvmScope scope)
  {
    return HookInput.readFrom(scope.getJsonMapper(), new java.io.ByteArrayInputStream(
      "{\"session_id\": \"00000000-0000-0000-0000-000000000001\"}".getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * Creates a temporary directory with the given prefix.
   *
   * @param prefix the prefix for the temporary directory name
   * @return the path to the created temporary directory
   * @throws NullPointerException if {@code prefix} is null
   */
  public static Path createTempDir(String prefix)
  {
    requireThat(prefix, "prefix").isNotNull();
    try
    {
      return Files.createTempDirectory(prefix);
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Creates a temporary project directory with a {@code .cat/issues} tree.
   *
   * @param prefix the prefix for the temporary directory name
   * @return the path to the created project directory
   * @throws NullPointerException if {@code prefix} is null
   */
  public static Path createTempCatProject(String prefix)
  {
    requireThat(prefix, "prefix").isNotNull();
    try
    {
      Path projectPath = Files.createTempDirectory(prefix);
      Files.createDirectories(projectPath.resolve(".cat").resolve("issues"));
      return projectPath;
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Builds a HookInput for bash command tests with a JvmScope.
   *
   * @param mapper the JSON mapper to use for constructing the input
   * @param command the bash command string
   * @param workingDirectory the working directory, or empty string if unavailable
   * @param sessionId the session ID
   * @return a HookInput with the given values and no tool result
   * @throws NullPointerException if {@code mapper}, {@code command}, {@code workingDirectory}, or
   *   {@code sessionId} are null
   */
  public static HookInput bashInput(JsonMapper mapper, String command, String workingDirectory,
    String sessionId)
  {
    return HookInput.forBash(mapper, command, workingDirectory, sessionId, null, null);
  }

  /**
   * Builds a HookInput for bash command tests with a JvmScope and tool result.
   *
   * @param mapper the JSON mapper to use for constructing the input
   * @param command the bash command string
   * @param workingDirectory the working directory, or empty string if unavailable
   * @param sessionId the session ID
   * @param toolResult the tool result node (for PostToolUse handlers)
   * @return a HookInput with the given values
   * @throws NullPointerException if {@code mapper}, {@code command}, {@code workingDirectory}, or
   *   {@code sessionId} are null
   */
  public static HookInput bashInput(JsonMapper mapper, String command, String workingDirectory,
    String sessionId, JsonNode toolResult)
  {
    return HookInput.forBash(mapper, command, workingDirectory, sessionId, null, toolResult);
  }

  /**
   * Builds a HookInput for bash command tests with a JvmScope and native agent ID.
   *
   * @param mapper the JSON mapper to use for constructing the input
   * @param command the bash command string
   * @param workingDirectory the working directory, or empty string if unavailable
   * @param sessionId the session ID
   * @param nativeAgentId the native (non-composite) agent ID, or null if not a subagent
   * @return a HookInput with the given values and no tool result
   * @throws NullPointerException if {@code mapper}, {@code command}, {@code workingDirectory}, or
   *   {@code sessionId} are null
   */
  public static HookInput bashInputWithAgentId(JsonMapper mapper, String command,
    String workingDirectory, String sessionId, String nativeAgentId)
  {
    return HookInput.forBash(mapper, command, workingDirectory, sessionId, nativeAgentId, null);
  }

  /**
   * Creates a temporary git repository with the specified initial branch name.
   * <p>
   * The repository is initialized with a single README.md commit so that worktrees can be created.
   *
   * @param branchName the branch name to use after initialization
   * @return the path to the created git repository
   * @throws IOException if repository creation fails
   */
  public static Path createTempGitRepo(String branchName) throws IOException
  {
    Path tempDir = Files.createTempDirectory("git-test-");

    runGit(tempDir, "init");
    runGit(tempDir, "config", "user.email", "test@example.com");
    runGit(tempDir, "config", "user.name", "Test User");

    Files.writeString(tempDir.resolve("README.md"), "test");
    runGit(tempDir, "add", "README.md");
    runGit(tempDir, "commit", "-m", "Initial commit");

    if (!branchName.equals("master") && !branchName.equals("main"))
      runGit(tempDir, "checkout", "-b", branchName);
    if (branchName.equals("main") && !getCurrentBranch(tempDir).equals("main"))
      runGit(tempDir, "branch", "-m", "main");

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
  public static Path createWorktree(Path mainRepo, Path worktreesDir, String branchName) throws IOException
  {
    Path worktreePath = worktreesDir.resolve(branchName);
    runGit(mainRepo, "worktree", "add", "-b", branchName, worktreePath.toString());
    return worktreePath;
  }

  /**
   * Runs a git command in the specified directory, discarding the output.
   *
   * @param directory the directory to run the command in
   * @param args the git command arguments
   * @throws WrappedCheckedException if the git command fails or is interrupted
   */
  public static void runGit(Path directory, String... args)
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
          line = reader.readLine();
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
   * Runs a git command in the specified directory and returns its output.
   *
   * @param directory the directory to run the command in
   * @param args the git command arguments
   * @return the trimmed output of the command
   * @throws IOException if the git command fails
   */
  public static String runGitCommandWithOutput(Path directory, String... args) throws IOException
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

      StringBuilder output = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
      {
        String line = reader.readLine();
        while (line != null)
        {
          if (output.length() > 0)
            output.append('\n');
          output.append(line);
          line = reader.readLine();
        }
      }

      int exitCode = process.waitFor();
      if (exitCode != 0)
        throw new IOException("Git command failed with exit code " + exitCode);
      return output.toString().strip();
    }
    catch (InterruptedException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Gets the current branch name for a directory.
   *
   * @param directory the directory to check
   * @return the branch name, or empty string if no branch output
   * @throws WrappedCheckedException if the git command fails or is interrupted
   */
  public static String getCurrentBranch(Path directory)
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

  /**
   * Deletes a directory and all its contents recursively.
   * <p>
   * Prints to stderr on failure so issues are visible in test output.
   *
   * @param directory the directory to delete
   */
  public static void deleteDirectoryRecursively(Path directory)
  {
    if (Files.notExists(directory))
      return;
    List<IOException> failures = new ArrayList<>();
    FileUtils.deleteDirectoryRecursively(directory, failures);
    for (IOException failure : failures)
      System.err.println("Failed to delete during cleanup: " + directory + " - " + failure.getMessage());
  }
}
