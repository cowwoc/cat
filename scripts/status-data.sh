#!/bin/bash
set -euo pipefail

# CAT Status Data Generator
# Outputs JSON with all status metrics for rendering by Claude
#
# Usage: status-data.sh [CAT_DIR]
#   CAT_DIR defaults to .claude/cat

CAT_DIR="${1:-.claude/cat}"

# Load project info
PROJECT_NAME=$(grep -m1 "^# " "$CAT_DIR/PROJECT.md" 2>/dev/null | sed 's/^# //' || echo "Unknown Project")

# Count tasks in a directory
count_tasks() {
    local dir="$1"
    local completed=0 pending=0 inprog=0 blocked=0
    local current=""

    for task_dir in "$dir"/*/; do
        [ -d "$task_dir" ] || continue
        local state_file="${task_dir}STATE.md"
        [ -f "$state_file" ] || continue

        local st=$(grep -m1 "^\- \*\*Status:\*\*" "$state_file" 2>/dev/null | sed 's/.*\*\* //')

        case "$st" in
            completed) ((completed++)) ;;
            in-progress) ((inprog++)); current=$(basename "$task_dir") ;;
            pending) ((pending++)) ;;
            blocked) ((blocked++)) ;;
        esac
    done

    local total=$((completed + pending + inprog + blocked))
    echo "$completed $total $current"
}

# Extract minor version description from ROADMAP.md
get_minor_description() {
    local minor="$1"
    local num="${minor#v}"
    grep -m1 "^\- \*\*${num}:\*\*" "$CAT_DIR/ROADMAP.md" 2>/dev/null | \
        sed 's/.*\*\* //' | cut -d'(' -f1 | sed 's/ *$//' || echo "Unknown"
}

# Gather all data
declare -A MINOR_STATS
declare -A MINOR_DESCRIPTIONS
declare -A MINOR_CURRENT
declare -A MAJOR_NAMES
TOTAL_COMPLETED=0
TOTAL_TASKS=0
CURRENT_MINOR=""
FIRST_PENDING=""

for major_dir in "$CAT_DIR"/v[0-9]*/; do
    [ -d "$major_dir" ] || continue
    major=$(basename "$major_dir")
    major_num="${major#v}"
    MAJOR_NAMES[$major]=$(grep -m1 "^## Version ${major_num}:" "$CAT_DIR/ROADMAP.md" 2>/dev/null | \
        sed "s/## Version ${major_num}: //" || echo "Version $major_num")

    for minor_dir in "$major_dir"v[0-9]*.[0-9]*/; do
        [ -d "$minor_dir" ] || continue
        task_dir="${minor_dir}task"
        [ -d "$task_dir" ] || continue

        minor=$(basename "$minor_dir")
        read completed total current <<< "$(count_tasks "$task_dir")"

        MINOR_STATS[$minor]="$completed/$total"
        MINOR_DESCRIPTIONS[$minor]=$(get_minor_description "$minor")
        MINOR_CURRENT[$minor]="$current"
        TOTAL_COMPLETED=$((TOTAL_COMPLETED + completed))
        TOTAL_TASKS=$((TOTAL_TASKS + total))
    done
done

# Find current minor in version-sorted order (fixes v0.10 before v0.2 bug)
for minor in $(echo "${!MINOR_STATS[@]}" | tr ' ' '\n' | sort -V); do
    IFS='/' read comp tot <<< "${MINOR_STATS[$minor]}"
    if [[ -n "${MINOR_CURRENT[$minor]}" ]]; then
        CURRENT_MINOR="$minor"
        break
    elif [[ "$comp" -lt "$tot" ]]; then
        CURRENT_MINOR="$minor"
        break
    fi
done

[ "$TOTAL_TASKS" -eq 0 ] && TOTAL_TASKS=1
PERCENT=$((TOTAL_COMPLETED * 100 / TOTAL_TASKS))

# Collect pending tasks for current minor
PENDING_TASKS=()
IN_PROGRESS_TASK=""
if [[ -n "$CURRENT_MINOR" ]]; then
    major="${CURRENT_MINOR%%.*}"
    for task_dir in "$CAT_DIR/$major/$CURRENT_MINOR/task"/*/; do
        [ -d "$task_dir" ] || continue
        state_file="${task_dir}STATE.md"
        [ -f "$state_file" ] || continue

        st=$(grep -m1 "^\- \*\*Status:\*\*" "$state_file" 2>/dev/null | sed 's/.*\*\* //')
        task_name=$(basename "$task_dir")

        if [ "$st" = "pending" ]; then
            # Skip infrastructure tasks
            if [[ "$task_name" =~ ^(create-|extend-|extract-|split-) ]]; then
                continue
            fi
            PENDING_TASKS+=("$task_name")
            [ -z "$FIRST_PENDING" ] && FIRST_PENDING="$task_name"
        elif [ "$st" = "in-progress" ]; then
            IN_PROGRESS_TASK="$task_name"
            FIRST_PENDING="$task_name"
        fi
    done
fi

# Output JSON
echo "{"
echo "  \"project_name\": \"$PROJECT_NAME\","
echo "  \"percent\": $PERCENT,"
echo "  \"completed\": $TOTAL_COMPLETED,"
echo "  \"total\": $TOTAL_TASKS,"
echo "  \"current_minor\": \"$CURRENT_MINOR\","
echo "  \"first_pending\": \"$FIRST_PENDING\","
echo "  \"in_progress_task\": \"$IN_PROGRESS_TASK\","

# Majors array
echo "  \"majors\": ["
first_major=true
for major in $(echo "${!MAJOR_NAMES[@]}" | tr ' ' '\n' | sort -V); do
    $first_major || echo ","
    first_major=false
    echo -n "    {\"version\": \"$major\", \"name\": \"${MAJOR_NAMES[$major]}\", \"minors\": ["

    first_minor=true
    for minor in $(echo "${!MINOR_STATS[@]}" | tr ' ' '\n' | sort -V); do
        [[ "${minor%%.*}" == "$major" ]] || continue
        $first_minor || echo -n ", "
        first_minor=false

        IFS='/' read comp tot <<< "${MINOR_STATS[$minor]}"
        desc="${MINOR_DESCRIPTIONS[$minor]}"
        # Escape quotes in description
        desc="${desc//\"/\\\"}"
        echo -n "{\"version\": \"$minor\", \"completed\": $comp, \"total\": $tot, \"description\": \"$desc\"}"
    done
    echo -n "]}"
done
echo ""
echo "  ],"

# Pending tasks array
echo "  \"pending_tasks\": ["
first=true
for task in "${PENDING_TASKS[@]}"; do
    $first || echo ","
    first=false
    echo -n "    \"$task\""
done
echo ""
echo "  ]"
echo "}"
