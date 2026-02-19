# Plan: Add STATE.md Regression Check to git-squash-quick.sh

## Goal

Add automated validation to git-squash-quick.sh that detects and aborts when another issue's STATE.md regresses from
closed to open, preventing stale worktree captures from reaching the final squash commit.

## Satisfies

None (infrastructure/prevention from M363)

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** False positives if the current issue legitimately changes another STATE.md
- **Mitigation:** Exclude the current issue's own STATE.md from the regression check

## Files to Modify

- plugin/scripts/git-squash-quick.sh - Add regression check after rebase, before commit-tree

## Acceptance Criteria

- [ ] git-squash-quick.sh aborts with clear error when another issue's STATE.md regressed from closed to open
- [ ] git-squash-quick.sh succeeds normally when current issue's STATE.md changes from open/in-progress to closed
- [ ] Bats test for regression detection added to tests/hooks/git-squash-quick.bats

## Execution Steps

1. **Add regression check function:** After the rebase step and before commit-tree, scan all STATE.md files in the
   rebased tree that differ from BASE. If any show `Status: open` where BASE has `Status: closed`, abort with an error
   listing the regressed files.
   - Files: plugin/scripts/git-squash-quick.sh
2. **Add Bats tests:** Test that squash aborts on STATE.md regression and succeeds on normal STATE.md changes.
   - Files: tests/hooks/git-squash-quick.bats
