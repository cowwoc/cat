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

# Detect current terminal and map to emoji-widths.json key
detect_terminal() {
    # Windows Terminal sets WT_SESSION
    if [[ -n "${WT_SESSION:-}" ]]; then
        echo "Windows Terminal"
        return
    fi

    # Check TERM_PROGRAM for common terminals
    case "${TERM_PROGRAM:-}" in
        "vscode"|"VSCode")
            echo "VS Code"
            return
            ;;
        "iTerm.app")
            echo "iTerm2"
            return
            ;;
        "Apple_Terminal")
            echo "macOS Terminal"
            return
            ;;
        "Hyper")
            echo "Hyper"
            return
            ;;
        "WarpTerminal")
            echo "Warp"
            return
            ;;
    esac

    # Check for other terminal-specific env vars
    if [[ -n "${KONSOLE_VERSION:-}" ]]; then
        echo "Konsole"
        return
    fi

    if [[ -n "${GNOME_TERMINAL_SCREEN:-}" ]]; then
        echo "GNOME Terminal"
        return
    fi

    if [[ -n "${KITTY_WINDOW_ID:-}" ]]; then
        echo "Kitty"
        return
    fi

    if [[ -n "${ALACRITTY_SOCKET:-}" || -n "${ALACRITTY_LOG:-}" ]]; then
        echo "Alacritty"
        return
    fi

    # Use default section
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
    error("Unknown character width for: \($char) (codepoint: \($char | explode)). Add to emoji-widths.json")
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
      error("Unknown character width for: \(.) - Add to emoji-widths.json")
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
