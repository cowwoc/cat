#!/bin/bash
# Hook: echo-emoji-lengths.sh
# Trigger: SessionStart
# Purpose: Detect terminal type, look up emoji widths, and output them for Claude to use

set -euo pipefail

# Read stdin (required for SessionStart hooks)
if [ -t 0 ]; then
    exit 0
fi

stdin_content=$(cat)

if [ -z "$stdin_content" ]; then
    exit 0
fi

#=============================================================================
# TERMINAL DETECTION (extracted from measure-emoji-widths.sh)
#=============================================================================

# Check if running in WSL
is_wsl() {
    [[ -f /proc/version ]] && grep -qi "microsoft\|wsl" /proc/version 2>/dev/null
}

# Get Windows parent process name via PowerShell (for WSL)
get_windows_parent_terminal() {
    if ! command -v powershell.exe &>/dev/null; then
        return 1
    fi

    local result
    result=$(timeout 3 powershell.exe -NoProfile -Command '
        $p = Get-Process -Id $PID 2>$null
        while ($p -and $p.ProcessName -match "^(wsl|bash|sh|zsh|fish|pwsh|powershell|conhost)$") {
            $p = Get-Process -Id $p.Parent.Id 2>$null
        }
        if ($p) { $p.ProcessName }
    ' 2>/dev/null | tr -d '\r\n')

    case "$result" in
        WindowsTerminal) echo "Windows Terminal"; return 0 ;;
        Code) echo "vscode"; return 0 ;;
        ConEmu*) echo "ConEmu"; return 0 ;;
        mintty) echo "mintty"; return 0 ;;
        *) return 1 ;;
    esac
}

detect_terminal() {
    # 1. Windows Terminal
    if [[ -n "${WT_SESSION:-}" ]]; then
        echo "Windows Terminal"
        return
    fi

    # 2. VS Code
    if [[ "${TERM_PROGRAM:-}" == "vscode" ]] || [[ -n "${VSCODE_INJECTION:-}" ]]; then
        echo "vscode"
        return
    fi

    # 3. iTerm2
    if [[ "${TERM_PROGRAM:-}" == "iTerm.app" ]] || [[ -n "${ITERM_SESSION_ID:-}" ]]; then
        echo "iTerm.app"
        return
    fi

    # 4. Apple Terminal
    if [[ "${TERM_PROGRAM:-}" == "Apple_Terminal" ]]; then
        echo "Apple_Terminal"
        return
    fi

    # 5. Konsole
    if [[ -n "${KONSOLE_VERSION:-}" ]]; then
        echo "konsole"
        return
    fi

    # 6. GNOME Terminal
    if [[ -n "${GNOME_TERMINAL_SCREEN:-}" ]] || [[ -n "${VTE_VERSION:-}" ]]; then
        echo "gnome-terminal"
        return
    fi

    # 7. Kitty
    if [[ -n "${KITTY_WINDOW_ID:-}" ]]; then
        echo "kitty"
        return
    fi

    # 8. WezTerm
    if [[ -n "${WEZTERM_PANE:-}" ]]; then
        echo "WezTerm"
        return
    fi

    # 9. Alacritty
    if [[ -n "${ALACRITTY_SOCKET:-}" ]] || [[ -n "${ALACRITTY_LOG:-}" ]]; then
        echo "Alacritty"
        return
    fi

    # 10. ConEmu
    if [[ -n "${ConEmuPID:-}" ]]; then
        echo "ConEmu"
        return
    fi

    # 11. Hyper
    if [[ "${TERM_PROGRAM:-}" == "Hyper" ]]; then
        echo "Hyper"
        return
    fi

    # 12. WSL fallback
    if is_wsl; then
        local win_terminal
        if win_terminal=$(get_windows_parent_terminal 2>/dev/null); then
            echo "$win_terminal"
            return
        fi
        echo "Windows Terminal"
        return
    fi

    # 13. TERM_PROGRAM fallback
    if [[ -n "${TERM_PROGRAM:-}" ]]; then
        echo "$TERM_PROGRAM"
        return
    fi

    # 14. Unknown
    echo "unknown"
}

#=============================================================================
# MAIN
#=============================================================================

terminal=$(detect_terminal)

# Look up emoji widths for this terminal
EMOJI_WIDTHS_FILE="${CLAUDE_PLUGIN_ROOT}/emoji-widths.json"

if [[ ! -f "$EMOJI_WIDTHS_FILE" ]]; then
    # Fallback if file doesn't exist
    jq -n --arg terminal "$terminal" '{
        "hookSpecificOutput": {
            "hookEventName": "SessionStart",
            "additionalContext": "Terminal type: \($terminal)\nWarning: emoji-widths.json not found"
        }
    }'
    exit 0
fi

# Check if terminal has mappings in emoji-widths.json
terminal_widths=$(jq -r --arg term "$terminal" '.terminals[$term] // empty' "$EMOJI_WIDTHS_FILE")

if [[ -z "$terminal_widths" ]]; then
    # Terminal not found - instruct Claude to tell user to run measurement script
    jq -n --arg terminal "$terminal" --arg plugin_root "\${CLAUDE_PLUGIN_ROOT}" '{
        "hookSpecificOutput": {
            "hookEventName": "SessionStart",
            "additionalContext": ("Terminal type: " + $terminal + "\n\nWARNING: No emoji width mappings found for this terminal.\n\nTell the user:\n\n   Please run the measurement script on your terminal and report the results:\n\n   1. Open an issue at: https://github.com/cowwoc/cat/issues/new\n   2. Run this command in the same terminal you use for Claude Code:\n      " + $plugin_root + "/scripts/measure-emoji-widths.sh --json\n   3. Paste the JSON output into the issue")
        }
    }'
    exit 0
fi

# Build emoji width instructions for Claude
# Format: "emoji=width" pairs, one per line for readability
width_instructions=$(jq -r --arg term "$terminal" '
    .terminals[$term] | to_entries |
    map("\(.key)=\(.value)") |
    join(", ")
' "$EMOJI_WIDTHS_FILE")

# Output the actual widths to use
jq -n --arg terminal "$terminal" --arg widths "$width_instructions" '{
    "hookSpecificOutput": {
        "hookEventName": "SessionStart",
        "additionalContext": ("Terminal type: " + $terminal + "\n\nEmoji display widths for box alignment (use these values when calculating display width):\n" + $widths + "\n\nAll other characters (ASCII, box-drawing │╭╮╰╯─) have width 1.")
    }
}'
