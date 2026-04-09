/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.bash;

import io.github.cowwoc.cat.claude.hook.BashHandler;
import io.github.cowwoc.cat.claude.hook.ClaudeHook;

import java.util.ArrayList;
import java.util.List;

/**
 * Warns when a piped bash command does not include {@code tee} to preserve the full output.
 * <p>
 * Piping output through filters discards all non-matching lines, making it impossible to
 * re-filter the output later. Using {@code tee} to capture the full output to a temporary
 * log file before filtering preserves the complete output for later inspection.
 */
public final class WarnPipedWithoutTee implements BashHandler
{
  /**
   * Creates a new handler for warning about piped commands without tee.
   */
  public WarnPipedWithoutTee()
  {
    // Handler class
  }

  @Override
  public Result check(ClaudeHook scope)
  {
    String command = scope.getCommand();
    List<String> pipeSegments = splitOnPipeOperator(command);

    // No pipe operators found — command does not need tee
    if (pipeSegments.size() <= 1)
      return Result.allow();

    // Check if tee is present anywhere in the pipeline
    for (String segment : pipeSegments)
    {
      String trimmed = segment.strip();
      if (startsWithCommand(trimmed, "tee"))
        return Result.allow();
    }

    return Result.warn("""
      Piped command detected without tee. The full output will be lost after filtering.

      Recommended pattern:
        LOG_FILE=$(mktemp)
        your-command 2>&1 | tee "$LOG_FILE" | grep pattern
        # Later: re-filter with grep/awk/etc. on $LOG_FILE
        rm -f "$LOG_FILE"

      Using tee preserves the complete output in a temporary log file, allowing you to
      re-filter without re-running the command. See plugin/rules/tee-piped-output.md.""");
  }

  /**
   * Splits a command string on pipe operators ({@code |}), ignoring pipes inside single or
   * double quotes and ignoring the logical OR operator ({@code ||}).
   *
   * @param command the command string to split
   * @return the list of pipeline segments
   */
  private List<String> splitOnPipeOperator(String command)
  {
    List<String> segments = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;

    for (int i = 0; i < command.length(); ++i)
    {
      char ch = command.charAt(i);

      if (ch == '\'' && !inDoubleQuote)
      {
        inSingleQuote = !inSingleQuote;
        current.append(ch);
      }
      else if (ch == '"' && !inSingleQuote)
      {
        inDoubleQuote = !inDoubleQuote;
        current.append(ch);
      }
      else if (ch == '\\' && !inSingleQuote && i + 1 < command.length())
      {
        // Skip escaped character
        current.append(ch);
        ++i;
        current.append(command.charAt(i));
      }
      else if (ch == '|' && !inSingleQuote && !inDoubleQuote)
      {
        // Check for || (logical OR) — not a pipe operator
        if (i + 1 < command.length() && command.charAt(i + 1) == '|')
        {
          current.append("||");
          ++i;
        }
        else
        {
          segments.add(current.toString());
          current = new StringBuilder();
        }
      }
      else
      {
        current.append(ch);
      }
    }
    segments.add(current.toString());
    return segments;
  }

  /**
   * Checks if a trimmed command segment starts with the specified command name.
   *
   * @param segment the trimmed command segment
   * @param commandName the command name to check for
   * @return true if the segment starts with the command name followed by whitespace or end of string
   */
  private boolean startsWithCommand(String segment, String commandName)
  {
    if (segment.equals(commandName))
      return true;
    return segment.startsWith(commandName + " ") || segment.startsWith(commandName + "\t");
  }
}
