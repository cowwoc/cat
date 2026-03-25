## Type

refactor

## Goal

Clarify the `revise` mode documentation in `plugin/skills/plan-builder-agent/first-use.md` to eliminate ambiguity about whether `revise` mode is appropriate for first-time plan generation (generating implementation steps from a lightweight plan.md) vs. mid-work revision (updating an existing full plan.md). The current "When to Use" section describes both scenarios under `revise` mode but the mode name implies only revision of existing content.

## Pre-conditions

- `plugin/skills/plan-builder-agent/first-use.md` describes `revise` mode for both initial step generation and mid-work revision
- `plugin/skills/work-implement-agent/first-use.md` invokes plan-builder-agent with `revise` mode

## Research Findings

### Current State Analysis

The `plugin/skills/plan-builder-agent/first-use.md` file already has partial clarification from a prior commit (`47b097c39`):

1. **Mode: `revise` section (line 31-33):** States "Used by `/cat:work` in two contexts: (1) generating execution steps for a lightweight plan... and (2) revising an existing plan when requirements change during implementation."
2. **When to Use section (lines 45-47):** Lists "Adding execution steps" and "Mid-work revision" as two bullet points.

### Remaining Gap

The mode name `revise` itself creates cognitive dissonance — a reader encountering `mode: revise` in a skill invocation naturally expects it to mean "revise existing content," not "generate new content from scratch." While the documentation now lists both use cases, it lacks:

- A **clarifying note** explaining why `revise` mode handles both scenarios (design rationale)
- A clear statement that `revise` is the only mode available and covers the full lifecycle of plan.md content generation

### Approach: Add Clarifying Note

Add a brief note to the `Mode: revise` section explaining the design rationale: `revise` mode treats adding execution steps to a lightweight plan as a form of revision (the plan already exists with goal and post-conditions; execution steps are added to it, not created from nothing). This addresses the name ambiguity without renaming the mode or adding a new mode.

### Files to Modify

- `plugin/skills/plan-builder-agent/first-use.md` — the only file requiring changes

## Sub-Agent Waves

### Wave 1

- In `plugin/skills/plan-builder-agent/first-use.md`, add a clarifying note after the `Mode: revise` description (after line 41) explaining the design rationale for why `revise` mode handles initial step generation. The note should state: adding execution steps to a lightweight plan.md (which already contains goal and post-conditions) is a revision of that existing document, not creation from scratch. The `revise` name accurately reflects this — the plan already exists, and the skill revises it to include implementation details.
- Verify the "When to Use" section (lines 45-47) clearly distinguishes the two invocation contexts. No changes needed if it already names both contexts explicitly with their trigger conditions.
- Run `mvn -f client/pom.xml test` to verify no tests break (documentation-only change, but tests must pass per project conventions).
- Update `.cat/issues/v2/v2.1/clarify-plan-builder-revise-mode-docs/index.json` to set status to `closed` and progress to `100%`.

## Post-conditions

- The `revise` mode documentation clearly states it handles both initial step generation and mid-work revision
- "When to Use" section explicitly names the two invocation contexts and when each applies
- The mode name ambiguity is addressed with a clarifying note explaining the design rationale
- No functional changes to behavior — documentation only
- All tests pass (`mvn -f client/pom.xml test` exits 0)
