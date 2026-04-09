---
description: >
  Analyze whether changes introduced by a rebase impact the current plan.md. Invoked by /cat:work-agent
  after Step 8 (rebase onto target branch). Returns compact JSON summary; writes full analysis to file.
user-invocable: false
argument-hint: "<cat_agent_id> <issue_path> <worktree_path> <old_fork_point> <new_fork_point>"
allowed-tools:
  - Read
  - Write
  - Bash
  - Glob
  - Grep
effort: medium
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" rebase-impact-agent "$ARGUMENTS"`
