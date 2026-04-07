---
description: MUST USE when writing 2+ independent files in one task - issues all Write/Edit calls in a single response (50-70% faster)
model: haiku
effort: low
user-invocable: false
allowed-tools: Write, Edit, Bash
argument-hint: "<cat_agent_id>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" batch-write-agent "$0"`
