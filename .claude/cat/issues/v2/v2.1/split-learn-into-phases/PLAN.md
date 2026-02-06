# Plan: split-learn-into-phases

## Current State
The learn skill (`plugin/skills/learn/SKILL.md`) is a monolithic 1,454 lines (59KB) that gets loaded entirely into the
main agent's context on every mistake. Most invocations only use a subset of the workflow (e.g., simple 5-whys don't
need the full RCA-AB-TEST, HOOK-WORKAROUNDS, or MULTIPLE-MISTAKES sections).

The skill already has auxiliary files referenced as markdown links (not `@${}` auto-loads):
- `rca-methods.md` (131 lines) - referenced from step 4
- `prevention-hierarchy.md` (64 lines) - referenced from step 6
- `documentation-priming.md` (150 lines) - referenced from step 1b
- `mistake-categories.md` (61 lines) - referenced from step 11
- `RCA-AB-TEST.md` (249 lines) - referenced from step 12
- `ANTI-PATTERNS.md` (118 lines) - referenced from Examples
- `HOOK-WORKAROUNDS.md` (66 lines) - referenced from step 4d
- `RELATED-FILES-CHECK.md` (60 lines) - referenced from step 9c
- `EXAMPLES.md` (60 lines) - referenced from Examples
- `MULTIPLE-MISTAKES.md` (54 lines) - referenced from step 4c
- `PRIMING-VERIFICATION.md` (41 lines) - referenced from step 9b

## Target State
Refactor into a thin orchestrator SKILL.md (~200-250 lines) that:
1. Contains the overall workflow structure and phase routing
2. Delegates detailed phase work to subagents that load phase-specific files
3. Follows the same pattern as `work/SKILL.md` (269 lines) which delegates to `phase-prepare.md`,
   `phase-execute.md`, `phase-review.md`, `phase-merge.md`

Proposed phase decomposition (based on workflow sections):
- **phase-investigate.md**: Steps 1-1b (verify event sequence, analyze documentation path) ~300 lines
- **phase-analyze.md**: Steps 2-4d (document mistake, gather context, perform RCA, depth verification) ~400 lines
- **phase-prevent.md**: Steps 5-9c (context degradation, prevention level, quality, implementation) ~500 lines
- **phase-record.md**: Steps 10-12 (verify, record learning, update counter, commit) ~250 lines

The orchestrator parses each phase's JSON result and routes to the next phase, keeping main agent context at ~15KB.

## Satisfies
None - infrastructure optimization

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** The learn skill's external interface (invocation and behavior) must remain identical
- **Mitigation:** Verify the orchestrator + phase files produce the same outputs as the monolithic version.
  Run existing tests. Test with a real mistake scenario.

## Files to Modify
- `plugin/skills/learn/SKILL.md` - Replace with thin orchestrator (~200-250 lines)
- `plugin/skills/learn/phase-investigate.md` - Create (steps 1-1b)
- `plugin/skills/learn/phase-analyze.md` - Create (steps 2-4d)
- `plugin/skills/learn/phase-prevent.md` - Create (steps 5-9c)
- `plugin/skills/learn/phase-record.md` - Create (steps 10-12)

Existing auxiliary files remain unchanged - they're already separate and referenced by link.

## Execution Steps
1. **Step 1:** Read the full current `plugin/skills/learn/SKILL.md` to understand all sections
2. **Step 2:** Create `plugin/skills/learn/phase-investigate.md` containing steps 1 through 1b (verify event sequence,
   analyze documentation path). Include all bash examples and anti-patterns for those steps. Define JSON output contract
   for investigation results (event_sequence, documents_read, skill_invocations, delegation_prompts).
3. **Step 3:** Create `plugin/skills/learn/phase-analyze.md` containing steps 2 through 4d (document mistake, gather
   context metrics, perform RCA, depth verification, multiple mistakes, architectural RCA, hook workarounds). Include
   references to existing auxiliary files (rca-methods.md, MULTIPLE-MISTAKES.md, HOOK-WORKAROUNDS.md). Define JSON
   output contract for analysis results (mistake_description, context_metrics, root_cause, rca_depth_verified).
4. **Step 4:** Create `plugin/skills/learn/phase-prevent.md` containing steps 5 through 9c (context degradation
   patterns, prevention level, prevention quality, replay verification, check existing prevention, misleading docs,
   implement prevention, priming verification, related files check). Include references to existing auxiliary files
   (prevention-hierarchy.md, PRIMING-VERIFICATION.md, RELATED-FILES-CHECK.md). Define JSON output contract for
   prevention results (prevention_type, files_modified, verification_status).
5. **Step 5:** Create `plugin/skills/learn/phase-record.md` containing steps 10 through 12 (verify prevention works,
   record learning in MEMORY.md, update retrospective counter, commit). Include references to existing auxiliary files
   (mistake-categories.md, RCA-AB-TEST.md). Define JSON output contract for recording results
   (learning_id, memory_updated, counter_updated, committed).
6. **Step 6:** Rewrite `plugin/skills/learn/SKILL.md` as a thin orchestrator that:
   - Keeps the frontmatter, Purpose, and When to Use sections
   - Defines 4 phases with subagent delegation (similar to work/SKILL.md pattern)
   - Each phase spawns a general-purpose subagent with the phase file
   - Orchestrator parses JSON results between phases
   - Includes error handling and phase routing
7. **Step 7:** Run `python3 /workspace/run_tests.py` to verify no regressions

## Success Criteria
- [ ] Learn skill SKILL.md reduced to ~200-250 lines (from 1,454)
- [ ] All 4 phase files created with complete step content
- [ ] No content lost - all steps, anti-patterns, and references preserved across phase files
- [ ] Orchestrator follows same delegation pattern as work/SKILL.md
- [ ] All tests pass
- [ ] Total content across all files roughly equals original (content redistributed, not deleted)
