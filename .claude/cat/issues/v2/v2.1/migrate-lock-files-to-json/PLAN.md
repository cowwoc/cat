# Plan: migrate-lock-files-to-json

## Current State
Lock files use key=value format (shell-friendly) written by issue-lock.sh,
but Python readers in cleanup_handler.py and get-cleanup-display.py were
incorrectly expecting JSON format.

## Target State
Standardize on JSON format for lock files:
- issue-lock.sh writes JSON
- Python readers use json.loads() (their original implementation)
- Consistent format across all lock file operations

## Satisfies
None - infrastructure/consistency improvement

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** Existing lock files use old format
- **Mitigation:** Migration script or backward-compatible reading

## Files to Modify
- plugin/scripts/issue-lock.sh - Write JSON instead of key=value
- plugin/hooks/skill_handlers/cleanup_handler.py - Revert to json.loads()
- plugin/scripts/get-cleanup-display.py - Revert to json.loads()

## Execution Steps
1. **Step 1:** Update issue-lock.sh to write JSON format
   - Files: plugin/scripts/issue-lock.sh
   - Verify: New lock files are valid JSON

2. **Step 2:** Revert Python readers to use json.loads()
   - Files: cleanup_handler.py, get-cleanup-display.py
   - Verify: Can read new JSON lock files

3. **Step 3:** Handle existing lock files (migration or backward compat)
   - Verify: Old lock files still readable during transition

4. **Step 4:** Run tests
   - Verify: All tests pass
