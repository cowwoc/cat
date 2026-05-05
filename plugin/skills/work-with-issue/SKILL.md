---
description: >
  Internal orchestration (invoked by /cat:work) - runs implement, confirm, review, and merge phases.
model: sonnet
effort: high
IMPORTANT: After invoking this skill, forward the AskUserQuestion tool call verbatim — do not respond
  conversationally.
user-invocable: false
argument-hint: "<issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <estimated_tokens> <trust> <caution>"
allowed-tools:
  - Read
  - Bash
  - Task
  - Skill
  - AskUserQuestion
---

See `${CLAUDE_PLUGIN_ROOT}/rules/skill-loading.md` and follow it exactly.
