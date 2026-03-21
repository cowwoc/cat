---
description: PREFER when reading 3+ related files - batch operation eliminates round-trips (50-70% faster)
model: haiku
user-invocable: false
allowed-tools: Bash
argument-hint: "<cat_agent_id>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" batch-read-agent "$0"`
