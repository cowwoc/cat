---
description: Use when code changes need quality review - multi-perspective review from architecture, security, design, testing, performance stakeholders
user-invocable: false
argument-hint: "<cat_agent_id> <issue_id> <worktree_path> <caution_level> <commits_compact>"
effort: high
---

!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill" stakeholder-review-agent "$ARGUMENTS"`
