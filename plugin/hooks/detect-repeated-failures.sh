#!/bin/bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
set -euo pipefail

# Post-Tool-Use-Failure hook: Detect repeated consecutive failures and suggest drift recovery
#
# Purpose: Track consecutive tool failures and suggest /cat:recover-from-drift after 2+ failures
# Triggers: PostToolUseFailure (all tools)
# Action: Inject suggestion to check plan alignment if repeated failures detected

# Error handler - fail gracefully
trap 'echo "ERROR in detect-repeated-failures.sh at line $LINENO: Command failed: $BASH_COMMAND" >&2; exit 0' ERR

# Read JSON data from stdin with timeout
JSON_INPUT=""
if [ -t 0 ]; then
  exit 0  # No input available
else
  JSON_INPUT="$(timeout 5s cat 2>/dev/null)" || exit 0
fi

# Exit if no JSON input
[[ -z "$JSON_INPUT" ]] && exit 0

# Extract session ID (required for tracking failures)
SESSION_ID=$(echo "$JSON_INPUT" | grep -o '"session_id"[[:space:]]*:[[:space:]]*"[^"]*"' | sed 's/"session_id"[[:space:]]*:[[:space:]]*"\([^"]*\)"/\1/' || echo "")

# Require session ID
if [[ -z "$SESSION_ID" ]]; then
  exit 0  # Fail gracefully without session ID
fi

# Extract tool name from JSON
TOOL_NAME=$(echo "$JSON_INPUT" | grep -o '"tool"[[:space:]]*:[[:space:]]*"[^"]*"' | sed 's/"tool"[[:space:]]*:[[:space:]]*"\([^"]*\)"/\1/' || echo "unknown")

# Failure tracking file (stores consecutive failure count)
FAILURE_TRACKING_FILE="/tmp/cat-failure-tracking-${SESSION_ID}.count"

# Read current failure count
if [[ -f "$FAILURE_TRACKING_FILE" ]]; then
  FAILURE_COUNT=$(cat "$FAILURE_TRACKING_FILE" 2>/dev/null || echo "0")
else
  FAILURE_COUNT=0
fi

# Increment failure count
FAILURE_COUNT=$((FAILURE_COUNT + 1))

# Write updated count
echo "$FAILURE_COUNT" > "$FAILURE_TRACKING_FILE"

# If 2 or more consecutive failures, suggest drift check
if [[ $FAILURE_COUNT -ge 2 ]]; then
  cat <<'SUGGESTION'
<system-reminder>
🔄 REPEATED TOOL FAILURES DETECTED

**Failure Count**: 2+ consecutive failures detected.

**Possible Causes**:
1. **Goal Drift**: You may be attempting actions not in the current PLAN.md execution step
2. **Legitimate Error**: The current step has a genuine technical issue

**RECOMMENDED ACTION**:
Consider running `/cat:recover-from-drift` to verify you are aligned with the current execution step.

The recovery skill will:
- Read the current PLAN.md
- Identify which step should be active
- Compare your failing action against the plan
- Determine if drift has occurred
- Provide specific guidance on how to proceed

**Key Principle**: Before repeatedly retrying or generalizing solutions, verify you are working on the correct step.
</system-reminder>
SUGGESTION
fi

# Cleanup old tracking files (1-day TTL)
find /tmp -maxdepth 1 -name "cat-failure-tracking-*.count" -mtime +1 -delete 2>/dev/null || true

exit 0
