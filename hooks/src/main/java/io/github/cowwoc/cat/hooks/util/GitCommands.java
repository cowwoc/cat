package io.github.cowwoc.cat.hooks.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Utility class for executing Git commands.
 * <p>
 * Provides common Git operations used across multiple bash handlers.
 * All methods use Locale.ROOT for case conversions. Methods that detect
 * branch or commit state throw {@code IOException} on failure to enforce
 * fail-fast behavior. Callers decide how to handle failures.
 */
public final class GitCommands
{
  /**
   * Private constructor to prevent instantiation.
   */
  private GitCommands()
  {
  }

  /**
   * Gets the message of the latest commit.
   *
   * @return the commit message, or empty string if no output
   * @throws IOException if the git command fails
   */
  public static String getLatestCommitMessage() throws IOException
  {
    return runGitCommand("log", "-1", "--format=%B");
  }

  /**
   * Gets the short hash of the latest commit.
   *
   * @return the commit hash (e.g., "abc1234")
   * @throws IOException if the git command fails or returns no output
   */
  public static String getLatestCommitHash() throws IOException
  {
    return runGitCommandSingleLine("log", "-1", "--format=%h");
  }

  /**
   * Gets the list of files changed in the latest commit.
   *
   * @return newline-separated file paths, or empty string if no output
   * @throws IOException if the git command fails
   */
  public static String getLatestCommitFiles() throws IOException
  {
    return runGitCommand("diff-tree", "--no-commit-id", "--name-only", "-r", "HEAD");
  }

  /**
   * Gets the list of staged files.
   *
   * @return list of staged file paths
   * @throws IOException if the git command fails
   */
  public static List<String> getStagedFiles() throws IOException
  {
    List<String> files = new ArrayList<>();
    ProcessBuilder pb = new ProcessBuilder("git", "diff", "--cached", "--name-only");
    pb.redirectErrorStream(true);
    Process process = pb.start();
    try (BufferedReader reader = new BufferedReader(
      new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
    {
      readLinesIntoList(reader, files);
    }
    int exitCode;
    try
    {
      exitCode = process.waitFor();
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while waiting for git diff --cached", e);
    }
    if (exitCode != 0)
      throw new IOException("git diff --cached --name-only failed with exit code " + exitCode);
    return files;
  }

  /**
   * Gets the current branch name.
   *
   * @return the branch name
   * @throws IOException if the git command fails or returns no output
   */
  public static String getCurrentBranch() throws IOException
  {
    return runGitCommandSingleLine("branch", "--show-current");
  }

  /**
   * Gets the current branch name for a specific directory.
   *
   * @param directory the directory to check
   * @return the branch name
   * @throws IllegalArgumentException if directory does not exist or is not a directory
   * @throws IOException if the git command fails or returns no output
   */
  public static String getCurrentBranch(String directory) throws IOException
  {
    File dir = new File(directory);
    if (!dir.isDirectory())
      throw new IllegalArgumentException("Not a directory: " + directory);
    ProcessBuilder pb = new ProcessBuilder("git", "-C", directory, "branch", "--show-current");
    pb.redirectErrorStream(true);
    Process process = pb.start();
    String branch;
    try (BufferedReader reader = new BufferedReader(
      new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
    {
      branch = reader.readLine();
    }
    int exitCode;
    try
    {
      exitCode = process.waitFor();
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while waiting for git command", e);
    }
    if (exitCode != 0)
      throw new IOException("git branch --show-current failed with exit code " + exitCode +
        " in directory: " + directory);
    if (branch == null || branch.isEmpty())
      throw new IOException("git branch --show-current returned no output in directory: " + directory);
    return branch.trim();
  }

  /**
   * Checks if the current directory is the main worktree.
   *
   * @return true if this is the main worktree, false otherwise
   * @throws IOException if the git command fails or returns no output
   */
  public static boolean isMainWorktree() throws IOException
  {
    String gitCommon = runGitCommandSingleLine("rev-parse", "--git-common-dir");
    String gitDir = runGitCommandSingleLine("rev-parse", "--git-dir");
    return gitCommon.equals(gitDir) || ".git".equals(gitCommon);
  }

  /**
   * Gets the commit hash for a given reference.
   *
   * @param ref the reference (branch name, tag, etc.)
   * @return the short commit hash
   * @throws IOException if the git command fails or returns no output
   */
  public static String getCommitHash(String ref) throws IOException
  {
    return runGitCommandSingleLine("rev-parse", "--short", ref);
  }

  /**
   * Runs a git command and returns all output lines joined with newlines.
   *
   * @param args the git command arguments
   * @return the command output trimmed
   * @throws IOException if the git command fails
   */
  private static String runGitCommand(String... args) throws IOException
  {
    String[] command = new String[args.length + 1];
    command[0] = "git";
    System.arraycopy(args, 0, command, 1, args.length);
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.redirectErrorStream(true);
    Process process = pb.start();
    StringBuilder output = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(
      new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
    {
      readAllLines(reader, output);
    }
    int exitCode;
    try
    {
      exitCode = process.waitFor();
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while waiting for git command: " +
        String.join(" ", command), e);
    }
    if (exitCode != 0)
      throw new IOException("git command failed with exit code " + exitCode + ": " +
        String.join(" ", command));
    return output.toString().trim();
  }

  /**
   * Runs a git command and returns only the first line of output.
   *
   * @param args the git command arguments
   * @return the first line of output trimmed
   * @throws IOException if the git command fails, is interrupted, or returns no output
   */
  private static String runGitCommandSingleLine(String... args) throws IOException
  {
    String[] command = new String[args.length + 1];
    command[0] = "git";
    System.arraycopy(args, 0, command, 1, args.length);
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.redirectErrorStream(true);
    Process process = pb.start();
    String line;
    try (BufferedReader reader = new BufferedReader(
      new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
    {
      line = reader.readLine();
    }
    int exitCode;
    try
    {
      exitCode = process.waitFor();
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while waiting for git command: " +
        String.join(" ", command), e);
    }
    if (exitCode != 0)
      throw new IOException("git command failed with exit code " + exitCode + ": " +
        String.join(" ", command));
    if (line == null || line.isEmpty())
      throw new IOException("git command returned no output: " + String.join(" ", command));
    return line.trim();
  }

  /**
   * Reads all lines from a reader into a StringBuilder.
   *
   * @param reader the reader to read from
   * @param output the StringBuilder to append to
   * @throws IOException if reading fails
   */
  private static void readAllLines(BufferedReader reader, StringBuilder output) throws IOException
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

  /**
   * Reads all non-empty lines from a reader into a list.
   *
   * @param reader the reader to read from
   * @param lines the list to add lines to
   * @throws IOException if reading fails
   */
  private static void readLinesIntoList(BufferedReader reader, List<String> lines) throws IOException
  {
    String line = reader.readLine();
    while (line != null)
    {
      String trimmed = line.trim();
      if (!trimmed.isEmpty())
        lines.add(trimmed);
      line = reader.readLine();
    }
  }

  /**
   * Gets the git directory path.
   *
   * @return the git directory
   * @throws IOException if the git command fails or returns no output
   */
  public static Path getGitDir() throws IOException
  {
    String gitDirPath = runGitCommandSingleLine("rev-parse", "--git-dir");
    return Paths.get(gitDirPath);
  }

  /**
   * Converts a string to lowercase using Locale.ROOT.
   * <p>
   * This avoids locale-specific issues with case conversion.
   *
   * @param value the string to convert
   * @return the lowercase string
   */
  public static String toLowerCase(String value)
  {
    return value.toLowerCase(Locale.ROOT);
  }
}
