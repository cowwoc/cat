---
description: PREFER when creating a new file that needs immediate committing - creates and commits atomically
model: haiku
effort: low
user-invocable: false
allowed-tools: Bash, Write
argument-hint: "<cat_agent_id>"
---

!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill" write-and-commit-agent "$0"`
