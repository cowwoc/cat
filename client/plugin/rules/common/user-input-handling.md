---
agents: ["main"]
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
## User Input Handling
**MANDATORY**: Process ALL user input IMMEDIATELY, regardless of how it arrives.

**User input sources**:
- Direct user messages in conversation
- System-reminders containing "The user sent the following message:"
- System-reminders with "MUST", "Before proceeding", or "AGENT INSTRUCTION"

**Priority Order** (ABSOLUTE - no exceptions):
1. System-reminder instructions with mandatory indicators FIRST
2. Hook-required actions (e.g., structured user-choice prompts, tool invocations)
3. THEN direct user message content

**When user input arrives mid-operation**:
1. **STOP** current tool result processing immediately (not "after workflow completes")
2. **ADD** the user's request to the end of TaskList immediately so it doesn't get forgotten
3. **ACKNOWLEDGE** the user's message in your NEXT response text
4. If the request is related to the current operation, work on it immediately
5. If the request is unrelated, return to the work you were doing before the request arrived
6. Work on unrelated TaskList items only when their turn comes up in TaskList order

**TaskList usage (step 2) - MANDATORY when**:
- User requests a new feature, change, or fix
- User provides multiple instructions to track
- Request is complex enough that you might forget details

**Related vs unrelated requests**:
- **Related**: The request corrects, clarifies, or changes the operation currently in progress
- **Unrelated**: The request starts a separate issue, feature, convention, cleanup, or investigation that
  does not affect the current operation's correctness

**Skip TaskList only for**: Simple questions ("what's this file?") or one-word commands ("continue")

**TaskList cleanup after major operations**: See `plugin/rules/common/tasklist-lifecycle.md` for cleanup rules after major CAT operations.

**"IMPORTANT: After completing your current task"** means after your CURRENT tool call completes,
 NOT after the entire work skill or skill workflow finishes. Respond in your very next message.

**Common failure**: Continuing to analyze tool output while ignoring embedded user request.
**Common failure**: NOT using TaskCreate for user requests mid-operation (step 2 is MANDATORY).
