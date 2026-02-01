# Plan: compress-concepts-batch-1

## Goal
Compress agent/context-related concept files using /cat:shrink-doc skill.

## Parent Task
compress-concepts-md (decomposed)

## Sequence
1 of 5

## Files
- plugin/concepts/agent-architecture.md
- plugin/concepts/subagent-delegation.md
- plugin/concepts/token-warning.md

## Acceptance Criteria
- [ ] All 3 files compressed
- [ ] All files score 1.0 on /compare-docs validation
- [ ] Tests pass

## Execution Steps
1. For each file: Invoke /cat:shrink-doc
2. Verify score = 1.0 from validation
3. Commit with message: "config: compress agent-related concepts"
