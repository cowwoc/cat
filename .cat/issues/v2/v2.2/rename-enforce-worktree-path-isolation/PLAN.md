# Plan: rename-enforce-worktree-path-isolation

## Goal

Rename EnforceWorktreePathIsolation to reflect its dual Read+Write role. The class now implements
both FileWriteHandler and ReadHandler but the name only communicates write isolation. Developers
looking for read-path enforcement may not find it.

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Rename touches multiple files (source, test, hooks.json registration)
- **Mitigation:** Mechanical rename with IDE support

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/write/EnforceWorktreePathIsolation.java` (rename)
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceWorktreePathIsolationTest.java` (rename)
- `plugin/hooks/hooks.json` (update class reference)
- `client/src/main/java/io/github/cowwoc/cat/hooks/PreReadHook.java` (update import)

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Rename class to EnforcePathIsolation (or similar) across all references
  - Files: all files listed above

## Post-conditions

- [ ] Class name reflects dual Read+Write isolation role
- [ ] All references updated consistently
- [ ] All tests pass
- [ ] E2E: Verify hooks still fire correctly after rename