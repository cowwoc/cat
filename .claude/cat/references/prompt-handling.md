# Prompt Handling Reference

## System-Reminder Instructions

**MANDATORY**: Process ALL `<system-reminder>` instructions IMMEDIATELY before any other action.

**Priority Order** (ABSOLUTE - no exceptions):
1. Process system-reminder instructions with "MUST" or "Before proceeding" language FIRST
2. Execute required actions from hooks (e.g., AskUserQuestion, tool invocations)
3. THEN respond to user message content

**When system-reminders appear**:
- **SessionStart**: Hook instructions appear in initial context - process BEFORE responding to user
- **After tool results**: Check for `<system-reminder>` tags - process BEFORE continuing

**Key Indicators Requiring Immediate Action**:
- "MUST" - Mandatory action, no exceptions
- "Before proceeding" - Execute before ANY response to user
- "AGENT INSTRUCTION" - Direct command to agent

## Mid-Operation Prompt Handling {#mid-operation-prompt-handling}

**CRITICAL**: System-reminders containing "The user sent the following message:" are USER REQUESTS.

1. **STOP** current task analysis immediately
2. Add user request to TodoWrite
3. If impacts current task → address now; else → add to end
4. Acknowledge: "Adding to TodoWrite for later" or "Addressing now"

**Common failure**: Continuing to analyze tool output while ignoring embedded user request.

## User Feedback Tracking

**CRITICAL**: Add ALL user issues to TodoWrite IMMEDIATELY, even if can't tackle right away.

**ALWAYS TodoWrite**: Multiple issues (even 2), list of problems, mid-work feedback
**NEVER**: Ignore issues, assume you'll remember, skip because "only 2-3 items"
