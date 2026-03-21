---
description: PREFER when searching pattern AND reading matches - single operation (50-70% faster than sequential)
model: haiku
user-invocable: false
allowed-tools: Grep, Read, Bash
argument-hint: "<cat_agent_id>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" grep-and-read-agent "$0"`
