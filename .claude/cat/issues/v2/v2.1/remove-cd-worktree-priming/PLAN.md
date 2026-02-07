# Plan: remove-cd-worktree-priming

## Goal
Remove all `cd` into worktree patterns from documentation, skills, and concepts that subagents consume. These patterns
prime LLM subagents to cd into worktrees, which corrupts the shell session when the worktree is later removed (M392,
M464, M474).

## Satisfies
- M392: Main agent must never cd into worktree directories
- M464: Concurrent git operations in worktrees corrupt shell state
- M474: Hook-based prevention insufficient — subagents have separate hook contexts

## Root Cause
Project hooks (.claude/settings.json) do not propagate to plugin subagent types (cat:work-execute, cat:work-merge).
Documentation-level prevention is the only effective approach for subagent behavior. Multiple files currently contain
`cd` into worktree examples that prime subagents to reproduce the pattern.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Must ensure replacements are functionally equivalent
- **Mitigation:** Each `cd` replaced with `git -C` equivalent; test suite verifies no regressions

## Files to Modify
- `plugin/skills/collect-results/content.md` (lines 68, 159, 272) - Replace 3x `cd "${WORKTREE}"` with `git -C`
- `plugin/concepts/merge-and-cleanup.md` (line 248) - Replace recovery `cd` with `git -C`
- `plugin/concepts/git-operations.md` (line 48) - Remove bad example showing `cd` into worktree
- `plugin/skills/decompose-issue/content.md` (line 51) - Replace `cd` with `git -C`
- `plugin/skills/work/phase-prepare.md` (lines 395, 408) - Replace `cd "$WORKTREE_PATH"` with `git -C`
- `plugin/scripts/check-existing-work.sh` (line 57) - Replace `cd` with `git -C` (bash script, lower priority)

## Acceptance Criteria
- [ ] Zero instances of `cd` into worktree paths in any skill content.md or concept .md file
- [ ] All replacements use `git -C` equivalents
- [ ] Non-git commands that needed cwd use explicit path arguments instead of cd
- [ ] All existing tests pass

## Execution Steps
1. **Step 1:** Replace all `cd "${WORKTREE}"` in collect-results/content.md with `git -C "${WORKTREE}"` equivalents
   - Files: `plugin/skills/collect-results/content.md`
2. **Step 2:** Replace recovery `cd` in merge-and-cleanup.md with `git -C` pattern
   - Files: `plugin/concepts/merge-and-cleanup.md`
3. **Step 3:** Fix git-operations.md bad example — remove the `cd` line from "Instead of" block or restructure
   - Files: `plugin/concepts/git-operations.md`
4. **Step 4:** Replace `cd` in decompose-issue/content.md with `git -C`
   - Files: `plugin/skills/decompose-issue/content.md`
5. **Step 5:** Replace `cd "$WORKTREE_PATH"` in phase-prepare.md with `git -C`
   - Files: `plugin/skills/work/phase-prepare.md`
6. **Step 6:** Replace `cd` in check-existing-work.sh with `git -C`
   - Files: `plugin/scripts/check-existing-work.sh`
7. **Step 7:** Run grep to verify zero remaining `cd.*worktree` patterns in skill/concept files
8. **Step 8:** Run all tests to verify no regressions
