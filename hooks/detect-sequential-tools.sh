#!/bin/bash
set -euo pipefail

# Error handler - output helpful message to stderr on failure
trap 'echo "ERROR in detect-sequential-tools.sh at line $LINENO: Command failed: $BASH_COMMAND" >&2; exit 1' ERR

# Hook to detect sequential independent tool execution anti-pattern
# Triggers reminder to batch independent tool calls for 25-30% message reduction
#
# BEHAVIOR:
# - Monitors ToolUse events for Read, Glob, Grep, and WebFetch tools
# - Tracks consecutive single-tool messages
# - Warns after 2+ sequential independent tool calls
# - Provides specific batching guidance

# Load helper scripts from local lib directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/json-parser.sh"
source "$SCRIPT_DIR/lib/session-helper.sh"
source "$SCRIPT_DIR/lib/json-output.sh"

# Parse hook input
INPUT=$(cat)
EVENT_NAME=$(echo "$INPUT" | jq -r '.eventName // empty')

# Only process ToolUse events
if [ "$EVENT_NAME" != "ToolUse" ]; then
  exit 0
fi

# Extract tool information and session ID
TOOL_NAME=$(echo "$INPUT" | jq -r '.tool.name // empty')
TOOL_COUNT=$(echo "$INPUT" | jq -r '.tool.count // 1')
SESSION_ID_RAW=$(get_required_session_id "$INPUT")
# Sanitize session ID for safe use in file paths (prevents path traversal)
SESSION_ID=$(validate_session_id "$SESSION_ID_RAW")
if [[ -z "$SESSION_ID" ]]; then
  echo "ERROR: Invalid session ID format" >&2
  exit 1
fi
STATE_FILE="/tmp/sequential-tool-tracker-$SESSION_ID.json"
CURRENT_TIME=$(date +%s)

# Initialize or load state
if [ ! -f "$STATE_FILE" ]; then
  echo "{\"last_tool_time\": 0, \"sequential_count\": 0, \"last_tool_names\": []}" > "$STATE_FILE"
fi

LAST_TOOL_TIME=$(jq -r '.last_tool_time // 0' "$STATE_FILE")
SEQUENTIAL_COUNT=$(jq -r '.sequential_count // 0' "$STATE_FILE")
LAST_TOOL_NAMES=$(jq -r '.last_tool_names // []' "$STATE_FILE")

# Tools that are commonly batchable
BATCHABLE_TOOLS=("Read" "Glob" "Grep" "WebFetch" "WebSearch")

# Check if current tool is batchable
IS_BATCHABLE=false
for tool in "${BATCHABLE_TOOLS[@]}"; do
  if [ "$TOOL_NAME" = "$tool" ]; then
    IS_BATCHABLE=true
    break
  fi
done

# If not batchable or multiple tools in one message, reset counter
if [ "$IS_BATCHABLE" = "false" ] || [ "$TOOL_COUNT" -gt 1 ]; then
  echo "{\"last_tool_time\": $CURRENT_TIME, \"sequential_count\": 0, \"last_tool_names\": []}" > "$STATE_FILE"
  exit 0
fi

# If more than 30 seconds since last tool, reset (different context)
TIME_DIFF=$((CURRENT_TIME - LAST_TOOL_TIME))
if [ "$TIME_DIFF" -gt 30 ]; then
  echo "{\"last_tool_time\": $CURRENT_TIME, \"sequential_count\": 1, \"last_tool_names\": [\"$TOOL_NAME\"]}" > "$STATE_FILE"
  exit 0
fi

# Increment sequential count
NEW_COUNT=$((SEQUENTIAL_COUNT + 1))
NEW_TOOL_NAMES=$(echo "$LAST_TOOL_NAMES" | jq --arg tool "$TOOL_NAME" '. + [$tool]')

# Update state
jq -n \
  --arg time "$CURRENT_TIME" \
  --argjson count "$NEW_COUNT" \
  --argjson tools "$NEW_TOOL_NAMES" \
  '{
    "last_tool_time": ($time | tonumber),
    "sequential_count": $count,
    "last_tool_names": $tools
  }' > "$STATE_FILE"

# Warn if 3+ sequential single-tool messages detected
if [ "$NEW_COUNT" -ge 3 ]; then
  # Get unique tool names
  TOOL_LIST=$(echo "$NEW_TOOL_NAMES" | jq -r 'unique | join(", ")')

  MESSAGE="## ⚠️ PERFORMANCE: Sequential Tool Execution Detected

**Pattern**: $NEW_COUNT consecutive single-tool messages
**Tools**: $TOOL_LIST

## 🚨 ANTI-PATTERN DETECTED (25-30% overhead)

**Current Pattern** (sequential execution):
\`\`\`
Message 1: $TOOL_NAME file_1
Message 2: $TOOL_NAME file_2
Message 3: $TOOL_NAME file_3
# Result: 3 round-trips = 200-300 extra messages per session
\`\`\`

## ✅ REQUIRED PATTERN (parallel execution)

**Batch Independent Tools in Single Message**:
\`\`\`
Single Message:
  $TOOL_NAME file_1 +
  $TOOL_NAME file_2 +
  $TOOL_NAME file_3
# Result: 1 round-trip = 67% message reduction
\`\`\`

## 📋 BATCHING RULES

**ALWAYS batch these tools when independent**:
1. ✅ Read operations - batch all file reads together
2. ✅ Glob patterns - batch all file searches together
3. ✅ Grep searches - batch all content searches together
4. ✅ WebFetch/WebSearch - batch all web operations together
5. ✅ Agent invocations - launch all agents in parallel

**Only use sequential when**:
❌ Operations have dependencies (later tool needs earlier tool's output)
❌ Conditional logic required between operations

## 💡 IMMEDIATE ACTION

**Next time you need multiple independent operations**:
- Identify ALL operations needed for current sub-task
- Launch ALL independent operations in ONE message
- Use multiple tool invocation blocks in single response

See: CLAUDE.md § Performance Optimization Requirements

**This reminder will reset after you batch tools or 30 seconds of inactivity.**"

  # Reset counter after warning
  echo "{\"last_tool_time\": $CURRENT_TIME, \"sequential_count\": 0, \"last_tool_names\": []}" > "$STATE_FILE"

  # Output warning using helper (PostToolUse event)
  output_hook_warning "PostToolUse" "$MESSAGE"
fi

exit 0
