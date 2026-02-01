#!/usr/bin/env python3
"""
Shared library for emoji display width handling.

Provides terminal detection and emoji-aware display width calculation
using the widths defined in emoji-widths.json.

Usage:
    from lib.emoji_widths import get_emoji_widths, display_width

    # Get widths for current terminal (auto-detected)
    widths = get_emoji_widths()

    # Calculate display width of text
    width = display_width("Hello ⚠️ World", widths)

    # Or use the convenience class
    from lib.emoji_widths import EmojiWidths
    ew = EmojiWidths()
    width = ew.display_width("Hello ⚠️ World")
"""

import os
import json
import subprocess
from typing import Dict, Optional
from pathlib import Path


def detect_terminal() -> str:
    """
    Detect the current terminal type.

    Checks environment variables in priority order to identify the terminal.
    Returns the terminal name or "unknown" if not detected.
    """
    # 1. Windows Terminal
    if os.environ.get("WT_SESSION"):
        return "Windows Terminal"

    # 2. VS Code
    if os.environ.get("TERM_PROGRAM") == "vscode" or os.environ.get("VSCODE_INJECTION"):
        return "vscode"

    # 3. iTerm2
    if os.environ.get("TERM_PROGRAM") == "iTerm.app" or os.environ.get("ITERM_SESSION_ID"):
        return "iTerm.app"

    # 4. Apple Terminal
    if os.environ.get("TERM_PROGRAM") == "Apple_Terminal":
        return "Apple_Terminal"

    # 5. Konsole
    if os.environ.get("KONSOLE_VERSION"):
        return "konsole"

    # 6. GNOME Terminal
    if os.environ.get("GNOME_TERMINAL_SCREEN") or os.environ.get("VTE_VERSION"):
        return "gnome-terminal"

    # 7. Kitty
    if os.environ.get("KITTY_WINDOW_ID"):
        return "kitty"

    # 8. WezTerm
    if os.environ.get("WEZTERM_PANE"):
        return "WezTerm"

    # 9. Alacritty
    if os.environ.get("ALACRITTY_SOCKET") or os.environ.get("ALACRITTY_LOG"):
        return "Alacritty"

    # 10. ConEmu
    if os.environ.get("ConEmuPID"):
        return "ConEmu"

    # 11. Hyper
    if os.environ.get("TERM_PROGRAM") == "Hyper":
        return "Hyper"

    # 12. WSL detection
    if _is_wsl():
        win_terminal = _get_windows_parent_terminal()
        if win_terminal:
            return win_terminal
        return "Windows Terminal"  # Default for WSL

    # 13. TERM_PROGRAM fallback
    term_program = os.environ.get("TERM_PROGRAM")
    if term_program:
        return term_program

    # 14. Unknown
    return "unknown"


def _is_wsl() -> bool:
    """Check if running in Windows Subsystem for Linux."""
    try:
        with open("/proc/version", "r") as f:
            version = f.read().lower()
            return "microsoft" in version or "wsl" in version
    except (FileNotFoundError, PermissionError):
        return False


def _get_windows_parent_terminal() -> Optional[str]:
    """Get Windows parent terminal process name via PowerShell (for WSL)."""
    try:
        result = subprocess.run(
            ["powershell.exe", "-NoProfile", "-Command", """
                $p = Get-Process -Id $PID 2>$null
                while ($p -and $p.ProcessName -match "^(wsl|bash|sh|zsh|fish|pwsh|powershell|conhost)$") {
                    $p = Get-Process -Id $p.Parent.Id 2>$null
                }
                if ($p) { $p.ProcessName }
            """],
            capture_output=True,
            text=True,
            timeout=3
        )
        process_name = result.stdout.strip()

        terminal_map = {
            "WindowsTerminal": "Windows Terminal",
            "Code": "vscode",
            "ConEmu": "ConEmu",
            "ConEmu64": "ConEmu",
            "mintty": "mintty"
        }
        return terminal_map.get(process_name)
    except (subprocess.TimeoutExpired, FileNotFoundError, subprocess.SubprocessError):
        return None


def find_plugin_root() -> Path:
    """
    Find the plugin root directory.

    Looks for CLAUDE_PLUGIN_ROOT environment variable, or walks up from
    this file's location to find emoji-widths.json.
    """
    # Check environment variable first
    env_root = os.environ.get("CLAUDE_PLUGIN_ROOT")
    if env_root:
        return Path(env_root)

    # Walk up from this file to find emoji-widths.json
    current = Path(__file__).resolve().parent
    for _ in range(5):  # Max 5 levels up
        candidate = current / "emoji-widths.json"
        if candidate.exists():
            return current
        current = current.parent

    # Default: assume we're in scripts/lib/, go up two levels
    return Path(__file__).resolve().parent.parent.parent


def load_emoji_widths_file(plugin_root: Optional[Path] = None) -> Dict:
    """
    Load the emoji-widths.json file.

    Returns the full JSON structure with 'emojis' and 'terminals' keys.
    """
    if plugin_root is None:
        plugin_root = find_plugin_root()

    widths_file = plugin_root / "emoji-widths.json"

    if not widths_file.exists():
        return {"emojis": {}, "terminals": {}}

    with open(widths_file, "r", encoding="utf-8") as f:
        return json.load(f)


def get_emoji_widths(terminal: Optional[str] = None,
                     plugin_root: Optional[Path] = None) -> Dict[str, int]:
    """
    Get emoji width mappings for the specified or detected terminal.

    Args:
        terminal: Terminal name. If None, auto-detects.
        plugin_root: Plugin root directory. If None, auto-finds.

    Returns:
        Dict mapping emoji strings to their display widths.
        Falls back to sensible defaults if terminal not found.
    """
    if terminal is None:
        terminal = detect_terminal()

    data = load_emoji_widths_file(plugin_root)

    # Get terminal-specific widths
    terminal_widths = data.get("terminals", {}).get(terminal)

    if terminal_widths:
        return terminal_widths

    # Fallback: default widths for common emojis
    # Most emojis are width 2 in modern terminals
    return {
        "☑️": 2, "🔄": 2, "🔳": 2, "🚫": 2, "🚧": 2,
        "📊": 2, "📦": 2, "🎯": 2, "📋": 2, "⚙️": 2, "🏆": 2,
        "🧠": 2, "🐱": 2, "🧹": 2, "🤝": 2, "✅": 2,
        "🔍": 2, "👀": 2, "🔭": 2, "⏳": 2, "⚡": 2, "🔒": 2, "✨": 2,
        "⚠️": 2, "🚨": 2,
        "✓": 1, "✗": 1, "→": 1, "•": 1, "▸": 1, "▹": 1, "◆": 1, "✦": 2, "⚠": 1
    }


def _is_likely_emoji(char: str) -> bool:
    """
    Check if a character is likely an emoji based on Unicode codepoint.

    Covers common emoji ranges without requiring a full emoji database.
    Does NOT include variation selectors (U+FE0F) which are modifiers, not emojis.
    """
    if not char:
        return False
    cp = ord(char[0])
    # Common emoji ranges:
    # U+1F300-U+1F9FF: Most pictographic emojis
    # U+1FA00-U+1FAFF: Extended symbols
    # U+2600-U+26FF: Misc symbols (sun, stars, etc.)
    # U+2700-U+27BF: Dingbats
    # NOTE: U+FE0F (variation selector) is NOT included - it's a modifier with width 0
    return (
        (0x1F300 <= cp <= 0x1F9FF) or
        (0x1FA00 <= cp <= 0x1FAFF) or
        (0x2600 <= cp <= 0x26FF) or
        (0x2700 <= cp <= 0x27BF)
    )


class UnknownEmojiError(Exception):
    """Raised when an emoji is encountered that's not in emoji-widths.json."""
    pass


def display_width(text: str, emoji_widths: Optional[Dict[str, int]] = None,
                  strict: bool = True) -> int:
    """
    Calculate the terminal display width of text.

    Accounts for emoji display widths which may differ from character count.

    Args:
        text: The text to measure.
        emoji_widths: Dict mapping emojis to widths. If None, uses defaults.
        strict: If True (default), raise UnknownEmojiError when encountering
                an emoji not in emoji_widths. If False, treat unknown emojis
                as width 1 (legacy behavior).

    Returns:
        The display width in terminal columns.

    Raises:
        UnknownEmojiError: When strict=True and an unknown emoji is found.
    """
    if emoji_widths is None:
        emoji_widths = get_emoji_widths()

    width = 0
    i = 0
    text_len = len(text)

    # Sort emojis by length (longest first) to match multi-char emojis correctly
    sorted_emojis = sorted(emoji_widths.keys(), key=len, reverse=True)

    while i < text_len:
        matched = False

        # Try to match known emojis
        for emoji in sorted_emojis:
            if text[i:].startswith(emoji):
                width += emoji_widths[emoji]
                i += len(emoji)
                matched = True
                break

        if not matched:
            char = text[i]
            # Fail-fast on unknown emojis
            if strict and _is_likely_emoji(char):
                # Find context for error message
                start = max(0, i - 10)
                end = min(text_len, i + 10)
                context = text[start:end]
                raise UnknownEmojiError(
                    f"Unknown emoji '{char}' (U+{ord(char):04X}) not in emoji-widths.json. "
                    f"Context: ...{context}... "
                    f"Add this emoji to emoji-widths.json with its display width."
                )
            # Regular character (ASCII, box-drawing, etc.) = width 1
            width += 1
            i += 1

    return width


def pad_to_width(text: str, target_width: int,
                 emoji_widths: Optional[Dict[str, int]] = None,
                 align: str = "left") -> str:
    """
    Pad text to a target display width.

    Args:
        text: The text to pad.
        target_width: The desired display width.
        emoji_widths: Dict mapping emojis to widths. If None, uses defaults.
        align: "left", "right", or "center".

    Returns:
        The padded text.
    """
    if emoji_widths is None:
        emoji_widths = get_emoji_widths()

    current_width = display_width(text, emoji_widths)
    padding_needed = target_width - current_width

    if padding_needed <= 0:
        return text

    if align == "right":
        return " " * padding_needed + text
    elif align == "center":
        left_pad = padding_needed // 2
        right_pad = padding_needed - left_pad
        return " " * left_pad + text + " " * right_pad
    else:  # left
        return text + " " * padding_needed


class EmojiWidths:
    """
    Convenience class for working with emoji display widths.

    Caches terminal detection and emoji width loading for efficiency.

    Usage:
        ew = EmojiWidths()
        width = ew.display_width("Hello ⚠️")
        padded = ew.pad_to_width("Hi", 10)
    """

    def __init__(self, terminal: Optional[str] = None,
                 plugin_root: Optional[Path] = None):
        """
        Initialize with optional terminal and plugin root override.

        Args:
            terminal: Terminal name. If None, auto-detects.
            plugin_root: Plugin root directory. If None, auto-finds.
        """
        self.terminal = terminal or detect_terminal()
        self.plugin_root = plugin_root or find_plugin_root()
        self._widths = None

    @property
    def widths(self) -> Dict[str, int]:
        """Get cached emoji widths dict."""
        if self._widths is None:
            self._widths = get_emoji_widths(self.terminal, self.plugin_root)
        return self._widths

    def display_width(self, text: str, strict: bool = True) -> int:
        """Calculate display width of text.

        Args:
            text: The text to measure.
            strict: If True (default), raise UnknownEmojiError on unknown emojis.

        Returns:
            The display width in terminal columns.
        """
        return display_width(text, self.widths, strict=strict)

    def pad_to_width(self, text: str, target_width: int, align: str = "left") -> str:
        """Pad text to target display width."""
        return pad_to_width(text, target_width, self.widths, align)


# For direct script execution - print diagnostic info
if __name__ == "__main__":
    terminal = detect_terminal()
    widths = get_emoji_widths(terminal)

    print(f"Terminal: {terminal}")
    print(f"Plugin root: {find_plugin_root()}")
    print(f"Emoji widths loaded: {len(widths)} entries")
    print()
    print("Sample widths:")
    for emoji in ["⚠️", "🚨", "✓", "✗", "📊"]:
        if emoji in widths:
            print(f"  {emoji} = {widths[emoji]}")

    print()
    print("Display width tests:")
    test_strings = [
        "Hello",
        "Hello ⚠️",
        "85% 🚨",
        "│ content │"
    ]
    for s in test_strings:
        print(f"  '{s}' = {display_width(s, widths)} columns")
