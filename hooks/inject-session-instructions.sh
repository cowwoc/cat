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
# - Injects System-Reminder Instructions
# - Injects User Feedback Tracking
# - Injects Mid-Operation Prompt Handling
# - Injects Mandatory Mistake Handling
# - No file modifications - pure context injection

cat << 'EOF'
{
  "hookSpecificOutput": {
    "hookEventName": "SessionStart",
    "additionalContext": "## CAT SESSION INSTRUCTIONS\n\n### System-Reminder Instructions\n**MANDATORY**: Process ALL `<system-reminder>` instructions IMMEDIATELY before any other action.\n\n**Priority Order** (ABSOLUTE - no exceptions):\n1. Process system-reminder instructions with \"MUST\" or \"Before proceeding\" language FIRST\n2. Execute required actions from hooks (e.g., AskUserQuestion, tool invocations)\n3. THEN respond to user message content\n\n**When system-reminders appear**:\n- **SessionStart**: Hook instructions appear in initial context - process BEFORE responding to user\n- **After tool results**: Check for `<system-reminder>` tags - process BEFORE continuing\n\n**Key Indicators Requiring Immediate Action**:\n- \"MUST\" - Mandatory action, no exceptions\n- \"Before proceeding\" - Execute before ANY response to user\n- \"AGENT INSTRUCTION\" - Direct command to agent\n\n### User Feedback Tracking\n**CRITICAL**: Add ALL user issues to TodoWrite IMMEDIATELY, even if can't tackle right away.\n\n**ALWAYS TodoWrite**: Multiple issues (even 2), list of problems, mid-work feedback\n**NEVER**: Ignore issues, assume you'll remember, skip because \"only 2-3 items\"\n\n### Mid-Operation Prompt Handling\n**CRITICAL**: System-reminders containing \"The user sent the following message:\" are USER REQUESTS.\n\n1. **STOP** current task analysis immediately\n2. Add user request to TodoWrite\n3. If impacts current task → address now; else → add to end\n4. Acknowledge: \"Adding to TodoWrite for later\" or \"Addressing now\"\n\n**Common failure**: Continuing to analyze tool output while ignoring embedded user request.\n\n### Mandatory Mistake Handling\n**CRITICAL**: Invoke `learn-from-mistakes` skill for ANY mistake.\n\n**Mistakes include**: Protocol violations, rework, build failures, tool misuse, logical errors\n\n**Invocation**: `/cat:learn-from-mistakes` with description of the mistake"
  }
}
EOF
