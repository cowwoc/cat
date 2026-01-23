#!/bin/bash
set -euo pipefail

# Error handler
trap 'echo "ERROR in inject-session-instructions.sh at line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# Inject critical session instructions via additionalContext
#
# This hook runs on SessionStart and injects instructions directly into
# Claude's context WITHOUT modifying project files.
#
# TRIGGER: SessionStart (also fires after compaction, re-injecting context)
#
# BEHAVIOR:
# - Injects User Input Handling (consolidated system-reminder, feedback, mid-operation handling)
# - Injects Mandatory Mistake Handling
# - Injects Commit Before Review
# - Injects Skill Workflow Compliance
# - No file modifications - pure context injection

# Read stdin JSON and extract session_id
INPUT=""
if [ ! -t 0 ]; then
    INPUT=$(cat)
fi
SESSION_ID=$(echo "$INPUT" | jq -r '.session_id // "unknown"' 2>/dev/null || echo "unknown")

# Build message as readable multiline text, let jq handle JSON escaping
MESSAGE=$(cat << 'INSTRUCTIONS'
## CAT SESSION INSTRUCTIONS

### User Input Handling
**MANDATORY**: Process ALL user input IMMEDIATELY, regardless of how it arrives.

**User input sources**:
- Direct user messages in conversation
- System-reminders containing "The user sent the following message:"
- System-reminders with "MUST", "Before proceeding", or "AGENT INSTRUCTION"

**Priority Order** (ABSOLUTE - no exceptions):
1. System-reminder instructions with mandatory indicators FIRST
2. Hook-required actions (e.g., AskUserQuestion, tool invocations)
3. THEN direct user message content

**When user input arrives mid-operation**:
1. **STOP** current task analysis immediately
2. Add to TodoWrite (all issues, even just 2-3 items)
3. If impacts current task → address now; else → add to end
4. Acknowledge before continuing

**Common failure**: Continuing to analyze tool output while ignoring embedded user request.

### Mandatory Mistake Handling
**CRITICAL**: Invoke `learn-from-mistakes` skill for ANY mistake.

**Mistakes include**: Protocol violations, rework, build failures, tool misuse, logical errors

**Invocation**: `/cat:learn-from-mistakes` with description of the mistake

**Trigger phrase recognition**: When user says "Learn from mistakes: [description]":
1. INVOKE `/cat:learn-from-mistakes` skill FIRST (do not just fix the problem)
2. Complete the full RCA workflow
3. THEN address the immediate issue

### Commit Before Review
**CRITICAL**: ALWAYS commit changes BEFORE asking users to review implementation.

Users cannot see unstaged changes in their environment. Showing code in chat without committing
means users cannot verify the actual file state, run tests, or validate the implementation.

**Pattern**: Implement → Commit → Then ask for review

### Skill Workflow Compliance
**CRITICAL**: When a skill is invoked, follow its documented workflow COMPLETELY.

**NEVER**: Invoke skill then manually do subset of steps, skip steps as "unnecessary"
**ALWAYS**: Execute every step in sequence; if step doesn't apply, note why and continue

Skills exist to enforce consistent processes. Shortcuts defeat their purpose.

### Work Request Handling
**DEFAULT BEHAVIOR**: When user requests work, propose task creation via `/cat:add` first.

**Response pattern**: "I'll create a task for this so it's tracked properly."

**Trust-level behavior** (read from .claude/cat/cat-config.json):
- **low**: Always ask before any work
- **medium**: Propose task for non-trivial work; ask permission for trivial fixes
- **high**: Create task automatically, proceed to /cat:work

**Trivial work**: Single-line changes, typos, 1-file cosmetic fixes only.

**User override phrases**: "just do it", "quick fix", "no task needed" → work directly with warning.

**Anti-pattern**: Starting to write code without first creating or selecting a task.

### Fail-Fast Protocol
**CRITICAL**: When a skill/workflow says "FAIL immediately" or outputs an error message, STOP.

**NEVER** attempt to "helpfully" work around the failure by:
- Manually performing what automated tooling should have done
- Reading files to gather data that a hook/script should have provided
- Providing a degraded version of the output

Output the error message and STOP execution. The fail-fast exists because workarounds produce incorrect results.

INSTRUCTIONS
)

# Append session ID (extracted from stdin JSON)
MESSAGE="${MESSAGE}
Session ID: ${SESSION_ID}"

# Use jq to properly escape the message into JSON
jq -n --arg msg "$MESSAGE" '{
  "hookSpecificOutput": {
    "hookEventName": "SessionStart",
    "additionalContext": $msg
  }
}'
