# Plan: compress-skills-md

## Goal
Compress all skills MD files (34 files in `plugin/skills/`) to reduce token usage while maintaining execution
equivalence.

## Parent Task
compress-md-files (decomposed)

## Satisfies
None - infrastructure/optimization sub-issue

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Compressed files might lose semantic content
- **Mitigation:** /compare-docs validation ensures score = 1.0 before applying

## Scope
| Category | Files | Path Pattern |
|----------|-------|--------------|
| Skills | 34 | `plugin/skills/*/SKILL.md` and extras |

## Acceptance Criteria
- [ ] All 34 skill files compressed
- [ ] Execution equivalence verified (all files score 1.0 on /compare-docs)
- [ ] No functionality regression

## Execution Steps
1. List all MD files in `plugin/skills/`
2. For each file: Run /cat:shrink-doc
3. Commit changes with appropriate message
