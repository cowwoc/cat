---
description: >
  Add a new issue to a version OR create a new version (major/minor/patch).
  Use when user says "add an issue", "add a new issue", "create a new issue", "new issue for", or "I need to track".
model: sonnet
effort: medium
IMPORTANT: After invoking this skill, forward the AskUserQuestion tool call verbatim — do not respond
  conversationally.
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - AskUserQuestion
  - Skill
argument-hint: "[description]"
---

See `${CLAUDE_PLUGIN_ROOT}/rules/skill-loading.md` and follow it exactly.
