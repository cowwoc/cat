#!/bin/bash
# Centralized Box Rendering Library
#
# Provides reusable functions for rendering ASCII boxes with proper
# emoji-aware width calculation using Python.
#
# Created as part of M142 prevention - LLMs cannot reliably calculate
# character-level padding for Unicode text. This library centralizes
# the rendering logic for all CAT workflows.
#
# Usage:
#   source "${SCRIPT_DIR}/lib/box.sh"
#   box_init 74  # Set box width
#   box_top "Title"
#   box_line "Content with emojis"
#   box_bottom
#
# Requirements: Python 3

set -euo pipefail

# === BOX STATE ===
# These can be overridden after sourcing
BOX_WIDTH=${BOX_WIDTH:-74}
INNER_BOX_WIDTH=${INNER_BOX_WIDTH:-59}

# === INITIALIZATION ===

# Initialize box with optional custom width
box_init() {
    BOX_WIDTH="${1:-74}"
    INNER_BOX_WIDTH="${2:-$((BOX_WIDTH - 15))}"
}

# === PYTHON HELPER FOR WIDTH CALCULATION ===
# Uses Python for reliable Unicode width calculation
display_width() {
    local str="$1"
    python3 -c "
import unicodedata
s = '''$str'''
width = 0
i = 0
while i < len(s):
    char = s[i]
    # Skip variation selector-16 (counted with previous emoji)
    if char == '\ufe0f':
        i += 1
        continue
    # Check if next char is VS16 - if so, current is emoji width 2
    if i + 1 < len(s) and s[i+1] == '\ufe0f':
        width += 2
        i += 2
        continue
    ea = unicodedata.east_asian_width(char)
    if ea in ('W', 'F'):
        width += 2
    else:
        width += 1
    i += 1
print(width)
"
}

# Pad string to exact display width
pad() {
    local str="$1"
    local target="$2"
    local current
    current=$(display_width "$str")
    local padding=$((target - current))

    if [ $padding -gt 0 ]; then
        printf '%s%*s' "$str" $padding ""
    elif [ $padding -lt 0 ]; then
        # String too long - truncate (shouldn't happen normally)
        printf '%s' "$str"
    else
        printf '%s' "$str"
    fi
}

# === BORDER FUNCTIONS ===

# Print top border with optional title
box_top() {
    local title="${1:-}"
    if [ -n "$title" ]; then
        local title_part="─── ${title} "
        local title_width
        title_width=$(display_width "$title_part")
        local dash_count=$((BOX_WIDTH - 2 - title_width))
        local dashes=""
        for ((i=0; i<dash_count; i++)); do dashes="${dashes}─"; done
        printf '╭%s%s╮\n' "$title_part" "$dashes"
    else
        printf '╭'
        for ((i=0; i<BOX_WIDTH-2; i++)); do printf '─'; done
        printf '╮\n'
    fi
}

# Print bottom border
box_bottom() {
    printf '╰'
    for ((i=0; i<BOX_WIDTH-2; i++)); do printf '─'; done
    printf '╯\n'
}

# Print horizontal divider
box_divider() {
    printf '├'
    for ((i=0; i<BOX_WIDTH-2; i++)); do printf '─'; done
    printf '┤\n'
}

# === CONTENT LINE FUNCTIONS ===

# Print a content line with borders
box_line() {
    local content="$1"
    local inner_width=$((BOX_WIDTH - 2))
    local padded
    padded=$(pad "$content" $inner_width)
    printf '│%s│\n' "$padded"
}

# Print empty line
box_empty() {
    printf '│%*s│\n' $((BOX_WIDTH - 2)) ""
}

# === INNER BOX FUNCTIONS ===

# Print inner box top with title
inner_top() {
    local title="$1"
    local title_part="─── ${title} "
    local title_width
    title_width=$(display_width "$title_part")

    # Calculate remaining dashes for inner box width
    local dash_count=$((INNER_BOX_WIDTH - 2 - title_width))
    local dashes=""
    for ((i=0; i<dash_count; i++)); do dashes="${dashes}─"; done

    local inner="╭${title_part}${dashes}╮"
    box_line "  ${inner}"
}

# Print inner box bottom
inner_bottom() {
    local dashes=""
    for ((i=0; i<INNER_BOX_WIDTH-2; i++)); do dashes="${dashes}─"; done
    box_line "  ╰${dashes}╯"
}

# Print inner box content line
inner_line() {
    local content="$1"
    local inner_content_width=$((INNER_BOX_WIDTH - 4))  # -4 for "│  " and "│"
    local padded
    padded=$(pad "$content" $inner_content_width)
    box_line "  │  ${padded}│"
}

# Print inner empty line
inner_empty() {
    local spaces=""
    for ((i=0; i<INNER_BOX_WIDTH-2; i++)); do spaces="${spaces} "; done
    box_line "  │${spaces}│"
}

# === PROGRESS BAR ===

# Generate progress bar string
# Usage: progress_bar PERCENTAGE [WIDTH]
progress_bar() {
    local pct="$1"
    local bar_width="${2:-45}"
    local filled=$((pct * bar_width / 100))
    local unfilled=$((bar_width - filled))

    local bar="["
    for ((i=0; i<filled; i++)); do bar="${bar}█"; done
    for ((i=0; i<unfilled; i++)); do bar="${bar}░"; done
    bar="${bar}]"
    printf '%s' "$bar"
}

# === UTILITY FUNCTIONS ===

# Generate dashes of specified count
dashes() {
    local count="$1"
    local result=""
    for ((i=0; i<count; i++)); do result="${result}─"; done
    printf '%s' "$result"
}

# Generate spaces of specified count
spaces() {
    local count="$1"
    printf '%*s' "$count" ""
}

# === CONVENIENCE FUNCTIONS ===

# Print a simple box with title and content lines
# Usage: simple_box "Title" "Line 1" "Line 2" ...
simple_box() {
    local title="$1"
    shift

    box_top "$title"
    box_empty
    for line in "$@"; do
        box_line "  $line"
    done
    box_empty
    box_bottom
}

# Print a checkpoint-style box
# Usage: checkpoint_box "CHECKPOINT_TYPE" "Task Name" "Key1" "Value1" "Key2" "Value2" ...
checkpoint_box() {
    local checkpoint_type="$1"
    local task_name="$2"
    shift 2

    box_top "$checkpoint_type"
    box_empty
    box_line "  Task: $task_name"
    box_empty
    box_divider

    while [ $# -ge 2 ]; do
        local key="$1"
        local value="$2"
        shift 2
        box_line "  $key: $value"
    done

    box_empty
    box_bottom
}

# =============================================================================
# SERVICE MODE
# When executed directly (not sourced), parse arguments and render from JSON
# =============================================================================

# Pad cell content to exact display width (for table cells)
_pad_cell() {
    local content="$1"
    local width="$2"
    local display_w
    display_w=$(display_width "$content")
    local padding=$((width - display_w))
    if [ $padding -lt 0 ]; then
        padding=0
    fi
    printf '%s%*s' "$content" "$padding" ""
}

# Render a table from JSON data
# JSON format: {"headers": [...], "rows": [[...], ...], "widths": [...], "footer": [...]}
render_table_from_json() {
    local json="$1"

    # Extract data using jq
    local num_cols
    num_cols=$(echo "$json" | jq -r '.widths | length')

    # Build top border
    local top_border="╭"
    local header_sep="├"
    local footer_sep="├"
    local bottom_border="╰"

    for ((i=0; i<num_cols; i++)); do
        local width
        width=$(echo "$json" | jq -r ".widths[$i]")
        local dash_str
        dash_str=$(dashes "$width")

        if [ $i -lt $((num_cols - 1)) ]; then
            top_border="${top_border}${dash_str}┬"
            header_sep="${header_sep}${dash_str}┼"
            footer_sep="${footer_sep}${dash_str}┼"
            bottom_border="${bottom_border}${dash_str}┴"
        else
            top_border="${top_border}${dash_str}╮"
            header_sep="${header_sep}${dash_str}┤"
            footer_sep="${footer_sep}${dash_str}┤"
            bottom_border="${bottom_border}${dash_str}╯"
        fi
    done

    # Print top border
    echo "$top_border"

    # Print header row
    local header_line="│"
    for ((i=0; i<num_cols; i++)); do
        local header
        header=$(echo "$json" | jq -r ".headers[$i]")
        local width
        width=$(echo "$json" | jq -r ".widths[$i]")
        header_line="${header_line}$(_pad_cell " $header" "$width")│"
    done
    echo "$header_line"

    # Print header separator
    echo "$header_sep"

    # Print data rows
    local num_rows
    num_rows=$(echo "$json" | jq -r '.rows | length')

    for ((r=0; r<num_rows; r++)); do
        local row_line="│"
        for ((i=0; i<num_cols; i++)); do
            local cell
            cell=$(echo "$json" | jq -r ".rows[$r][$i]")
            local width
            width=$(echo "$json" | jq -r ".widths[$i]")
            row_line="${row_line}$(_pad_cell " $cell" "$width")│"
        done
        echo "$row_line"
    done

    # Check for footer
    local has_footer
    has_footer=$(echo "$json" | jq -r 'if .footer then "yes" else "no" end')

    if [ "$has_footer" = "yes" ]; then
        # Print footer separator
        echo "$footer_sep"

        # Print footer row
        local footer_line="│"
        for ((i=0; i<num_cols; i++)); do
            local cell
            cell=$(echo "$json" | jq -r ".footer[$i]")
            local width
            width=$(echo "$json" | jq -r ".widths[$i]")
            footer_line="${footer_line}$(_pad_cell " $cell" "$width")│"
        done
        echo "$footer_line"
    fi

    # Print bottom border
    echo "$bottom_border"
}

# Render a simple box from JSON data
# JSON format: {"title": "...", "lines": [...], "width": 74}
render_box_from_json() {
    local json="$1"

    local title
    title=$(echo "$json" | jq -r '.title // ""')
    local width
    width=$(echo "$json" | jq -r '.width // 74')

    box_init "$width"
    box_top "$title"
    box_empty

    # Print each line
    local num_lines
    num_lines=$(echo "$json" | jq -r '.lines | length')

    for ((i=0; i<num_lines; i++)); do
        local line
        line=$(echo "$json" | jq -r ".lines[$i]")
        box_line "  $line"
    done

    box_empty
    box_bottom
}

# Service mode activation - only runs when script is executed, not sourced
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    case "${1:-}" in
        table)
            if [ -z "${2:-}" ]; then
                echo "ERROR: JSON data required" >&2
                exit 1
            fi
            render_table_from_json "$2"
            ;;
        box)
            if [ -z "${2:-}" ]; then
                echo "ERROR: JSON data required" >&2
                exit 1
            fi
            render_box_from_json "$2"
            ;;
        *)
            echo "Usage: box.sh <table|box> '<json>'" >&2
            echo "" >&2
            echo "Table JSON: {\"headers\": [...], \"widths\": [...], \"rows\": [[...], ...], \"footer\": [...]}" >&2
            echo "Box JSON:   {\"title\": \"...\", \"lines\": [...], \"width\": 74}" >&2
            exit 1
            ;;
    esac
fi
