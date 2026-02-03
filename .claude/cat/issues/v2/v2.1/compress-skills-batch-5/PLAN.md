# Plan: compress-skills-batch-5

## Goal
Compress skill files batch 5 (files 37-45) using /cat:shrink-doc with equivalence validation.

## Parent Task
compress-skills-md (decomposed)

## Satisfies
None - subtask of parent

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Compressed files might lose semantic content
- **Mitigation:** /compare-docs validation ensures score = 1.0 before applying

## Scope
| # | File |
|---|------|
| 37 | plugin/skills/token-report/SKILL.md |
| 38 | plugin/skills/validate-git-safety/SKILL.md |
| 39 | plugin/skills/work-execute/SKILL.md |
| 40 | plugin/skills/work-merge/SKILL.md |
| 41 | plugin/skills/work-prepare/SKILL.md |
| 42 | plugin/skills/work-review/SKILL.md |
| 43 | plugin/skills/work-with-task/SKILL.md |
| 44 | plugin/skills/work/SKILL.md |
| 45 | plugin/skills/write-and-commit/SKILL.md |

## Acceptance Criteria
- [ ] All 9 files compressed
- [ ] All files score 1.0 on /compare-docs validation
- [ ] No functionality regression

## Execution Steps
1. For each file in scope: Run /cat:shrink-doc
   - Verify: Score = 1.0 from /compare-docs validation
2. Commit changes with message: "config: compress skills batch 5 (token-report through write-and-commit)"
