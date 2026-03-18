---
description: Use when code changes need quality review - multi-perspective review from architecture, security, design, testing, performance stakeholders
model: sonnet
user-invocable: false
argument-hint: "<catAgentId> <issueId> <worktreePath> <verifyLevel> <commitsCompact>"
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-skill" stakeholder-review-agent "$ARGUMENTS"`
