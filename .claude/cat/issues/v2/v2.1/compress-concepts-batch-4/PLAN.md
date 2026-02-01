# Plan: compress-concepts-batch-4

## Goal
Compress versioning-related concept files using /cat:shrink-doc skill.

## Parent Task
compress-concepts-md (decomposed)

## Sequence
4 of 5

## Files
- plugin/concepts/hierarchy.md
- plugin/concepts/version-paths.md
- plugin/concepts/version-scheme.md
- plugin/concepts/version-completion.md

## Acceptance Criteria
- [ ] All 4 files compressed
- [ ] All files score 1.0 on /compare-docs validation
- [ ] Tests pass

## Execution Steps
1. For each file: Invoke /cat:shrink-doc
2. Verify score = 1.0 from validation
3. Commit with message: "config: compress versioning-related concepts"
