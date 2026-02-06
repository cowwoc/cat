# Plan: compress-concepts-md

## Goal
Compress all concept MD files (18 files in `plugin/concepts/`) to reduce token usage while maintaining execution
equivalence.

## Parent Task
compress-md-files (decomposed)

## Satisfies
None - infrastructure/optimization subtask

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Compressed files might lose semantic content
- **Mitigation:** /compare-docs validation ensures score = 1.0 before applying

## Scope
| Category | Files | Path Pattern |
|----------|-------|--------------|
| Concepts | 18 | `plugin/concepts/*.md` |

## Acceptance Criteria
- [ ] All 18 concept files compressed
- [ ] Execution equivalence verified (all files score 1.0 on /compare-docs)
- [ ] No functionality regression

## Execution Steps
1. List all MD files in `plugin/concepts/`
2. For each file: Run /cat:shrink-doc
3. Commit changes with appropriate message
