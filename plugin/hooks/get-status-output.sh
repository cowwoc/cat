#!/bin/bash
# get-status-output.sh - Pre-compute status display before skill runs
#
# TRIGGER: UserPromptSubmit
#
# When user invokes /cat:status, this hook:
# 1. Runs the status script to collect project data
# 2. Computes the exact box lines via Python
# 3. Returns the pre-rendered display via additionalContext
#
# The skill then just outputs the pre-computed result - no agent computation needed.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/json-parser.sh"
source "$SCRIPT_DIR/lib/json-output.sh"

# Initialize hook
if ! init_hook; then
    echo '{}'
    exit 0
fi

# Check if this is a /cat:status command
if [[ ! "$USER_PROMPT" =~ ^[[:space:]]*/cat:status[[:space:]]*$ ]] && \
   [[ ! "$USER_PROMPT" =~ ^[[:space:]]*cat:status[[:space:]]*$ ]]; then
    echo '{}'
    exit 0
fi

# Find project root (look for .claude/cat directory)
PROJECT_ROOT=""
if [[ -n "${CLAUDE_PROJECT_DIR:-}" ]] && [[ -d "${CLAUDE_PROJECT_DIR}/.claude/cat" ]]; then
    PROJECT_ROOT="$CLAUDE_PROJECT_DIR"
elif [[ -d ".claude/cat" ]]; then
    PROJECT_ROOT="$(pwd)"
else
    # No CAT project found, let the skill handle the error
    echo '{}'
    exit 0
fi

# Find plugin root for scripts
PLUGIN_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
STATUS_SCRIPT="$PLUGIN_ROOT/scripts/status.sh"
BOX_SCRIPT="$PLUGIN_ROOT/scripts/build-box-lines.py"

if [[ ! -f "$STATUS_SCRIPT" ]] || [[ ! -f "$BOX_SCRIPT" ]]; then
    echo '{}'
    exit 0
fi

# Run status script to get JSON data
STATUS_JSON=$("$STATUS_SCRIPT" "$PROJECT_ROOT/.claude/cat" 2>/dev/null) || {
    echo '{}'
    exit 0
}

# Check for error in status output
if echo "$STATUS_JSON" | jq -e '.error' >/dev/null 2>&1; then
    echo '{}'
    exit 0
fi

# Extract data from status JSON
PROJECT=$(echo "$STATUS_JSON" | jq -r '.project // "Project"')
PERCENT=$(echo "$STATUS_JSON" | jq -r '.overall.percent // 0')
COMPLETED=$(echo "$STATUS_JSON" | jq -r '.overall.completed // 0')
TOTAL=$(echo "$STATUS_JSON" | jq -r '.overall.total // 0')
ACTIVE_MINOR=$(echo "$STATUS_JSON" | jq -r '.current.minor // "none"')
PENDING_COUNT=$(echo "$STATUS_JSON" | jq -r '.current.pendingTasks | length // 0')

# Helper function to calculate display width using the Python script
display_width() {
    echo "$1" | python3 "$BOX_SCRIPT" --format json 2>/dev/null | jq -r '.widths | to_entries[0].value // 0'
}

# Build progress bar (25 chars wide)
FILLED=$((PERCENT * 25 / 100))
EMPTY=$((25 - FILLED))
PROGRESS_BAR=$(printf '%0.s█' $(seq 1 $FILLED 2>/dev/null) || true)
PROGRESS_BAR+=$(printf '%0.s░' $(seq 1 $EMPTY 2>/dev/null) || true)

# Build content items for the outer box
CONTENT_ITEMS=()
CONTENT_ITEMS+=("📊 Overall: [${PROGRESS_BAR}] ${PERCENT}%")
CONTENT_ITEMS+=("🏆 ${COMPLETED}/${TOTAL} tasks complete")
CONTENT_ITEMS+=("")  # blank line

# Build inner boxes for each major version
MAJORS=$(echo "$STATUS_JSON" | jq -r '.majors[] | @base64')

for major_b64 in $MAJORS; do
    major=$(echo "$major_b64" | base64 -d)
    major_id=$(echo "$major" | jq -r '.id')
    major_name=$(echo "$major" | jq -r '.name')

    # Get minors for this major
    MINORS=$(echo "$STATUS_JSON" | jq -r --arg mid "$major_id" '.minors[] | select(.major == $mid) | @base64')

    INNER_CONTENT=()
    for minor_b64 in $MINORS; do
        minor=$(echo "$minor_b64" | base64 -d)
        minor_id=$(echo "$minor" | jq -r '.id')
        minor_desc=$(echo "$minor" | jq -r '.description // ""')
        minor_completed=$(echo "$minor" | jq -r '.completed')
        minor_total=$(echo "$minor" | jq -r '.total')
        minor_in_progress=$(echo "$minor" | jq -r '.inProgress // ""')

        # Determine emoji
        if [[ "$minor_completed" -eq "$minor_total" ]] && [[ "$minor_total" -gt 0 ]]; then
            emoji="☑️"
        elif [[ -n "$minor_in_progress" ]] || [[ "$minor_id" == "$ACTIVE_MINOR" ]]; then
            emoji="🔄"
        else
            emoji="🔳"
        fi

        # Format: emoji version: description (completed/total)
        if [[ -n "$minor_desc" ]]; then
            INNER_CONTENT+=("${emoji} ${minor_id}: ${minor_desc} (${minor_completed}/${minor_total})")
        else
            INNER_CONTENT+=("${emoji} ${minor_id}: (${minor_completed}/${minor_total})")
        fi

        # If this is the active minor, show pending tasks
        if [[ "$minor_id" == "$ACTIVE_MINOR" ]]; then
            PENDING_TASKS=$(echo "$STATUS_JSON" | jq -r '.current.pendingTasks[]' 2>/dev/null || true)
            for task in $PENDING_TASKS; do
                # Truncate long task names
                if [[ ${#task} -gt 25 ]]; then
                    task="${task:0:22}..."
                fi
                INNER_CONTENT+=("   🔳 ${task}")
            done
        fi
    done

    # Build inner box header text: 📦 v1: Name
    HEADER="📦 ${major_id}: ${major_name}"
    HEADER_DISPLAY_WIDTH=$(display_width "$HEADER")
    # Header line format: ╭─ HEADER ─...─╮ needs HEADER + 4 chars (╭─ and ─╮) + 1 space after header
    # So minimum inner box width = HEADER_DISPLAY_WIDTH + 1 (for trailing space before dashes)
    HEADER_MIN_WIDTH=$((HEADER_DISPLAY_WIDTH + 1))

    # Compute inner box lines - first pass to get content max width
    CONTENT_MAX=$(printf '%s\n' "${INNER_CONTENT[@]}" | python3 "$BOX_SCRIPT" --format json 2>/dev/null | jq -r '.max_content_width')

    # Use the larger of header width or content width for consistent inner box
    if [[ $HEADER_MIN_WIDTH -gt $CONTENT_MAX ]]; then
        INNER_MAX=$HEADER_MIN_WIDTH
    else
        INNER_MAX=$CONTENT_MAX
    fi

    # Recompute inner box lines with forced max width
    INNER_JSON=$(printf '%s\n' "${INNER_CONTENT[@]}" | python3 "$BOX_SCRIPT" --max-width "$INNER_MAX" --format json 2>/dev/null) || continue

    # Build inner box header: ╭─ 📦 v1: Name ─────╮
    # Content line width = 4 + INNER_MAX (for "│ " prefix and " │" suffix)
    # Header line format: ╭─ HEADER <space><dashes>╮
    # Header line width = 3 + HEADER_DISPLAY_WIDTH + 1 + REMAINING + 1
    #                   = 5 + HEADER_DISPLAY_WIDTH + REMAINING
    # For equal width: 4 + INNER_MAX = 5 + HEADER_DISPLAY_WIDTH + REMAINING
    # So: REMAINING = INNER_MAX - HEADER_DISPLAY_WIDTH - 1
    REMAINING=$((INNER_MAX - HEADER_DISPLAY_WIDTH - 1))
    if [[ $REMAINING -lt 0 ]]; then REMAINING=0; fi
    if [[ $REMAINING -gt 0 ]]; then
        DASHES=$(printf '%0.s─' $(seq 1 $REMAINING))
    else
        DASHES=""
    fi

    INNER_TOP="╭─ ${HEADER} ${DASHES}╮"
    INNER_BOTTOM=$(echo "$INNER_JSON" | jq -r '.bottom')

    # Add inner box to outer content
    CONTENT_ITEMS+=("$INNER_TOP")
    while IFS= read -r line; do
        CONTENT_ITEMS+=("$line")
    done < <(echo "$INNER_JSON" | jq -r '.lines[]')
    CONTENT_ITEMS+=("$INNER_BOTTOM")
    CONTENT_ITEMS+=("")  # blank line between majors
done

# Add footer
CONTENT_ITEMS+=("🎯 Active: ${ACTIVE_MINOR}")
CONTENT_ITEMS+=("📋 Available: ${PENDING_COUNT} pending tasks")

# Compute outer box
OUTER_JSON=$(printf '%s\n' "${CONTENT_ITEMS[@]}" | python3 "$BOX_SCRIPT" --format json 2>/dev/null) || {
    echo '{}'
    exit 0
}

# Build final output
OUTER_TOP=$(echo "$OUTER_JSON" | jq -r '.top')
OUTER_BOTTOM=$(echo "$OUTER_JSON" | jq -r '.bottom')
OUTER_LINES=$(echo "$OUTER_JSON" | jq -r '.lines[]')

# Assemble complete box
FINAL_BOX="${OUTER_TOP}"$'\n'
while IFS= read -r line; do
    FINAL_BOX+="${line}"$'\n'
done <<< "$OUTER_LINES"
FINAL_BOX+="${OUTER_BOTTOM}"

# Build the complete output message
OUTPUT=$(cat << EOF
PRE-COMPUTED STATUS DISPLAY (copy exactly):

${FINAL_BOX}

NEXT STEPS table:
| Option | Action | Command |
|--------|--------|---------|
| [**1**] | Execute a task | \`/cat:work ${ACTIVE_MINOR}-<task-name>\` |
| [**2**] | Add new task | \`/cat:add\` |

Legend: ☑️ Completed · 🔄 In Progress · 🔳 Pending · 🚫 Blocked · 🚧 Gate Waiting

INSTRUCTION: Output the above box and tables EXACTLY as shown. Do not recalculate.
EOF
)

output_hook_message "UserPromptSubmit" "$OUTPUT"
exit 0
