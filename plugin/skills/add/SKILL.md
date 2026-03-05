---
description: Add a new issue to a version or create a new version (major, minor, or patch).
model: sonnet
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - AskUserQuestion
  - Skill
argument-hint: "[description]"
disable-model-invocation: true
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" add "${CLAUDE_SESSION_ID}" "$ARGUMENTS"`
