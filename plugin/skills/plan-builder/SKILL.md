---
description: >
  Build or revise a plan.md for an issue. Centralizes curiosity-based planning depth, approach research,
  execution plan format, and comprehensiveness requirements. Invoked by /cat:work to generate full
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
argument-hint: "<curiosity> <mode> <contextPath> [revision-context]"
user-invocable: false
effort: high
---

See `${CLAUDE_PLUGIN_ROOT}/rules/skill-loading.md` and follow it exactly.
