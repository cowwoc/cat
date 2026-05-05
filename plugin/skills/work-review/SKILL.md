---
description: Internal review phase (invoked by /cat:work-with-issue) - runs stakeholder review and deferred concern wizard
model: sonnet
effort: medium
user-invocable: false
argument-hint: "<issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <all_commits_compact> <trust> <caution>"
allowed-tools:
  - Read
  - Bash
  - Task
  - Skill
  - AskUserQuestion
---

See `${CLAUDE_PLUGIN_ROOT}/rules/skill-loading.md` and follow it exactly.
