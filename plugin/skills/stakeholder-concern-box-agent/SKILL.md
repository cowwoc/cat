---
description: Internal - renders a stakeholder concern box during review
model: haiku
effort: low
user-invocable: false
argument-hint: "<severity> <stakeholder> <description> <location>"
---
!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-stakeholder-concern-box" "$1" "$2" "$3" "$4"`
