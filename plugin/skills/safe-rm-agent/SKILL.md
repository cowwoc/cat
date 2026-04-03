---
description: "MANDATORY: Use instead of rm -rf or rm -r to prevent shell session breakage"
model: haiku
effort: low
user-invocable: false
argument-hint: "<cat_agent_id>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" safe-rm-agent "$0"`
