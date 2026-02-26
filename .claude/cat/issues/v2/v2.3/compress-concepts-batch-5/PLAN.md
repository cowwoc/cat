# Plan: compress-concepts-batch-5

## Goal
Compress process-related concept files using /cat:shrink-doc skill.

## Parent Task
compress-concepts-md (decomposed)

## Sequence
5 of 5

## Files
- plugin/concepts/issue-resolution.md
- plugin/concepts/questioning.md
- plugin/concepts/research-pitfalls.md
- plugin/concepts/duplicate-issue.md

## Acceptance Criteria
- [ ] All 4 files compressed
- [ ] All files score 1.0 on /compare-docs validation
- [ ] Tests pass

## Execution Steps
1. For each file: Invoke /cat:shrink-doc
2. Commit with message: "config: compress process-related concepts"
