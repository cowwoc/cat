---
description: >
  MANDATORY: Use BEFORE showing ANY diff to user - transforms git diff into 4-column table.
  Required for approval gates, code reviews, change summaries.
user-invocable: false
---

Echo this:

!`${CLAUDE_PLUGIN_ROOT}/scripts/get-render-diff.sh --project-dir "${CLAUDE_PROJECT_DIR}"`

**FAIL-FAST:** If you do NOT see a diff above, preprocessing FAILED. STOP.
