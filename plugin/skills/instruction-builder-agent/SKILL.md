---
description: "INVOKE IMMEDIATELY when asked to design, create, update, or write any skill, command, or test case scenario file. Also invoke IMMEDIATELY when asked to continue the instruction-builder-agent workflow or proceed to Step 6 (SPRT), Step 7 (investigation), or Step 8 (analysis)."
user-invocable: false
argument-hint: "<cat_agent_id>"
effort: high
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" instruction-builder-agent "$0"`
