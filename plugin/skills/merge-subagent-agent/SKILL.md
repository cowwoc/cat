---
description: Use when subagent work is done and needs merging back - merge subagent branch into issue branch
model: haiku
effort: low
user-invocable: false
argument-hint: "<cat_agent_id>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" merge-subagent-agent "$0"`
