---
description: Internal confirm phase (invoked by /cat:work-with-issue) - verifies plan.md post-conditions via verify-implementation skill
model: sonnet
effort: medium
user-invocable: false
argument-hint: "<issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <execution_commits_json_path> <files_changed> <trust> <caution>"
allowed-tools:
  - Read
  - Bash
  - Task
  - Skill
---

See `${CLAUDE_PLUGIN_ROOT}/rules/skill-loading.md` and follow it exactly.
