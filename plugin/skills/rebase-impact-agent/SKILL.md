---
description: >
  Analyze whether changes introduced by a rebase impact the current plan.md. Invoked by /cat:work
  after Step 8 (rebase onto target branch). Returns compact JSON summary; writes full analysis to file.
model: sonnet
user-invocable: false
argument-hint: "<catAgentId> <issuePath> <worktreePath> <old_fork_point> <new_fork_point>"
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - Grep
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" rebase-impact-agent "$ARGUMENTS"`
