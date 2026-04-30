---
description: Internal review phase (invoked by /cat:work-with-issue-agent) - runs stakeholder review and deferred concern wizard
user-invocable: false
argument-hint: "<cat_agent_id> <issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <all_commits_compact> <trust> <caution>"
allowed-tools:
  - Read
  - Bash
  - Task
  - Skill
  - AskUserQuestion
effort: medium
---

!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill" work-review-agent "$ARGUMENTS"`
