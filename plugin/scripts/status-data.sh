#!/bin/bash
set -euo pipefail

# CAT Status Data Generator
# Outputs JSON with all status metrics for rendering by Claude
#
# Usage: status-data.sh [CAT_DIR]
#   CAT_DIR defaults to .claude/cat/issues
#
# Directory structure:
#   .claude/cat/issues/vN/vN.M/{task-name}/STATE.md
#   .claude/cat/issues/vN/vN.M/{task-name}/PLAN.md
#
# Fixed in M138: Support tasks directly in minor dirs (not in task/ subdir)

CAT_DIR="${1:-.claude/cat/issues}"

# Load project info
PROJECT_NAME=$(grep -m1 "^# " "$CAT_DIR/PROJECT.md" 2>/dev/null | sed 's/^# //' || echo "Unknown Project")

# Calculate relative time from timestamp
relative_time() {
    local timestamp="$1"
    local now=$(date +%s)
    local then=$(date -d "$timestamp" +%s 2>/dev/null || echo "$now")
    local diff=$((now - then))

    if [ "$diff" -lt 60 ]; then
        echo "just now"
    elif [ "$diff" -lt 3600 ]; then
        local mins=$((diff / 60))
        [ "$mins" -eq 1 ] && echo "1 min ago" || echo "${mins} mins ago"
    elif [ "$diff" -lt 86400 ]; then
        local hrs=$((diff / 3600))
        [ "$hrs" -eq 1 ] && echo "1 hr ago" || echo "${hrs} hrs ago"
    elif [ "$diff" -lt 604800 ]; then
        local days=$((diff / 86400))
        [ "$days" -eq 1 ] && echo "1 day ago" || echo "${days} days ago"
    else
        echo "$timestamp"
    fi
}

# Get task status from STATE.md
# Supports format: "status: pending" (current) or "- **Status:** pending" (legacy)
get_task_status() {
    local state_file="$1"
    [ -f "$state_file" ] || { echo "pending"; return; }

    # Try current format first: "status: pending"
    local st=$(grep -m1 -i "^status:" "$state_file" 2>/dev/null | sed 's/^[Ss]tatus:[[:space:]]*//')
    if [ -z "$st" ]; then
        # Fall back to legacy format: "- **Status:** pending"
        st=$(grep -m1 "^\- \*\*Status:\*\*" "$state_file" 2>/dev/null | sed 's/.*\*\* //')
    fi
    [ -z "$st" ] && st="pending"
    echo "$st"
}

# Collect recent completed tasks (returns JSON array)
collect_recent_tasks() {
    local recent_tasks=()

    # Find all completed task STATE.md files (tasks directly in minor dirs)
    for state_file in "$CAT_DIR"/v*/v*.*/*/STATE.md; do
        [ -f "$state_file" ] || continue

        # Check if status is completed
        local task_status=$(get_task_status "$state_file")
        [ "$task_status" = "completed" ] || continue

        # Extract task info
        local task_dir=$(dirname "$state_file")
        local task_name=$(basename "$task_dir")
        local version_dir=$(dirname "$task_dir")
        local version=$(basename "$version_dir")

        # Get completion timestamp from git
        local completed=$(git log -1 --format="%ci" -- "$state_file" 2>/dev/null | cut -d' ' -f1,2 || echo "")
        [ -z "$completed" ] && continue

        # Get tokens used (if available)
        local tokens=$(grep -m1 -i "^tokens.*:" "$state_file" 2>/dev/null | sed 's/.*:[[:space:]]*//' | tr -d ',')
        [ -z "$tokens" ] && tokens="0"

        # Convert to epoch for sorting
        local epoch=$(date -d "$completed" +%s 2>/dev/null || echo "0")

        # Store as sortable entry: epoch|version-task|completed|tokens
        recent_tasks+=("${epoch}|${version}-${task_name}|${completed}|${tokens}")
    done

    # Sort by epoch descending, take top 3
    printf '%s\n' "${recent_tasks[@]}" 2>/dev/null | sort -t'|' -k1 -rn | head -3
}

# Count tasks in a minor version directory
count_tasks() {
    local minor_dir="$1"
    local completed=0 pending=0 inprog=0 blocked=0
    local current=""

    for task_dir in "$minor_dir"/*/; do
        [ -d "$task_dir" ] || continue
        local task_name=$(basename "$task_dir")

        # Must have either STATE.md or PLAN.md to be a task
        local state_file="${task_dir}STATE.md"
        local plan_file="${task_dir}PLAN.md"
        [ -f "$state_file" ] || [ -f "$plan_file" ] || continue

        # Get status from STATE.md
        local st=$(get_task_status "$state_file")

        case "$st" in
            completed|done) ((completed++)) ;;
            in-progress|active) ((inprog++)); current="$task_name" ;;
            pending) ((pending++)) ;;
            blocked) ((blocked++)) ;;
            *) ((pending++)) ;;
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

        minor=$(basename "$minor_dir")
        read completed total current <<< "$(count_tasks "$minor_dir")"

        MINOR_STATS[$minor]="$completed/$total"
        MINOR_DESCRIPTIONS[$minor]=$(get_minor_description "$minor")
        MINOR_CURRENT[$minor]="$current"
        TOTAL_COMPLETED=$((TOTAL_COMPLETED + completed))
        TOTAL_TASKS=$((TOTAL_TASKS + total))
    done
done

# Find current minor in version-sorted order
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
    minor_dir="$CAT_DIR/$major/$CURRENT_MINOR"

    for task_dir in "$minor_dir"/*/; do
        [ -d "$task_dir" ] || continue
        task_name=$(basename "$task_dir")

        # Must have either STATE.md or PLAN.md to be a task
        state_file="${task_dir}STATE.md"
        plan_file="${task_dir}PLAN.md"
        [ -f "$state_file" ] || [ -f "$plan_file" ] || continue

        # Skip infrastructure tasks
        if [[ "$task_name" =~ ^(create-|extend-|extract-|split-) ]]; then
            continue
        fi

        st=$(get_task_status "$state_file")

        if [ "$st" = "pending" ]; then
            PENDING_TASKS+=("$task_name")
            [ -z "$FIRST_PENDING" ] && FIRST_PENDING="$task_name"
        elif [ "$st" = "in-progress" ] || [ "$st" = "active" ]; then
            IN_PROGRESS_TASK="$task_name"
            FIRST_PENDING="$task_name"
        fi
    done
fi

# Collect recent completed tasks
RECENT_TASKS=$(collect_recent_tasks)

# Output JSON
echo "{"
echo "  \"project_name\": \"$PROJECT_NAME\","
echo "  \"percent\": $PERCENT,"
echo "  \"completed\": $TOTAL_COMPLETED,"
echo "  \"total\": $TOTAL_TASKS,"
echo "  \"current_minor\": \"$CURRENT_MINOR\","
echo "  \"first_pending\": \"$FIRST_PENDING\","
echo "  \"in_progress_task\": \"$IN_PROGRESS_TASK\","

# Recent tasks array
echo "  \"recent_tasks\": ["
first_recent=true
while IFS='|' read -r epoch task_id completed tokens; do
    [ -z "$epoch" ] && continue
    $first_recent || echo ","
    first_recent=false
    rel_time=$(relative_time "$completed")
    if [ "$tokens" -ge 1000 ] 2>/dev/null; then
        tokens_fmt="$((tokens / 1000))K"
    else
        tokens_fmt="$tokens"
    fi
    echo -n "    {\"task\": \"$task_id\", \"completed\": \"$completed\", \"relative\": \"$rel_time\", \"tokens\": \"$tokens_fmt\"}"
done <<< "$RECENT_TASKS"
echo ""
echo "  ],"

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
