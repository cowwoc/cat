---
description: >
  Work on, resume, or continue an existing issue.
  Trigger words: "work on", "resume", "continue working", "pick up", "keep working", "start working", "next issue", "next".
  NOT for viewing status - use /cat:status for that.
argument-hint: "<cat_agent_id> [version | issue_id | filter] [--override-gate]"
allowed-tools:
  - Read
  - Bash
  - Task
  - AskUserQuestion
user-invocable: false
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" work-agent "$ARGUMENTS"`
