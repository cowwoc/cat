---
description: Internal implement phase (invoked by /cat:work-with-issue) - displays banners, verifies lock, spawns implementation subagent
model: sonnet
effort: medium
user-invocable: false
argument-hint: "<issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <estimated_tokens> <trust> <caution>"
allowed-tools:
  - Read
  - Bash
  - Task
  - Skill
---

See `${CLAUDE_PLUGIN_ROOT}/rules/skill-loading.md` and follow it exactly.
