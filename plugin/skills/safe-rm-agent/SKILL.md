---
description: "MANDATORY: Use instead of rm -rf or rm -r to prevent shell session breakage"
model: haiku
user-invocable: false
argument-hint: "<catAgentId>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" safe-rm-agent "$0"`
