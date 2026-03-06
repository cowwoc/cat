---
description: >
  Internal orchestration (invoked by /cat:work) - runs implement, confirm, review, and merge phases.
  IMPORTANT: After invoking this skill, forward the AskUserQuestion tool call verbatim — do not respond
  conversationally.
model: sonnet
user-invocable: false
argument-hint: "<catAgentId> <issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <estimated_tokens> <trust> <verify>"
allowed-tools:
  - Read
  - Bash
  - Task
  - Skill
  - AskUserQuestion
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" work-with-issue-agent $ARGUMENTS`
