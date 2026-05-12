---
description: >
  Internal orchestration (invoked by /cat:work) - runs implement, confirm, review, and merge phases.
model: sonnet
effort: high
IMPORTANT: After invoking this skill, forward any structured approval tool call verbatim — do not respond
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
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

See `${CAT_PLUGIN_ROOT}/rules/common/skill-loading.md` and follow it exactly.
