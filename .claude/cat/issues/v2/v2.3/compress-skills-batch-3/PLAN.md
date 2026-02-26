# Plan: compress-skills-batch-3

## Goal
Compress skill files batch 3 (files 19-27) using /cat:shrink-doc with equivalence validation.

## Parent Task
compress-skills-md (decomposed)

## Satisfies
None - sub-issue of parent

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Compressed files might lose semantic content
- **Mitigation:** /compare-docs validation ensures score = 1.0 before applying

## Scope
| # | File |
|---|------|
| 19 | plugin/skills/help/SKILL.md |
| 20 | plugin/skills/init/SKILL.md |
| 21 | plugin/skills/learn/SKILL.md |
| 22 | plugin/skills/merge-subagent/SKILL.md |
| 23 | plugin/skills/monitor-subagents/SKILL.md |
| 24 | plugin/skills/optimize-execution/SKILL.md |
| 25 | plugin/skills/register-hook/SKILL.md |
| 26 | plugin/skills/remove/SKILL.md |
| 27 | plugin/skills/render-diff/SKILL.md |

## Acceptance Criteria
- [ ] All 9 files compressed
- [ ] All files score 1.0 on /compare-docs validation
- [ ] No functionality regression

## Execution Steps
1. For each file in scope: Run /cat:shrink-doc
2. Commit changes with message: "config: compress skills batch 3 (help through render-diff)"
