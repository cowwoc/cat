#!/bin/bash
# Batch Opportunity Prediction Hook
#
# ADDED: 2025-12-01
# PURPOSE: Pre-execution hook that tracks sequential read operations
#          and predicts when batching would be more efficient.
#
# Key insight: Multiple sequential Read/Glob/Grep operations waste
# overhead. Detecting patterns early allows proactive batching.
#
# Trigger: PreToolUse on Read|Glob|Grep
# State: /tmp/batch_tracker_${SESSION_ID}.json

set -euo pipefail
trap 'echo "ERROR in predict-batch-opportunity.sh at line $LINENO: Command failed: $BASH_COMMAND" >&2; exit 1' ERR

# Read stdin
INPUT=$(cat)

# Extract tool info
TOOL_NAME=$(echo "$INPUT" | jq -r '.tool_name // .tool.name // empty' 2>/dev/null || echo "")
SESSION_ID=$(echo "$INPUT" | jq -r '.session_id // empty' 2>/dev/null || echo "")
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // .tool_input.path // .tool_input.pattern // empty' 2>/dev/null || echo "")

# Only track Read, Glob, Grep
if [[ "$TOOL_NAME" != "Read" && "$TOOL_NAME" != "Glob" && "$TOOL_NAME" != "Grep" ]]; then
    exit 0
fi

# Exit if no session ID (can't track without it)
if [[ -z "$SESSION_ID" ]]; then
    exit 0
fi

TIMESTAMP=$(date +%s)
TRACKER_FILE="/tmp/batch_tracker_${SESSION_ID}.json"
WINDOW_SECONDS=30  # Track operations within 30-second windows
THRESHOLD=3        # Warn after 3 sequential operations

# Initialize tracker if needed (with atomic creation to prevent race conditions)
if [[ ! -f "$TRACKER_FILE" ]]; then
    # Use atomic write to prevent race condition with concurrent hooks
    echo '{"operations": [], "warnings_shown": 0, "last_warning": 0}' > "${TRACKER_FILE}.init.$$" 2>/dev/null
    mv "${TRACKER_FILE}.init.$$" "$TRACKER_FILE" 2>/dev/null || rm -f "${TRACKER_FILE}.init.$$"
fi

# Verify tracker file exists and is readable before proceeding
if [[ ! -f "$TRACKER_FILE" ]] || [[ ! -r "$TRACKER_FILE" ]]; then
    exit 0  # Silently exit if file not accessible - advisory hook shouldn't block
fi

# Add this operation (with safe temp file handling)
# Use unique temp file per process to avoid conflicts
TEMP_FILE="${TRACKER_FILE}.tmp.$$"
if jq --arg tool "$TOOL_NAME" \
      --arg path "$FILE_PATH" \
      --arg ts "$TIMESTAMP" \
      '.operations += [{"tool": $tool, "path": $path, "timestamp": ($ts | tonumber)}]' \
      "$TRACKER_FILE" > "$TEMP_FILE" 2>/dev/null && [[ -s "$TEMP_FILE" ]]; then
    mv "$TEMP_FILE" "$TRACKER_FILE" 2>/dev/null || rm -f "$TEMP_FILE"
else
    rm -f "$TEMP_FILE"
    exit 0  # Silently exit on jq failure - advisory hook shouldn't block
fi

# Clean old operations (outside window)
CUTOFF=$((TIMESTAMP - WINDOW_SECONDS))
TEMP_FILE="${TRACKER_FILE}.tmp.$$"
if jq --arg cutoff "$CUTOFF" \
      '.operations = [.operations[] | select(.timestamp > ($cutoff | tonumber))]' \
      "$TRACKER_FILE" > "$TEMP_FILE" 2>/dev/null && [[ -s "$TEMP_FILE" ]]; then
    mv "$TEMP_FILE" "$TRACKER_FILE" 2>/dev/null || rm -f "$TEMP_FILE"
else
    rm -f "$TEMP_FILE"
    exit 0
fi

# Count recent operations (with defensive defaults)
RECENT_COUNT=$(jq '.operations | length' "$TRACKER_FILE" 2>/dev/null || echo "0")
LAST_WARNING=$(jq '.last_warning // 0' "$TRACKER_FILE" 2>/dev/null || echo "0")
WARNING_COOLDOWN=60  # Only warn once per minute

# Check if we should warn
if [[ $RECENT_COUNT -ge $THRESHOLD ]]; then
    TIME_SINCE_WARNING=$((TIMESTAMP - LAST_WARNING))

    if [[ $TIME_SINCE_WARNING -gt $WARNING_COOLDOWN ]]; then
        # Update last warning time (with safe temp file handling)
        TEMP_FILE="${TRACKER_FILE}.tmp.$$"
        if jq --arg ts "$TIMESTAMP" '.last_warning = ($ts | tonumber) | .warnings_shown += 1' \
              "$TRACKER_FILE" > "$TEMP_FILE" 2>/dev/null && [[ -s "$TEMP_FILE" ]]; then
            mv "$TEMP_FILE" "$TRACKER_FILE" 2>/dev/null || rm -f "$TEMP_FILE"
        else
            rm -f "$TEMP_FILE"
        fi

        # Get list of recent paths
        RECENT_PATHS=$(jq -r '.operations | map(.path) | join(", ")' "$TRACKER_FILE" 2>/dev/null || echo "(unknown)")

        cat >&2 << EOF

💡 BATCH OPPORTUNITY DETECTED
───────────────────────────────────────────────────────────────────────────────

$RECENT_COUNT sequential $TOOL_NAME operations in the last ${WINDOW_SECONDS}s.

Recent targets: $RECENT_PATHS

CONSIDER BATCHING:
  • Use parallel tool calls in a single message
  • Use Glob with pattern matching instead of multiple Reads
  • Use Grep with broader scope instead of multiple searches
  • Use the batch-read skill for coordinated file reading

EFFICIENCY TIP:
  Instead of:  Read file1, Read file2, Read file3 (sequential)
  Use:         Read file1 + Read file2 + Read file3 (parallel in one message)

───────────────────────────────────────────────────────────────────────────────

EOF
    fi
fi

# Always allow - this is advisory only
exit 0
