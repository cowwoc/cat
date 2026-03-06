---
description: >
  Shrink, compress, or reduce document file size while maintaining meaning.
  Trigger words: "shrink", "compress", "reduce token usage", "make smaller", "reduce size".
  Applies to PLAN.md, STATE.md, or other documentation files.
  Validates with /compare-docs to ensure no semantic loss.
model: sonnet
user-invocable: false
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" optimize-doc-agent "$0"`
