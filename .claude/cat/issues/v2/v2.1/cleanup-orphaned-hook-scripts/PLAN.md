# Plan: cleanup-orphaned-hook-scripts

## Goal
Remove bash scripts in `plugin/hooks/` that are no longer registered in hooks.json or verify they are still needed as
utilities.

## Current State
Four scripts exist in `plugin/hooks/` but are NOT registered in hooks.json:
- `detect-token-threshold.sh`
- `inject-claudemd-section.sh`
- `validate-worktree-branch.sh`
- `verify-state-in-commit.sh`

Two infrastructure scripts should be kept:
- `session-start.sh` - JDK bootstrap + Java dispatcher launcher
- `jlink-config.sh` - jlink bundle configuration (sourced by session-start.sh)

## Target State
Orphaned scripts either removed (if truly unused) or documented and registered (if still needed).

## Execution Steps
1. For each orphaned script, search for references (sourced by other scripts, called from Java, etc.)
2. If no references found, delete the script
3. If references found, document why it exists and ensure proper registration

## Acceptance Criteria
- [ ] Each orphaned script investigated for references
- [ ] Unused scripts removed
- [ ] Any kept scripts documented with rationale
- [ ] No regressions in hook behavior
