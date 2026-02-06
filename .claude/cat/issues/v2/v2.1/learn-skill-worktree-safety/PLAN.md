# Plan: learn-skill-worktree-safety

## Goal
Add active worktree detection to learn skill phase-record.md so it uses `git -C` instead of `cd` and checks for
active worktrees before committing to base branch. Prevents M464 recurrence where `cd` into worktree during
concurrent git operations corrupted shell state.

## Satisfies
None - infrastructure/retrospective action item

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Learn skill git commands may behave differently with `-C` flag
- **Mitigation:** All git commands support `-C`; behavior is identical to running from that directory

## Files to Modify
- `plugin/skills/learn/phase-record.md` - The only file that needs changes

## Root Cause (M464)
Learn Phase 4 subagent used bare `git add` and `git commit` commands from whatever cwd it had. When cwd was a worktree
(e.g., `/workspace`), concurrent git operations on the same repo caused shell state corruption (all bash commands
returning exit code 1).

## Acceptance Criteria
- [ ] All `git add` and `git commit` commands in phase-record.md use `git -C "${CLAUDE_PROJECT_DIR}"` prefix
- [ ] Active worktree check added before Step 12 git operations
- [ ] If active worktrees detected, phase outputs warning but continues (non-blocking)
- [ ] No bare `cd` commands into repository directories
- [ ] Bug fixed, regression test added, no new issues

## Execution Steps
1. **Step 1:** In `plugin/skills/learn/phase-record.md`, find Step 12 (lines ~203-267) where git commands are used
2. **Step 2:** Add worktree detection check before the git commit block:
   ```bash
   # Check for active worktrees (warning only - non-blocking)
   ACTIVE_WORKTREES=$(git -C "${CLAUDE_PROJECT_DIR}" worktree list --porcelain | grep -c "^worktree " || true)
   if [[ $ACTIVE_WORKTREES -gt 1 ]]; then
     echo "WARNING: $((ACTIVE_WORKTREES - 1)) active worktree(s) detected. Committing to base branch."
   fi
   ```
3. **Step 3:** Replace all bare `git add` and `git commit` commands with `git -C` variants:
   - `git add "$MISTAKES_FILE" "$INDEX_FILE"` → `git -C "${CLAUDE_PROJECT_DIR}" add "$MISTAKES_FILE" "$INDEX_FILE"`
   - `git commit -m ...` → `git -C "${CLAUDE_PROJECT_DIR}" commit -m ...`
4. **Step 4:** Also fix the RETRO_DIR variable to be project-relative:
   - Ensure `RETRO_DIR` is defined relative to `${CLAUDE_PROJECT_DIR}` so paths resolve correctly
5. **Step 5:** Run all tests: `python3 /workspace/run_tests.py`

## Success Criteria
- [ ] All git commands in phase-record.md use `git -C` instead of bare `git`
- [ ] Worktree detection warning present before git operations
- [ ] No `cd` commands that change into repo directories
- [ ] All tests pass
