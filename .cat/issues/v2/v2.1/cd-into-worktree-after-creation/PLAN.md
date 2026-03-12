# Plan: cd-into-worktree-after-creation

## Goal

Make the main agent change its working directory into the issue worktree after work-prepare creates it, and restore cwd
to the project directory after work-merge removes the worktree.

## Satisfies

None (workflow improvement)

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Main agent cwd becomes invalid after worktree removal if cd-back is missed
- **Mitigation:** Explicit cd-back step after work-with-issue returns; safe-rm already enforces worktree safety

## Files to Modify

- `plugin/skills/work/first-use.md` — Add explicit `cd` steps after Phase 1 and after Phase 2-4
- `plugin/skills/work-with-issue/first-use.md` — Fix hardcoded `cd /workspace` and missing cd-back in error handling

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

- Add `cd ${worktree_path}` step between Phase 1 "Store phase 1 results" and Phase 2-4 delegation
  - Files: `plugin/skills/work/first-use.md`
- Add `cd ${CLAUDE_PROJECT_DIR}` step after work-with-issue returns (before "Next Issue" section)
  - Files: `plugin/skills/work/first-use.md`
- Fix hardcoded `cd /workspace` → `cd "${CLAUDE_PROJECT_DIR}"` in work-with-issue Step 9
  - Files: `plugin/skills/work-with-issue/first-use.md`
- Add missing `cd "${CLAUDE_PROJECT_DIR}"` to work-with-issue error handling
  - Files: `plugin/skills/work-with-issue/first-use.md`

## Post-conditions

- [ ] `plugin/skills/work/first-use.md` contains an explicit `cd ${worktree_path}` instruction after Phase 1 READY
- [ ] `plugin/skills/work/first-use.md` contains an explicit `cd ${CLAUDE_PROJECT_DIR}` instruction after Phase 2-4
      completes
- [ ] `plugin/skills/work-with-issue/first-use.md` uses `cd "${CLAUDE_PROJECT_DIR}"` (not hardcoded `/workspace`) before
      merge subagent
- [ ] `plugin/skills/work-with-issue/first-use.md` error handling restores cwd before lock release
- [ ] E2E: Run `/cat:work` on an issue and verify the main agent's shell cwd is inside the worktree during Phase 2-4
      orchestration
