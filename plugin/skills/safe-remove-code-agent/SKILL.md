---
description: Use when removing code patterns across multiple files - safe removal with validation and rollback
model: sonnet
user-invocable: false
allowed-tools: Bash, Read, Edit, Grep, Glob
argument-hint: "<catAgentId>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" safe-remove-code-agent "$0"`
