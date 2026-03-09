---
description: PREFER when writing 3+ independent files - batch operation eliminates round-trips (50-70% faster)
model: haiku
user-invocable: false
allowed-tools: Write, Edit, Bash
argument-hint: "<catAgentId>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" batch-write-agent "$0"`
