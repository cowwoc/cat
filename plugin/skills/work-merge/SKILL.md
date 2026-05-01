---
description: >
  Internal merge phase (invoked by /cat:work-with-issue) - pre-merge squash/rebase, approval gate,
  then executes merge and cleanup. IMPORTANT: After invoking this skill, forward the AskUserQuestion
  tool call verbatim — do not respond conversationally.
user-invocable: false
argument-hint: "<issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <commits_json_path> <trust> <caution>"
allowed-tools:
  - Read
  - Bash
  - Task
  - Skill
  - AskUserQuestion
effort: medium
---

See `${CLAUDE_PLUGIN_ROOT}/rules/skill-loading.md` and follow it exactly.
