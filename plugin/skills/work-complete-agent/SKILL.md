---
description: Internal (invoked by /cat:work after merge) - generates the Issue Complete summary box
model: haiku
user-invocable: false
argument-hint: "<catAgentId> <completedIssue> <targetBranch>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" work-complete-agent $ARGUMENTS`
