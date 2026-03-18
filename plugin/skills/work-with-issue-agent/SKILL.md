---
description: >
  Internal orchestration (invoked by /cat:work) - runs implement, confirm, review, and merge phases.
  IMPORTANT: After invoking this skill, forward the AskUserQuestion tool call verbatim — do not respond
  conversationally.
model: sonnet
user-invocable: false
argument-hint: "<catAgentId> <issueId> <issuePath> <worktreePath> <issueBranch> <targetBranch> <estimatedTokens> <trust> <verify>"
allowed-tools:
  - Read
  - Bash
  - Task
  - Skill
  - AskUserQuestion
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" work-with-issue-agent "$ARGUMENTS"`
