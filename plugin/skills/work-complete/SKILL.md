---
description: Internal (invoked by /cat:work after merge) - generates the Issue Complete summary box
user-invocable: false
argument-hint: "<completedIssue> <baseBranch>"
---

!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" work-complete "${CLAUDE_SESSION_ID}" "$ARGUMENTS"`
