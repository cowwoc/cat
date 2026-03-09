---
description: Internal implement phase (invoked by /cat:work-with-issue) - displays banners, verifies lock, spawns implementation subagent
model: sonnet
user-invocable: false
argument-hint: "<catAgentId> <issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <estimated_tokens> <trust> <verify>"
allowed-tools:
  - Read
  - Bash
  - Task
  - Skill
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" work-implement-agent "$ARGUMENTS"`
