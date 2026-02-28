---
description: Use when code changes need quality review - multi-perspective review from architecture, security, design, testing, performance stakeholders
model: sonnet
user-invocable: false
argument-hint: "<issue_id> <worktree_path> <verify_level> <commits_compact>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" stakeholder-review "${CLAUDE_PROJECT_DIR}" "$0"`
