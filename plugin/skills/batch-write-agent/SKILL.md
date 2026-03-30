---
description: PREFER when writing 2+ independent files - batch operation eliminates round-trips (50-70% faster)
model: haiku
user-invocable: false
allowed-tools: Write, Edit, Bash
argument-hint: "<cat_agent_id>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" batch-write-agent "$0"`
