#!/usr/bin/env bash
# render-diff.sh - Transform git diff to 4-column table format
# Optimized for approval gate reviews
#
# Usage: render-diff.sh [diff-file]
#   diff-file: Path to diff file (default: reads from stdin)
#
# Example:
#   git diff main..HEAD | ./render-diff.sh
#   ./render-diff.sh diff-output.txt

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Calculate display width of a string (handles Unicode, emojis, wide chars)
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

# Read terminal width from config
CONFIG_FILE="${CLAUDE_PROJECT_DIR:-.}/.claude/cat/cat-config.json"
WIDTH=$(jq -r '.terminalWidth // 50' "$CONFIG_FILE" 2>/dev/null || echo "50")

# Column widths (fixed)
COL_OLD=4      # Old line number
COL_SYM=3      # Symbol (+/-)
COL_NEW=4      # New line number

# Calculate content width: total - borders - col widths - internal padding
# │Old │ x │New │ Content                                         │
# 1   4  1 1 1 1 1  4  1 1      CONTENT_WIDTH                     1 = 17 fixed chars
CONTENT_WIDTH=$((WIDTH - 17))

# Parse arguments
DIFF_INPUT="${1:-/dev/stdin}"

# Read diff content
if [[ "$DIFF_INPUT" == "/dev/stdin" ]]; then
    DIFF_CONTENT=$(cat)
else
    if [[ ! -f "$DIFF_INPUT" ]]; then
        echo "ERROR: Diff file not found: $DIFF_INPUT" >&2
        exit 1
    fi
    DIFF_CONTENT=$(cat "$DIFF_INPUT")
fi

# Handle empty diff
if [[ -z "$DIFF_CONTENT" ]]; then
    echo "No changes to display."
    exit 0
fi

# Track used symbols for legend
USED_MINUS=false
USED_PLUS=false
USED_SPACE=false
USED_TAB=false
USED_WRAP=false
USED_BRACKET=false

# File type detection for wrapping behavior
PROSE_EXTENSIONS="md|txt|license|LICENSE"
CODE_EXTENSIONS="js|ts|jsx|tsx|java|py|sh|go|rs|c|cpp|h|hpp|rb|php|swift|kt|scala|cs"
CONFIG_EXTENSIONS="json|yaml|yml|toml|xml|ini|conf|cfg"

# Detect if file is prose (should wrap)
is_prose_file() {
    local filename="$1"
    local ext="${filename##*.}"
    [[ "$ext" =~ ^($PROSE_EXTENSIONS)$ ]]
}

# === HELPER FUNCTIONS ===

# Generate repeated characters
fill_char() {
    local char="$1"
    local count="$2"
    if [[ "$count" -le 0 ]]; then
        return
    fi
    for ((i=0; i<count; i++)); do
        printf '%s' "$char"
    done
}

# Pad number to width (right-aligned)
pad_num() {
    local num="$1"
    local width="$2"
    printf '%*s' "$width" "$num"
}

# Truncate string to target display width
truncate_to_width() {
    local str="$1"
    local target_width="$2"
    python3 -c "
import unicodedata
s = '''$str'''
target = $target_width
result = ''
width = 0
for char in s:
    ea = unicodedata.east_asian_width(char)
    char_width = 2 if ea in ('W', 'F') else 1
    if width + char_width > target:
        break
    result += char
    width += char_width
print(result, end='')
"
}

# Check if a line change is whitespace-only
is_whitespace_only_change() {
    local old_line="$1"
    local new_line="$2"
    local old_stripped="${old_line//[[:space:]]/}"
    local new_stripped="${new_line//[[:space:]]/}"
    [[ "$old_stripped" == "$new_stripped" ]]
}

# Make whitespace visible
visualize_whitespace() {
    local line="$1"
    local result=""
    i=0
    local len=${#line}

    while [[ $i -lt $len ]]; do
        local char="${line:$i:1}"
        if [[ "$char" == $'\t' ]]; then
            result+="→"
            USED_TAB=true
        elif [[ "$char" == " " ]]; then
            result+="·"
            USED_SPACE=true
        else
            result+="$char"
        fi
        ((i++)) || true
    done
    echo "$result"
}

# Find common prefix length between two strings
common_prefix_len() {
    local s1="$1"
    local s2="$2"
    local len=0
    local max_len=${#s1}
    [[ ${#s2} -lt $max_len ]] && max_len=${#s2}

    while [[ $len -lt $max_len ]] && [[ "${s1:$len:1}" == "${s2:$len:1}" ]]; do
        ((len++))
    done
    echo "$len"
}

# Find common suffix length between two strings
common_suffix_len() {
    local s1="$1"
    local s2="$2"
    local len=0
    local len1=${#s1}
    local len2=${#s2}

    while [[ $len -lt $len1 ]] && [[ $len -lt $len2 ]] && \
          [[ "${s1:$((len1-len-1)):1}" == "${s2:$((len2-len-1)):1}" ]]; do
        ((len++))
    done
    echo "$len"
}

# Apply word-level diff highlighting with [] brackets
# Returns highlighted version of line, or original if no good diff found
highlight_word_diff() {
    local old_line="$1"
    local new_line="$2"
    local is_old="$3"  # "old" or "new"

    local prefix_len suffix_len
    prefix_len=$(common_prefix_len "$old_line" "$new_line")
    suffix_len=$(common_suffix_len "$old_line" "$new_line")

    local old_len=${#old_line}
    local new_len=${#new_line}

    # Avoid overlap
    local old_diff_len=$((old_len - prefix_len - suffix_len))
    local new_diff_len=$((new_len - prefix_len - suffix_len))

    # Skip if entire line changed or no meaningful diff
    if [[ $prefix_len -eq 0 ]] && [[ $suffix_len -eq 0 ]]; then
        if [[ "$is_old" == "old" ]]; then
            echo "$old_line"
        else
            echo "$new_line"
        fi
        return
    fi

    # Skip if diff portion is too small or too large
    if [[ $old_diff_len -le 0 ]] || [[ $new_diff_len -le 0 ]]; then
        if [[ "$is_old" == "old" ]]; then
            echo "$old_line"
        else
            echo "$new_line"
        fi
        return
    fi

    USED_BRACKET=true

    if [[ "$is_old" == "old" ]]; then
        local prefix="${old_line:0:$prefix_len}"
        local diff_part="${old_line:$prefix_len:$old_diff_len}"
        local suffix="${old_line:$((prefix_len + old_diff_len))}"
        echo "${prefix}[${diff_part}]${suffix}"
    else
        local prefix="${new_line:0:$prefix_len}"
        local diff_part="${new_line:$prefix_len:$new_diff_len}"
        local suffix="${new_line:$((prefix_len + new_diff_len))}"
        echo "${prefix}[${diff_part}]${suffix}"
    fi
}

# === BOX RENDERING HELPERS ===

# Print hunk box top with file header
print_hunk_top() {
    local filename="$1"
    local file_text="FILE: ${filename}"
    local file_len
    file_len=$(display_width "$file_text")
    local padding=$((WIDTH - 4 - file_len))

    echo "╭$(fill_char '─' $((WIDTH - 2)))╮"
    printf '│ %s%*s │\n' "$file_text" "$padding" ""
}

# Print column header row with hunk context
print_column_header() {
    local context="$1"

    # Separator after file header
    echo "├$(fill_char '─' $COL_OLD)┬$(fill_char '─' $COL_SYM)┬$(fill_char '─' $COL_NEW)┬$(fill_char '─' $((CONTENT_WIDTH + 1)))┤"

    # Header row with context
    local context_text=""
    if [[ -n "$context" ]]; then
        context_text="⌁ ${context}"
    fi

    # Truncate context if too long (by display width, not char count)
    local ctx_len
    ctx_len=$(display_width "$context_text")
    if [[ $ctx_len -gt $CONTENT_WIDTH ]]; then
        context_text=$(truncate_to_width "$context_text" $((CONTENT_WIDTH - 1)))"…"
    fi

    printf '│%s│%s│%s│ %-*s│\n' \
        "$(pad_num "Old" $COL_OLD)" \
        "   " \
        "$(pad_num "New" $COL_NEW)" \
        "$CONTENT_WIDTH" "$context_text"

    # Separator after headers
    echo "├$(fill_char '─' $COL_OLD)┼$(fill_char '─' $COL_SYM)┼$(fill_char '─' $COL_NEW)┼$(fill_char '─' $((CONTENT_WIDTH + 1)))┤"
}

# Print content row
print_row() {
    local old_num="$1"
    local symbol="$2"
    local new_num="$3"
    local content="$4"

    printf '│%s│ %s │%s│ ' \
        "$(pad_num "$old_num" $COL_OLD)" \
        "$symbol" \
        "$(pad_num "$new_num" $COL_NEW)"

    local content_len
    content_len=$(display_width "$content")

    if [[ $content_len -le $CONTENT_WIDTH ]]; then
        printf '%-*s│\n' "$CONTENT_WIDTH" "$content"
    else
        # Wrap long lines
        local first_part="${content:0:$((CONTENT_WIDTH - 1))}"
        printf '%s↩│\n' "$first_part"
        USED_WRAP=true

        local remaining="${content:$((CONTENT_WIDTH - 1))}"
        while [[ -n "$remaining" ]]; do
            local part_len
            part_len=$(display_width "$remaining")

            if [[ $part_len -le $CONTENT_WIDTH ]]; then
                printf '│%*s│   │%*s│ %-*s│\n' \
                    "$COL_OLD" "" \
                    "$COL_NEW" "" \
                    "$CONTENT_WIDTH" "$remaining"
                remaining=""
            else
                local next_part="${remaining:0:$((CONTENT_WIDTH - 1))}"
                printf '│%*s│   │%*s│ %s↩│\n' \
                    "$COL_OLD" "" \
                    "$COL_NEW" "" \
                    "$next_part"
                remaining="${remaining:$((CONTENT_WIDTH - 1))}"
            fi
        done
    fi
}

# Print hunk box bottom
print_hunk_bottom() {
    echo "╰$(fill_char '─' $COL_OLD)┴$(fill_char '─' $COL_SYM)┴$(fill_char '─' $COL_NEW)┴$(fill_char '─' $((CONTENT_WIDTH + 1)))╯"
}

# Print binary file box
print_binary_file() {
    local filename="$1"
    local file_text="FILE: ${filename} (binary)"
    local file_len
    file_len=$(display_width "$file_text")
    local padding=$((WIDTH - 4 - file_len))

    echo "╭$(fill_char '─' $((WIDTH - 2)))╮"
    printf '│ %s%*s │\n' "$file_text" "$padding" ""
    echo "├$(fill_char '─' $((WIDTH - 2)))┤"
    printf '│ %-*s │\n' $((WIDTH - 4)) "Binary file changed"
    echo "╰$(fill_char '─' $((WIDTH - 2)))╯"
}

# Print renamed file box
print_renamed_file() {
    local old_path="$1"
    local new_path="$2"
    local file_text="FILE: ${old_path} → ${new_path} (renamed)"
    local file_len
    file_len=$(display_width "$file_text")
    local padding=$((WIDTH - 4 - file_len))

    echo "╭$(fill_char '─' $((WIDTH - 2)))╮"
    if [[ $file_len -gt $((WIDTH - 4)) ]]; then
        # Too long, truncate
        printf '│ %-*s │\n' $((WIDTH - 4)) "${file_text:0:$((WIDTH - 7))}..."
    else
        printf '│ %s%*s │\n' "$file_text" "$padding" ""
    fi
    echo "├$(fill_char '─' $((WIDTH - 2)))┤"
    printf '│ %-*s │\n' $((WIDTH - 4)) "File renamed (no content changes)"
    echo "╰$(fill_char '─' $((WIDTH - 2)))╯"
}

# Print legend
print_legend() {
    local legend_items=()

    if [[ "$USED_MINUS" == "true" ]]; then
        legend_items+=("-  del")
    fi
    if [[ "$USED_PLUS" == "true" ]]; then
        legend_items+=("+  add")
    fi
    if [[ "$USED_BRACKET" == "true" ]]; then
        legend_items+=("[]  changed")
    fi
    if [[ "$USED_SPACE" == "true" ]]; then
        legend_items+=("·  space")
    fi
    if [[ "$USED_TAB" == "true" ]]; then
        legend_items+=("→  tab")
    fi
    if [[ "$USED_WRAP" == "true" ]]; then
        legend_items+=("↩  wrap")
    fi

    if [[ ${#legend_items[@]} -eq 0 ]]; then
        return
    fi

    echo ""
    echo "╭$(fill_char '─' $((WIDTH - 2)))╮"
    printf '│ %-*s│\n' $((WIDTH - 3)) "Legend"
    echo "├$(fill_char '─' $((WIDTH - 2)))┤"

    local legend_line=""
    for item in "${legend_items[@]}"; do
        if [[ -n "$legend_line" ]]; then
            legend_line+="    "
        fi
        legend_line+="$item"
    done

    printf '│  %-*s│\n' $((WIDTH - 4)) "$legend_line"
    echo "╰$(fill_char '─' $((WIDTH - 2)))╯"
}

# === PARSE DIFF INTO STRUCTURED DATA ===

# Arrays to hold parsed data
declare -a FILES=()
declare -a HUNKS=()
declare -a HUNK_FILES=()
declare -a HUNK_CONTEXTS=()
declare -a HUNK_OLD_STARTS=()
declare -a HUNK_NEW_STARTS=()
declare -a HUNK_LINES=()

# Track renames and binary files
declare -A RENAMED_FILES=()
declare -a BINARY_FILES=()

CURRENT_FILE=""
CURRENT_HUNK_IDX=-1
CURRENT_LINES=""
RENAME_FROM=""
IS_BINARY=false

# First pass: parse diff structure
while IFS= read -r line || [[ -n "$line" ]]; do

    # New file
    if [[ "$line" =~ ^diff\ --git\ a/(.+)\ b/(.+)$ ]]; then
        # Save previous hunk if any
        if [[ $CURRENT_HUNK_IDX -ge 0 ]] && [[ -n "$CURRENT_LINES" ]]; then
            HUNK_LINES[$CURRENT_HUNK_IDX]="$CURRENT_LINES"
        fi

        CURRENT_FILE="${BASH_REMATCH[2]}"
        FILES+=("$CURRENT_FILE")
        RENAME_FROM=""
        IS_BINARY=false
        CURRENT_LINES=""
        continue
    fi

    # Rename detection
    if [[ "$line" =~ ^rename\ from\ (.+)$ ]]; then
        RENAME_FROM="${BASH_REMATCH[1]}"
        continue
    fi
    if [[ "$line" =~ ^rename\ to\ (.+)$ ]] && [[ -n "$RENAME_FROM" ]]; then
        RENAMED_FILES["$CURRENT_FILE"]="$RENAME_FROM"
        continue
    fi

    # Binary file detection
    if [[ "$line" =~ ^Binary\ files ]]; then
        BINARY_FILES+=("$CURRENT_FILE")
        IS_BINARY=true
        continue
    fi

    # Skip other metadata
    if [[ "$line" == "index "* ]] || [[ "$line" == "---"* ]] || [[ "$line" == "+++"* ]] || \
       [[ "$line" == "new file"* ]] || [[ "$line" == "deleted file"* ]] || [[ "$line" == "similarity"* ]]; then
        continue
    fi

    # Hunk header
    if [[ "$line" =~ ^@@\ -([0-9]+)(,[0-9]+)?\ \+([0-9]+)(,[0-9]+)?\ @@(.*)$ ]]; then
        # Save previous hunk
        if [[ $CURRENT_HUNK_IDX -ge 0 ]] && [[ -n "$CURRENT_LINES" ]]; then
            HUNK_LINES[$CURRENT_HUNK_IDX]="$CURRENT_LINES"
        fi

        CURRENT_HUNK_IDX=$((${#HUNKS[@]}))
        HUNKS+=("$CURRENT_HUNK_IDX")
        HUNK_FILES+=("$CURRENT_FILE")
        HUNK_OLD_STARTS+=("${BASH_REMATCH[1]}")
        HUNK_NEW_STARTS+=("${BASH_REMATCH[3]}")

        # Extract context (function name etc)
        ctx="${BASH_REMATCH[5]}"
        ctx="${ctx#"${ctx%%[![:space:]]*}"}"  # trim leading whitespace
        HUNK_CONTEXTS+=("$ctx")

        CURRENT_LINES=""
        continue
    fi

    # Content lines (store for later processing)
    if [[ "$line" =~ ^[\ +-] ]] || [[ "$line" == "\\ No newline at end of file" ]]; then
        if [[ -n "$CURRENT_LINES" ]]; then
            CURRENT_LINES+=$'\n'"$line"
        else
            CURRENT_LINES="$line"
        fi
    fi

done <<< "$DIFF_CONTENT"

# Save final hunk
if [[ $CURRENT_HUNK_IDX -ge 0 ]] && [[ -n "$CURRENT_LINES" ]]; then
    HUNK_LINES[$CURRENT_HUNK_IDX]="$CURRENT_LINES"
fi

# === RENDER OUTPUT ===

FIRST_BOX=true

# Render binary files first
for file in "${BINARY_FILES[@]}"; do
    if [[ "$FIRST_BOX" == "false" ]]; then
        echo ""
    fi
    FIRST_BOX=false
    print_binary_file "$file"
done

# Render renamed files (without content changes)
for file in "${!RENAMED_FILES[@]}"; do
    # Check if this file has any hunks
    has_hunks=false
    for ((h=0; h<${#HUNKS[@]}; h++)); do
        if [[ "${HUNK_FILES[$h]}" == "$file" ]]; then
            has_hunks=true
            break
        fi
    done

    if [[ "$has_hunks" == "false" ]]; then
        if [[ "$FIRST_BOX" == "false" ]]; then
            echo ""
        fi
        FIRST_BOX=false
        print_renamed_file "${RENAMED_FILES[$file]}" "$file"
    fi
done

# Render each hunk as a separate box
for ((h=0; h<${#HUNKS[@]}; h++)); do
    file="${HUNK_FILES[$h]}"
    context="${HUNK_CONTEXTS[$h]}"
    old_line="${HUNK_OLD_STARTS[$h]}"
    new_line="${HUNK_NEW_STARTS[$h]}"
    lines="${HUNK_LINES[$h]}"

    # Skip binary files (already rendered)
    is_binary=false
    for bf in "${BINARY_FILES[@]}"; do
        if [[ "$bf" == "$file" ]]; then
            is_binary=true
            break
        fi
    done
    [[ "$is_binary" == "true" ]] && continue

    # Print box
    if [[ "$FIRST_BOX" == "false" ]]; then
        echo ""
    fi
    FIRST_BOX=false

    print_hunk_top "$file"
    print_column_header "$context"

    # Process hunk lines
    # Collect lines for word-diff detection
    declare -a del_lines=()
    declare -a add_lines=()
    declare -a line_types=()
    declare -a line_contents=()

    while IFS= read -r hline || [[ -n "$hline" ]]; do
        if [[ "$hline" =~ ^\+(.*)$ ]]; then
            line_types+=("add")
            line_contents+=("${BASH_REMATCH[1]}")
        elif [[ "$hline" =~ ^-(.*)$ ]]; then
            line_types+=("del")
            line_contents+=("${BASH_REMATCH[1]}")
        elif [[ "$hline" =~ ^\ (.*)$ ]]; then
            line_types+=("ctx")
            line_contents+=("${BASH_REMATCH[1]}")
        elif [[ "$hline" == "\\ No newline at end of file" ]]; then
            continue
        fi
    done <<< "$lines"

    # Render lines with word-diff detection
    i=0
    num_lines=${#line_types[@]}

    while [[ $i -lt $num_lines ]]; do
        ltype="${line_types[$i]}"
        lcontent="${line_contents[$i]}"

        if [[ "$ltype" == "ctx" ]]; then
            print_row "$old_line" " " "$new_line" "$lcontent"
            ((old_line++)) || true
            ((new_line++)) || true
            ((i++)) || true

        elif [[ "$ltype" == "del" ]]; then
            USED_MINUS=true

            # Check for adjacent add for word-diff
            if [[ $((i + 1)) -lt $num_lines ]] && [[ "${line_types[$((i+1))]}" == "add" ]]; then
                del_content="$lcontent"
                add_content="${line_contents[$((i+1))]}"

                # Check if whitespace-only change
                if is_whitespace_only_change "$del_content" "$add_content"; then
                    # Mark whitespace symbols as used (subshell won't persist)
                    [[ "$del_content" == *$'\t'* ]] && USED_TAB=true
                    [[ "$del_content" == *" "* ]] && USED_SPACE=true
                    [[ "$add_content" == *$'\t'* ]] && USED_TAB=true
                    [[ "$add_content" == *" "* ]] && USED_SPACE=true
                    del_content=$(visualize_whitespace "$del_content")
                    add_content=$(visualize_whitespace "$add_content")
                    print_row "$old_line" "-" "" "$del_content"
                    ((old_line++)) || true
                    ((i++)) || true
                    USED_PLUS=true
                    print_row "" "+" "$new_line" "$add_content"
                    ((new_line++)) || true
                    ((i++)) || true
                else
                    # Apply word-level diff
                    highlighted_del=$(highlight_word_diff "$del_content" "$add_content" "old")
                    highlighted_add=$(highlight_word_diff "$del_content" "$add_content" "new")

                    print_row "$old_line" "-" "" "$highlighted_del"
                    ((old_line++)) || true
                    ((i++)) || true
                    USED_PLUS=true
                    print_row "" "+" "$new_line" "$highlighted_add"
                    ((new_line++)) || true
                    ((i++)) || true
                fi
            else
                # Just a deletion
                print_row "$old_line" "-" "" "$lcontent"
                ((old_line++)) || true
                ((i++)) || true
            fi

        elif [[ "$ltype" == "add" ]]; then
            USED_PLUS=true
            print_row "" "+" "$new_line" "$lcontent"
            ((new_line++)) || true
            ((i++)) || true
        else
            ((i++)) || true
        fi
    done

    print_hunk_bottom

    # Clear arrays for next hunk
    unset del_lines add_lines line_types line_contents
done

# Print legend
print_legend
