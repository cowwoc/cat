package io.github.cowwoc.cat.hooks.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Utility class for executing Git commands.
 *
 * <p>Provides common Git operations used across multiple bash handlers.
 * All methods use Locale.ROOT for case conversions and handle errors gracefully
 * by returning empty strings or empty lists.</p>
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
   * @return the commit message, or empty string if unavailable
   */
  public static String getLatestCommitMessage()
  {
    return runGitCommand("log", "-1", "--format=%B");
  }

  /**
   * Gets the short hash of the latest commit.
   *
   * @return the commit hash (e.g., "abc1234"), or "HEAD" if unavailable
   */
  public static String getLatestCommitHash()
  {
    String hash = runGitCommandSingleLine("log", "-1", "--format=%h");
    if (hash.isEmpty())
      return "HEAD";
    return hash;
  }

  /**
   * Gets the list of files changed in the latest commit.
   *
   * @return newline-separated file paths, or empty string if unavailable
   */
  public static String getLatestCommitFiles()
  {
    return runGitCommand("diff-tree", "--no-commit-id", "--name-only", "-r", "HEAD");
  }

  /**
   * Gets the list of staged files.
   *
   * @return list of staged file paths
   */
  public static List<String> getStagedFiles()
  {
    List<String> files = new ArrayList<>();
    try
    {
      ProcessBuilder pb = new ProcessBuilder("git", "diff", "--cached", "--name-only");
      pb.redirectErrorStream(true);
      Process process = pb.start();
      try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
      {
        readLinesIntoList(reader, files);
      }
      process.waitFor();
    }
    catch (Exception _)
    {
      // Return empty list - git command failures are expected in some contexts
    }
    return files;
  }

  /**
   * Gets the current branch name.
   *
   * @return the branch name, or empty string if unavailable
   */
  public static String getCurrentBranch()
  {
    return runGitCommandSingleLine("branch", "--show-current");
  }

  /**
   * Gets the current branch name for a specific directory.
   *
   * @param directory the directory to check
   * @return the branch name, or empty string if unavailable
   */
  public static String getCurrentBranch(String directory)
  {
    File dir = new File(directory);
    if (!dir.isDirectory())
      return "";
    try
    {
      ProcessBuilder pb = new ProcessBuilder("git", "-C", directory, "branch", "--show-current");
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
        return branch.trim();
    }
    catch (Exception _)
    {
      // Return empty string - git command failures are expected in some contexts
    }
    return "";
  }

  /**
   * Checks if the current directory is the main worktree.
   *
   * @return true if this is the main worktree, false otherwise
   */
  public static boolean isMainWorktree()
  {
    try
    {
      String gitCommon = runGitCommandSingleLine("rev-parse", "--git-common-dir");
      String gitDir = runGitCommandSingleLine("rev-parse", "--git-dir");
      if (!gitCommon.isEmpty() && !gitDir.isEmpty())
        return gitCommon.equals(gitDir) || ".git".equals(gitCommon);
    }
    catch (Exception _)
    {
      // Return false - git command failures are expected in some contexts
    }
    return false;
  }

  /**
   * Gets the commit hash for a given reference.
   *
   * @param ref the reference (branch name, tag, etc.)
   * @return the short commit hash, or empty string if unavailable
   */
  public static String getCommitHash(String ref)
  {
    return runGitCommandSingleLine("rev-parse", "--short", ref);
  }

  /**
   * Runs a git command and returns all output lines joined with newlines.
   *
   * @param args the git command arguments
   * @return the command output trimmed, or empty string on error
   */
  private static String runGitCommand(String... args)
  {
    try
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
      process.waitFor();
      return output.toString().trim();
    }
    catch (Exception _)
    {
      return "";
    }
  }

  /**
   * Runs a git command and returns only the first line of output.
   *
   * @param args the git command arguments
   * @return the first line of output trimmed, or empty string on error
   */
  private static String runGitCommandSingleLine(String... args)
  {
    try
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
      process.waitFor();
      if (line != null)
        return line.trim();
    }
    catch (Exception _)
    {
      // Return empty string - git command failures are expected in some contexts
    }
    return "";
  }

  /**
   * Reads all lines from a reader into a StringBuilder.
   *
   * @param reader the reader to read from
   * @param output the StringBuilder to append to
   * @throws Exception if reading fails
   */
  private static void readAllLines(BufferedReader reader, StringBuilder output) throws Exception
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
   * @throws Exception if reading fails
   */
  private static void readLinesIntoList(BufferedReader reader, List<String> lines) throws Exception
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
   * Converts a string to lowercase using Locale.ROOT.
   *
   * <p>This avoids locale-specific issues with case conversion.</p>
   *
   * @param value the string to convert
   * @return the lowercase string
   */
  public static String toLowerCase(String value)
  {
    return value.toLowerCase(Locale.ROOT);
  }
}
