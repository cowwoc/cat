---
description: Internal - renders a stakeholder selection box during review
model: haiku
effort: low
user-invocable: false
argument-hint: "<selected_count> <total_count> <running> <skipped>"
---
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-stakeholder-selection-box" "$1" "$2" "$3" "$4"`
