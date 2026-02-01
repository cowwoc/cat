# Plan: compress-concepts-batch-3

## Goal
Compress git-related concept files using /cat:shrink-doc skill.

## Parent Task
compress-concepts-md (decomposed)

## Sequence
3 of 5

## Files
- plugin/concepts/commit-types.md
- plugin/concepts/git-operations.md
- plugin/concepts/merge-and-cleanup.md

## Acceptance Criteria
- [ ] All 3 files compressed
- [ ] All files score 1.0 on /compare-docs validation
- [ ] Tests pass

## Execution Steps
1. For each file: Invoke /cat:shrink-doc
2. Verify score = 1.0 from validation
3. Commit with message: "config: compress git-related concepts"
