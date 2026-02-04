# Plan: compress-skills-batch-2

## Goal
Compress skill files batch 2 (files 10-18) using /cat:shrink-doc with equivalence validation.

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
| 10 | plugin/skills/get-history/SKILL.md |
| 11 | plugin/skills/get-session-id/SKILL.md |
| 12 | plugin/skills/git-amend/SKILL.md |
| 13 | plugin/skills/git-commit/SKILL.md |
| 14 | plugin/skills/git-merge-linear/SKILL.md |
| 15 | plugin/skills/git-rebase/SKILL.md |
| 16 | plugin/skills/git-rewrite-history/SKILL.md |
| 17 | plugin/skills/git-squash/SKILL.md |
| 18 | plugin/skills/grep-and-read/SKILL.md |

## Acceptance Criteria
- [ ] All 9 files compressed
- [ ] All files score 1.0 on /compare-docs validation
- [ ] No functionality regression

## Execution Steps
1. For each file in scope: Run /cat:shrink-doc
2. Commit changes with message: "config: compress skills batch 2 (get-history through grep-and-read)"
