---
description: Internal confirm phase (invoked by /cat:work-with-issue) - verifies PLAN.md post-conditions via verify-implementation skill
model: sonnet
user-invocable: false
argument-hint: "<catAgentId> <issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <execution_commits_json_path> <files_changed> <trust> <verify>"
allowed-tools:
  - Read
  - Bash
  - Task
  - Skill
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" work-confirm-agent "$ARGUMENTS"`
