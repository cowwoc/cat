---
description: Use when code changes need quality review - multi-perspective review from architecture, security, design, testing, performance stakeholders
model: sonnet
user-invocable: false
argument-hint: "<catAgentId> <issue_id> <worktree_path> <verify_level> <commits_compact>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" stakeholder-review-agent $ARGUMENTS`
