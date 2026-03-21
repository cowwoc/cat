---
description: Internal prepare phase (invoked by /cat:work) - finds next issue, acquires lock, creates worktree
model: sonnet
user-invocable: false
argument-hint: "<cat_agent_id>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" work-prepare-agent "$0"`
