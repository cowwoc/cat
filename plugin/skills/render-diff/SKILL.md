---
description: >
  MANDATORY: Use BEFORE showing ANY diff to user - transforms git diff into 4-column table.
  Required for approval gates, code reviews, change summaries.
model: haiku
allowed-tools:
  - Skill
user-invocable: false
---

Without any preamble, invoke the echo skill with this exact content:

!`${CLAUDE_PLUGIN_ROOT}/scripts/get-render-diff.sh --project-dir "${CLAUDE_PROJECT_DIR}"`

Then after the echo completes, output:

**FAIL-FAST:** If you do NOT see a diff above, preprocessing FAILED. STOP.
