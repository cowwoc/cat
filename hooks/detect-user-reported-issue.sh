#!/bin/bash
# User Issue Auto-Detection Hook
#
# ADDED: 2025-12-01
# PURPOSE: Detect when user reports issues/bugs and flag as detection gaps
#          requiring TDD workflow before fixing.
#
# Key insight: When users discover issues that our validation missed,
# that's a "detection gap" - we need to write a test FIRST to prevent regression.
#
# Trigger: UserPromptSubmit
# Output: Flags detection gap in /tmp/pending_detection_gaps_${SESSION_ID}.json

set -euo pipefail
trap 'echo "ERROR in detect-user-reported-issue.sh at line $LINENO: Command failed: $BASH_COMMAND" >&2; exit 1' ERR

# Read stdin
INPUT=$(cat)

# Extract user message
USER_MESSAGE=$(echo "$INPUT" | jq -r '.user_message // .message // .content // empty' 2>/dev/null || echo "")
SESSION_ID=$(echo "$INPUT" | jq -r '.session_id // empty' 2>/dev/null || echo "")

if [[ -z "$USER_MESSAGE" ]]; then
    exit 0
fi

# Patterns indicating user discovered an issue
ISSUE_PATTERNS=(
    "this is wrong"
    "this is incorrect"
    "that's wrong"
    "that's incorrect"
    "bug in"
    "there's a bug"
    "doesn't work"
    "isn't working"
    "not working"
    "broken"
    "should be"
    "should have been"
    "you missed"
    "missing from"
    "forgot to"
    "failed to"
    "why didn't"
    "why isn't"
    "still showing"
    "still has"
    "still contains"
    "didn't catch"
    "wasn't caught"
    "wasn't detected"
    "not detected"
    "false positive"
    "false negative"
    "incorrect output"
    "wrong output"
    "wrong result"
    "incorrect result"
)

# Convert message to lowercase for matching
USER_MESSAGE_LOWER=$(echo "$USER_MESSAGE" | tr '[:upper:]' '[:lower:]')

# Check for issue patterns
MATCHED_PATTERN=""
for pattern in "${ISSUE_PATTERNS[@]}"; do
    if [[ "$USER_MESSAGE_LOWER" == *"$pattern"* ]]; then
        MATCHED_PATTERN="$pattern"
        break
    fi
done

if [[ -z "$MATCHED_PATTERN" ]]; then
    # No issue pattern detected
    exit 0
fi

# Issue detected - flag as detection gap
TIMESTAMP=$(date -Iseconds)
GAPS_FILE="/tmp/pending_detection_gaps_${SESSION_ID}.json"

# Initialize file if doesn't exist
if [[ ! -f "$GAPS_FILE" ]]; then
    echo '{"gaps": [], "created": "'$TIMESTAMP'"}' > "$GAPS_FILE"
fi

# Add gap entry
GAP_ID="GAP-$(date +%s)"
jq --arg id "$GAP_ID" \
   --arg pattern "$MATCHED_PATTERN" \
   --arg message "$USER_MESSAGE" \
   --arg timestamp "$TIMESTAMP" \
   '.gaps += [{
     "id": $id,
     "pattern": $pattern,
     "user_message": $message,
     "timestamp": $timestamp,
     "status": "pending_tdd",
     "test_written": false
   }]' "$GAPS_FILE" > "${GAPS_FILE}.tmp" && mv "${GAPS_FILE}.tmp" "$GAPS_FILE"

# Alert the agent
cat >&2 << EOF

🔍 DETECTION GAP IDENTIFIED
╭─────────────────────────────────────────────────────────────────────────────╮
   The user reported an issue that our validation didn't catch.
   This is a DETECTION GAP requiring TDD workflow.

   Pattern matched: "$MATCHED_PATTERN"
   Gap ID: $GAP_ID

   REQUIRED WORKFLOW (Test-Driven Bug Fix):
   1. Write a FAILING test that reproduces the user's issue
   2. Verify the test FAILS (proves it catches the bug)
   3. Fix the code
   4. Verify the test PASSES

   Invoke: Skill: tdd-implementation
╰─────────────────────────────────────────────────────────────────────────────╯

EOF

exit 0
