---
description: Use when renaming, removing, or moving a symbol across multiple files — scan all occurrences
  first, build a complete file-to-changes plan, apply all edits without intermediate compilation, then verify
  the build once. Use this for coordinated symbol changes; batch-write behavior is governed by .claude/rules/batch-write.md.
model: haiku
effort: medium
user-invocable: false
allowed-tools: Bash, Grep, Read, Edit
argument-hint: "<symbol> [symbol] ..."
---

See `${CLAUDE_PLUGIN_ROOT}/rules/skill-loading.md` and follow it exactly.
