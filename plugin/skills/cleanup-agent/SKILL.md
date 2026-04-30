---
description: Use when session crashed or locks blocking - cleans abandoned worktrees, lock files, and orphaned branches
model: haiku
effort: low
context: fork
allowed-tools:
  - Bash
  - Read
user-invocable: false
argument-hint: "<cat_agent_id>"
---

!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill" cleanup-agent "$0"`
