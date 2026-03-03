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

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

- Add `cd ${worktree_path}` step between Phase 1 "Store phase 1 results" and Phase 2-4 delegation
  - Files: `plugin/skills/work/first-use.md`
- Add `cd ${CLAUDE_PROJECT_DIR}` step after work-with-issue returns (before "Next Issue" section)
  - Files: `plugin/skills/work/first-use.md`

## Post-conditions

- [ ] `plugin/skills/work/first-use.md` contains an explicit `cd ${worktree_path}` instruction after Phase 1 READY
- [ ] `plugin/skills/work/first-use.md` contains an explicit `cd ${CLAUDE_PROJECT_DIR}` instruction after Phase 2-4
      completes
- [ ] E2E: Run `/cat:work` on an issue and verify the main agent's shell cwd is inside the worktree during Phase 2-4
      orchestration
