# Plan: merge-and-cleanup-script

## Goal
Create a script to handle the happy path of the merging phase in work.md, automating git merge,
worktree removal, branch deletion, and lock release for faster task completion cycles.

## Satisfies
None - infrastructure/performance task

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Script must handle edge cases gracefully (dirty worktree, merge conflicts)
- **Mitigation:** Script only handles happy path; non-happy paths exit with clear error messages

## Files to Modify
- plugin/scripts/merge-and-cleanup.sh - new script (primary)
- plugin/commands/work.md - reference the new script in merging phase (optional)

## Acceptance Criteria
- [ ] Script performs fast-forward merge to base branch
- [ ] Script removes worktree cleanly
- [ ] Script deletes task branch
- [ ] Script releases task lock
- [ ] Script exits cleanly with appropriate error codes on failures

## Execution Steps
1. **Step 1:** Create merge-and-cleanup.sh script
   - Files: plugin/scripts/merge-and-cleanup.sh
   - Verify: Script exists and is executable

2. **Step 2:** Implement merge logic
   - Fast-forward only merge to base branch
   - Verify: Test with sample branch

3. **Step 3:** Implement cleanup logic
   - Remove worktree, delete branch, release lock
   - Verify: Resources cleaned up properly

4. **Step 4:** Add error handling
   - Exit codes and error messages for each failure point
   - Verify: Script fails gracefully on edge cases
