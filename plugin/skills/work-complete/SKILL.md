---
description: Internal (invoked by /cat:work after merge) - generates the Issue Complete summary box
model: haiku
effort: low
user-invocable: false
argument-hint: "<completed_issue> <target_branch>"
---

OUTPUT=!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-output" work-complete $1 $2"
