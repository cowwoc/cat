---
description: >
  MANDATORY: Use BEFORE showing ANY diff to user - transforms git diff into 4-column table.
  Required for approval gates, code reviews, change summaries.
model: haiku
user-invocable: false
argument-hint: "<catAgentId> <issue-path>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" get-diff-agent "$0" "$1"`
