# Plan: add-pipelining-to-optimize-execution

## Goal
Add pipelining analysis AND establish script extraction as a principle referenced from skill-builder.
The optimize-execution skill identifies batching, parallelization, and caching opportunities and now includes:
1. Pipelining - where phase N+1 can start before phase N fully completes when only partial output is needed
2. Script extraction - architectural principle added to skill-builder, referenced from optimize-execution for detection

## Problem
The optimize-execution skill (plugin/skills/optimize-execution/content.md) analyzes three optimization
patterns: batch_candidates, parallel_candidates, and cache_candidates. It is missing two additional
patterns:

1. **Pipelining analysis** - relevant for skill workflows where:
   - Review could start on early files while later files are still being implemented
   - Stakeholder reviewers could be spawned as soon as the diff is available, before squashing
   - Lock acquisition could overlap with PLAN.md reading
   - Pipelining is distinct from parallelization (running independent tasks simultaneously) - it is about overlapping dependent phases when partial output suffices for the next phase to begin

2. **Script extraction opportunities** - architectural principle that skills must not contain inline bash for deterministic operations:
   - Principle established in `/cat:skill-builder` as authority on skill structure
   - optimize-execution references skill-builder when detecting inline bash patterns
   - Reduces token consumption (Claude doesn't read/reason about implementation)
   - Deterministic execution (identical every time)
   - Fewer tool call round-trips
   - Fail-fast with structured JSON errors

## Satisfies
None - infrastructure improvement

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Pipelining recommendations may not always be actionable given Claude Code sequential model
- **Mitigation:** Include applicability notes about execution model constraints

## Files to Modify
- plugin/skills/optimize-execution/content.md - Add pipelining analysis pattern, reference skill-builder for script extraction
- plugin/skills/skill-builder/content.md - Add script extraction architectural principle

## Acceptance Criteria
- [ ] optimize-execution skill includes pipeline_candidates alongside existing batch/parallel/cache
- [ ] optimize-execution skill includes script_extraction_candidates as optimization pattern
- [ ] Pipelining pattern defined with detection criteria and examples
- [ ] Script extraction principle added to skill-builder as architectural rule
- [ ] optimize-execution references skill-builder for script extraction guidance (not self-contained)
- [ ] Examples relevant to Claude skill workflows (not generic CS patterns)
- [ ] Applicability notes about Claude Code execution model constraints included
- [ ] Hybrid workflow pattern documented in skill-builder (script + skill markdown)
- [ ] Functionality works, tests passing, no regressions

## Execution Steps
1. **Step 1:** Add pipelining pattern to optimize-execution analysis categories
   - File: plugin/skills/optimize-execution/content.md
   - Find the section that defines analysis categories (batch_candidates, parallel_candidates, cache_candidates)
   - Add pipeline_candidates: "Dependent operations where next phase can start with partial output from current phase"
   - Add detection criteria: Look for sequential phases where phase N+1 only needs partial output from phase N
2. **Step 2:** Add script extraction architectural principle to skill-builder
   - File: plugin/skills/skill-builder/content.md
   - Add new section in "Handling Complex Cases" after fail-fast discussion
   - Define mandatory principle: Skills must not contain inline bash for deterministic operations
   - Document hybrid workflow architecture (skill + script collaboration)
   - Include when to extract vs when NOT to extract
   - Add script conventions and result handling table pattern
   - Include before/after example (git-merge-linear)
3. **Step 3:** Update optimize-execution to reference skill-builder for script extraction
   - File: plugin/skills/optimize-execution/content.md
   - Replace verbose script extraction section with reference to skill-builder
   - Keep detection criteria and impact notes
   - Direct readers to skill-builder for architectural details
4. **Step 4:** Add examples for pipelining pattern
   - File: plugin/skills/optimize-execution/content.md
   - Pipelining examples:
     - "Stakeholder review spawn after diff available, before commit squash" (review needs diff, not squash order)
     - "PLAN.md read overlapping with lock acquisition" (independent operations masked as sequential)
     - "Implementation subagent start after first execution step read, before full PLAN.md parse"
   - Include applicability note: "Claude Code tool calls are sequential within a message. Pipelining
     applies when skill steps have false serial dependencies - reordering steps to overlap output
     availability with consumption."
5. **Step 5:** Update output format examples to include new patterns
   - File: plugin/skills/optimize-execution/content.md
   - Add pipeline_candidates and script_extraction_candidates to JSON structure examples
   - Add example entries in executionPatterns and optimizations sections
6. **Step 6:** Run existing tests to verify no regressions
   - Run: mvn -f hooks/pom.xml test

## Success Criteria
- [ ] pipeline_candidates pattern added to optimization analysis
- [ ] script_extraction_candidates pattern added to optimization analysis
- [ ] Script extraction principle added to skill-builder as architectural rule
- [ ] optimize-execution references skill-builder (not self-contained)
- [ ] Skill-relevant examples demonstrate practical pipelining opportunities
- [ ] Hybrid workflow pattern documented in skill-builder (script + skill markdown collaboration)
- [ ] Anti-patterns documented in skill-builder for script extraction
- [ ] Applicability constraints clearly documented
- [ ] All existing tests pass with no regressions