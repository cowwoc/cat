## Type

refactor

## Goal

Improve the comment/documentation clarity for revise mode usage in `plugin/skills/plan-builder-agent/first-use.md`. A comment or description in the file is unclear about when revise mode should be used vs. when a different approach is needed, leading to potential misuse.

## Current State

In `plugin/skills/plan-builder-agent/first-use.md`, the revise mode documentation has three inconsistencies:

1. **Line 140 workflow header says "For Mid-Work Revision (mode=revise)"** — This implies revise mode is only for
   mid-work changes, but the "When to Use" section (lines 42-45) says revise mode is also used for initial
   implementation step generation from lightweight plans.
2. **The `### Mode: revise` section (lines 29-39)** says generically "Used by `/cat:work`" without distinguishing
   the two distinct use cases (initial step generation vs. mid-work revision).
3. **The "When to Use" section (lines 42-45)** correctly lists both use cases but uses the term "Initial
   implementation" which could be confused with creating a plan from scratch rather than adding steps to an existing
   lightweight plan.

## Target State

All three sections consistently describe revise mode's dual purpose:
- Adding implementation steps to a lightweight plan (created by `/cat:add`, containing only goal and post-conditions)
- Revising an existing plan when requirements change during implementation

The workflow section header accurately reflects both use cases. The mode description clearly states the two contexts.

## Parent Requirements

None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — documentation/comment only, no functional behavior changes
- **Mitigation:** Visual review of updated text

## Files to Modify
- `plugin/skills/plan-builder-agent/first-use.md` — Clarify three sections describing revise mode usage
- `.cat/issues/v2/v2.1/clarify-plan-builder-revise-comment/index.json` — Set status to `closed` and progress to `100%`

## Pre-conditions

- `plugin/skills/plan-builder-agent/first-use.md` contains the unclear revise mode descriptions

## Sub-Agent Waves

### Wave 1
- Read `plugin/skills/plan-builder-agent/first-use.md`
- Update the `### Mode: revise` section (around line 29) to explicitly describe the two use cases:
  (1) generating implementation steps for a lightweight plan, and (2) revising an existing plan mid-work
- Update the `## When to Use` section (around line 42) to use clearer terminology — replace "Initial implementation"
  with wording that distinguishes "adding execution steps to a lightweight plan" from "creating a plan from scratch"
- Update the workflow section header (line 140) from "For Mid-Work Revision (mode=revise)" to a header that
  accurately covers both use cases (e.g., "For Revise Mode (mode=revise)")
- Ensure all three sections are consistent with each other
- Update `.cat/issues/v2/v2.1/clarify-plan-builder-revise-comment/index.json` to set status to `closed` and progress
  to `100%`
- Commit with message: `refactor: clarify revise mode documentation in plan-builder-agent`

## Post-conditions

- The `### Mode: revise` section clearly describes both use cases (step generation and mid-work revision)
- The `## When to Use` section uses unambiguous terminology distinguishing adding steps from creating plans
- The workflow section header accurately reflects both use cases of revise mode
- No functional behavior changes — only comment/documentation clarity improvements
- E2E verification: review the updated sections and confirm a reader can determine when to use revise mode without ambiguity
