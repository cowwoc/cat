# Plan: remove-auto-remove-worktrees-config

## Goal

Remove the `autoRemoveWorktrees` configuration option entirely. Hard-code the auto-remove behavior as the default
(always remove worktrees after issue completion). Remove the option from `cat-config.json`, `Config.java`,
`GetConfigOutput.java`, and the `/cat:config` wizard.

## Satisfies

- None

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Code that reads `autoRemoveWorktrees` must be updated to use the hard-coded default.
- **Mitigation:** Search for all usages before removing.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/Config.java` - remove `autoRemoveWorktrees` field and getter
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetConfigOutput.java` - remove Cleanup line from box
- `plugin/skills/config/first-use.md` - remove "Cleanup" menu entry and cleanup wizard step
- `.claude/cat/cat-config.json` - remove `autoRemoveWorktrees` key
- Any other callers of `getAutoRemoveWorktrees()` - replace with `true` (hard-coded)

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Steps

1. **Find all usages** of `autoRemoveWorktrees` and `getAutoRemoveWorktrees`:
   ```bash
   grep -r "autoRemoveWorktrees\|getAutoRemoveWorktrees" /workspace/client /workspace/plugin --include="*.java" --include="*.md" --include="*.json" -l
   ```

2. **Update `Config.java`**:
   - Remove `autoRemoveWorktrees` from the `defaults` map
   - Remove `getAutoRemoveWorktrees()` method

3. **Update all callers** of `getAutoRemoveWorktrees()` — replace with `true` (hard-coded auto-remove).

4. **Update `GetConfigOutput.java`** — remove the Cleanup line from the CURRENT_SETTINGS box.

5. **Update `plugin/skills/config/first-use.md`**:
   - Remove "🧹 Cleanup" entry from main menu
   - Remove the `<step name="cleanup">` wizard step

6. **Update `.claude/cat/cat-config.json`** — remove the `autoRemoveWorktrees` key.

7. **Run `mvn -f client/pom.xml verify`** to confirm tests pass.

8. **Rebuild jlink**:
   ```bash
   cd client && bash build-jlink.sh
   ```

9. **Commit** with message:
   `refactor: remove autoRemoveWorktrees config option and hard-code auto-remove behavior`

## Post-conditions

- [ ] `autoRemoveWorktrees` does not appear in `cat-config.json`, `Config.java`, skill files, or `GetConfigOutput.java`
- [ ] Worktree auto-remove behavior is unchanged (always auto-remove)
- [ ] `/cat:config` CURRENT_SETTINGS box no longer shows Cleanup
- [ ] `mvn -f client/pom.xml verify` passes
