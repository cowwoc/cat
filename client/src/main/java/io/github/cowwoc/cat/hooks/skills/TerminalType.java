/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.that;

/**
 * Supported terminal types for emoji width calculation.
 */
public enum TerminalType
{
  /**
   * Windows Terminal.
   */
  WINDOWS_TERMINAL("Windows Terminal"),
  /**
   * Visual Studio Code integrated terminal.
   */
  VSCODE("vscode"),
  /**
   * iTerm2 for macOS.
   */
  ITERM("iTerm.app"),
  /**
   * Apple's built-in Terminal.
   */
  APPLE_TERMINAL("Apple_Terminal"),
  /**
   * KDE Konsole.
   */
  KONSOLE("konsole"),
  /**
   * GNOME Terminal.
   */
  GNOME_TERMINAL("gnome-terminal"),
  /**
   * Kitty terminal.
   */
  KITTY("kitty"),
  /**
   * WezTerm terminal.
   */
  WEZTERM("WezTerm"),
  /**
   * Alacritty terminal.
   */
  ALACRITTY("Alacritty"),
  /**
   * ConEmu for Windows.
   */
  CONEMU("ConEmu"),
  /**
   * Hyper terminal.
   */
  HYPER("Hyper"),
  /**
   * Unknown terminal type (fallback).
   */
  UNKNOWN("unknown");

  private final String jsonKey;

  /**
   * Creates a terminal type.
   *
   * @param jsonKey the key used in emoji-widths.json
   */
  TerminalType(String jsonKey)
  {
    assert that(jsonKey, "jsonKey").isNotBlank().elseThrow();
    this.jsonKey = jsonKey;
  }

  /**
   * Gets the JSON key for this terminal type.
   *
   * @return the key used in emoji-widths.json terminals section
   */
  public String getJsonKey()
  {
    return jsonKey;
  }

  /**
   * Detects the current terminal type from environment variables.
   *
   * @return the detected terminal type, or UNKNOWN if not detected
   */
  public static TerminalType detect()
  {
    return detect(System.getenv());
  }

  /**
   * Detects the terminal type from the provided environment variables.
   *
   * @param env the environment variables map
   * @return the detected terminal type, or UNKNOWN if not detected
   * @throws NullPointerException if {@code env} is null
   */
  public static TerminalType detect(Map<String, String> env)
  {
    assert that(env, "env").isNotNull().elseThrow();

    // Windows Terminal
    if (env.get("WT_SESSION") != null)
      return WINDOWS_TERMINAL;

    // VS Code
    String termProgram = env.get("TERM_PROGRAM");
    if ("vscode".equals(termProgram) || env.get("VSCODE_INJECTION") != null)
      return VSCODE;

    // iTerm2
    if ("iTerm.app".equals(termProgram) || env.get("ITERM_SESSION_ID") != null)
      return ITERM;

    // Apple Terminal
    if ("Apple_Terminal".equals(termProgram))
      return APPLE_TERMINAL;

    // Konsole
    if (env.get("KONSOLE_VERSION") != null)
      return KONSOLE;

    // GNOME Terminal
    if (env.get("GNOME_TERMINAL_SCREEN") != null || env.get("VTE_VERSION") != null)
      return GNOME_TERMINAL;

    // Kitty
    if (env.get("KITTY_WINDOW_ID") != null)
      return KITTY;

    // WezTerm
    if (env.get("WEZTERM_PANE") != null)
      return WEZTERM;

    // Alacritty
    if (env.get("ALACRITTY_SOCKET") != null || env.get("ALACRITTY_LOG") != null)
      return ALACRITTY;

    // ConEmu
    if (env.get("ConEmuPID") != null)
      return CONEMU;

    // Hyper
    if ("Hyper".equals(termProgram))
      return HYPER;

    // WSL detection - check /proc/version for "microsoft" or "wsl"
    if (isWsl())
      return WINDOWS_TERMINAL;  // Default for WSL

    // TERM_PROGRAM fallback - try to match
    if (termProgram != null)
    {
      for (TerminalType type : values())
      {
        if (type.jsonKey.equalsIgnoreCase(termProgram))
          return type;
      }
    }

    return UNKNOWN;
  }

  /**
   * Checks if running in Windows Subsystem for Linux.
   *
   * @return true if WSL detected
   */
  private static boolean isWsl()
  {
    try
    {
      String version = Files.readString(Path.of("/proc/version"));
      String lower = version.toLowerCase(Locale.ROOT);
      return lower.contains("microsoft") || lower.contains("wsl");
    }
    catch (Exception _)
    {
      return false;
    }
  }
}
