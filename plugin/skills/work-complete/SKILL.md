---
description: Internal (invoked by /cat:work after merge) - generates the Issue Complete summary box
model: haiku
user-invocable: false
argument-hint: "<completedIssue> <targetBranch>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" work-complete "${CLAUDE_SESSION_ID}" "${CLAUDE_PROJECT_DIR}" $ARGUMENTS`
