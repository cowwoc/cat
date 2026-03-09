---
description: >
  Compress or reduce document file size while preserving behavioral semantics.
  Trigger words: "shrink", "compress", "reduce token usage", "make smaller", "reduce size".
  Applies to PLAN.md, STATE.md, or other documentation files.
  Validates with /compare-docs to ensure no semantic loss.
argument-hint: "<catAgentId> <file-path>"
model: sonnet
user-invocable: false
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" optimize-doc-agent "$0" "$1"`
