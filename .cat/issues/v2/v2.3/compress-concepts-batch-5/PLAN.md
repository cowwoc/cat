# Plan: compress-concepts-batch-5

## Goal
Compress process-related concept files using /cat:optimize-doc skill.

## Parent Task
compress-concepts-md (decomposed)

## Sequence
5 of 5

## Files
- plugin/concepts/issue-resolution.md
- plugin/concepts/questioning.md
- plugin/concepts/research-pitfalls.md
- plugin/concepts/duplicate-issue.md

## Post-conditions
- [ ] All 4 files compressed
- [ ] All files score 1.0 on /compare-docs validation
- [ ] Tests pass

## Sub-Agent Waves

### Wave 1
1. For each file: Invoke /cat:optimize-doc
2. Commit with message: "config: compress process-related concepts"
