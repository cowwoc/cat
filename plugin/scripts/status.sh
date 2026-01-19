#!/bin/bash
set -euo pipefail

# CAT Status Display Generator
# Outputs formatted box display directly
#
# Usage: status.sh [CAT_DIR]
#   CAT_DIR defaults to .claude/cat
#
# Created as prevention for M142 (5th recurrence of status alignment issues)
# Root cause: LLMs cannot reliably calculate character-level padding
# Solution: Script handles both data collection AND rendering using Python for width calculation

CAT_DIR="${1:-.claude/cat}"

# Verify structure exists
if [ ! -d "$CAT_DIR" ]; then
    echo "ERROR: No planning structure found at $CAT_DIR"
    echo "Run /cat:init to initialize"
    exit 1
fi

# === CONSTANTS ===
BOX_WIDTH=74  # Total display width including borders
INNER_BOX_WIDTH=59  # Width of nested version boxes

# === PYTHON HELPER FOR WIDTH CALCULATION ===
# Uses Python for reliable Unicode width calculation
display_width() {
    python3 -c "
import unicodedata
s = '''$1'''
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

# Print a content line with borders
line() {
    local content="$1"
    local inner_width=$((BOX_WIDTH - 2))
    local padded
    padded=$(pad "$content" $inner_width)
    printf '│%s│\n' "$padded"
}

# Print empty line
empty() {
    printf '│%*s│\n' $((BOX_WIDTH - 2)) ""
}

# Print horizontal divider
divider() {
    printf '├'
    for ((i=0; i<BOX_WIDTH-2; i++)); do printf '─'; done
    printf '┤\n'
}

# Print top border
top() {
    printf '╭'
    for ((i=0; i<BOX_WIDTH-2; i++)); do printf '─'; done
    printf '╮\n'
}

# Print bottom border
bottom() {
    printf '╰'
    for ((i=0; i<BOX_WIDTH-2; i++)); do printf '─'; done
    printf '╯\n'
}

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
    line "  ${inner}"
}

# Print inner box bottom
inner_bottom() {
    local dashes=""
    for ((i=0; i<INNER_BOX_WIDTH-2; i++)); do dashes="${dashes}─"; done
    line "  ╰${dashes}╯"
}

# Print inner box content line
inner_line() {
    local content="$1"
    local inner_content_width=$((INNER_BOX_WIDTH - 4))  # -4 for "│  " and "│"
    local padded
    padded=$(pad "$content" $inner_content_width)
    line "  │  ${padded}│"
}

# Print inner empty line
inner_empty() {
    local spaces=""
    for ((i=0; i<INNER_BOX_WIDTH-2; i++)); do spaces="${spaces} "; done
    line "  │${spaces}│"
}

# Progress bar
progress_bar() {
    local pct="$1"
    local bar_width=45
    local filled=$((pct * bar_width / 100))
    local unfilled=$((bar_width - filled))

    local bar="["
    for ((i=0; i<filled; i++)); do bar="${bar}█"; done
    for ((i=0; i<unfilled; i++)); do bar="${bar}░"; done
    bar="${bar}]"
    printf '%s' "$bar"
}

# Get task status from STATE.md
get_task_status() {
    local state_file="$1"
    [ -f "$state_file" ] || { echo "pending"; return; }

    local st
    st=$(grep -m1 -i "^status:" "$state_file" 2>/dev/null | sed 's/^[Ss]tatus:[[:space:]]*//')
    if [ -z "$st" ]; then
        st=$(grep -m1 "^\- \*\*Status:\*\*" "$state_file" 2>/dev/null | sed 's/.*\*\* //')
    fi
    [ -z "$st" ] && st="pending"
    echo "$st"
}

# === DATA COLLECTION ===

PROJECT_NAME=$(grep -m1 "^# " "$CAT_DIR/PROJECT.md" 2>/dev/null | sed 's/^# //' || echo "Unknown Project")

# Collect stats
declare -A MINOR_COMPLETED MINOR_TOTAL MINOR_DESC
declare -A MAJOR_NAMES
TOTAL_COMPLETED=0
TOTAL_TASKS=0
CURRENT_MINOR=""
IN_PROGRESS_TASK=""
PENDING_TASKS=()

for major_dir in "$CAT_DIR"/v[0-9]*/; do
    [ -d "$major_dir" ] || continue
    major=$(basename "$major_dir")
    major_num="${major#v}"

    MAJOR_NAMES[$major]=$(grep -m1 "^## Version ${major_num}:" "$CAT_DIR/ROADMAP.md" 2>/dev/null | \
        sed "s/## Version ${major_num}: //" || echo "Version $major_num")

    for minor_dir in "$major_dir"v[0-9]*.[0-9]*/; do
        [ -d "$minor_dir" ] || continue
        minor=$(basename "$minor_dir")

        local_completed=0
        local_total=0
        local_inprog=""

        for task_dir in "$minor_dir"/*/; do
            [ -d "$task_dir" ] || continue
            task_name=$(basename "$task_dir")

            state_file="${task_dir}STATE.md"
            plan_file="${task_dir}PLAN.md"
            [ -f "$state_file" ] || [ -f "$plan_file" ] || continue

            st=$(get_task_status "$state_file")
            ((local_total++)) || true

            case "$st" in
                completed|done) ((local_completed++)) ;;
                in-progress|active) local_inprog="$task_name" ;;
            esac
        done

        MINOR_COMPLETED[$minor]=$local_completed
        MINOR_TOTAL[$minor]=$local_total
        MINOR_DESC[$minor]=$(grep -m1 "^\- \*\*${minor#v}:\*\*" "$CAT_DIR/ROADMAP.md" 2>/dev/null | \
            sed 's/.*\*\* //' | cut -d'(' -f1 | sed 's/ *$//' || echo "")

        TOTAL_COMPLETED=$((TOTAL_COMPLETED + local_completed))
        TOTAL_TASKS=$((TOTAL_TASKS + local_total))

        # Determine current minor
        if [ -z "$CURRENT_MINOR" ]; then
            if [ -n "$local_inprog" ]; then
                CURRENT_MINOR="$minor"
                IN_PROGRESS_TASK="$local_inprog"
            elif [ "$local_completed" -lt "$local_total" ]; then
                CURRENT_MINOR="$minor"
            fi
        fi
    done
done

# Collect pending tasks for current minor
if [ -n "$CURRENT_MINOR" ]; then
    major="${CURRENT_MINOR%%.*}"
    minor_dir="$CAT_DIR/$major/$CURRENT_MINOR"

    for task_dir in "$minor_dir"/*/; do
        [ -d "$task_dir" ] || continue
        task_name=$(basename "$task_dir")

        state_file="${task_dir}STATE.md"
        plan_file="${task_dir}PLAN.md"
        [ -f "$state_file" ] || [ -f "$plan_file" ] || continue

        st=$(get_task_status "$state_file")
        [ "$st" = "pending" ] && PENDING_TASKS+=("$task_name")
    done
fi

[ "$TOTAL_TASKS" -eq 0 ] && TOTAL_TASKS=1
PERCENT=$((TOTAL_COMPLETED * 100 / TOTAL_TASKS))

# === RENDER OUTPUT ===

top
empty
line "  📊 Overall: $(progress_bar $PERCENT) ${PERCENT}%"
line "  🏆 ${TOTAL_COMPLETED}/${TOTAL_TASKS} tasks complete"
empty
divider

# Render major versions
for major in $(echo "${!MAJOR_NAMES[@]}" | tr ' ' '\n' | sort -V); do
    empty
    inner_top "📦 ${major}: ${MAJOR_NAMES[$major]}"
    inner_empty

    for minor in $(echo "${!MINOR_COMPLETED[@]}" | tr ' ' '\n' | sort -V); do
        [[ "${minor%%.*}" == "$major" ]] || continue

        comp=${MINOR_COMPLETED[$minor]}
        tot=${MINOR_TOTAL[$minor]}
        desc=${MINOR_DESC[$minor]}

        # Determine emoji
        if [ "$comp" -eq "$tot" ] && [ "$tot" -gt 0 ]; then
            emoji="☑️"
        elif [ "$minor" = "$CURRENT_MINOR" ]; then
            emoji="🔄"
        else
            emoji="🔳"
        fi

        if [ "$minor" = "$CURRENT_MINOR" ]; then
            inner_line "${emoji} ${minor}: ${desc} (${comp}/${tot}) | Exit: 0/0"

            # Show in-progress task
            if [ -n "$IN_PROGRESS_TASK" ]; then
                inner_line "  🔄 ${IN_PROGRESS_TASK}"
            fi

            # Show pending tasks (up to 5)
            shown=0
            for task in "${PENDING_TASKS[@]}"; do
                [ "$task" = "$IN_PROGRESS_TASK" ] && continue
                [ $shown -ge 5 ] && break
                inner_line "  🔳 ${task}"
                ((shown++)) || true
            done

            # Show "and N more"
            remaining=${#PENDING_TASKS[@]}
            [ -n "$IN_PROGRESS_TASK" ] && ((remaining--)) || true
            remaining=$((remaining - shown))
            [ $remaining -gt 0 ] && inner_line "  📋 ... and ${remaining} more pending tasks"
        else
            inner_line "${emoji} ${minor}: ${desc} (${comp}/${tot})"
        fi
    done

    inner_empty
    inner_bottom
done

empty
divider
line "  🎯 Active: ${CURRENT_MINOR} - ${MINOR_DESC[$CURRENT_MINOR]:-}"
line "  📋 Available: ${#PENDING_TASKS[@]} pending tasks"
divider
empty
bottom
