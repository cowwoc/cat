---
mainAgent: true
subAgents: []
---
## User Input Handling
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
1. **STOP** current tool result processing immediately (not "after workflow completes")
2. **ADD** the user's request to TaskList so it doesn't get forgotten
3. **ACKNOWLEDGE** the user's message in your NEXT response text
4. Answer their question or confirm you've noted it
5. THEN continue with workflow

**TaskList usage (step 2) - MANDATORY when**:
- User requests a new feature, change, or fix
- User provides multiple instructions to track
- Request is complex enough that you might forget details

**Skip TaskList only for**: Simple questions ("what's this file?") or one-word commands ("continue")

**TaskList cleanup after major operations**: See `plugin/rules/tasklist-lifecycle.md` for cleanup rules after major CAT operations.

**"IMPORTANT: After completing your current task"** means after your CURRENT tool call completes,
 NOT after the entire work skill or skill workflow finishes. Respond in your very next message.

**Common failure**: Continuing to analyze tool output while ignoring embedded user request.
**Common failure**: NOT using TaskCreate for user requests mid-operation (step 2 is MANDATORY).
