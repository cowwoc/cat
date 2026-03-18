---
description: >
  Internal merge phase (invoked by /cat:work-with-issue) - pre-merge squash/rebase, approval gate,
  then executes merge and cleanup. IMPORTANT: After invoking this skill, forward the AskUserQuestion
  tool call verbatim — do not respond conversationally.
model: sonnet
user-invocable: false
argument-hint: "<catAgentId> <issueId> <issuePath> <worktreePath> <issueBranch> <targetBranch> <commitsJsonPath> <trust> <verify>"
allowed-tools:
  - Read
  - Bash
  - Task
  - Skill
  - AskUserQuestion
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" work-merge-agent "$ARGUMENTS"`
