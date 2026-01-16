# Plan: retrospective-action-items-A006-A013-A014

## Objective
implement retrospective action items A006, A013, A014

## Details
## Problem Solved
- Subagent prompts missing critical instructions (M076-M092)
- Main agent performing work instead of delegating (M088, M089, M091)
- Parser test documentation gaps (M027, M031, M032)

## Solution Implemented
- A013: Added Mandatory Subagent Prompt Checklist to spawn-subagent/SKILL.md
  - STATE.md requirements with Resolution field (M092)
  - Parser test style guidance (M079)
  - Verification table with mistake references
- A014: Enhanced main_agent_boundaries in execute-task.md
  - Orchestration enforcement table for all phases
  - Correct workflow pattern (explore → plan → implement)
  - Anti-patterns for M088, M089, M091
- A006: Already implemented in testing-claude.md (verified present)

Retrospective: R006

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
