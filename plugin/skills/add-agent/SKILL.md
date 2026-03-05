---
description: >
  Add a new issue to a version OR create a new version (major/minor/patch).
  Use when user says "add an issue", "add a new issue", "create a new issue", "new issue for", or "I need to track".
  IMPORTANT: After invoking this skill, forward the AskUserQuestion tool call verbatim — do not respond
  conversationally.
model: sonnet
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - Skill
argument-hint: "[description]"
user-invocable: false
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" add-agent $ARGUMENTS`
