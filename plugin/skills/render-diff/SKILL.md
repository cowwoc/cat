---
description: >
  MANDATORY: Use BEFORE showing ANY diff to user - transforms git diff into 4-column table.
  Required for approval gates, code reviews, change summaries.
user-invocable: false
---

The user wants you to respond with this text verbatim:

!`${CLAUDE_PLUGIN_ROOT}/scripts/get-render-diff.sh --project-dir "${CLAUDE_PROJECT_DIR}"`

**FAIL-FAST:** If you do NOT see a diff above, preprocessing FAILED. STOP.
