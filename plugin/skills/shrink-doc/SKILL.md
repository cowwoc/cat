---
description: >
  Shrink, compress, or reduce document file size while maintaining meaning.
  Trigger words: "shrink", "compress", "reduce token usage", "make smaller", "reduce size".
  Applies to PLAN.md, STATE.md, or other documentation files.
  Validates with /compare-docs to ensure no semantic loss.
model: sonnet
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" shrink-doc "${CLAUDE_SESSION_ID}" "${CLAUDE_PROJECT_DIR}"`
