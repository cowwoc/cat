#!/bin/bash
# Pad status display lines with proper emoji-aware width calculation
#
# Usage: echo '<json>' | ./pad-status-lines.sh
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

set -euo pipefail
trap 'echo "ERROR in pad-status-lines.sh line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WIDTHS_FILE="${SCRIPT_DIR}/../emoji-widths.json"

if [[ ! -f "$WIDTHS_FILE" ]]; then
    echo "ERROR: emoji-widths.json not found at $WIDTHS_FILE" >&2
    exit 1
fi

# Read input JSON from stdin
input=$(cat)

# Process with jq - calculate display width and pad each line
jq -r --slurpfile widths "$WIDTHS_FILE" '
# Function to get emoji width from widths file
def emoji_width(char):
  ($widths[0].default[char] // $widths[0].terminals["Windows Terminal"][char] // 2);

# Function to calculate display width of a string
def display_width:
  # Split into characters and sum widths
  split("") | map(
    if . == "☑" or . == "️" then 0  # Variation selector handled with base
    elif . == "☑️" then 2
    elif . == "🔄" or . == "🔳" or . == "🚫" or . == "🚧" then 2
    elif . == "📊" or . == "📦" or . == "🎯" or . == "📋" then 2
    elif . == "⚙" or . == "🏆" or . == "🐱" then 2
    elif . == "✓" or . == "✗" or . == "→" or . == "•" then 1
    elif . == "▸" or . == "▹" or . == "◆" or . == "⚠" then 1
    elif . == "█" or . == "░" then 1
    elif . == "─" or . == "│" or . == "╭" or . == "╮" or . == "╰" or . == "╯" then 1
    elif . == "├" or . == "┤" then 1
    elif (. | test("^[\u0000-\u007F]$")) then 1  # ASCII
    elif (. | test("^[\u0080-\u00FF]$")) then 1  # Latin-1
    elif . == "\uFE0F" then 0  # Variation selector-16
    else 2  # Default for unknown (likely emoji)
    end
  ) | add // 0;

# Function to handle emoji sequences (combined characters)
def adjusted_display_width:
  # Handle common emoji sequences
  gsub("☑️"; "XX") |      # Replace 2-char emoji with 2 Xs
  gsub("⚙️"; "XX") |      # Replace 2-char emoji with 2 Xs
  gsub("🔄"; "XX") |
  gsub("🔳"; "XX") |
  gsub("🚫"; "XX") |
  gsub("🚧"; "XX") |
  gsub("📊"; "XX") |
  gsub("📦"; "XX") |
  gsub("🎯"; "XX") |
  gsub("📋"; "XX") |
  gsub("🏆"; "XX") |
  gsub("🐱"; "XX") |
  # Single-width symbols stay as single char
  gsub("✓"; "X") |
  gsub("✗"; "X") |
  gsub("→"; "X") |
  gsub("•"; "X") |
  gsub("▸"; "X") |
  gsub("▹"; "X") |
  gsub("◆"; "X") |
  gsub("⚠"; "X") |
  # Now count length (all replaced chars are ASCII)
  length;

# Process each line
.[] |
  .content as $content |
  .width as $target_width |
  (.nest // 0) as $nest |

  # Calculate display width of content
  ($content | adjusted_display_width) as $display_width |

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
