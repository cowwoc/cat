---
description: >
  Build or revise a plan.md for an issue. Centralizes curiosity-based planning depth, approach research,
  execution plan format, and comprehensiveness requirements. Invoked by /cat:work-agent to generate full
  implementation steps before spawning the implementation subagent, and for mid-work revisions when
  requirements change during implementation.
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
argument-hint: "<cat_agent_id> <curiosity> <mode> <contextPath> [revision-context]"
user-invocable: false
effort: high
---

!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-skill" plan-builder-agent "$ARGUMENTS"`
