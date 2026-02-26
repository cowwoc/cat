/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.MainJvmScope;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Generates a formatted statusline for Claude Code.
 * <p>
 * Reads JSON from an input stream and produces an ANSI-formatted statusline containing:
 * <ul>
 *   <li>Git worktree/branch name (or "N/A" if not in a git repo)</li>
 *   <li>Model display name</li>
 *   <li>Session duration (formatted as human-readable time)</li>
 *   <li>Session ID (first 8 characters)</li>
 *   <li>Color-coded context usage bar (green below 50%, yellow 50-80%, red above 80%)</li>
 * </ul>
 * <p>
 * Input JSON fields (from Claude Code):
 * <ul>
 *   <li>{@code model.display_name} - model display name (defaults to "unknown")</li>
 *   <li>{@code session_id} - session ID (defaults to "unknown")</li>
 *   <li>{@code cost.total_duration_ms} - session duration in milliseconds (defaults to 0)</li>
 *   <li>{@code context_window.used_percentage} - context usage percentage 0-100 (defaults to 0, clamped to 100)</li>
 * </ul>
 */
public final class StatuslineCommand
{
  // ANSI color codes
  private static final String RESET = "\033[0m";
  private static final String BOLD = "\033[1m";
  private static final String DIM = "\033[2m";
  private static final String GREEN = "\033[32m";
  private static final String YELLOW = "\033[33m";
  private static final String RED = "\033[31m";
  private static final String CYAN = "\033[36m";
  private static final String GRAY = "\033[90m";

  private static final int USAGE_BAR_SEGMENTS = 10;

  private final JsonMapper mapper;

  /**
   * Creates a new StatuslineCommand.
   *
   * @param scope the JVM scope for accessing shared services
   * @throws NullPointerException if {@code scope} is null
   */
  public StatuslineCommand(JvmScope scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.mapper = scope.getJsonMapper();
  }

  /**
   * Reads JSON from the input stream and writes the formatted statusline to the output stream.
   * <p>
   * On input parse failure or missing fields, defaults are used for graceful degradation.
   *
   * @param inputStream  the input stream providing JSON data
   * @param outputStream the output stream to write the statusline to
   * @throws NullPointerException if {@code inputStream} or {@code outputStream} are null
   * @throws IOException          if an I/O error occurs reading from the input stream
   */
  public void execute(InputStream inputStream, PrintStream outputStream) throws IOException
  {
    requireThat(inputStream, "inputStream").isNotNull();
    requireThat(outputStream, "outputStream").isNotNull();

    String jsonInput = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

    String displayName = "unknown";
    String sessionId = "unknown";
    long totalDurationMs = 0;
    int usedPercentage = 0;

    try
    {
      JsonNode root = mapper.readTree(jsonInput);

      JsonNode modelNode = root.get("model");
      if (modelNode != null && !modelNode.isNull())
      {
        JsonNode displayNameNode = modelNode.get("display_name");
        if (displayNameNode != null && !displayNameNode.isNull())
          displayName = displayNameNode.asString();
      }

      JsonNode sessionIdNode = root.get("session_id");
      if (sessionIdNode != null && !sessionIdNode.isNull())
        sessionId = sessionIdNode.asString();

      JsonNode costNode = root.get("cost");
      if (costNode != null && !costNode.isNull())
      {
        JsonNode durationNode = costNode.get("total_duration_ms");
        if (durationNode != null && !durationNode.isNull() && durationNode.canConvertToLong())
          totalDurationMs = durationNode.longValue();
      }

      JsonNode contextNode = root.get("context_window");
      if (contextNode != null && !contextNode.isNull())
      {
        JsonNode percentageNode = contextNode.get("used_percentage");
        if (percentageNode != null && !percentageNode.isNull() && percentageNode.canConvertToInt())
          usedPercentage = percentageNode.intValue();
      }
    }
    catch (JacksonException _)
    {
      // Use defaults on parse failure (graceful degradation)
    }

    // Clamp values to valid ranges
    if (totalDurationMs < 0)
      totalDurationMs = 0;
    if (usedPercentage < 0)
      usedPercentage = 0;
    if (usedPercentage > 100)
      usedPercentage = 100;

    // Get git info (graceful degradation if not in git repo)
    String gitInfo = getGitInfo(null);

    // Format duration
    String duration = formatDuration(totalDurationMs);

    // Session ID (first 8 chars), sanitized against ANSI injection
    String shortSessionId;
    if (sessionId.length() > 8)
      shortSessionId = sessionId.substring(0, 8);
    else
      shortSessionId = sessionId;
    shortSessionId = sanitizeForTerminal(shortSessionId);

    // Sanitize display name against ANSI injection
    displayName = sanitizeForTerminal(displayName);

    // Usage color
    String usageColor = getUsageColor(usedPercentage);

    // Usage bar
    String usageBar = createUsageBar(usedPercentage);

    outputStream.println(CYAN + BOLD + gitInfo + RESET + " " + GRAY + "|" + RESET + " " +
      DIM + displayName + RESET + " " + GRAY + "|" + RESET + " " +
      DIM + "⏱ " + duration + RESET + " " + GRAY + "|" + RESET + " " +
      DIM + "📋 " + shortSessionId + RESET + " " + GRAY + "|" + RESET + " " +
      usageColor + usageBar + " " + usedPercentage + "%" + RESET);
  }

  /**
   * Gets the git branch or worktree name for the given directory (or current directory if null).
   * <p>
   * Returns "N/A" if not in a git repository.
   *
   * @param directory the directory to check, or {@code null} to use the current working directory
   * @return the git info string
   */
  String getGitInfo(Path directory)
  {
    try
    {
      // Check if we are inside a git work tree
      String checkOutput;
      if (directory != null)
        checkOutput = GitCommands.runGit(directory, "rev-parse", "--is-inside-work-tree");
      else
        checkOutput = GitCommands.runGit("rev-parse", "--is-inside-work-tree");
      if (!"true".equals(checkOutput))
        return "N/A";

      // Get current branch
      String branch;
      if (directory != null)
        branch = GitCommands.runGit(directory, "branch", "--show-current");
      else
        branch = GitCommands.runGit("branch", "--show-current");
      if (branch.isEmpty())
        branch = "detached";

      // Check if we're in a worktree (git-dir contains "worktrees")
      String gitDir;
      if (directory != null)
        gitDir = GitCommands.runGit(directory, "rev-parse", "--git-dir");
      else
        gitDir = GitCommands.runGit("rev-parse", "--git-dir");

      if (gitDir.contains("worktrees"))
      {
        // Get worktree name from top-level directory
        String topLevel;
        if (directory != null)
          topLevel = GitCommands.runGit(directory, "rev-parse", "--show-toplevel");
        else
          topLevel = GitCommands.runGit("rev-parse", "--show-toplevel");
        if (!topLevel.isEmpty())
        {
          int lastSlash = topLevel.lastIndexOf('/');
          String worktreeName;
          if (lastSlash >= 0)
            worktreeName = topLevel.substring(lastSlash + 1);
          else
            worktreeName = topLevel;
          if (!worktreeName.isEmpty())
            return worktreeName;
        }
      }

      return branch;
    }
    catch (IOException _)
    {
      return "N/A";
    }
  }

  /**
   * Formats a duration in milliseconds to a human-readable string.
   * <p>
   * Format examples: "45s", "3m30s", "1h2m".
   *
   * @param milliseconds the duration in milliseconds
   * @return the formatted duration string
   */
  private String formatDuration(long milliseconds)
  {
    long seconds = milliseconds / 1000;
    long minutes = seconds / 60;
    long hours = minutes / 60;

    if (hours > 0)
      return hours + "h" + (minutes % 60) + "m";
    if (minutes > 0)
      return minutes + "m" + (seconds % 60) + "s";
    return seconds + "s";
  }

  /**
   * Returns the ANSI color code for the given usage percentage.
   * <p>
   * Red above 80%, yellow between 50% and 80%, green below or equal to 50%.
   *
   * @param percentage the usage percentage (0-100)
   * @return the ANSI color escape code string
   */
  private String getUsageColor(int percentage)
  {
    if (percentage > 80)
      return RED;
    if (percentage > 50)
      return YELLOW;
    return GREEN;
  }

  /**
   * Creates a 10-segment usage bar using block characters.
   * <p>
   * Filled segments use "█" and empty segments use "░".
   *
   * @param percentage the usage percentage (0-100)
   * @return the usage bar string
   */
  private String createUsageBar(int percentage)
  {
    int filled = percentage / USAGE_BAR_SEGMENTS;
    StringBuilder bar = new StringBuilder(USAGE_BAR_SEGMENTS);
    for (int i = 0; i < USAGE_BAR_SEGMENTS; ++i)
    {
      if (i < filled)
        bar.append('█');
      else
        bar.append('░');
    }
    return bar.toString();
  }

  /**
   * Strips control characters from a string to prevent ANSI injection in terminal output.
   * <p>
   * Removes characters with code points below U+0020 (except newline U+000A) and U+007F (DEL).
   *
   * @param value the string to sanitize
   * @return the sanitized string with control characters removed
   */
  private String sanitizeForTerminal(String value)
  {
    StringBuilder result = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); ++i)
    {
      char c = value.charAt(i);
      if (c == '\n' || (c >= 0x20 && c != 0x7F))
        result.append(c);
    }
    return result.toString();
  }

  /**
   * Main entry point. Reads JSON from stdin and writes the statusline to stdout.
   *
   * @param args command-line arguments (unused)
   */
  public static void main(String[] args)
  {
    try (JvmScope scope = new MainJvmScope())
    {
      StatuslineCommand cmd = new StatuslineCommand(scope);
      try
      {
        cmd.execute(System.in, System.out);
      }
      catch (IOException e)
      {
        System.err.println("Error generating statusline: " + e.getMessage());
        System.exit(1);
      }
    }
  }
}
