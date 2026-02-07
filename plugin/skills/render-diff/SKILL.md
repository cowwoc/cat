---
description: >
  MANDATORY: Use BEFORE showing ANY diff to user - transforms git diff into 4-column table.
  Required for approval gates, code reviews, change summaries.
model: haiku
allowed-tools:
  - Skill
user-invocable: false
---

!`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" render-diff`
