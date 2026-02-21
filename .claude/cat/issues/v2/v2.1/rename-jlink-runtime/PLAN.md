# Plan: rename-jlink-runtime

## Goal

Change `RUNTIME_NAME` in `jlink-config.sh` from `cat-jdk-${JDK_VERSION}` (e.g., `cat-jdk-25`) to
`cat-${CAT_VERSION}` (e.g., `cat-2.1`), decoupling the runtime directory name from the JDK version and tying it to
the CAT release version instead.

## Satisfies

None (infrastructure improvement)

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Other scripts or documentation may reference the old `cat-jdk-25` path
- **Mitigation:** Grep for all `cat-jdk` references and update them; session-start.sh already uses
  `cat-${plugin_version}` for archive names so the pattern is established

## Files to Modify

- `plugin/hooks/jlink-config.sh` - Replace `JDK_VERSION`-based `RUNTIME_NAME` with `CAT_VERSION` sourced from
  `plugin.json`; remove `JDK_VERSION` constant if no longer used elsewhere
- `plugin/hooks/README.md` - Update all `cat-jdk-25` references to `cat-${CAT_VERSION}` pattern

## Acceptance Criteria

- [ ] `RUNTIME_NAME` uses CAT version (from `plugin.json`) instead of JDK version
- [ ] No remaining references to `cat-jdk-` pattern in the codebase
- [ ] `jlink-config.sh info` displays the new runtime name
- [ ] `jlink-config.sh build` creates runtime directory with new name
- [ ] E2E: `./jlink-config.sh info` shows `Runtime Name: cat-2.1` (matching plugin.json version)

## Execution Steps

1. **Read plugin.json version in jlink-config.sh:** Add logic to read CAT version from
   `plugin.json` (located at `${SCRIPT_DIR}/../.claude-plugin/plugin.json`). Replace `RUNTIME_NAME` definition.
   - Files: `plugin/hooks/jlink-config.sh`
2. **Remove JDK_VERSION if unused:** Check if `JDK_VERSION` is still needed for `check_java_version()`. If so, keep
   it; if not, remove it.
   - Files: `plugin/hooks/jlink-config.sh`
3. **Update README references:** Replace all `cat-jdk-25` occurrences with the new naming pattern.
   - Files: `plugin/hooks/README.md`
4. **Run tests:** Verify no regressions.
