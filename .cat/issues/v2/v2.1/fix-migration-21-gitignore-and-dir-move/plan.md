# Plan: fix-migration-21-gitignore-and-dir-move

## Problem

The `2.1.sh` migration script has two gaps that leave the repository in a partially migrated state
after it runs:

1. **Orphaned `.gitignore` comments** — Phase 6 removes stale pattern lines (`/worktrees/`,
   `/locks/`, `/verify/`) but leaves behind their associated comment lines:

   ```
   # Temporary worktrees created for issue isolation
   # Lock files used to prevent concurrent access
   # Verification output from audit and verify commands
   ```

   The resulting `.gitignore` contains three dangling comment blocks with no corresponding patterns.

2. **Directories not migrated** — The script removes `.gitignore` entries for these directories
   (because they moved to external storage), but never moves or removes the directories themselves.
   After migration, the following still exist under `.cat/`:

   - `locks/` — cross-session; should move to `{projectCatDir}/locks/`
   - `worktrees/` — cross-session; should move to `{projectCatDir}/worktrees/`
   - `sessions/` — session-scoped; stale after migration, should be deleted
   - `verify/` — session-scoped; stale after migration, should be deleted
   - `e2e-config-test/` — session-scoped; stale after migration, should be deleted

## Satisfies

None

## Expected vs Actual

- **Expected:** After running `2.1.sh`, `.cat/.gitignore` contains no dangling comments, and
  the `locks/`, `worktrees/`, `sessions/`, `verify/`, `e2e-config-test/` directories no longer
  exist under `.cat/`.
- **Actual:** Three orphaned comment lines remain in `.gitignore`, and all five directories remain in
  `.cat/` unchanged.

## Root Cause

**Bug 1:** Phase 6 `sed` deletion targets only lines containing the stale pattern path string. It does
not look ahead or behind to also remove associated comment and blank lines.

**Bug 2:** The script has no phase that moves `locks/` and `worktrees/` to external storage or
deletes the session-scoped directories.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Moving `locks/` could interfere with a concurrently running session that holds a lock.
  Deletion of `sessions/` and `verify/` is safe since these are always stale at migration time.
- **Mitigation:** Move `locks/` and `worktrees/` only when the source exists; delete session-scoped
  directories unconditionally.

## Files to Modify

- `plugin/migrations/2.1.sh` — fix Phase 6 to strip orphaned comments; add Phase 10 to migrate or
  remove the directories

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- **Fix Phase 6 — remove orphaned comment lines:** When removing a stale pattern from `.gitignore`,
  also delete any immediately preceding comment line(s) associated with it. Use `awk` or multi-pass
  `sed` to detect and strip comment+blank-line blocks above each removed pattern.
  - Files: `plugin/migrations/2.1.sh`

- **Add Phase 10 — migrate cross-session directories:** Compute `{projectCatDir}` as
  `${CLAUDE_CONFIG_DIR:-$HOME/.config/claude}/projects/$(pwd | sed 's|[/.]|-|g')/cat`. If
  `.cat/locks/` exists, move its contents to `{projectCatDir}/locks/` and remove the source
  dir. Do the same for `.cat/worktrees/`.
  - Files: `plugin/migrations/2.1.sh`

- **Add Phase 10 — delete session-scoped directories:** Unconditionally remove
  `.cat/sessions/`, `.cat/verify/`, and `.cat/e2e-config-test/` when present
  (contents are always stale after migration).
  - Files: `plugin/migrations/2.1.sh`

- **Write failing tests:** Add Bats tests that:
  - Verify `.gitignore` contains no comment lines after Phase 6 removes stale patterns
  - Verify `locks/` and `worktrees/` are absent from `.cat/` after Phase 10
  - Verify `sessions/`, `verify/`, `e2e-config-test/` are absent from `.cat/` after Phase 10
  - Files: `plugin/migrations/tests/test_migration_2_1.bats` (or existing test file)

- **Run tests and verify they pass**

## Post-conditions

- [ ] After running `2.1.sh`, `.cat/.gitignore` contains no orphaned comment lines (only
  `cat-config.local.json` remains, matching the template)
- [ ] After running `2.1.sh`, `.cat/locks/` does not exist (contents moved to external storage
  or dir was already absent)
- [ ] After running `2.1.sh`, `.cat/worktrees/` does not exist (contents moved to external
  storage or dir was already absent)
- [ ] After running `2.1.sh`, `.cat/sessions/`, `.cat/verify/`, and
  `.cat/e2e-config-test/` do not exist
- [ ] Running `2.1.sh` a second time is a no-op (idempotent)
- [ ] All tests pass (`mvn -f client/pom.xml test` or Bats test suite)
