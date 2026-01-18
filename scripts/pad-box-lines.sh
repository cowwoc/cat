#!/bin/bash
# Pad box display lines with proper emoji-aware width calculation
#
# Usage: echo '<json>' | ./pad-box-lines.sh
#
# Input: JSON array of line objects:
#   [
#     {"content": "  ☑️ v0.1: Core parser (5/5)", "width": 56, "nest": 1},
#     {"content": "    🔳 pending-task-1", "width": 56, "nest": 1}
#   ]
#
# - content: Text between borders (no │ characters)
# - width: Target box width (72 for outer, 56 for nested)
# - nest: Nesting level (0=outer box only, 1=inside nested box, 2=outer+nested borders)
#
# Output: Padded lines with borders, one per line
#
# FAIL-FAST: Script fails if any emoji is not defined in emoji-widths.json

set -euo pipefail
trap 'echo "ERROR in pad-box-lines.sh line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WIDTHS_FILE="${SCRIPT_DIR}/../emoji-widths.json"

if [[ ! -f "$WIDTHS_FILE" ]]; then
    echo "ERROR: emoji-widths.json not found at $WIDTHS_FILE" >&2
    exit 1
fi

# Check if running in WSL
is_wsl() {
    [[ -f /proc/version ]] && grep -qi "microsoft\|wsl" /proc/version 2>/dev/null
}

# Detect current terminal and map to emoji-widths.json key
detect_terminal() {
    # Windows Terminal sets WT_SESSION
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

    # WSL: assume Windows Terminal (most common)
    if is_wsl; then
        echo "Windows Terminal"
        return
    fi

    # Fallback: use TERM_PROGRAM if set
    if [[ -n "${TERM_PROGRAM:-}" ]]; then
        echo "$TERM_PROGRAM"
        return
    fi

    # Final fallback
    echo "default"
}

TERMINAL=$(detect_terminal)

# Read input JSON from stdin
input=$(cat)

# Process with jq - calculate display width and pad each line
# Pass detected terminal as argument
jq -re --slurpfile widths "$WIDTHS_FILE" --arg terminal "$TERMINAL" '
# Get widths for this terminal, falling back to default section
def get_widths:
  if $terminal == "default" then
    $widths[0].default
  else
    ($widths[0].terminals[$terminal] // $widths[0].default)
  end;

# Store widths for lookups
get_widths as $w |

# Characters that are always width 1 (box drawing, ASCII punctuation used in boxes)
def is_box_char:
  . as $c |
  ($c == "─" or $c == "│" or $c == "╭" or $c == "╮" or $c == "╰" or $c == "╯" or
   $c == "├" or $c == "┤" or $c == "█" or $c == "░");

# Check if character is ASCII (0x00-0x7F)
def is_ascii:
  . as $c | ($c | explode | .[0]) < 128;

# Check if character is a variation selector (should be width 0)
def is_variation_selector:
  . == "\u200B" or . == "\uFE0F" or . == "\uFE0E";

# Calculate display width for a single character
# FAIL-FAST: error if emoji not in widths file
def char_width:
  . as $char |
  if is_variation_selector then 0
  elif is_box_char then 1
  elif is_ascii then 1
  elif $w[$char] != null then $w[$char]
  else
    # Check for combined emoji (char + variation selector)
    # These are stored as the combined string in the widths file
    error("Unknown character: \($char)\nTerminal: \(if $terminal == "default" then "unknown" else $terminal end)\n\nTo contribute width data for your terminal:\n  1. Download: https://github.com/cowsay-agent-template/cat/blob/main/scripts/measure-emoji-widths.sh\n  2. Run: ./measure-emoji-widths.sh\n  3. Submit your emoji-widths.json: https://github.com/cowsay-agent-template/cat/issues")
  end;

# Calculate display width of entire string
# Handle emoji sequences by checking for known emojis first
def display_width:
  # First, replace known emoji sequences with placeholder of correct width
  . as $str |
  # Build list of all emojis from widths file (sorted by length descending for greedy matching)
  ($w | keys | sort_by(- length)) as $emoji_list |

  # Replace each emoji with spaces matching its width
  reduce $emoji_list[] as $emoji (
    $str;
    gsub($emoji; " " * $w[$emoji])
  ) |

  # Now calculate width of remaining characters
  split("") | map(
    if . == " " then 1  # Spaces from emoji replacement
    elif is_variation_selector then 0
    elif is_box_char then 1
    elif is_ascii then 1
    else
      # Unknown non-ASCII character - fail fast
      error("Unknown character: \(.)\nTerminal: \(if $terminal == "default" then "unknown" else $terminal end)\n\nTo contribute width data for your terminal:\n  1. Download: https://github.com/cowsay-agent-template/cat/blob/main/scripts/measure-emoji-widths.sh\n  2. Run: ./measure-emoji-widths.sh\n  3. Submit your emoji-widths.json: https://github.com/cowsay-agent-template/cat/issues")
    end
  ) | add // 0;

# Process each line
.[] |
  .content as $content |
  .width as $target_width |
  (.nest // 0) as $nest |

  # Calculate display width of content
  ($content | display_width) as $display_width |

  # Calculate padding needed (subtract 2 for left/right borders)
  ([$target_width - 2 - $display_width, 0] | max) as $padding |

  # Build the padded content
  ($content + (" " * $padding)) as $padded |

  # Add borders based on nesting level
  if $nest == 0 then
    "│" + $padded + "│"
  elif $nest == 1 then
    "│  │" + $padded + "│          │"
  elif $nest == 2 then
    "│  " + $padded + "          │"
  else
    "│" + $padded + "│"
  end
' <<< "$input"
