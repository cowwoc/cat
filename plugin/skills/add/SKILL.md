---
description: >
  Add a new issue to a version OR create a new version (major/minor/patch).
  Use when user says "add an issue", "add a new issue", "create a new issue", "new issue for", or "I need to track".
model: sonnet
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - AskUserQuestion
  - Skill
argument-hint: "[description]"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" add "${CLAUDE_SESSION_ID}" "${CLAUDE_PROJECT_DIR}"`
