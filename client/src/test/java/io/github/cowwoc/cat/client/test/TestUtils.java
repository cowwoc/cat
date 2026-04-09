/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.ClaudeHook;
import io.github.cowwoc.cat.claude.tool.ClaudeTool;
import io.github.cowwoc.cat.claude.hook.JvmScope;
import io.github.cowwoc.cat.claude.hook.util.FileUtils;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

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
   * Creates a {@link TestClaudeHook} with a bash command payload using auto-generated temporary
   * directories for project, plugin, and config paths.
   * <p>
   * If {@code sessionId} is blank, the resulting JSON payload omits the {@code session_id} field,
   * causing {@link io.github.cowwoc.cat.claude.hook.AbstractClaudeHook} to throw
   * {@link IllegalArgumentException} on construction.
   *
   * @param command the bash command string
   * @param workingDirectory the working directory
   * @param sessionId the session ID, or blank to omit it from the payload
   * @return a TestClaudeHook with the given bash payload
   * @throws NullPointerException if {@code command}, {@code workingDirectory}, or {@code sessionId}
   *   are null
   */
  public static TestClaudeHook bashHook(String command, String workingDirectory, String sessionId)
  {
    requireThat(command, "command").isNotNull();
    requireThat(workingDirectory, "workingDirectory").isNotNull();
    requireThat(sessionId, "sessionId").isNotNull();
    Path projectPath = createTempDir("bash-hook-project-");
    Path pluginRoot = createTempDir("bash-hook-plugin-");
    Path claudeConfigPath = createTempDir("bash-hook-config-");
    // Copy emoji-widths.json so that DisplayUtils can initialize in hook handlers under test.
    // Maven sets user.dir to the client/ module directory during test execution.
    Path emojiWidths = Path.of(System.getProperty("user.dir")).resolve("../plugin/emoji-widths.json");
    try
    {
      Files.copy(emojiWidths, pluginRoot.resolve("emoji-widths.json"));
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
    return bashHook(command, workingDirectory, sessionId, projectPath, pluginRoot, claudeConfigPath);
  }

  /**
   * Creates a {@link TestClaudeHook} with a bash command payload using the specified paths.
   * <p>
   * If {@code sessionId} is blank, the resulting JSON payload omits the {@code session_id} field,
   * causing {@link io.github.cowwoc.cat.claude.hook.AbstractClaudeHook} to throw
   * {@link IllegalArgumentException} on construction.
   *
   * @param command the bash command string
   * @param workingDirectory the working directory
   * @param sessionId the session ID, or blank to omit it from the payload
   * @param projectPath the project directory path
   * @param pluginRoot the plugin root directory path
   * @param claudeConfigPath the Claude config directory path
   * @return a TestClaudeHook with the given bash payload
   * @throws NullPointerException if any parameter is null
   */
  public static TestClaudeHook bashHook(String command, String workingDirectory, String sessionId,
    Path projectPath, Path pluginRoot, Path claudeConfigPath)
  {
    requireThat(command, "command").isNotNull();
    requireThat(workingDirectory, "workingDirectory").isNotNull();
    requireThat(sessionId, "sessionId").isNotNull();
    requireThat(projectPath, "projectPath").isNotNull();
    requireThat(pluginRoot, "pluginRoot").isNotNull();
    requireThat(claudeConfigPath, "claudeConfigPath").isNotNull();
    JsonMapper mapper = new JsonMapper();
    ObjectNode root = mapper.createObjectNode();
    root.put("tool_name", "Bash");
    ObjectNode toolInput = mapper.createObjectNode();
    toolInput.put("command", command);
    root.set("tool_input", toolInput);
    root.put("cwd", workingDirectory);
    if (!sessionId.isBlank())
      root.put("session_id", sessionId);
    return new TestClaudeHook(root, projectPath, pluginRoot, claudeConfigPath);
  }

  /**
   * Creates a {@link TestClaudeHook} with a bash command payload and a native agent ID, using the
   * specified paths.
   * <p>
   * The native agent ID is embedded in the JSON payload under the {@code agent_id} field. This is used
   * to simulate subagent-owned worktree removal checks.
   * <p>
   * If {@code sessionId} is blank, the resulting JSON payload omits the {@code session_id} field,
   * causing {@link io.github.cowwoc.cat.claude.hook.AbstractClaudeHook} to throw
   * {@link IllegalArgumentException} on construction.
   *
   * @param command the bash command string
   * @param workingDirectory the working directory
   * @param sessionId the session ID, or blank to omit it from the payload
   * @param nativeAgentId the native (non-composite) agent ID to embed in the payload, or empty to omit
   * @param projectPath the project directory path
   * @param pluginRoot the plugin root directory path
   * @param claudeConfigPath the Claude config directory path
   * @return a TestClaudeHook with the given bash payload including agent_id
   * @throws NullPointerException if any parameter is null
   */
  public static TestClaudeHook bashHookWithAgentId(String command, String workingDirectory,
    String sessionId, String nativeAgentId, Path projectPath, Path pluginRoot, Path claudeConfigPath)
  {
    requireThat(command, "command").isNotNull();
    requireThat(workingDirectory, "workingDirectory").isNotNull();
    requireThat(sessionId, "sessionId").isNotNull();
    requireThat(nativeAgentId, "nativeAgentId").isNotNull();
    requireThat(projectPath, "projectPath").isNotNull();
    requireThat(pluginRoot, "pluginRoot").isNotNull();
    requireThat(claudeConfigPath, "claudeConfigPath").isNotNull();
    JsonMapper mapper = new JsonMapper();
    ObjectNode root = mapper.createObjectNode();
    root.put("tool_name", "Bash");
    ObjectNode toolInput = mapper.createObjectNode();
    toolInput.put("command", command);
    root.set("tool_input", toolInput);
    root.put("cwd", workingDirectory);
    if (!sessionId.isBlank())
      root.put("session_id", sessionId);
    if (!nativeAgentId.isBlank())
      root.put("agent_id", nativeAgentId);
    return new TestClaudeHook(root, projectPath, pluginRoot, claudeConfigPath);
  }

  /**
   * Creates a {@link TestClaudeHook} with a bash command payload, reusing the paths from an
   * existing {@link ClaudeTool}.
   * <p>
   * This overload is useful when the test already has an infrastructure scope (for setup operations
   * like creating lock files or worktree directories) and needs a separate hook scope that carries
   * the command and session ID for {@code BashHandler.check(ClaudeHook)}.
   *
   * @param command the bash command string
   * @param workingDirectory the working directory
   * @param sessionId the session ID
   * @param pathSource the existing scope whose project, plugin, and config paths to reuse
   * @return a TestClaudeHook with the given bash payload and paths from {@code pathSource}
   * @throws NullPointerException if any parameter is null
   */
  public static TestClaudeHook bashHook(String command, String workingDirectory, String sessionId,
    ClaudeTool pathSource)
  {
    requireThat(command, "command").isNotNull();
    requireThat(workingDirectory, "workingDirectory").isNotNull();
    requireThat(sessionId, "sessionId").isNotNull();
    requireThat(pathSource, "pathSource").isNotNull();
    return bashHook(command, workingDirectory, sessionId,
      pathSource.getProjectPath(), pathSource.getPluginRoot(), pathSource.getClaudeConfigPath());
  }

  /**
   * Creates a {@link TestClaudeHook} with a bash command payload, reusing paths from an existing
   * {@link ClaudeHook} scope.
   *
   * @param command the bash command string
   * @param workingDirectory the working directory
   * @param sessionId the session ID
   * @param pathSource the existing hook scope whose project, plugin, and config paths to reuse
   * @return a TestClaudeHook with the given bash payload and paths from {@code pathSource}
   * @throws NullPointerException if any parameter is null
   */
  public static TestClaudeHook bashHook(String command, String workingDirectory, String sessionId,
    ClaudeHook pathSource)
  {
    requireThat(command, "command").isNotNull();
    requireThat(workingDirectory, "workingDirectory").isNotNull();
    requireThat(sessionId, "sessionId").isNotNull();
    requireThat(pathSource, "pathSource").isNotNull();
    return bashHook(command, workingDirectory, sessionId,
      pathSource.getProjectPath(), pathSource.getPluginRoot(), pathSource.getClaudeConfigPath());
  }

  /**
   * Creates a {@link TestClaudeHook} with a bash command payload and a tool result, using
   * auto-generated temporary directories for project, plugin, and config paths.
   * <p>
   * The tool result is embedded in the JSON payload under the {@code tool_result} field. This is used
   * to test PostToolUse handlers that inspect the command output (e.g., exit code, stdout, stderr).
   * <p>
   * If {@code sessionId} is blank, the resulting JSON payload omits the {@code session_id} field,
   * causing {@link io.github.cowwoc.cat.claude.hook.AbstractClaudeHook} to throw
   * {@link IllegalArgumentException} on construction.
   *
   * @param command the bash command string
   * @param workingDirectory the working directory
   * @param sessionId the session ID, or blank to omit it from the payload
   * @param toolResult the tool result node to embed in the payload, or null to omit
   * @return a TestClaudeHook with the given bash payload including tool_result
   * @throws NullPointerException if {@code command}, {@code workingDirectory}, or {@code sessionId}
   *   are null
   */
  public static TestClaudeHook bashHookWithToolResult(String command, String workingDirectory,
    String sessionId, JsonNode toolResult)
  {
    requireThat(command, "command").isNotNull();
    requireThat(workingDirectory, "workingDirectory").isNotNull();
    requireThat(sessionId, "sessionId").isNotNull();
    JsonMapper mapper = new JsonMapper();
    ObjectNode root = mapper.createObjectNode();
    root.put("tool_name", "Bash");
    ObjectNode toolInput = mapper.createObjectNode();
    toolInput.put("command", command);
    root.set("tool_input", toolInput);
    root.put("cwd", workingDirectory);
    if (!sessionId.isBlank())
      root.put("session_id", sessionId);
    if (toolResult != null)
      root.set("tool_result", toolResult);
    Path projectPath = createTempDir("bash-hook-project-");
    Path pluginRoot = createTempDir("bash-hook-plugin-");
    Path claudeConfigPath = createTempDir("bash-hook-config-");
    return new TestClaudeHook(root, projectPath, pluginRoot, claudeConfigPath);
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

      StringJoiner output = new StringJoiner("\n");
      try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
      {
        String line = reader.readLine();
        while (line != null)
        {
          output.add(line);
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
   * Writes a lock file for the given session and issue ID using the scope's project CAT directory.
   *
   * @param scope the JVM scope providing the lock directory path
   * @param issueId the issue identifier (becomes the lock filename stem)
   * @param sessionId the session ID to embed in the lock content
   * @throws IOException if the lock file cannot be written
   * @throws NullPointerException if {@code scope}, {@code issueId}, or {@code sessionId} are null
   */
  public static void writeLockFile(JvmScope scope, String issueId, String sessionId) throws IOException
  {
    Path lockDir = scope.getCatWorkPath().resolve("locks");
    Files.createDirectories(lockDir);
    String content = """
      {"session_id": "%s", "worktrees": {}, "created_at": 1000000, "created_iso": "2026-01-01T00:00:00Z"}
      """.formatted(sessionId);
    Files.writeString(lockDir.resolve(issueId + ".lock"), content);
  }

  /**
   * Creates the worktree directory for the given issue ID using the scope's project CAT directory.
   *
   * @param scope the JVM scope providing the worktree base path
   * @param issueId the issue identifier
   * @return the created worktree directory path
   * @throws IOException if the directory cannot be created
   * @throws NullPointerException if {@code scope} or {@code issueId} are null
   */
  public static Path createWorktreeDir(JvmScope scope, String issueId) throws IOException
  {
    Path worktreeDir = scope.getCatWorkPath().resolve("worktrees").resolve(issueId);
    Files.createDirectories(worktreeDir);
    return worktreeDir;
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
