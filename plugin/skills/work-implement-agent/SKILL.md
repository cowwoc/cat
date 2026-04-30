---
description: Internal implement phase (invoked by /cat:work-with-issue-agent) - displays banners, verifies lock, spawns implementation subagent
user-invocable: false
argument-hint: "<cat_agent_id> <issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <estimated_tokens> <trust> <caution>"
allowed-tools:
  - Read
  - Bash
  - Task
  - Skill
effort: medium
---

!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill" work-implement-agent "$ARGUMENTS"`
