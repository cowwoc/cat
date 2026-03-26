# Plan: refactor-wave-context-decomposition

## Current State
Context usage is used as a trigger for decomposing issues into sub-issues (`plugin/concepts/token-warning.md`:
when a subagent exceeds 40% context or triggers compaction, the orchestrator splits remaining work into
sub-issues). Wave splitting is not used for context management. `plan-builder-agent` does not size waves
based on estimated context cost, and `work-implement-agent` does not reactively resize waves when a
completed subagent reports high context usage.

## Target State
1. **Wave splitting handles context management:** `plan-builder-agent` proactively sizes waves so each
   subagent stays under 40% context. `work-implement-agent` reactively splits the next wave when a
   completed subagent reports > 40% context usage (by updating plan.md before spawning the next wave).
2. **Sub-issue decomposition uses structural criteria only:** Context usage no longer triggers sub-issue
   decomposition. An issue is decomposed into sub-issues only when: (a) a merge boundary is required
   between phases, (b) components are independently deliverable and reviewable, or (c) work spans
   genuinely disjoint subsystems.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Removes context-based sub-issue decomposition trigger; changes wave planning guidance
- **Mitigation:** Update all affected files atomically; verify plan-builder-agent produces correct wave
  sizing for a representative issue

## Files to Modify
- `plugin/concepts/token-warning.md` — replace "high context → decompose issue into sub-issues" with
  "high context → split remaining waves"
- `plugin/concepts/execution-model.md` — add wave-based context management and structural sub-issue
  decomposition criteria (this file is created by the dependency issue
  `2.1-canonicalize-wave-hierarchy-doc`)
- `plugin/skills/plan-builder-agent/first-use.md` — add wave sizing guidance: estimate scope per wave
  using file count and change complexity; split wave if estimated cost exceeds 40% of subagent context
  budget; aim for waves of roughly equal scope
- `plugin/skills/work-implement-agent/first-use.md` — add reactive wave re-splitting: when a completed
  subagent reports `percent_of_context > 40`, update plan.md to split the next wave in half before
  spawning it
- `plugin/skills/decompose-issue-agent/first-use.md` — replace context-based decomposition rationale
  with structural criteria; remove any guidance that treats token count as a decomposition trigger

## Pre-conditions
- [ ] `2.1-canonicalize-wave-hierarchy-doc` is closed (so `plugin/concepts/execution-model.md` exists)

## Sub-Agent Waves

### Wave 1
- Update `plugin/concepts/token-warning.md`: replace context-based sub-issue decomposition trigger with
  guidance to split remaining waves when context exceeds 40%
  - Files: `plugin/concepts/token-warning.md`
- Update `plugin/concepts/execution-model.md`: add section on wave-based context management (proactive
  sizing and reactive re-splitting) and structural sub-issue decomposition criteria
  - Files: `plugin/concepts/execution-model.md`
- Update `plugin/skills/decompose-issue-agent/first-use.md`: remove context-based decomposition
  rationale; replace with structural criteria (merge boundary, independent deliverables, disjoint
  subsystems)
  - Files: `plugin/skills/decompose-issue-agent/first-use.md`
- Remove residual context-trigger lines from `plugin/skills/decompose-issue-agent/first-use.md`:
  delete line 226 ("If decomposing due to subagent context limits") and the entry at line 519
  ("cat:token-report - Triggers decomposition decisions") so that no text implies context can
  trigger decomposition
  - Files: `plugin/skills/decompose-issue-agent/first-use.md`
- Update `plugin/skills/plan-builder-agent/first-use.md`: add wave sizing guidance — when planning
  waves, estimate scope (files to modify × change complexity); if a wave would exceed 40% context
  budget, split it into two waves of equal scope; document the heuristic
  - Files: `plugin/skills/plan-builder-agent/first-use.md`
- Update `plugin/skills/work-implement-agent/first-use.md`: after collecting results from a completed
  wave, check `percent_of_context`; if > 40, split the next wave in plan.md by halving its items before
  spawning
  - Files: `plugin/skills/work-implement-agent/first-use.md`

## Post-conditions
- [ ] `plugin/concepts/token-warning.md` no longer instructs decomposing issues based on context usage
- [ ] `plugin/skills/plan-builder-agent/first-use.md` contains wave sizing guidance with a concrete
  heuristic for staying under 40% context per subagent
- [ ] `plugin/skills/work-implement-agent/first-use.md` contains reactive wave re-splitting logic when
  a subagent reports > 40% context usage
- [ ] `plugin/skills/decompose-issue-agent/first-use.md` lists only structural criteria for
  decomposition (no mention of context or token counts as triggers)
- [ ] `plugin/concepts/execution-model.md` documents both proactive wave sizing and reactive re-splitting
- [ ] E2E: Given a plan.md with one oversized wave (10+ files), confirm plan-builder-agent splits it
  into two waves; given a subagent result with percent_of_context=45, confirm work-implement-agent
  splits the next wave before spawning
