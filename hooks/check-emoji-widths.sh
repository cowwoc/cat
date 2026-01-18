#!/bin/bash
set -euo pipefail
trap 'echo "ERROR in check-emoji-widths.sh at line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# Check if current terminal is supported in emoji-widths.json
#
# TRIGGER: SessionStart
#
# BEHAVIOR:
# - Detects terminal emulator
# - Checks if it exists in emoji-widths.json
# - If not found, warns user and explains how to contribute

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLUGIN_ROOT="$(dirname "$SCRIPT_DIR")"
WIDTHS_FILE="${PLUGIN_ROOT}/emoji-widths.json"

# Read stdin (required for hooks)
INPUT=""
if [ ! -t 0 ]; then
    INPUT=$(cat)
fi

# Check if emoji-widths.json exists
if [[ ! -f "$WIDTHS_FILE" ]]; then
    exit 0
fi

#=============================================================================
# TERMINAL DETECTION (same logic as measure-emoji-widths.sh)
#=============================================================================

is_wsl() {
    [[ -f /proc/version ]] && grep -qi "microsoft\|wsl" /proc/version 2>/dev/null
}

detect_terminal() {
    # Windows Terminal
    if [[ -n "${WT_SESSION:-}" ]]; then
        echo "Windows Terminal"
        return
    fi

    # VS Code
    if [[ "${TERM_PROGRAM:-}" == "vscode" ]] || [[ -n "${VSCODE_INJECTION:-}" ]]; then
        echo "vscode"
        return
    fi

    # iTerm2
    if [[ "${TERM_PROGRAM:-}" == "iTerm.app" ]] || [[ -n "${ITERM_SESSION_ID:-}" ]]; then
        echo "iTerm.app"
        return
    fi

    # Apple Terminal
    if [[ "${TERM_PROGRAM:-}" == "Apple_Terminal" ]]; then
        echo "Apple_Terminal"
        return
    fi

    # Konsole
    if [[ -n "${KONSOLE_VERSION:-}" ]]; then
        echo "konsole"
        return
    fi

    # GNOME Terminal
    if [[ -n "${GNOME_TERMINAL_SCREEN:-}" ]] || [[ -n "${VTE_VERSION:-}" ]]; then
        echo "gnome-terminal"
        return
    fi

    # Kitty
    if [[ -n "${KITTY_WINDOW_ID:-}" ]]; then
        echo "kitty"
        return
    fi

    # WezTerm
    if [[ -n "${WEZTERM_PANE:-}" ]]; then
        echo "WezTerm"
        return
    fi

    # Alacritty
    if [[ -n "${ALACRITTY_SOCKET:-}" ]] || [[ -n "${ALACRITTY_LOG:-}" ]]; then
        echo "Alacritty"
        return
    fi

    # ConEmu
    if [[ -n "${ConEmuPID:-}" ]]; then
        echo "ConEmu"
        return
    fi

    # Hyper
    if [[ "${TERM_PROGRAM:-}" == "Hyper" ]]; then
        echo "Hyper"
        return
    fi

    # WSL fallback - assume Windows Terminal
    if is_wsl; then
        echo "Windows Terminal"
        return
    fi

    # Use TERM_PROGRAM if set
    if [[ -n "${TERM_PROGRAM:-}" ]]; then
        echo "$TERM_PROGRAM"
        return
    fi

    echo "unknown"
}

TERMINAL=$(detect_terminal)

# Check if this terminal exists in emoji-widths.json
SUPPORTED=$(jq -r --arg term "$TERMINAL" '
    if .terminals[$term] then "yes" else "no" end
' "$WIDTHS_FILE" 2>/dev/null || echo "no")

if [[ "$SUPPORTED" == "no" ]]; then
    # Build warning message
    WARNING=$(cat << EOF
## Unsupported Terminal Detected

**Terminal:** $TERMINAL

This terminal has no measured emoji widths in \`emoji-widths.json\`.
Status displays will use default widths (emoji=2, symbol=1), which may cause alignment issues.

**To contribute measurements for your terminal:**

1. Run from an interactive terminal (not inside Claude Code):
   \`\`\`
   ./scripts/measure-emoji-widths.sh --json > my-terminal.json
   \`\`\`

2. Review the output to verify it detected your terminal correctly

3. Submit the measurements to the CAT project

Using default emoji widths.
EOF
)

    # Output as additionalContext
    jq -n --arg msg "$WARNING" '{
        "hookSpecificOutput": {
            "hookEventName": "SessionStart",
            "additionalContext": $msg
        }
    }'
else
    # Supported - no output needed
    echo '{}'
fi
