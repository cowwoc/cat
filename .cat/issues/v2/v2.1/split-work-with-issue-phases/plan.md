# Plan: split-work-with-issue-phases

## Current State

`plugin/skills/work-with-issue-agent/first-use.md` is 1,710 lines (67KB) and is loaded entirely into
the main agent's context on every `/cat:work` invocation via `skill-loader`. The file contains
instructions for all phases: implement (Steps 1-3), confirm (Step 4), review (Steps 5-6), and
merge (Steps 7-11). At the time of invocation, the main agent context is already 40-80K tokens;
loading the full 17K-token file for all phases — including phases not yet executing — compounds
context bloat and triggers compaction events.

## Target State

`first-use.md` is split into per-phase files. The main agent loads only the current phase's file at
the start of each phase transition. The full 67KB file is never loaded into a single context; instead
each phase loads its 300-500 line slice. Estimated savings: ~600K cost-weighted tokens per session,
2 fewer compaction events.

## Parent Requirements

None — infrastructure optimization.

## Approaches

### A: Separate Phase Skills (Recommended)
- **Risk:** MEDIUM
- **Scope:** 6 files (moderate)
- **Description:** Extract each phase into its own skill file (`work-implement-agent/first-use.md`,
  `work-confirm-agent/first-use.md`, `work-review-agent/first-use.md`). The existing
  `work-with-issue-agent/first-use.md` becomes a thin orchestrator (~100 lines) that invokes each
  phase skill in sequence via Skill tool. Each phase skill is loaded on demand with only its
  phase-specific content. `work-merge-agent` already follows this pattern.

### B: Conditional Loading in Orchestrator
- **Risk:** HIGH
- **Scope:** 5 files (moderate)
- **Description:** Keep `work-with-issue-agent` as the sole skill but make `first-use.md` a small
  dispatcher that explicitly `Read`s the phase file at each phase transition. Agents can read files
  during execution, so each phase's content is loaded lazily. Risk: Read tool loads content into
  the same context, so savings only apply if phases don't all execute in the same agent turn.

> Approach A is preferred. It mirrors the existing `work-merge-agent` pattern and achieves genuine
> context isolation because each phase skill runs in a separate Skill invocation context.

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** work-with-issue-agent orchestration logic changes; phase skill invocation
  signatures must exactly match what work-with-issue-agent currently does internally
- **Mitigation:** Keep existing first-use.md intact until all phase skills are verified; run full
  /cat:work end-to-end test before removing original file

## Files to Modify
- `plugin/skills/work-with-issue-agent/first-use.md` — replace with thin orchestrator (~100 lines)
  that invokes phase skills in sequence via Skill tool; preserve MANDATORY STEPS preamble and
  argument parsing
- `plugin/skills/work-with-issue-agent/SKILL.md` — no change required (continues loading first-use.md)
- `plugin/skills/work-implement-agent/first-use.md` — new file: Steps 1-3 (lines 1-530 of current
  first-use.md): banner display, lock verification, implement phase
- `plugin/skills/work-implement-agent/SKILL.md` — new file: skill definition for implement phase
- `plugin/skills/work-confirm-agent/first-use.md` — new file: Step 4 (lines 531-748): verify-implementation
- `plugin/skills/work-confirm-agent/SKILL.md` — new file: skill definition for confirm phase
- `plugin/skills/work-review-agent/first-use.md` — new file: Steps 5-6 (lines 749-1191): stakeholder
  review + deferred concern review
- `plugin/skills/work-review-agent/SKILL.md` — new file: skill definition for review phase
- `plugin/skills/work-merge-agent/first-use.md` — new file: Steps 7-11 (lines 1192-1710): squash,
  rebase, approval gate, merge

  NOTE: `work-merge-agent` already exists as a skill but its first-use.md may need updating to include
  Steps 7-11 from work-with-issue-agent.

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Read `plugin/skills/work-with-issue-agent/first-use.md` and identify exact line boundaries for each
  phase: implement (lines 1-530), confirm (lines 531-748), review (lines 749-1191), merge (lines
  1192-1710). Confirm boundaries by checking step header lines (## Step N).
  - Files: `plugin/skills/work-with-issue-agent/first-use.md`

### Wave 2
- Create `plugin/skills/work-implement-agent/` directory with `SKILL.md` and `first-use.md` containing
  the implement phase content (Steps 1-3). SKILL.md model: sonnet, user-invocable: false, argument-hint
  matching what work-with-issue-agent currently passes for this phase.
  - Files: `plugin/skills/work-implement-agent/SKILL.md`,
    `plugin/skills/work-implement-agent/first-use.md`
- Create `plugin/skills/work-confirm-agent/` with `SKILL.md` and `first-use.md` containing Step 4
  (confirm/verify-implementation phase).
  - Files: `plugin/skills/work-confirm-agent/SKILL.md`,
    `plugin/skills/work-confirm-agent/first-use.md`
- Create `plugin/skills/work-review-agent/` with `SKILL.md` and `first-use.md` containing Steps 5-6
  (stakeholder review + deferred concern review).
  - Files: `plugin/skills/work-review-agent/SKILL.md`,
    `plugin/skills/work-review-agent/first-use.md`
- Update `plugin/skills/work-merge-agent/first-use.md` if needed to include Steps 7-11 content (squash,
  rebase, approval gate, merge, return success). Verify it already has or needs this content.
  - Files: `plugin/skills/work-merge-agent/first-use.md`

### Wave 3
- Replace `plugin/skills/work-with-issue-agent/first-use.md` with a thin orchestrator that:
  1. Preserves the MANDATORY STEPS preamble and arguments section (lines 1-80)
  2. Replaces Steps 1-11 body with Skill tool invocations for each phase skill:
     - `cat:work-implement-agent` with the implement phase arguments
     - `cat:work-confirm-agent` with the confirm phase arguments
     - `cat:work-review-agent` with the review phase arguments
     - `cat:work-merge-agent` with the merge phase arguments
  3. Passes all required arguments to each phase skill (issue_id, issue_path, worktree_path, etc.)
  - Files: `plugin/skills/work-with-issue-agent/first-use.md`

## Post-conditions
- [ ] `plugin/skills/work-with-issue-agent/first-use.md` is ≤150 lines (thin orchestrator only)
- [ ] 4 per-phase skill files exist and each is ≤500 lines
- [ ] No content duplication between phase files
- [ ] All MANDATORY STEPS references are preserved in the orchestrator
- [ ] E2E: Run `/cat:work` on an issue and verify all 4 phases complete correctly with the split files
