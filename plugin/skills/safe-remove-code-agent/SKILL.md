---
description: Use when removing code patterns across multiple files - safe removal with validation and rollback
user-invocable: false
allowed-tools: Bash, Read, Edit, Grep, Glob
argument-hint: "<cat_agent_id>"
effort: medium
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" safe-remove-code-agent "$0"`
