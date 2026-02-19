---
description: "Generate Issue Complete box after merge. Invoked by /cat:work."
user-invocable: false
arguments:
  - completedIssue
  - baseBranch
---
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-next-task-box" --completed-issue $completedIssue --base-branch $baseBranch --session-id "${CLAUDE_SESSION_ID}" --project-dir "${CLAUDE_PROJECT_DIR}"`
