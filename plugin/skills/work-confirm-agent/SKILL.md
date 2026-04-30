---
description: Internal confirm phase (invoked by /cat:work-with-issue-agent) - verifies plan.md post-conditions via verify-implementation skill
user-invocable: false
argument-hint: "<cat_agent_id> <issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <execution_commits_json_path> <files_changed> <trust> <caution>"
allowed-tools:
  - Read
  - Bash
  - Task
  - Skill
effort: medium
---

!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill" work-confirm-agent "$ARGUMENTS"`
