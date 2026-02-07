# Plan: remove-version-from-cat-config

## Current State
The `version` field exists in both `cat-config.json` (tracks migration state) and `plugin.json` (tracks installed
plugin version). The migration system in `check-upgrade.sh` compares these two values to detect when upgrades need
migrations. This dual-version tracking is redundant if migration state is stored differently.

## Target State
Remove the `version` field from `cat-config.json`. All code that needs the current plugin version reads it from
`plugin.json` via `get_plugin_version()`. Migration state tracking uses an alternative mechanism (e.g., a
`last-migrated-version` field or separate state file).

## Satisfies
None - infrastructure cleanup

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Migration system relies on comparing config version vs plugin version
- **Mitigation:** Ensure migration detection still works after removing the field; add migration script to clean up

## Research Findings

The `version` field in cat-config.json serves a specific purpose: tracking which version the installation has been
migrated to. The migration system works as follows:

1. `get_config_version()` in `migrations/lib/utils.sh` reads version from cat-config.json
2. `get_plugin_version()` reads version from plugin.json
3. `check-upgrade.sh` compares these two values on SessionStart
4. If plugin version > config version, migrations are run
5. After migrations, `set_config_version()` updates cat-config.json to match

Files that read/write the version field:
- `plugin/migrations/lib/utils.sh` - `get_config_version()` (line 81), `set_config_version()` (line 127)
- `plugin/hooks/check-upgrade.sh` - Compares versions (lines 26-27, 44, 62, 113)
- `plugin/migrations/1.0.8.sh` - Baseline migration that adds version field (lines 25, 36)
- `plugin/skills/init/SKILL.md` - Initializes config with plugin version (line 566)

The field is NOT simply redundant - it tracks migration state. Removing it requires either:
- (A) Renaming it to `last_migrated_version` to clarify its purpose (minimal change)
- (B) Moving migration state to a separate file (cleaner separation)
- (C) Removing it entirely and deriving migration state from applied migration records

## Files to Modify
- `plugin/migrations/lib/utils.sh` - Update `get_config_version()` and `set_config_version()` to use new field name
  or separate file
- `plugin/hooks/check-upgrade.sh` - Update version comparison logic
- `plugin/skills/init/SKILL.md` - Update init step that sets version in config
- `plugin/migrations/1.0.8.sh` - Update baseline migration
- `.claude/cat/cat-config.json` - Remove `version` field (via migration)
- New migration script to rename/remove the field

## Execution Steps
1. **Decide approach** - Choose between (A) rename, (B) separate file, or (C) remove entirely
2. **Update migrations/lib/utils.sh** - Modify version tracking functions per chosen approach
3. **Update check-upgrade.sh** - Ensure upgrade detection still works
4. **Update init skill** - Ensure new installations initialize correctly
5. **Create migration script** - Migrate existing installations from old field to new mechanism
6. **Update cat-config.json** - Remove or rename the version field
7. **Run tests** - `python3 /workspace/run_tests.py`

## Success Criteria
- [ ] All tests pass after refactoring
- [ ] Migration detection still works (upgrades trigger migrations)
- [ ] New installations initialize correctly
- [ ] The `version` field purpose is unambiguous (not confused with plugin version)
