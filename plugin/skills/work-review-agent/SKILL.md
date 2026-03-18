---
description: Internal review phase (invoked by /cat:work-with-issue) - runs stakeholder review and deferred concern wizard
model: sonnet
user-invocable: false
argument-hint: "<catAgentId> <issueId> <issuePath> <worktreePath> <issueBranch> <targetBranch> <allCommitsCompact> <trust> <verify>"
allowed-tools:
  - Read
  - Bash
  - Task
  - Skill
  - AskUserQuestion
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" work-review-agent "$ARGUMENTS"`
