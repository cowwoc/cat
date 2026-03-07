---
description: >
  Analyze whether changes introduced by a rebase impact the current PLAN.md. Invoked by /cat:work
  after Step 8 (rebase onto target branch). Returns compact JSON summary; writes full analysis to file.
model: sonnet
user-invocable: false
argument-hint: "<catAgentId> <issue_path> <worktree_path> <old_fork_point> <new_fork_point>"
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - Grep
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" rebase-impact-agent "$ARGUMENTS"`
