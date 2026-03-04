# Plan: document-path-config-verification

## Goal

Document path and config verification requirements so agents always check worktree context and read
cat-config.json before using values, clarifying abort semantics.

## Satisfies

Retrospective action item A003 (ineffective).

## Root Cause

Agents assume paths and config values are correct without verification, leading to wrong-branch merges,
wrong-directory file operations, and silent fallback to incorrect defaults.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** None — documentation only
- **Mitigation:** Review existing conventions for overlap

## Files to Modify

- `plugin/concepts/worktree-isolation.md` or equivalent — add verification checklist
- Session instructions injection — add fail-fast rule for path/config reads

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

- Document mandatory verification steps for worktree path, base branch, and config values
  - Files: `plugin/concepts/worktree-isolation.md`
- Add fail-fast rule: agents must read cat-config.json before using branch names or paths
  - Files: session instructions or conventions file

## Post-conditions

- [ ] A verification checklist exists for path and config values
- [ ] Fail-fast rule documented for config reads before use
