/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.session;

import io.github.cowwoc.cat.claude.hook.ClaudeHook;
import io.github.cowwoc.cat.claude.hook.skills.DisplayUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Warns the user when the detected terminal type is not found in emoji-widths.json.
 * <p>
 * When DisplayUtils falls back to another terminal's emoji widths, this handler emits a warning to stderr
 * explaining what happened and how to contribute measurements for the user's terminal. The warning is
 * displayed at most once per session using a marker file in the session directory.
 */
public final class WarnUnknownTerminal implements SessionStartHandler
{
  private static final String MARKER_FILE = "terminal-warning-emitted";

  /**
   * Creates a new WarnUnknownTerminal handler.
   */
  public WarnUnknownTerminal()
  {
  }

  /**
   * Checks whether DisplayUtils is using fallback widths and emits a warning if so.
   * <p>
   * Uses a marker file in the session directory to ensure the warning is only shown once per session.
   *
   * @return a result with a stderr warning if fallback widths are in use and the warning hasn't been shown
   *   yet, or an empty result otherwise
   */
  @Override
  public Result handle(ClaudeHook scope)
  {
    DisplayUtils display = scope.getDisplayUtils();
    if (!display.isUsingFallbackWidths())
      return Result.empty();

    Path sessionDir = scope.getClaudeSessionPath(scope.getSessionId());
    Path markerFile = sessionDir.resolve(MARKER_FILE);
    if (Files.exists(markerFile))
      return Result.empty();

    try
    {
      Files.createDirectories(sessionDir);
      Files.writeString(markerFile, "");
    }
    catch (IOException _)
    {
      // Best-effort: if we can't write the marker, still show the warning
    }

    String terminalKey = display.getDetectedTerminalKey();
    String warning = "WARNING: Terminal type \"" + terminalKey + "\" not found in emoji-widths.json. " +
      "Using fallback emoji widths (box alignment may be slightly off).\n" +
      "To add support for your terminal:\n" +
      "  1. Run: https://github.com/cowwoc/cat/blob/main/plugin/scripts/measure-emoji-widths.sh\n" +
      "  2. Submit the results at: https://github.com/cowwoc/cat/issues";
    return Result.stderr(warning);
  }
}
