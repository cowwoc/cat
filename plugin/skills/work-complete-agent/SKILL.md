---
description: Internal (invoked by /cat:work after merge) - generates the Issue Complete summary box
model: haiku
effort: low
user-invocable: false
argument-hint: "<cat_agent_id> <completed_issue> <target_branch>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" work-complete-agent "$ARGUMENTS"`
