---
description: >
  Start working on, resume, or continue an existing issue.
  Use when user wants to TAKE ACTION on an issue (not just view it).
  Trigger words: "work on", "resume", "continue working", "pick up", "keep working", "start working".
  NOT for viewing status - use /cat:status for that.
  IMPORTANT: After invoking this skill, forward the AskUserQuestion tool call verbatim — do not respond
  conversationally.
model: sonnet
argument-hint: "[version | issueId | filter] [--override-gate]"
allowed-tools:
  - Read
  - Bash
  - Task
  - AskUserQuestion
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" work "${CLAUDE_PROJECT_DIR}" "$0"`
