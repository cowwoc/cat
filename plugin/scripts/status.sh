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
# Solution: Script handles both data collection AND rendering using shared box library

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CAT_DIR="${1:-.claude/cat}"

# Verify structure exists
if [ ! -d "$CAT_DIR" ]; then
    echo "ERROR: No planning structure found at $CAT_DIR"
    echo "Run /cat:init to initialize"
    exit 1
fi

# Source shared box rendering library
source "${SCRIPT_DIR}/lib/box.sh"

# Initialize box dimensions
box_init 74 59

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
                completed|done) ((local_completed++)) || true ;;
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

box_top
box_empty
box_line "  📊 Overall: $(progress_bar $PERCENT) ${PERCENT}%"
box_line "  🏆 ${TOTAL_COMPLETED}/${TOTAL_TASKS} tasks complete"
box_empty
box_divider

# Render major versions
for major in $(echo "${!MAJOR_NAMES[@]}" | tr ' ' '\n' | sort -V); do
    box_empty
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

box_empty
box_divider

# Handle case when no active minor version (empty CURRENT_MINOR)
if [ -n "$CURRENT_MINOR" ]; then
    box_line "  🎯 Active: ${CURRENT_MINOR} - ${MINOR_DESC[$CURRENT_MINOR]:-}"
    box_line "  📋 Available: ${#PENDING_TASKS[@]} pending tasks"
else
    box_line "  🎯 Active: None - all tasks complete or no tasks defined"
    box_line "  📋 Available: 0 pending tasks"
fi

box_divider
box_empty
box_bottom
