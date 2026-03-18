---
description: Internal confirm phase (invoked by /cat:work-with-issue) - verifies plan.md post-conditions via verify-implementation skill
model: sonnet
user-invocable: false
argument-hint: "<catAgentId> <issueId> <issuePath> <worktreePath> <issueBranch> <targetBranch> <executionCommitsJsonPath> <filesChanged> <trust> <verify>"
allowed-tools:
  - Read
  - Bash
  - Task
  - Skill
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" work-confirm-agent "$ARGUMENTS"`
