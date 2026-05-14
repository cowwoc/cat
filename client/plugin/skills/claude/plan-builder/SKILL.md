---
description: >
  Build or revise a plan.md for an issue. Centralizes curiosity-based planning depth, approach research,
  execution plan format, and comprehensiveness requirements. Invoked by /cat:work to generate full
  implementation steps before spawning the implementation subagent, and for mid-work revisions when
  requirements change during implementation.
model: sonnet
effort: high
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
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

<!-- cat:include ../../include/plan-builder.md -->
