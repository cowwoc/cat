#!/bin/bash
set -euo pipefail

# CAT Status Data Collector
# Outputs JSON with project status data
#
# Usage: status.sh [CAT_DIR]
#   CAT_DIR defaults to .claude/cat

CAT_DIR="${1:-.claude/cat}"

# Verify structure exists
if [ ! -d "$CAT_DIR" ]; then
    echo '{"error": "No planning structure found. Run /cat:init to initialize."}'
    exit 1
fi

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

# Build JSON arrays
MAJORS_JSON="[]"
MINORS_JSON="[]"

for major_dir in "$CAT_DIR"/v[0-9]*/; do
    [ -d "$major_dir" ] || continue
    major=$(basename "$major_dir")
    major_num="${major#v}"

    major_name=$(grep -m1 "^## Version ${major_num}:" "$CAT_DIR/ROADMAP.md" 2>/dev/null | \
        sed "s/## Version ${major_num}: //" || echo "Version $major_num")
    MAJOR_NAMES[$major]="$major_name"

    for minor_dir in "$major_dir"v[0-9]*.[0-9]*/; do
        [ -d "$minor_dir" ] || continue
        minor=$(basename "$minor_dir")

        local_completed=0
        local_total=0
        local_inprog=""
        tasks_json="[]"

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

            # Add task to JSON array
            tasks_json=$(echo "$tasks_json" | jq --arg name "$task_name" --arg status "$st" \
                '. + [{"name": $name, "status": $status}]')
        done

        MINOR_COMPLETED[$minor]=$local_completed
        MINOR_TOTAL[$minor]=$local_total
        desc=$(grep -m1 "^\- \*\*${minor#v}:\*\*" "$CAT_DIR/ROADMAP.md" 2>/dev/null | \
            sed 's/.*\*\* //' | cut -d'(' -f1 | sed 's/ *$//' || echo "")
        MINOR_DESC[$minor]="$desc"

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

        # Add minor to JSON
        MINORS_JSON=$(echo "$MINORS_JSON" | jq \
            --arg id "$minor" \
            --arg major "$major" \
            --arg desc "$desc" \
            --argjson completed "$local_completed" \
            --argjson total "$local_total" \
            --arg inProgress "${local_inprog:-}" \
            --argjson tasks "$tasks_json" \
            '. + [{
                "id": $id,
                "major": $major,
                "description": $desc,
                "completed": $completed,
                "total": $total,
                "inProgress": $inProgress,
                "tasks": $tasks
            }]')
    done

    # Add major to JSON
    MAJORS_JSON=$(echo "$MAJORS_JSON" | jq \
        --arg id "$major" \
        --arg name "$major_name" \
        '. + [{"id": $id, "name": $name}]')
done

# Collect pending tasks for current minor
PENDING_JSON="[]"
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
        if [ "$st" = "pending" ]; then
            PENDING_JSON=$(echo "$PENDING_JSON" | jq --arg name "$task_name" '. + [$name]')
        fi
    done
fi

[ "$TOTAL_TASKS" -eq 0 ] && TOTAL_TASKS=1
PERCENT=$((TOTAL_COMPLETED * 100 / TOTAL_TASKS))

# === OUTPUT JSON ===

jq -n \
    --arg project "$PROJECT_NAME" \
    --argjson percent "$PERCENT" \
    --argjson completed "$TOTAL_COMPLETED" \
    --argjson total "$TOTAL_TASKS" \
    --arg currentMinor "${CURRENT_MINOR:-}" \
    --arg inProgressTask "${IN_PROGRESS_TASK:-}" \
    --argjson pendingTasks "$PENDING_JSON" \
    --argjson majors "$MAJORS_JSON" \
    --argjson minors "$MINORS_JSON" \
    '{
        "project": $project,
        "overall": {
            "percent": $percent,
            "completed": $completed,
            "total": $total
        },
        "current": {
            "minor": $currentMinor,
            "inProgressTask": $inProgressTask,
            "pendingTasks": $pendingTasks
        },
        "majors": $majors,
        "minors": $minors
    }'
