---
description: >
  MANDATORY: Use BEFORE showing ANY diff to user - transforms git diff into 4-column table.
  Required for approval gates, code reviews, change summaries.
model: haiku
effort: low
user-invocable: false
argument-hint: "<cat_agent_id> <issue-path>"
---

!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill" get-diff-agent "$0" "$1"`
