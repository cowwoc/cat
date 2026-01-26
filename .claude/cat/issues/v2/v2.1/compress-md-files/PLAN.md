# Plan: compress-md-files

## Goal
Compress all plugin MD files to reduce token usage while maintaining execution equivalence.

## Satisfies
None - infrastructure/optimization task

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Compressed files might lose semantic content
- **Mitigation:** /compare-docs validation ensures score = 1.0 before applying

## Scope

| Category | Files | Notes |
|----------|-------|-------|
| Skills | 34 | `plugin/skills/*/SKILL.md` + extras |
| Concepts | 18 | `plugin/concepts/*.md` |
| Templates | 12 | `plugin/templates/*.md` |
| Stakeholders | 11 | `plugin/stakeholders/*.md` |
| Commands | 10 | `plugin/commands/*.md` |
| Lang | 1 | `plugin/lang/*.md` |
| **Total** | **86** | **~26,121 lines** |

**Target**: ~50% reduction = ~13,000 lines saved

**Excluded**: `plugin/node_modules/` (third-party files)

## Acceptance Criteria
- [ ] Execution equivalence verified (all files score 1.0 on /compare-docs)
- [ ] No functionality regression (skills and commands work correctly)

## Execution Steps
1. **Process by category** (largest files first within each):
   - Skills (34 files)
   - Commands (10 files)
   - Concepts (18 files)
   - Templates (12 files)
   - Stakeholders (11 files)
   - Lang (1 file)
2. **For each file:** Run /cat:shrink-doc
   - Verify: Score = 1.0 from /compare-docs validation
3. **After all files:** Test affected skills/commands
   - Verify: Commands execute without errors
