# Plan: fix-versioned-dependency-resolution

## Goal

Fix `is_dependency_satisfied()` in `get-available-issues.sh` to resolve version-qualified dependency names
(e.g., `2.1-port-lock-and-worktree`) to their actual directory names (e.g., `port-lock-and-worktree` under `v2.1/`).
Currently, issues with version-qualified dependencies are incorrectly reported as blocked even when the dependency is
closed.

## Satisfies

None (infrastructure bugfix)

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Incorrect stripping could match wrong issues across versions
- **Mitigation:** Use version prefix to search in the correct version directory first, fall back to cross-version search

## Files to Modify

- `plugin/scripts/get-available-issues.sh` - Update `is_dependency_satisfied()` to handle version-qualified names

## Post-conditions

- [ ] `is_dependency_satisfied "2.1-port-lock-and-worktree"` returns `true` when `port-lock-and-worktree` under `v2.1/`
  is closed
- [ ] Bare dependency names (e.g., `migrate-python-to-java`) continue to resolve correctly
- [ ] Version-qualified names where the dependency is NOT closed still return `false`
- [ ] E2E: `get-available-issues.sh` returns `found` for `2.1-port-issue-discovery` (currently returns `blocked`)

## Execution Steps

1. **Update `is_dependency_satisfied()` in `get-available-issues.sh`:** When the initial `find` for the literal
   dependency name fails, check if the name matches the version-qualified format (`N.N-name`). If so, extract the
   version (`N.N`) and bare name, then search for the bare name under the corresponding version directory
   (`$CAT_DIR/vN/vN.N/bare_name/STATE.md`).
   - Files: `plugin/scripts/get-available-issues.sh`
2. **Run tests:** Verify existing tests pass and the fix resolves the blocking issue.
   - Files: test suite
