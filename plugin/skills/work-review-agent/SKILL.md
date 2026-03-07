---
description: Internal review phase (invoked by /cat:work-with-issue) - runs stakeholder review and deferred concern wizard
model: sonnet
user-invocable: false
argument-hint: "<catAgentId> <issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <all_commits_compact> <trust> <verify>"
allowed-tools:
  - Read
  - Bash
  - Task
  - Skill
  - AskUserQuestion
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" work-review-agent $ARGUMENTS`
