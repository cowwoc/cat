#!/bin/bash
# Measure emoji display widths for the current terminal
#
# Usage: ./measure-emoji-widths.sh [--json] [--detect-only]
#
# This script measures how many terminal columns each emoji occupies
# by using ANSI cursor position queries. Results can be used to build
# the emoji-widths.json lookup table.
#
# Requirements:
# - Must run in an interactive terminal (not piped) for measurement
# - --detect-only works without interactive terminal

set -euo pipefail

# Emojis used in CAT status displays and config
EMOJIS=(
    # Status indicators
    "☑️"    # Completed (check box)
    "🔄"    # In progress
    "🔳"    # Pending
    "🚫"    # Blocked
    "🚧"    # Gate waiting
    "📊"    # Progress/stats
    "📦"    # Package/version
    "🎯"    # Target/quest
    "📋"    # Clipboard/tasks
    "⚙️"    # Settings/mode
    "🏆"    # Trophy/count
    # Config settings
    "🧠"    # Context/brain
    "🐱"    # CAT behavior
    "🧹"    # Cleanup
    "🤝"    # Trust
    "✅"    # Verify
    "🔍"    # Curiosity (low)
    "👀"    # Curiosity (medium)
    "🔭"    # Curiosity (high)
    "⏳"    # Patience
    "⚡"    # Fast/none
    "🔒"    # Secure/all
    "✨"    # Sparkles
    "⚠️"    # Warning (with variation selector)
    # Simple characters
    "✓"     # Simple checkmark
    "✗"     # Simple X
    "→"     # Arrow
    "•"     # Bullet
    "▸"     # Triangle
    "▹"     # Triangle hollow
    "◆"     # Diamond
    "⚠"     # Warning (without variation selector)
)

JSON_OUTPUT=false
DETECT_ONLY=false
SHOW_HELP=false

for arg in "$@"; do
    case "$arg" in
        --json) JSON_OUTPUT=true ;;
        --detect-only) DETECT_ONLY=true ;;
        --help|-h) SHOW_HELP=true ;;
    esac
done

if $SHOW_HELP; then
    cat << 'EOF'
Usage: measure-emoji-widths.sh [OPTIONS]

Measure emoji display widths for the current terminal.

OPTIONS:
  --detect-only    Only detect terminal type (no measurement, works without tty)
  --json           Output in JSON format
  --help, -h       Show this help message

EXAMPLES:
  # Detect terminal (works anywhere)
  ./measure-emoji-widths.sh --detect-only

  # Measure widths (requires interactive terminal)
  ./measure-emoji-widths.sh

  # Save measurements to file
  ./measure-emoji-widths.sh --json > measurements.json

NOTE: Width measurement requires an interactive terminal with /dev/tty.
      Run --detect-only if measurement hangs or fails.
EOF
    exit 0
fi

# Check if /dev/tty is accessible (only needed for measurement mode)
check_tty() {
    if [[ -e /dev/tty ]] && [[ -c /dev/tty ]]; then
        # Use timeout to prevent hanging
        if timeout 1 bash -c 'echo -n "" > /dev/tty' 2>/dev/null; then
            return 0
        fi
    fi
    return 1
}

TTY_AVAILABLE=false
if ! $DETECT_ONLY; then
    if check_tty; then
        TTY_AVAILABLE=true
    else
        echo "ERROR: /dev/tty not accessible (running in non-interactive environment?)" >&2
        echo "Use --detect-only to just detect terminal without measuring." >&2
        exit 1
    fi
fi

#=============================================================================
# TERMINAL DETECTION
# Priority order uses most reliable method for each terminal
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

    # Query Windows process tree for terminal (with timeout)
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

# Detect terminal emulator - returns canonical name
detect_terminal() {
    #---------------------------------------------------------------------------
    # 1. Windows Terminal: Check WT_SESSION (set when running in WT)
    #---------------------------------------------------------------------------
    if [[ -n "${WT_SESSION:-}" ]]; then
        echo "Windows Terminal"
        return
    fi

    #---------------------------------------------------------------------------
    # 2. VS Code: Check TERM_PROGRAM or VSCODE_* variables
    #---------------------------------------------------------------------------
    if [[ "${TERM_PROGRAM:-}" == "vscode" ]] || [[ -n "${VSCODE_INJECTION:-}" ]]; then
        echo "vscode"
        return
    fi

    #---------------------------------------------------------------------------
    # 3. iTerm2: Check TERM_PROGRAM or ITERM_SESSION_ID
    #---------------------------------------------------------------------------
    if [[ "${TERM_PROGRAM:-}" == "iTerm.app" ]] || [[ -n "${ITERM_SESSION_ID:-}" ]]; then
        echo "iTerm.app"
        return
    fi

    #---------------------------------------------------------------------------
    # 4. Apple Terminal: Check TERM_PROGRAM
    #---------------------------------------------------------------------------
    if [[ "${TERM_PROGRAM:-}" == "Apple_Terminal" ]]; then
        echo "Apple_Terminal"
        return
    fi

    #---------------------------------------------------------------------------
    # 5. Konsole: Check KONSOLE_VERSION
    #---------------------------------------------------------------------------
    if [[ -n "${KONSOLE_VERSION:-}" ]]; then
        echo "konsole"
        return
    fi

    #---------------------------------------------------------------------------
    # 6. GNOME Terminal: Check GNOME_TERMINAL_SCREEN or VTE_VERSION
    #---------------------------------------------------------------------------
    if [[ -n "${GNOME_TERMINAL_SCREEN:-}" ]] || [[ -n "${VTE_VERSION:-}" ]]; then
        echo "gnome-terminal"
        return
    fi

    #---------------------------------------------------------------------------
    # 7. Kitty: Check KITTY_WINDOW_ID
    #---------------------------------------------------------------------------
    if [[ -n "${KITTY_WINDOW_ID:-}" ]]; then
        echo "kitty"
        return
    fi

    #---------------------------------------------------------------------------
    # 8. WezTerm: Check WEZTERM_PANE
    #---------------------------------------------------------------------------
    if [[ -n "${WEZTERM_PANE:-}" ]]; then
        echo "WezTerm"
        return
    fi

    #---------------------------------------------------------------------------
    # 9. Alacritty: Check ALACRITTY_SOCKET or ALACRITTY_LOG
    #---------------------------------------------------------------------------
    if [[ -n "${ALACRITTY_SOCKET:-}" ]] || [[ -n "${ALACRITTY_LOG:-}" ]]; then
        echo "Alacritty"
        return
    fi

    #---------------------------------------------------------------------------
    # 10. ConEmu: Check ConEmuPID
    #---------------------------------------------------------------------------
    if [[ -n "${ConEmuPID:-}" ]]; then
        echo "ConEmu"
        return
    fi

    #---------------------------------------------------------------------------
    # 11. Hyper: Check TERM_PROGRAM
    #---------------------------------------------------------------------------
    if [[ "${TERM_PROGRAM:-}" == "Hyper" ]]; then
        echo "Hyper"
        return
    fi

    #---------------------------------------------------------------------------
    # 12. WSL: Try PowerShell to detect Windows terminal
    #---------------------------------------------------------------------------
    if is_wsl; then
        local win_terminal
        if win_terminal=$(get_windows_parent_terminal 2>/dev/null); then
            echo "$win_terminal"
            return
        fi
        # WSL but couldn't detect specific terminal
        echo "Windows Terminal"  # Most likely if in WSL
        return
    fi

    #---------------------------------------------------------------------------
    # 13. Fallback: Use TERM_PROGRAM if set
    #---------------------------------------------------------------------------
    if [[ -n "${TERM_PROGRAM:-}" ]]; then
        echo "$TERM_PROGRAM"
        return
    fi

    #---------------------------------------------------------------------------
    # 14. Final fallback: unknown
    #---------------------------------------------------------------------------
    echo "unknown"
}


#=============================================================================
# WIDTH MEASUREMENT (requires /dev/tty)
#=============================================================================

# Get cursor position using ANSI DSR (Device Status Report)
# Returns column number or empty string on failure
get_cursor_col() {
    local pos col
    local old_stty
    local char

    # Save and set terminal mode
    old_stty=$(stty -g -F /dev/tty 2>/dev/null || stty -g < /dev/tty) || return 1
    stty raw -echo -F /dev/tty 2>/dev/null || stty raw -echo < /dev/tty || return 1

    # Request cursor position
    printf '\e[6n' > /dev/tty

    # Read response with timeout (ESC [ row ; col R)
    pos=""
    local count=0
    while IFS= read -r -n1 -t2 char < /dev/tty; do
        pos+="$char"
        [[ "$char" == "R" ]] && break
        ((count++))
        # Safety limit
        if ((count > 20)); then
            break
        fi
    done

    # Restore terminal
    stty "$old_stty" -F /dev/tty 2>/dev/null || stty "$old_stty" < /dev/tty

    # Parse response
    if [[ "$pos" =~ \[([0-9]+)\;([0-9]+)R ]]; then
        echo "${BASH_REMATCH[2]}"
    else
        echo ""
    fi
}

# Test if terminal responds to cursor position queries
test_terminal_response() {
    local result
    result=$(get_cursor_col)
    [[ -n "$result" ]] && [[ "$result" != "0" ]]
}

# Measure width of a string
measure_width() {
    local str="$1"
    local start_col end_col width

    printf '\r' > /dev/tty
    start_col=$(get_cursor_col)
    if [[ -z "$start_col" ]]; then
        echo "2"  # Default fallback
        return
    fi
    printf '%s' "$str" > /dev/tty
    end_col=$(get_cursor_col)
    if [[ -z "$end_col" ]]; then
        printf '\r\e[K' > /dev/tty
        echo "2"  # Default fallback
        return
    fi
    width=$((end_col - start_col))
    printf '\r\e[K' > /dev/tty

    echo "$width"
}

#=============================================================================
# MAIN
#=============================================================================

main() {
    local terminal

    terminal=$(detect_terminal)

    if $DETECT_ONLY; then
        if $JSON_OUTPUT; then
            echo "{"
            echo "  \"terminal\": \"$terminal\""
            echo "}"
        else
            echo "Terminal: $terminal"
        fi
        return
    fi

    # Measurement mode - test if terminal responds first
    echo "Testing terminal response..." >&2
    if ! test_terminal_response; then
        echo "ERROR: Terminal does not respond to cursor position queries." >&2
        echo "This can happen if:" >&2
        echo "  - Running inside Claude Code (use --detect-only instead)" >&2
        echo "  - Output is redirected (run without redirection first)" >&2
        echo "  - Terminal doesn't support ANSI escape sequences" >&2
        exit 1
    fi
    echo "Terminal responds. Measuring emoji widths..." >&2
    if $JSON_OUTPUT; then
        echo "{"
        echo "  \"terminal\": \"$terminal\","
        echo "  \"widths\": {"

        # Collect all measurements first, then output JSON
        # (avoids /dev/tty escape sequences interfering with stdout)
        local -a measurements=()
        for emoji in "${EMOJIS[@]}"; do
            local width
            width=$(measure_width "$emoji")
            measurements+=("$emoji:$width")
        done

        # Now output JSON without any /dev/tty interference
        local first=true
        for entry in "${measurements[@]}"; do
            local emoji="${entry%:*}"
            local width="${entry#*:}"

            if $first; then
                first=false
            else
                echo ","
            fi

            printf '    "%s": %d' "$emoji" "$width"
        done

        echo ""
        echo "  }"
        echo "}"
    else
        echo "Emoji Width Measurement"
        echo "======================="
        echo "Terminal: $terminal"
        echo ""
        echo "Widths:"
        echo "-------"

        for emoji in "${EMOJIS[@]}"; do
            local width
            width=$(measure_width "$emoji")
            printf '%s = %d\n' "$emoji" "$width"
        done

        echo ""
        echo "Run with --json for machine-readable output"
    fi
}

main "$@"
