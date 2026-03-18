# Plan: compress-concepts-batch-4

## Goal
Compress versioning-related concept files using /cat:optimize-doc skill.

## Parent Task
compress-concepts-md (decomposed)

## Sequence
4 of 5

## Files
- plugin/concepts/hierarchy.md
- plugin/concepts/version-paths.md
- plugin/concepts/version-scheme.md
- plugin/concepts/version-completion.md

## Post-conditions
- [ ] All 4 files compressed
- [ ] All files score 1.0 on /compare-docs validation
- [ ] Tests pass

## Sub-Agent Waves

### Wave 1
1. For each file: Invoke /cat:optimize-doc
2. Commit with message: "config: compress versioning-related concepts"
