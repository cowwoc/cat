---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---
# Work Complete

Generate the Issue Complete box after a successful merge, discovering the next available task.

Output the skill result verbatim. Do not summarize, interpret, or modify the output.

**Parse the output to determine next task status:**
- If output contains "**Next:**" followed by an issue ID → next task found
- If output contains "Scope Complete" → no next task

<output skill="work-complete">
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-next-task-box" --completed-issue $completedIssue --base-branch $baseBranch --session-id "${CLAUDE_SESSION_ID}" --project-dir "${CLAUDE_PROJECT_DIR}"`
</output>
