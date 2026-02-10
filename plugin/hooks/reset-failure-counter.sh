#!/bin/bash
set -euo pipefail

# Post-Tool-Use-Success hook: Reset consecutive failure counter after successful tool execution
#
# Purpose: Clear the failure tracking counter when a tool succeeds
# Triggers: PostToolUse (all tools, on success)
# Action: Remove failure tracking file to reset counter

# Error handler - fail gracefully
trap 'exit 0' ERR

# Read JSON data from stdin with timeout
JSON_INPUT=""
if [ -t 0 ]; then
  exit 0  # No input available
else
  JSON_INPUT="$(timeout 5s cat 2>/dev/null)" || exit 0
fi

# Exit if no JSON input
[[ -z "$JSON_INPUT" ]] && exit 0

# Extract session ID
SESSION_ID=$(echo "$JSON_INPUT" | grep -o '"session_id"[[:space:]]*:[[:space:]]*"[^"]*"' | sed 's/"session_id"[[:space:]]*:[[:space:]]*"\([^"]*\)"/\1/' || echo "")

# Require session ID
if [[ -z "$SESSION_ID" ]]; then
  exit 0
fi

# Check if tool execution was successful (no error field in JSON)
# If there's an error field, this is a failure - don't reset
if echo "$JSON_INPUT" | grep -q '"error"[[:space:]]*:'; then
  exit 0  # Tool failed, don't reset counter
fi

# Failure tracking file
FAILURE_TRACKING_FILE="/tmp/cat-failure-tracking-${SESSION_ID}.count"

# Remove the failure tracking file to reset counter
rm -f "$FAILURE_TRACKING_FILE" 2>/dev/null || true

exit 0
