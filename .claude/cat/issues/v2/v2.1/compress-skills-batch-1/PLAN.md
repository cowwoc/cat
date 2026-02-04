# Plan: compress-skills-batch-1

## Goal
Compress skill files batch 1 (files 1-9) using /cat:shrink-doc with equivalence validation.

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
| 1 | plugin/skills/add/SKILL.md |
| 2 | plugin/skills/batch-read/SKILL.md |
| 3 | plugin/skills/cleanup/SKILL.md |
| 4 | plugin/skills/collect-results/SKILL.md |
| 5 | plugin/skills/compare-docs/SKILL.md |
| 6 | plugin/skills/config/SKILL.md |
| 7 | plugin/skills/decompose-issue/SKILL.md |
| 8 | plugin/skills/delegate/SKILL.md |
| 9 | plugin/skills/format-documentation/SKILL.md |

## Acceptance Criteria
- [ ] All 9 files compressed
- [ ] All files score 1.0 on /compare-docs validation
- [ ] No functionality regression

## Execution Steps
1. For each file in scope: Run /cat:shrink-doc
2. Commit changes with message: "config: compress skills batch 1 (add through format-documentation)"
