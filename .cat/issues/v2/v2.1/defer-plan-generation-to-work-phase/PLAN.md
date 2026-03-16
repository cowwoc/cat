# Plan

## Goal

Update `/cat:add-agent` to create a lightweight PLAN.md at issue creation time (goal + pre/post-conditions only, no
implementation steps). Move full plan generation (implementation steps) to `cat:work-implement-agent`, where
`cat:plan-builder-agent` is invoked immediately before spawning the implementation subagent.

**Reason:** Implementation details may change between the time an issue is created and when it is actually worked on.
Deferring full plan generation ensures the implementation plan reflects the current codebase state when work begins.

## Pre-conditions

- `/cat:add-agent` currently invokes `cat:plan-builder-agent` in the `issue_create` step to generate a full PLAN.md
  (including implementation steps) at issue creation time
- `cat:work-implement-agent` currently assumes a complete PLAN.md exists when it runs

## Post-conditions

1. `/cat:add-agent` creates a lightweight PLAN.md containing only: goal description, pre-conditions, and
   post-conditions — no implementation steps or approach sections
2. `cat:work-implement-agent` invokes `cat:plan-builder-agent` at the start, before spawning the implementation
   subagent, to generate the full implementation plan
3. All tests pass (no regressions)
4. E2E verification: create a new issue via `/cat:add` and confirm its PLAN.md has no implementation steps; then
   start `/cat:work` on that issue and confirm `cat:plan-builder-agent` runs and populates implementation steps
   before the implementation subagent spawns
5. Existing open issues with full PLAN.md files (containing implementation steps) are unmodified by this change
6. `cat:plan-builder-agent` description updated to reflect it is now invoked by `cat:work-implement-agent` before
   spawning the implementation subagent (not by `/cat:add` for initial plans)
