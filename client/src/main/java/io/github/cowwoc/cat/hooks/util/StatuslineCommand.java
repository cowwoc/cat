/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.util;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.ClaudeStatusline;
import io.github.cowwoc.cat.hooks.Config;
import io.github.cowwoc.cat.hooks.MainClaudeStatusline;
import io.github.cowwoc.cat.hooks.SharedSecrets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
 *   <li>{@code model.display_name} - model display name (required)</li>
 *   <li>{@code session_id} - session ID (required)</li>
 *   <li>{@code cost.total_duration_ms} - session duration in milliseconds (required)</li>
 *   <li>{@code context_window.current_usage.input_tokens} - non-cached input tokens used (required when
 *       {@code current_usage} is present; defaults to 0 when {@code current_usage} is absent)</li>
 *   <li>{@code context_window.current_usage.cache_read_input_tokens} - tokens read from the prompt cache
 *       (optional; defaults to 0 when absent)</li>
 *   <li>{@code context_window.current_usage.cache_creation_input_tokens} - tokens written to the prompt cache
 *       (optional; defaults to 0 when absent)</li>
 *   <li>{@code context_window.context_window_size} - total context window size in tokens (required when
 *       {@code context_window} is present)</li>
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

  private static final int SYSTEM_PROMPT_TOKENS = 6_400;
  private static final int TOOL_DEFINITIONS_TOKENS = 7_100;
  // Fixed overhead always present in usedTokens; subtracted from both numerator and denominator
  private static final int FIXED_OVERHEAD = SYSTEM_PROMPT_TOKENS + TOOL_DEFINITIONS_TOKENS;
  // Reserved space Claude holds back as the autocompact buffer; not included in usedTokens,
  // so it only reduces the denominator (usable context), never the numerator
  private static final int AUTOCOMPACT_BUFFER_TOKENS = 21_000;

  // Component emojis
  private static final String WORKTREE_EMOJI = "🌿";
  private static final String MODEL_EMOJI = "🤖";
  private static final String TIME_EMOJI = "⏰";
  private static final String SESSION_EMOJI = "🆔";
  private static final String USAGE_EMOJI = "📊";

  static
  {
    SharedSecrets.setStatuslineCommandAccess(StatuslineCommand::scaleContextPercent);
  }

  private final ClaudeStatusline scope;
  private final JsonMapper mapper;

  /**
   * Creates a new StatuslineCommand.
   *
   * @param scope the statusline scope for accessing JSON parsing and the CAT work path
   * @throws NullPointerException if {@code scope} is null
   */
  public StatuslineCommand(ClaudeStatusline scope)
  {
    requireThat(scope, "scope").isNotNull();
    this.scope = scope;
    this.mapper = scope.getJsonMapper();
  }

  /**
   * Writes the formatted statusline to the output stream using data already parsed into the scope.
   *
   * @param outputStream the output stream to write the statusline to
   * @throws NullPointerException if {@code outputStream} is null
   * @throws IOException          if an I/O error occurs
   */
  public void execute(PrintStream outputStream) throws IOException
  {
    execute(outputStream, scope.getCatWorkPath().resolve("locks"), 0);
  }

  /**
   * Writes the formatted statusline to the output stream using data already parsed into the scope.
   *
   * @param outputStream the output stream to write the statusline to
   * @param lockDir      the locks directory containing {@code .lock} files
   * @throws NullPointerException if {@code outputStream} or {@code lockDir} are null
   * @throws IOException          if an I/O error occurs
   */
  public void execute(PrintStream outputStream, Path lockDir) throws IOException
  {
    execute(outputStream, lockDir, 0);
  }

  /**
   * Writes the formatted statusline to the output stream using data already parsed into the scope.
   * <p>
   * When the combined visible width of all components exceeds {@code terminalWidth}, each component is
   * rendered on its own line. When {@code terminalWidth} is 0, all components are always rendered on a
   * single line regardless of their combined width.
   *
   * @param outputStream  the output stream to write the statusline to
   * @param lockDir       the locks directory containing {@code .lock} files
   * @param terminalWidth the terminal width in columns; {@code 0} means unlimited (no wrapping)
   * @throws NullPointerException     if {@code outputStream} or {@code lockDir} are null
   * @throws IllegalArgumentException if {@code terminalWidth} is negative
   * @throws IOException              if an I/O error occurs
   */
  public void execute(PrintStream outputStream, Path lockDir, int terminalWidth) throws IOException
  {
    requireThat(outputStream, "outputStream").isNotNull();
    requireThat(lockDir, "lockDir").isNotNull();
    requireThat(terminalWidth, "terminalWidth").isGreaterThanOrEqualTo(0);

    String displayName = scope.getModelDisplayName();
    String sessionId = scope.getSessionId();
    Duration totalDuration = scope.getTotalDuration();
    int usedTokens = scope.getUsedTokens();

    // Get active issue for the current session
    String activeIssue = getActiveIssue(sessionId, lockDir);

    // Format duration
    String duration = formatDuration(totalDuration);

    // Session ID, with control characters removed to prevent ANSI injection
    String displaySessionId = removeControlCharacters(sessionId);

    // Remove control characters from display name to prevent ANSI injection
    displayName = removeControlCharacters(displayName);

    int totalContext = scope.getTotalContext();

    // Scale used tokens against the usable context window; skip scaling when no context data yet
    int contextPercent;
    if (totalContext == 0)
      contextPercent = 0;
    else
      contextPercent = scaleContextPercent(usedTokens, totalContext);

    // Usage color
    String usageColor = getUsageColor(contextPercent);

    // Usage bar
    String usageBar = createUsageBar(contextPercent);

    // Build the individual component strings
    List<String> components = new ArrayList<>();
    if (!activeIssue.isEmpty())
      components.add(WORKTREE_COLOR + WORKTREE_EMOJI + " " + activeIssue + RESET);
    components.add(MODEL_COLOR + MODEL_EMOJI + " " + displayName + RESET);
    components.add(TIME_COLOR + TIME_EMOJI + " " + duration + RESET);
    components.add(SESSION_COLOR + SESSION_EMOJI + " " + displaySessionId + RESET);
    components.add(usageColor + USAGE_EMOJI + " " + usageBar + " " + String.format("%3d%%", contextPercent) + RESET);

    // Decide layout: single line vs per-component lines
    String separator = " " + SEPARATOR_COLOR + "|" + RESET + " ";
    int separatorWidth = 3; // " | " is 3 visible columns
    boolean wrap = false;
    if (terminalWidth > 0)
    {
      // Measure total visible width: sum of component widths + (n-1) separators
      int totalWidth = 0;
      for (String component : components)
        totalWidth += plainWidth(component);
      totalWidth += separatorWidth * (components.size() - 1);
      wrap = totalWidth > terminalWidth;
    }

    if (wrap)
    {
      // One component per line
      StringJoiner joiner = new StringJoiner("\n");
      for (String component : components)
        joiner.add(component);
      outputStream.println(joiner.toString());
    }
    else
    {
      // All on one line
      StringJoiner joiner = new StringJoiner(separator);
      for (String component : components)
        joiner.add(component);
      outputStream.println(joiner.toString());
    }
  }

  /**
   * Returns the visible display width of the given ANSI-formatted string.
   * <p>
   * ANSI escape sequences (starting with ESC followed by {@code [} and ending at the first letter) are
   * stripped before measurement. Each emoji character is counted as 2 columns, and each other character
   * as 1 column.
   *
   * @param ansiText the ANSI-formatted string to measure
   * @return the number of terminal columns the string occupies
   * @throws NullPointerException if {@code ansiText} is null
   */
  public static int plainWidth(String ansiText)
  {
    requireThat(ansiText, "ansiText").isNotNull();
    int width = 0;
    int i = 0;
    while (i < ansiText.length())
    {
      char c = ansiText.charAt(i);
      // Detect ANSI escape sequence: ESC '[' ... letter
      if (c == '\033' && i + 1 < ansiText.length() && ansiText.charAt(i + 1) == '[')
      {
        // Skip until we find the terminating letter (A-Za-z)
        i += 2;
        while (i < ansiText.length() && !Character.isLetter(ansiText.charAt(i)))
          ++i;
        // Skip the terminating letter
        if (i < ansiText.length())
          ++i;
        continue;
      }
      // Count emoji as width 2, other characters as width 1
      int codePoint = ansiText.codePointAt(i);
      if (Character.isSupplementaryCodePoint(codePoint))
      {
        // Supplementary (emoji/surrogate pair): 2 Java chars, treat as width 2
        width += 2;
        i += 2;
      }
      else if (codePoint > 0xFF)
      {
        // Non-ASCII BMP character that may be wide (e.g., enclosed alphanumerics like 🆔)
        // Treat as width 2
        width += 2;
        ++i;
      }
      else
      {
        width += 1;
        ++i;
      }
    }
    return width;
  }

  /**
   * Returns the active CAT issue ID for the given session by scanning lock files in the locks directory.
   * <p>
   * Scans {@code lockDir/*.lock} files for one whose {@code session_id} JSON field matches
   * {@code sessionId}. Returns the matching lock filename without the {@code .lock} suffix, with
   * control characters removed. Returns {@code ""} if no match is found or the lock directory does not
   * exist. On I/O or parse failure, returns an error indicator string of the form
   * {@code "⚠ CAT: <message>"} with control characters removed, so the error is visible in the
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
            return removeControlCharacters("⚠ CAT: lock file is empty: " +
              lockFile.getFileName());
          JsonNode root = mapper.readTree(content);
          if (root == null || root.isNull() || root.isMissingNode())
            return removeControlCharacters("⚠ CAT: lock file parsed to null node: " +
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
      String errorMsg = "⚠ CAT: " + e.getMessage();
      return removeControlCharacters(errorMsg);
    }
  }

  /**
   * Formats a duration to HH:MM format.
   *
   * @param duration the duration to format
   * @return the formatted duration string in HH:MM format
   */
  private String formatDuration(Duration duration)
  {
    long totalSeconds = duration.toMillis() / 1000;
    long hours = totalSeconds / 3600;
    long minutes = (totalSeconds % 3600) / 60;

    return String.format("%02d:%02d", hours, minutes);
  }

  /**
   * Scales used tokens against the usable context window to produce a 0–100 display percentage.
   * <p>
   * The usable context is {@code totalContext - FIXED_OVERHEAD - AUTOCOMPACT_BUFFER_TOKENS}.
   * The effective used tokens are {@code usedTokens - FIXED_OVERHEAD}. The autocompact buffer
   * ({@code AUTOCOMPACT_BUFFER_TOKENS}) is reserved capacity not included in {@code usedTokens},
   * so it only reduces the denominator (usable context), not the numerator. Tokens at or below
   * the fixed overhead map to 0%; a full context maps to 100%.
   *
   * @param usedTokens   the number of tokens used in the context window
   * @param totalContext the total context window size in tokens
   * @return the scaled percentage in the range [0, 100]
   */
  private static int scaleContextPercent(int usedTokens, int totalContext)
  {
    int usableContext = totalContext - FIXED_OVERHEAD - AUTOCOMPACT_BUFFER_TOKENS;
    if (usableContext <= 0)
      return 0;
    int effectiveUsed = usedTokens - FIXED_OVERHEAD;
    return Math.min(100, Math.max(0, effectiveUsed * 100 / usableContext));
  }

  /**
   * Returns the RGB color code for the given usage percentage.
   * <p>
   * Colors scale from green (0%) to red (100%).
   *
   * @param contextPercent the scaled context usage percentage (0–100)
   * @return the RGB color escape code string
   */
  private String getUsageColor(int contextPercent)
  {
    if (contextPercent >= 80)
    {
      // Red above 80%
      int red = 255;
      int green = Math.max(0, (int) ((100 - contextPercent) * 255.0 / 50));
      return "\033[38;2;" + red + ";" + green + ";0m";
    }
    if (contextPercent >= 50)
    {
      // Orange-red between 50% and 80%
      int red = 255;
      int green = (int) ((100 - contextPercent) * 255.0 / 50);
      return "\033[38;2;" + red + ";" + green + ";0m";
    }
    // Green-yellow below 50%
    int red = (int) (contextPercent * 255.0 / 50);
    int green = 255;
    return "\033[38;2;" + red + ";" + green + ";0m";
  }

  /**
   * Creates a 20-segment usage bar using block characters.
   * <p>
   * Filled segments use "█" and empty segments use "░".
   *
   * @param contextPercent the scaled context usage percentage (0–100)
   * @return the usage bar string
   */
  private String createUsageBar(int contextPercent)
  {
    int filled = (contextPercent * USAGE_BAR_SEGMENTS) / 100;

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
    try
    {
      try (ClaudeStatusline scope = new MainClaudeStatusline(System.in))
      {
        try
        {
          run(scope, args, System.out);
        }
        catch (IllegalArgumentException | IOException e)
        {
          System.out.println("⚠ CAT: " + Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
        }
        catch (RuntimeException | AssertionError e)
        {
          printError("Unexpected error", e);
        }
      }
      catch (IOException e)
      {
        printError("Failed to read statusline input", e);
      }
    }
    // Scope creation failed (e.g., missing CLAUDE_PROJECT_DIR) - cannot use scope services
    catch (RuntimeException | AssertionError e)
    {
      printError("Failed to initialize statusline", e);
    }
  }

  /**
   * Logs an error and prints a user-visible warning to stdout.
   *
   * @param logMessage the message to log at error level
   * @param throwable the exception to log and display
   */
  private static void printError(String logMessage, Throwable throwable)
  {
    Logger log = LoggerFactory.getLogger(StatuslineCommand.class);
    log.error(logMessage, throwable);
    System.out.println("⚠ CAT: " +
      Objects.toString(throwable.getMessage(), throwable.getClass().getSimpleName()));
  }

  /**
   * Writes the statusline to the output stream using data already parsed into the scope.
   * <p>
   * The terminal width is read from the {@code displayWidth} field in {@code .cat/config.json}
   * (default: {@code 120}). When wrapping is disabled ({@code displayWidth == 0}), all components
   * are rendered on a single line regardless of combined width.
   *
   * @param scope the statusline scope with pre-parsed JSON data
   * @param args  command-line arguments (unused)
   * @param out   the output stream to write the statusline to
   * @throws NullPointerException if any of {@code scope}, {@code args}, or {@code out} are null
   * @throws IOException          if an I/O error occurs
   */
  public static void run(ClaudeStatusline scope, String[] args, PrintStream out) throws IOException
  {
    requireThat(scope, "scope").isNotNull();
    requireThat(args, "args").isNotNull();
    requireThat(out, "out").isNotNull();
    if (args.length > 0)
      throw new IllegalArgumentException("Unexpected arguments: " + String.join(" ", args));
    StatuslineCommand cmd = new StatuslineCommand(scope);
    Config config = Config.load(scope.getJsonMapper(), scope.getProjectPath());
    int terminalWidth = config.getInt("displayWidth", 0);
    cmd.execute(out, scope.getCatWorkPath().resolve("locks"), terminalWidth);
  }
}
