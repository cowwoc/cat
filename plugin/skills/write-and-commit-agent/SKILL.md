---
description: PREFER when creating a new file that needs immediate committing - creates and commits atomically
model: haiku
user-invocable: false
allowed-tools: Bash, Write
argument-hint: "<catAgentId>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" write-and-commit-agent "$0"`
