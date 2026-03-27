# Plan: wave-parallel-by-default

## Current State
Wave execution is documented and treated as sequential by default — `plan-builder-agent` and
`parallel-execution.md` describe waves as running in dependency order (Wave 1 completes before
Wave 2 starts). Parallelism is opt-in and requires 2+ waves to activate. The ordering implies
a sequential mental model even when no dependency exists between waves.

## Target State
Jobs are parallel by default. Multiple jobs spawn simultaneously in one API response unless
an explicit dependency between them requires sequential ordering. `plan-builder-agent` guidance
and all documentation reflect this: when writing a plan, assume jobs run in parallel; add
sequential ordering only when a job depends on output or side-effects from a prior job.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** Documentation and guidance change only; actual spawn behavior in
  `work-implement-agent` already spawns all waves simultaneously — this aligns docs and guidance
  to match the implementation
- **Mitigation:** No behavior change in execution; only planning guidance and documentation updates

## Files to Modify
- `plugin/concepts/execution-model.md` — document parallel-by-default as the authoritative rule;
  define when sequential ordering is required (explicit dependency: Agent N consumes output or
  side-effects of Agent N-1); rename section headers from `## Execution Waves` / `### Wave N` to
  `## Jobs` / `### Job N` throughout
- `plugin/concepts/parallel-execution.md` — update any remaining sequential-default language to
  parallel-by-default
- `plugin/skills/plan-builder-agent/first-use.md` — update job planning guidance: default to
  parallel jobs; add sequential ordering only when dependency exists; provide examples of
  dependency indicators (shared files, output consumed by next job, ordering constraint); add
  job sizing guidance (how to size job batches)
- `plugin/skills/decompose-issue-agent/first-use.md` — update job planning guidance to match
  parallel-by-default
- `plugin/skills/work-implement-agent/first-use.md` — update grep patterns that detect section
  headers to match `## Jobs` / `### Job N` (not `## Execution Waves` / `### Wave N`)

## Pre-conditions
- [ ] `2.1-canonicalize-wave-hierarchy-doc` is closed (so `plugin/concepts/execution-model.md` exists)

## Jobs

### Job 1
- Update `plugin/concepts/execution-model.md`: add parallel-by-default rule; define sequential
  ordering criteria (explicit dependency between waves: shared output, side-effects, or ordering
  constraint); add examples of each
  - Files: `plugin/concepts/execution-model.md`
- Update `plugin/concepts/parallel-execution.md`: replace any sequential-default framing with
  parallel-by-default; ensure the "When to Use Execution Waves" section reflects that multiple
  waves are the default structure and sequential ordering is the exception
  - Files: `plugin/concepts/parallel-execution.md`
- Update `plugin/skills/plan-builder-agent/first-use.md`: revise wave planning guidance to
  assume parallel execution; instruct agent to add sequential dependency markers only when a wave
  genuinely depends on prior wave output; add concrete examples of dependency indicators
  - Files: `plugin/skills/plan-builder-agent/first-use.md`
- Update `plugin/skills/decompose-issue-agent/first-use.md`: align job planning guidance with
  parallel-by-default
  - Files: `plugin/skills/decompose-issue-agent/first-use.md`

## Post-conditions
- [x] `plugin/concepts/execution-model.md` states parallel-by-default as the authoritative rule
  with clear criteria for when sequential ordering applies
- [x] `plugin/concepts/parallel-execution.md` contains no sequential-default framing
- [x] `plugin/skills/plan-builder-agent/first-use.md` instructs parallel-by-default when
  writing jobs; sequential only when dependency exists
- [x] `plugin/skills/decompose-issue-agent/first-use.md` reflects parallel-by-default
- [x] E2E: Given an issue with two independent work items, confirm plan-builder-agent places them
  in separate parallel jobs (not sequential); given items where Job 2 consumes Job 1 output,
  confirm sequential ordering is applied
- [x] `plugin/concepts/execution-model.md` uses `## Jobs` / `### Job N` section headers
  throughout (not `## Execution Waves` / `### Wave N`)
- [x] `plugin/skills/work-implement-agent/first-use.md` detects `## Jobs` / `### Job N`
  sections (not `## Execution Waves` / `### Wave N`)
- [x] `plugin/skills/plan-builder-agent/first-use.md` includes job sizing guidance
- [x] `plugin/rules/rename-convention.md` has been added
