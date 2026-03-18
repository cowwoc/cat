# Plan: context-aware-merge-delegation

## Goal

Ensure work-with-issue proactively delegates the merge phase to a work-merge subagent when main agent
context exceeds 37k tokens, preventing session compaction during merge/cleanup.

## Satisfies

None

## Root Cause

The work-with-issue skill delegates implementation and stakeholder review to subagents, but the merge
phase sometimes runs inline on the main agent. When main context is large (>60k tokens after review),
merge operations (rebase, squash, conflict resolution) can trigger session compaction, losing JSONL
traceability and increasing token costs.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** LOW — work-merge subagent already exists and works
- **Mitigation:** work-merge is already a proven subagent skill

## Files to Modify

- `plugin/skills/work-with-issue/SKILL.md` — add context-size check before merge phase, delegate when
  context > 37k tokens

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Add context-size decision rule to work-with-issue merge step: check main context size and delegate to
  work-merge subagent when threshold exceeded
  - Files: `plugin/skills/work-with-issue/SKILL.md`

## Post-conditions

- [ ] work-with-issue documents context-aware merge delegation threshold
- [ ] Merge phase delegates to work-merge subagent when main context > 37k tokens
