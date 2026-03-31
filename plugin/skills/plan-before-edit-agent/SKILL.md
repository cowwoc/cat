---
description: Use when renaming, removing, or moving a symbol across multiple files — scan all occurrences
  first, build a complete file-to-changes plan, apply all edits without intermediate compilation, then verify
  the build once. Use instead of cat:batch-write-agent when changes are coordinated across a shared symbol.
model: haiku
user-invocable: false
allowed-tools: Bash, Grep, Read, Edit
argument-hint: "<cat_agent_id> <symbol> [symbol] ..."
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" plan-before-edit-agent "$0"`
