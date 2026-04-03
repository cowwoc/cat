---
description: >
  Add a new issue to a version OR create a new version (major/minor/patch).
  Use when user says "add an issue", "add a new issue", "create a new issue", "new issue for", or "I need to track".
  IMPORTANT: After invoking this skill, forward the AskUserQuestion tool call verbatim — do not respond
  conversationally.
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - AskUserQuestion
  - Skill
argument-hint: "<cat_agent_id> [description]"
user-invocable: false
effort: medium
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" add-agent "$ARGUMENTS"`
