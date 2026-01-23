# Plan: fix-config-documentation

## Problem
Config template contains undocumented `yoloMode` option, and README.md is missing
documentation for `autoRemoveWorktrees` which is an active, used config option.

## Satisfies
None - documentation cleanup task

## Root Cause
- `yoloMode` was added to template but never implemented or documented
- `autoRemoveWorktrees` is used in code but was omitted from README documentation
- CHANGELOG claimed it was added to README but it wasn't

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** None - documentation only
- **Mitigation:** Verify template matches documented options

## Files to Modify
- `plugin/.claude/cat/templates/config.json` - remove yoloMode
- `README.md` - add autoRemoveWorktrees to Options Reference table

## Acceptance Criteria
- [ ] `yoloMode` removed from config template
- [ ] `autoRemoveWorktrees` documented in README Options Reference
- [ ] README options match template defaults

## Execution Steps
1. **Remove yoloMode from template:**
   - File: plugin/.claude/cat/templates/config.json
   - Verify: `grep yoloMode` returns nothing

2. **Add autoRemoveWorktrees to README:**
   - File: README.md
   - Add to Options Reference table with description
   - Verify: Option documented with type, default, description
