# Plan: work-prepare-discover-only

## Goal
Add a `--discover-only` flag to `work-prepare` that returns next-issue information without creating
worktrees or acquiring locks, preventing orphaned artifacts when the work cycle is never started.

## Satisfies
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Existing callers of `work-prepare` rely on side effects (worktree creation, lock acquisition).
  The new flag is opt-in and does not change existing behavior.
- **Mitigation:** Flag is additive; existing callers work unchanged.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/WorkPrepare.java` — add `--discover-only`
  flag that skips worktree creation and lock acquisition, returning only the issue metadata
- `plugin/skills/work/SKILL.md` — update the "Next Issue" section to call `work-prepare --discover-only`
  when discovering the next issue for the work-complete box, and fall back to cleanup if a previously
  acquired lock/worktree exists
- `plugin/skills/work-complete/SKILL.md` — update next-issue discovery to use `--discover-only`

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- Add `--discover-only` flag to `WorkPrepare.java`:
  - When flag is set: run all discovery logic (find next issue, compute goal/tokens) but skip
    worktree creation, lock acquisition, and branch creation
  - Return same JSON structure as normal mode but with `lock_acquired: false` and no worktree_path
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/WorkPrepare.java`
- Run `mvn -f client/pom.xml test` to verify no regressions

### Wave 2
- Update `plugin/skills/work/SKILL.md` — "Next Issue" section:
  - Replace the current `work-prepare` call (which creates worktrees/locks) with
    `work-prepare --discover-only` when detecting the next issue for the work-complete display
  - Add cleanup guard: if a full `work-prepare` was run (lock_acquired: true) and auto-continue
    doesn't happen (user stops or trust=low), release the lock and remove the worktree before stopping
  - Files: `plugin/skills/work/SKILL.md`
- Update `plugin/skills/work-complete/SKILL.md` if it calls `work-prepare` directly:
  - Use `--discover-only` for discovery-only calls
  - Files: `plugin/skills/work-complete/SKILL.md`

## Post-conditions
- [ ] `work-prepare --discover-only` returns next-issue JSON without creating worktrees, branches, or locks
- [ ] `/cat:work` and `/cat:work-complete` use `--discover-only` when discovering the next issue for display
- [ ] No orphaned worktrees or branches remain after a session ends without starting the next issue
- [ ] All existing tests pass
- [ ] New test verifies `--discover-only` mode does not create worktrees or acquire locks
