# Plan: add-pipelining-to-optimize-execution

## Goal
Add pipelining analysis to the optimize-execution skill. Currently the skill identifies batching,
parallelization, and caching opportunities but does not analyze pipelining - where phase N+1 can
start before phase N fully completes when only partial output is needed.

## Problem
The optimize-execution skill (plugin/skills/optimize-execution/content.md) analyzes three optimization
patterns: batch_candidates, parallel_candidates, and cache_candidates. It is missing pipelining
analysis, which is relevant for skill workflows where:
- Review could start on early files while later files are still being implemented
- Stakeholder reviewers could be spawned as soon as the diff is available, before squashing
- Lock acquisition could overlap with PLAN.md reading

Pipelining is distinct from parallelization (running independent tasks simultaneously) - it is about
overlapping dependent phases when partial output suffices for the next phase to begin.

## Satisfies
None - infrastructure improvement

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Pipelining recommendations may not always be actionable given Claude Code sequential model
- **Mitigation:** Include applicability notes about execution model constraints

## Files to Modify
- plugin/skills/optimize-execution/content.md - Add pipelining analysis pattern

## Acceptance Criteria
- [ ] optimize-execution skill includes pipeline_candidates alongside existing batch/parallel/cache
- [ ] Pipelining pattern defined with detection criteria and examples
- [ ] Examples relevant to Claude skill workflows (not generic CS pipelining)
- [ ] Applicability note about Claude Code execution model constraints included
- [ ] Functionality works, tests passing, no regressions

## Execution Steps
1. **Step 1:** Add pipelining pattern to optimize-execution analysis categories
   - File: plugin/skills/optimize-execution/content.md
   - Find the section that defines analysis categories (batch_candidates, parallel_candidates, cache_candidates)
   - Add pipeline_candidates: "Dependent operations where next phase can start with partial output from current phase"
   - Add detection criteria: Look for sequential phases where phase N+1 only needs partial output from phase N
2. **Step 2:** Add pipelining examples relevant to skill workflows
   - File: plugin/skills/optimize-execution/content.md
   - Add examples section for pipelining with skill-relevant scenarios:
     - "Stakeholder review spawn after diff available, before commit squash" (review needs diff, not squash order)
     - "PLAN.md read overlapping with lock acquisition" (independent operations masked as sequential)
     - "Implementation subagent start after first execution step read, before full PLAN.md parse"
   - Include applicability note: "Claude Code tool calls are sequential within a message. Pipelining
     applies when skill steps have false serial dependencies - reordering steps to overlap output
     availability with consumption."
3. **Step 3:** Run existing tests to verify no regressions
   - Run: mvn -f hooks/pom.xml test

## Success Criteria
- [ ] pipeline_candidates pattern added to optimization analysis
- [ ] Skill-relevant examples demonstrate practical pipelining opportunities
- [ ] Applicability constraints clearly documented
- [ ] All existing tests pass with no regressions