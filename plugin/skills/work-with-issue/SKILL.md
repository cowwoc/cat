---
description: Internal orchestration (invoked by /cat:work) - runs implement, confirm, review, and merge phases
model: sonnet
user-invocable: false
argument-hint: "<issue_id> <issue_path> <worktree_path> <branch> <base_branch> <estimated_tokens> <trust> <verify>"
allowed-tools:
  - Read
  - Bash
  - Task
  - Skill
  - AskUserQuestion
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" work-with-issue "${CLAUDE_SESSION_ID}" "${CLAUDE_PROJECT_DIR}"`
