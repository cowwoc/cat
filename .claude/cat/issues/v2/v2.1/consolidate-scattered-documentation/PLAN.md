# Plan: consolidate-scattered-documentation

## Goal
Validate and implement an approach for consolidating scattered documentation: extract all execution-relevant
information across a document (or multiple files), apply backward chaining to reconstruct cohesive execution steps,
then verify no information was lost using compare-docs' binary execution-equivalence verdict.

This is an **experimental/validation issue** — the primary deliverable is evidence of whether the approach works,
not a production-ready tool.

## Satisfies
None

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Backward chaining may not naturally handle non-procedural content (reference tables, configuration
  lists, glossary entries); extraction across multiple files may lose cross-file context; consolidation may
  inadvertently merge distinct concepts that happen to share terminology
- **Mitigation:** Validate on 2-3 real documents of varying complexity before committing to the approach; use
  compare-docs binary verdict as hard gate; document failure modes explicitly

## Dependencies
- `2.1-enhance-compare-docs-grading` — needs severity classification and binary verdict to validate consolidation
  output

## Approach to Validate

### Three-Phase Consolidation Pipeline

**Phase 1: Extraction**
- Use compare-docs' extraction algorithm to pull all execution-relevant semantic units from the source document(s)
- Each unit tagged with: category, location (file + section), quote, and cross-references
- For multi-file consolidation: extract independently per file, then merge extraction results
- Key question to validate: Does cross-file extraction preserve inter-document dependencies?

**Phase 2: Backward Chaining Reconstruction**
- Take the extracted semantic units as input
- Apply skill-builder's backward chaining methodology:
  1. Identify the document's goal (what should the reader be able to do after reading?)
  2. Work backward: what must the reader know to achieve the goal?
  3. For each prerequisite, what must they know to understand that?
  4. Continue until reaching atomic, self-evident concepts
  5. Reverse into forward execution order
- Reconstruct the document following this forward chain, placing each semantic unit at its natural point in the flow
- Key question to validate: Does backward chaining produce a coherent ordering for non-procedural content
  (reference material, configuration tables)?

**Phase 3: Verification**
- Run compare-docs on original vs. consolidated document
- Binary verdict (`execution_equivalent`) must be `true`
- If `false`, examine HIGH/MEDIUM findings to identify what the consolidation process lost
- Key question to validate: Is compare-docs sensitive enough to detect reordering-induced semantic loss
  (e.g., a conditional that now appears before the concept it references)?

## Files to Modify
- `plugin/skills/consolidate-doc-agent/SKILL.md` — new skill: consolidate scattered documentation using
  extract → backward-chain → verify pipeline
- `plugin/skills/consolidate-doc-agent/first-use.md` — methodology, examples, failure modes, and limitations
  discovered during validation
- `plugin/concepts/doc-consolidation.md` — concept doc: when to consolidate vs. shrink, the three-phase pipeline,
  known failure modes

## Pre-conditions
- [ ] `2.1-enhance-compare-docs-grading` is closed (binary verdict available for verification)

## Execution Waves

### Wave 1: Validate on Simple Document
- Select a single real document with scattered/duplicated information (candidate: a skill first-use.md that has
  related instructions in multiple sections)
- Manually execute the three-phase pipeline:
  1. Extract semantic units using compare-docs extraction methodology
  2. Apply backward chaining to reconstruct execution order
  3. Write consolidated version
  4. Run compare-docs to verify execution equivalence
- Document results: what worked, what failed, what was surprising
- Record the binary verdict and any HIGH/MEDIUM findings
- Files: validation notes (temporary), `plugin/skills/consolidate-doc-agent/first-use.md` (draft)

### Wave 2: Validate on Multi-File Document
- Select 2-3 related files that share overlapping content (candidate: a concept doc + skill first-use.md that
  partially duplicate each other)
- Execute the three-phase pipeline across files:
  1. Extract from each file independently
  2. Merge extractions, identifying duplicates and cross-references
  3. Apply backward chaining to merged set
  4. Write consolidated version
  5. Run compare-docs against EACH original file to verify no information lost
- Document additional failure modes specific to multi-file consolidation
- Key risks to watch for:
  - Cross-file references that become dangling after consolidation
  - Audience-specific content from different files merged inappropriately
  - Implicit ordering between files lost during extraction
- Files: validation notes (temporary), `plugin/skills/consolidate-doc-agent/first-use.md` (update)

### Wave 3: Validate on Non-Procedural Content
- Select a document that is primarily reference material (tables, configuration options, glossary)
- Execute the three-phase pipeline
- Determine whether backward chaining produces meaningful results for non-procedural content
- If not, document the limitation and identify what content types this approach handles well vs. poorly
- Possible outcome: backward chaining works for procedural/instructional docs but a different reconstruction
  strategy is needed for reference docs (e.g., topical clustering instead of backward chaining)
- Files: validation notes (temporary), `plugin/skills/consolidate-doc-agent/first-use.md` (update)

### Wave 4: Write Skill and Documentation
- Based on validation findings, write the consolidate-doc-agent skill:
  - If approach validated: full methodology with the three-phase pipeline
  - If partially validated: methodology with explicit scope limitations (e.g., "procedural docs only")
  - If invalidated: document why and what alternative approach emerged during validation
- Create concept documentation
- Files: `plugin/skills/consolidate-doc-agent/SKILL.md`,
  `plugin/skills/consolidate-doc-agent/first-use.md` (finalize),
  `plugin/concepts/doc-consolidation.md`
- Run `mvn -f client/pom.xml test` and confirm all tests pass

## Post-conditions
- [ ] Three-phase pipeline (extract → backward-chain → verify) validated on at least 3 document types:
  simple single-file, multi-file, and non-procedural
- [ ] Each validation produced a binary execution-equivalence verdict from compare-docs
- [ ] Failure modes and limitations documented with specific evidence from validation rounds
- [ ] `plugin/skills/consolidate-doc-agent/` exists with SKILL.md and first-use.md
- [ ] `plugin/concepts/doc-consolidation.md` exists documenting when to use consolidation vs. optimization
- [ ] Skill scope explicitly states which document types the approach handles well and which it does not
- [ ] `mvn -f client/pom.xml test` exits 0 with no regressions
