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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringJoiner;

/**
 * Generates a formatted statusline for Claude Code.
 * <p>
 * Reads JSON from an input stream and produces an ANSI-formatted statusline containing:
 * <ul>
 *   <li>Active CAT issue ID (or absent if no issue is locked for this session)</li>
 *   <li>Model display name</li>
 *   <li>Session duration (formatted as human-readable time)</li>
 *   <li>Session ID (full UUID)</li>
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

  private final JvmScope scope;
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
    this.scope = scope;
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
    execute(inputStream, outputStream, scope.getCatWorkPath().resolve("locks"));
  }

  /**
   * Reads JSON from the input stream and writes the formatted statusline to the output stream.
   * <p>
   * On input parse failure or missing fields, defaults are used for graceful degradation.
   *
   * @param inputStream  the input stream providing JSON data
   * @param outputStream the output stream to write the statusline to
   * @param lockDir      the locks directory containing {@code .lock} files
   * @throws NullPointerException if {@code inputStream}, {@code outputStream}, or {@code lockDir} are null
   * @throws IOException          if an I/O error occurs reading from the input stream
   */
  public void execute(InputStream inputStream, PrintStream outputStream, Path lockDir) throws IOException
  {
    requireThat(inputStream, "inputStream").isNotNull();
    requireThat(outputStream, "outputStream").isNotNull();
    requireThat(lockDir, "lockDir").isNotNull();

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

    // Get active issue for the current session
    String activeIssue = getActiveIssue(sessionId, lockDir);

    // Format duration
    String duration = formatDuration(totalDurationMs);

    // Session ID, with control characters removed to prevent ANSI injection
    String displaySessionId = removeControlCharacters(sessionId);

    // Remove control characters from display name to prevent ANSI injection
    displayName = removeControlCharacters(displayName);

    // Calculate scaled usage percentage
    int contextPct = Math.min(100, (usedPercentage * 1000) / 835);

    // Usage color
    String usageColor = getUsageColor(usedPercentage);

    // Usage bar
    String usageBar = createUsageBar(usedPercentage);

    // Build statusline segments using StringJoiner with separator delimiter
    String separator = " " + SEPARATOR_COLOR + "|" + RESET + " ";
    StringJoiner joiner = new StringJoiner(separator);

    // Conditionally prepend the worktree/issue element
    if (!activeIssue.isEmpty())
      joiner.add(WORKTREE_COLOR + WORKTREE_EMOJI + " " + activeIssue + RESET);

    joiner.add(MODEL_COLOR + MODEL_EMOJI + " " + displayName + RESET);
    joiner.add(TIME_COLOR + TIME_EMOJI + " " + duration + RESET);
    joiner.add(SESSION_COLOR + SESSION_EMOJI + " " + displaySessionId + RESET);
    joiner.add(usageColor + USAGE_EMOJI + " " + usageBar + " " + String.format("%3d%%", contextPct) + RESET);

    outputStream.println(joiner.toString());
  }

  /**
   * Returns the active CAT issue ID for the given session by scanning lock files in the locks directory.
   * <p>
   * Scans {@code lockDir/*.lock} files for one whose {@code session_id} JSON field matches
   * {@code sessionId}. Returns the matching lock filename without the {@code .lock} suffix, with
   * control characters removed. Returns {@code ""} if no match is found or the lock directory does not
   * exist. On I/O or parse failure, returns an error indicator string of the form
   * {@code "⚠ <ExceptionClass>: <message>"} with control characters removed, so the error is visible in the
   * statusline.
   *
   * @param sessionId the session ID to look up
   * @param lockDir   the locks directory containing {@code .lock} files
   * @return the active issue ID, {@code ""} if none is found, or an error indicator string on failure
   * @throws NullPointerException if {@code sessionId} or {@code lockDir} are null
   */
  public String getActiveIssue(String sessionId, Path lockDir)
  {
    requireThat(sessionId, "sessionId").isNotNull();
    requireThat(lockDir, "lockDir").isNotNull();
    if (!Files.exists(lockDir))
      return "";
    try
    {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(lockDir, "*.lock"))
      {
        for (Path lockFile : stream)
        {
          String content = Files.readString(lockFile, StandardCharsets.UTF_8);
          if (content.isBlank())
            return removeControlCharacters("⚠ MalformedJson: lock file is empty: " +
              lockFile.getFileName());
          JsonNode root = mapper.readTree(content);
          if (root == null || root.isNull() || root.isMissingNode())
            return removeControlCharacters("⚠ MalformedJson: lock file parsed to null node: " +
              lockFile.getFileName());
          JsonNode sessionIdNode = root.get("session_id");
          if (sessionIdNode != null && !sessionIdNode.isNull() &&
            sessionId.equals(sessionIdNode.asString()))
          {
            String fileName = lockFile.getFileName().toString();
            // Strip the ".lock" suffix
            String issueId = fileName.substring(0, fileName.length() - ".lock".length());
            return removeControlCharacters(issueId);
          }
        }
      }
      return "";
    }
    catch (IOException | JacksonException e)
    {
      String errorMsg = "⚠ " + e.getClass().getSimpleName() + ": " + e.getMessage();
      return removeControlCharacters(errorMsg);
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
   * Removes control characters from a string to prevent ANSI injection in terminal output.
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
   * This method removes all C0 control characters (code points U+0000 to U+001F), DEL (U+007F), and C1
   * control characters (U+0080 to U+009F). This blocks the ESC character (U+001B) which initiates most
   * ANSI escape sequences, the CSI character (U+009B) used in 8-bit terminal sequences, and other control
   * characters with special terminal meanings (bell, backspace, tab, newline, carriage return, etc.).
   * <p>
   * Removed characters include:
   * <ul>
   *   <li>U+001B (ESC) - initiates ANSI escape sequences</li>
   *   <li>U+0000-U+001F - all C0 control characters</li>
   *   <li>U+007F (DEL) - recognized as control by some terminals</li>
   *   <li>U+0080-U+009F - C1 control characters (including CSI at U+009B)</li>
   * </ul>
   *
   * @param value the string to process
   * @return the string with control characters removed
   */
  private String removeControlCharacters(String value)
  {
    StringBuilder result = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); ++i)
    {
      char c = value.charAt(i);
      if ((c >= 0x20 && c < 0x7F) || c >= 0xA0)
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
