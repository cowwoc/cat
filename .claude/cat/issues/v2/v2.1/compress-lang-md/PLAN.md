# Plan: compress-lang-md

## Goal
Compress all lang MD files (1 file in `plugin/lang/`) to reduce token usage while maintaining execution equivalence.

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
| Lang | 1 | `plugin/lang/*.md` |

## Acceptance Criteria
- [ ] All 1 lang file compressed
- [ ] Execution equivalence verified (all files score 1.0 on /compare-docs)
- [ ] No functionality regression

## Execution Steps
1. List all MD files in `plugin/lang/`
2. For each file: Run /cat:shrink-doc
3. Commit changes with appropriate message
