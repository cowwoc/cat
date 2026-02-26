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

  // RGB color codes for components
  private static final String WORKTREE_COLOR = "\033[38;2;255;255;255m";  // Bright White
  private static final String MODEL_COLOR = "\033[38;2;220;150;9m";       // Warm Gold
  private static final String TIME_COLOR = "\033[38;2;255;127;80m";       // Coral
  private static final String SESSION_COLOR = "\033[38;2;147;112;219m";   // Medium Purple

  // Separator color
  private static final String SEPARATOR_COLOR = "\033[38;2;64;64;64m";    // Dark Gray

  private static final int USAGE_BAR_SEGMENTS = 20;

  // Component emojis
  private static final String WORKTREE_EMOJI = "🌿";
  private static final String MODEL_EMOJI = "🤖";
  private static final String TIME_EMOJI = "⏰";
  private static final String SESSION_EMOJI = "🆔";
  private static final String USAGE_EMOJI = "📊";

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
    execute(inputStream, outputStream, null);
  }

  /**
   * Reads JSON from the input stream and writes the formatted statusline to the output stream.
   * <p>
   * On input parse failure or missing fields, defaults are used for graceful degradation.
   *
   * @param inputStream  the input stream providing JSON data
   * @param outputStream the output stream to write the statusline to
   * @param directory    the directory to use for git operations, or {@code null} to use the current working directory
   * @throws NullPointerException if {@code inputStream} or {@code outputStream} are null
   * @throws IOException          if an I/O error occurs reading from the input stream
   */
  public void execute(InputStream inputStream, PrintStream outputStream, Path directory) throws IOException
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
    String gitInfo = getGitInfo(directory);

    // Format duration
    String duration = formatDuration(totalDurationMs);

    // Session ID, sanitized against ANSI injection
    String displaySessionId = sanitizeForTerminal(sessionId);

    // Sanitize display name against ANSI injection
    displayName = sanitizeForTerminal(displayName);

    // Calculate scaled usage percentage
    int contextPct = Math.min(100, (usedPercentage * 1000) / 835);

    // Usage color
    String usageColor = getUsageColor(usedPercentage);

    // Usage bar
    String usageBar = createUsageBar(usedPercentage);

    // Format the statusline with component emojis and colors
    String statusline = WORKTREE_COLOR + WORKTREE_EMOJI + " " + gitInfo + RESET + " " +
      SEPARATOR_COLOR + "|" + RESET + " " +
      MODEL_COLOR + MODEL_EMOJI + " " + displayName + RESET + " " +
      SEPARATOR_COLOR + "|" + RESET + " " +
      TIME_COLOR + TIME_EMOJI + " " + duration + RESET + " " +
      SEPARATOR_COLOR + "|" + RESET + " " +
      SESSION_COLOR + SESSION_EMOJI + " " + displaySessionId + RESET + " " +
      SEPARATOR_COLOR + "|" + RESET + " " +
      usageColor + USAGE_EMOJI + " " + usageBar + " " + String.format("%3d%%", contextPct) + RESET;

    outputStream.println(statusline);
  }

  /**
   * Gets the repository directory name for the given directory (or current directory if null).
   * <p>
   * Returns "N/A" if not in a git repository.
   *
   * @param directory the directory to check, or {@code null} to use the current working directory
   * @return the git info string (repository directory name)
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

      // Get the top-level directory of the repository
      String topLevel;
      if (directory != null)
        topLevel = GitCommands.runGit(directory, "rev-parse", "--show-toplevel");
      else
        topLevel = GitCommands.runGit("rev-parse", "--show-toplevel");

      if (topLevel.isEmpty())
        return "N/A";

      // Extract the directory name (basename)
      int lastSlash = topLevel.lastIndexOf('/');
      String dirName;
      if (lastSlash >= 0)
        dirName = topLevel.substring(lastSlash + 1);
      else
        dirName = topLevel;

      if (dirName.isEmpty())
        return "N/A";

      return dirName;
    }
    catch (IOException _)
    {
      return "N/A";
    }
  }

  /**
   * Formats a duration in milliseconds to HH:MM format.
   *
   * @param milliseconds the duration in milliseconds
   * @return the formatted duration string in HH:MM format
   */
  private String formatDuration(long milliseconds)
  {
    long totalSeconds = milliseconds / 1000;
    long hours = totalSeconds / 3600;
    long minutes = (totalSeconds % 3600) / 60;

    return String.format("%02d:%02d", hours, minutes);
  }

  /**
   * Returns the RGB color code for the given usage percentage.
   * <p>
   * Colors scale from green (0%) to red (100%), with a non-linear scaling
   * where 83.5% raw becomes 100% scaled.
   *
   * @param percentage the usage percentage (0-100)
   * @return the RGB color escape code string
   */
  private String getUsageColor(int percentage)
  {
    // Scale: contextPct = (used% * 1000) / 835, clamped to 0-100
    int contextPct = Math.min(100, (percentage * 1000) / 835);

    if (contextPct >= 80)
    {
      // Red above 80%
      int red = 255;
      int green = Math.max(0, (int) ((100 - contextPct) * 255.0 / 50));
      return "\033[38;2;" + red + ";" + green + ";0m";
    }
    if (contextPct >= 50)
    {
      // Orange-red between 50% and 80%
      int red = 255;
      int green = (int) ((100 - contextPct) * 255.0 / 50);
      return "\033[38;2;" + red + ";" + green + ";0m";
    }
    // Green-yellow below 50%
    int red = (int) (contextPct * 255.0 / 50);
    int green = 255;
    return "\033[38;2;" + red + ";" + green + ";0m";
  }

  /**
   * Creates a 20-segment usage bar using block characters.
   * <p>
   * Filled segments use "█" and empty segments use "░".
   *
   * @param percentage the usage percentage (0-100)
   * @return the usage bar string
   */
  private String createUsageBar(int percentage)
  {
    // Scale: contextPct = (used% * 1000) / 835, clamped to 0-100
    int contextPct = Math.min(100, (percentage * 1000) / 835);
    int filled = (contextPct * USAGE_BAR_SEGMENTS) / 100;

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
   * This method protects against ANSI injection attacks where untrusted input (e.g., model name,
   * session ID from external sources) could contain escape sequences that manipulate terminal behavior.
   * Potential attacks include:
   * <ul>
   *   <li><b>Cursor movement:</b> Sequences like ESC[H move the cursor, allowing attackers to overwrite
   *       previous output and spoof system messages (e.g., hiding an error to make output look successful)</li>
   *   <li><b>Screen clearing:</b> Sequences like ESC[2J clear the screen, destroying legitimate output</li>
   *   <li><b>Text attribute injection:</b> Sequences like ESC[0m (RESET) or ESC[1m (BOLD) manipulate colors
   *       and text styling to hide malicious content or mislead users</li>
   *   <li><b>Terminal emulator exploits:</b> Control sequences can trigger vulnerabilities in specific
   *       terminal emulators (e.g., xterm, iTerm2)</li>
   *   <li><b>Output spoofing:</b> Attackers inject sequences to make malicious output appear to be
   *       legitimate system output by matching the statusline format</li>
   * </ul>
   * <p>
   * This method removes all C0 control characters (code points U+0000 to U+001F, except newline U+000A)
   * and DEL (U+007F). This blocks the ESC character (U+001B) which initiates most ANSI escape sequences,
   * plus other control characters with special terminal meanings (bell, backspace, tab, etc.).
   * <p>
   * Removed characters include:
   * <ul>
   *   <li>U+001B (ESC) - initiates ANSI escape sequences</li>
   *   <li>U+0000-U+0008, U+000B-U+001F - various C0 controls</li>
   *   <li>U+007F (DEL) - recognized as control by some terminals</li>
   * </ul>
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
