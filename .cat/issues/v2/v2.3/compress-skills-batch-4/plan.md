# Plan: compress-skills-batch-4

## Goal
Compress skill files batch 4 (files 28-36) using /cat:optimize-doc with equivalence validation.

## Parent Task
compress-skills-md (decomposed)

## Parent Requirements
None - sub-issue of parent

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Compressed files might lose semantic content
- **Mitigation:** /compare-docs validation ensures score = 1.0 before applying

## Scope
| # | File |
|---|------|
| 28 | plugin/skills/research/SKILL.md |
| 29 | plugin/skills/run-retrospective/SKILL.md |
| 30 | plugin/skills/safe-remove-code/SKILL.md |
| 31 | plugin/skills/safe-rm/SKILL.md |
| 32 | plugin/skills/optimize-doc/SKILL.md |
| 33 | plugin/skills/skill-builder/SKILL.md |
| 34 | plugin/skills/stakeholder-review/SKILL.md |
| 35 | plugin/skills/status/SKILL.md |
| 36 | plugin/skills/tdd-implementation-agent/SKILL.md |

## Post-conditions
- [ ] All 9 files compressed
- [ ] All files score 1.0 on /compare-docs validation
- [ ] No functionality regression

## Sub-Agent Waves

### Wave 1
1. Use /cat:delegate to process all files in parallel: `/cat:delegate --skill optimize-doc file1.md file2.md ...` (M369)
2. Commit changes with message: "config: compress skills batch 4 (research through tdd-implementation-agent)"

**Note (M427):** Use delegate for batch operations - parallel execution is faster than sequential.
