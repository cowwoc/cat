---
description: >
  Build or revise a PLAN.md for an issue. Centralizes effort-based planning depth, approach research,
  execution plan format, and comprehensiveness requirements. Invoked by /cat:add for initial plans
  and by /cat:work for mid-work revisions.
model: sonnet
allowed-tools:
  - Read
  - Write
  - Edit
  - Bash
  - Glob
  - Grep
  - Agent
  - WebSearch
  - WebFetch
argument-hint: "<catAgentId> <issue_path> <effort> [revision-context]"
user-invocable: false
---

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" plan-builder-agent "$ARGUMENTS"`
