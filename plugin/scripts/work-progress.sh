#!/bin/bash
set -euo pipefail

# Work Command Progress Display Generator
#
# Renders various progress boxes for the /cat:work command with proper
# emoji-aware width calculation.
#
# Usage: work-progress.sh BOX_TYPE [ARGS...]
#
# Box types:
#   header TASK_NAME [STATUS]           - Task header box
#   checkpoint TASK_NAME TIME TOKENS TOKEN_PCT BRANCH
#   task-complete TASK_NAME [NEXT_TASK] [NEXT_GOAL]
#   task-complete-auto TASK_NAME NEXT_TASK NEXT_GOAL
#   scope-complete SCOPE_DESC
#   blocked TASK_NAME REASON
#   no-tasks

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/box.sh"

# Work boxes use width 65 (63 interior + 2 borders)
box_init 65

box_header() {
    local task_name="${1:-task}"
    local status="${2:-}"
    local status_char=""
    [[ "$status" == "success" ]] && status_char=" ✓"
    [[ "$status" == "failed" ]] && status_char=" ✗"

    # Style E format: header in rounded box
    local content="  🐱 CAT › ${task_name}${status_char}"
    local inner_width=$((BOX_WIDTH - 2))
    local content_width
    content_width=$(display_width "$content")
    local padding=$((inner_width - content_width))

    # Round corners: ╭ ╮ ╰ ╯
    printf '╭%s╮\n' "$(printf '─%.0s' $(seq 1 $inner_width))"
    if [ $padding -gt 0 ]; then
        printf '│%s%*s│\n' "$content" $padding ""
    else
        printf '│%s│\n' "$content"
    fi
    printf '╰%s╯\n' "$(printf '─%.0s' $(seq 1 $inner_width))"
}

box_checkpoint() {
    local task_name="${1:-task}"
    local time="${2:-N}"
    local tokens="${3:-N}"
    local token_pct="${4:-N}"
    local branch="${5:-task-branch}"

    box_top "✅ CHECKPOINT: Task Complete"
    box_empty
    box_line "  Task: ${task_name}"
    box_empty
    box_divider
    box_line "  Time: ${time} minutes | Tokens: ${tokens} (${token_pct}% of context)"
    box_divider
    box_line "  Branch: ${branch}"
    box_empty
    box_bottom
}

box_task_complete() {
    local task_name="${1:-task}"
    local next_task="${2:-}"
    local next_goal="${3:-}"

    box_top "✓ Task Complete"
    box_empty
    box_line "  ${task_name} merged to main."
    box_empty

    if [ -n "$next_task" ]; then
        box_divider
        box_line "  Next Up: ${next_task}"
        if [ -n "$next_goal" ]; then
            box_line "  ${next_goal}"
        fi
        box_empty
        box_line "  /cat:work to continue"
        box_divider
        box_empty
    fi

    box_bottom
}

box_task_complete_auto() {
    local task_name="${1:-task}"
    local next_task="${2:-next-task}"
    local next_goal="${3:-}"

    box_top "✓ Task Complete"
    box_empty
    box_line "  ${task_name} merged to main."
    box_empty
    box_divider
    box_line "  Next: ${next_task}"
    if [ -n "$next_goal" ]; then
        box_line "  ${next_goal}"
    fi
    box_empty
    box_line "  Auto-continuing in 3s..."
    box_line "  • Type \"stop\" to pause after this task"
    box_line "  • Type \"abort\" to cancel immediately"
    box_divider
    box_empty
    box_bottom
}

box_scope_complete() {
    local scope_desc="${1:-All tasks complete}"

    box_top "✓ Scope Complete"
    box_empty
    box_line "  ${scope_desc} - all tasks complete!"
    box_empty
    box_bottom
}

box_blocked() {
    local task_name="${1:-task}"
    local reason="${2:-Unknown reason}"

    box_top "⏸️ NO EXECUTABLE TASKS AVAILABLE"
    box_empty
    box_line "  Task \`${task_name}\` is locked by another session."
    box_empty
    box_line "  Blocked tasks:"
    # Split reason by | for multiple blocked tasks
    IFS='|' read -ra BLOCKED <<< "$reason"
    for task in "${BLOCKED[@]}"; do
        box_line "  - ${task}"
    done
    box_empty
    box_bottom
}

box_no_tasks() {
    box_top "⏸️ NO EXECUTABLE TASKS"
    box_empty
    box_line "  No executable tasks found."
    box_empty
    box_line "  Possible reasons:"
    box_line "  - All tasks completed"
    box_line "  - Remaining tasks have unmet dependencies"
    box_line "  - Exit gate tasks waiting for non-gating tasks"
    box_line "  - Entry gates not satisfied"
    box_line "  - All eligible tasks are locked"
    box_line "  - No tasks defined yet"
    box_empty
    box_line "  Use /cat:status to see current state."
    box_line "  Use /cat:add to add new tasks."
    box_empty
    box_bottom
}

box_progress() {
    local current_phase="${1:-1}"
    local phase_status="${2:-active}"
    local detail="${3:-}"

    # Phase names
    local -a phases=("Preparing" "Executing" "Reviewing" "Merging")

    # Style E format:
    # ✓ = Completed step
    # ► = Current step (with ◀── current marker)
    # ◦ = Pending step

    # Render each phase line
    for i in {1..4}; do
        local phase_name="${phases[$((i-1))]}"
        local symbol=""
        local suffix=""

        if [[ $i -lt $current_phase ]]; then
            # Completed phases
            symbol="✓"
            suffix=""
        elif [[ $i -eq $current_phase ]]; then
            # Current phase
            if [[ "$phase_status" == "complete" ]]; then
                symbol="✓"
                suffix="     ◀── current"
                [[ -n "$detail" ]] && suffix="     ◀── $detail"
            elif [[ "$phase_status" == "failed" ]]; then
                symbol="✗"
                suffix="     ◀── FAILED"
                [[ -n "$detail" ]] && suffix="     ◀── $detail"
            else
                symbol="►"
                suffix="     ◀── current"
                [[ -n "$detail" ]] && suffix="     ◀── $detail"
            fi
        else
            # Pending phases
            symbol="◦"
            suffix=""
        fi

        echo "  ${symbol} ${phase_name}${suffix}"
    done
}

# Main dispatcher
BOX_TYPE="${1:-}"
shift || true

case "$BOX_TYPE" in
    header)
        box_header "$@"
        ;;
    checkpoint)
        box_checkpoint "$@"
        ;;
    task-complete)
        box_task_complete "$@"
        ;;
    task-complete-auto)
        box_task_complete_auto "$@"
        ;;
    scope-complete)
        box_scope_complete "$@"
        ;;
    blocked)
        box_blocked "$@"
        ;;
    no-tasks)
        box_no_tasks
        ;;
    progress)
        box_progress "$@"
        ;;
    *)
        echo "Usage: work-progress.sh BOX_TYPE [ARGS...]" >&2
        echo "" >&2
        echo "Box types:" >&2
        echo "  header TASK_NAME [STATUS]" >&2
        echo "  checkpoint TASK_NAME TIME TOKENS TOKEN_PCT BRANCH" >&2
        echo "  task-complete TASK_NAME [NEXT_TASK] [NEXT_GOAL]" >&2
        echo "  task-complete-auto TASK_NAME NEXT_TASK NEXT_GOAL" >&2
        echo "  scope-complete SCOPE_DESC" >&2
        echo "  blocked TASK_NAME BLOCKED_TASKS" >&2
        echo "  no-tasks" >&2
        echo "  progress PHASE [STATUS] [DETAIL]  - Phase indicator (PHASE=1-4)" >&2
        exit 1
        ;;
esac
