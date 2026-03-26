# Plan: canonicalize-wave-hierarchy-doc

## Current State
Documentation about the execution model (version → issue → sub-issue → wave → subagent hierarchy,
wave definition, wave↔subagent relationship, parallelism, and ordering) is scattered across 9+
files with no single authoritative source. `plugin/concepts/parallel-execution.md` incorrectly
states that waves run sequentially, contradicting the actual implementation in
`plugin/skills/work-implement-agent/first-use.md` (which spawns all waves simultaneously).

## Target State
A single canonical concept doc (`plugin/concepts/execution-model.md`) covers the full execution
model. All other files remove duplicated content and reference the canonical doc. The sequential
wave description in `plugin/concepts/parallel-execution.md` is corrected to match the
implementation.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — documentation only
- **Mitigation:** Verify all content from source files is preserved in canonical doc before removing it

## Files to Modify
- `plugin/concepts/execution-model.md` — create: canonical execution model doc (new file)
- `plugin/concepts/parallel-execution.md` — remove duplicated hierarchy/wave content, fix sequential
  wave description, add reference to canonical doc
- `plugin/concepts/hierarchy.md` — remove any wave-level content, add reference to canonical doc
- `plugin/concepts/token-warning.md` — keep decomposition trigger logic, add reference to canonical
  doc for hierarchy context
- `plugin/skills/decompose-issue-agent/first-use.md` — add reference to canonical doc for hierarchy
  and wave definitions
- `plugin/skills/plan-builder-agent/first-use.md` — add reference to canonical doc for wave
  definitions
- `plugin/skills/work-implement-agent/first-use.md` — add reference to canonical doc for execution
  model

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Read all source files listed in Files to Modify to extract all content about the execution model
  (hierarchy, wave definition, wave↔subagent relationship, parallelism, ordering, decomposition)
  - Files: `plugin/concepts/parallel-execution.md`, `plugin/concepts/hierarchy.md`,
    `plugin/concepts/token-warning.md`, `plugin/skills/decompose-issue-agent/first-use.md`,
    `plugin/skills/plan-builder-agent/first-use.md`, `plugin/skills/work-implement-agent/first-use.md`
- Create `plugin/concepts/execution-model.md` as the canonical source. Must cover: full hierarchy
  (version → issue → sub-issue → wave → subagent), wave definition (a batch of work items executed
  by one subagent in an isolated worktree), wave parallelism (all waves spawn simultaneously in one
  API response), ordering (sequential only when dependency exists), sub-issue decomposition trigger,
  context-size-based decomposition
  - Files: `plugin/concepts/execution-model.md`
- Update `plugin/concepts/parallel-execution.md`: remove content now in canonical doc, fix the
  incorrect claim that waves run sequentially (they run in parallel), add
  `See plugin/concepts/execution-model.md` reference
  - Files: `plugin/concepts/parallel-execution.md`
- Update `plugin/concepts/hierarchy.md`: remove wave-level content now in canonical doc, add
  reference to `plugin/concepts/execution-model.md`
  - Files: `plugin/concepts/hierarchy.md`
- Update `plugin/concepts/token-warning.md`: add reference to `plugin/concepts/execution-model.md`
  for hierarchy context
  - Files: `plugin/concepts/token-warning.md`
- Update `plugin/skills/decompose-issue-agent/first-use.md`: add reference to
  `plugin/concepts/execution-model.md` for hierarchy and wave definitions
  - Files: `plugin/skills/decompose-issue-agent/first-use.md`
- Update `plugin/skills/plan-builder-agent/first-use.md`: add reference to
  `plugin/concepts/execution-model.md` for wave definitions
  - Files: `plugin/skills/plan-builder-agent/first-use.md`
- Update `plugin/skills/work-implement-agent/first-use.md`: add reference to
  `plugin/concepts/execution-model.md` for execution model
  - Files: `plugin/skills/work-implement-agent/first-use.md`

## Post-conditions
- [ ] `plugin/concepts/execution-model.md` exists and covers all aspects of the execution model
- [ ] No other file contains a full standalone description of the hierarchy, wave definition,
  wave↔subagent relationship, or wave parallelism — only references to the canonical doc
- [ ] `plugin/concepts/parallel-execution.md` no longer states that waves run sequentially
- [ ] All 6 modified files contain a reference to `plugin/concepts/execution-model.md`
- [ ] All content from source files is preserved in the canonical doc (nothing lost)
- [ ] E2E: Load `plugin/concepts/execution-model.md` and confirm it answers: what is a wave, how
  many subagents does a wave spawn, do waves run in parallel or sequentially, what triggers
  sub-issue decomposition
