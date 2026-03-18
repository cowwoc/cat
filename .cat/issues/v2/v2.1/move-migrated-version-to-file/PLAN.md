# Plan: move-migrated-version-to-file

## Goal
Move `last_migrated_version` from `cat-config.json` into a standalone `.cat/VERSION` file. This simplifies
config and makes the migration version a plain text file (no JSON parsing needed).

## Satisfies
None - infrastructure cleanup

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Existing installations have version in cat-config.json
- **Mitigation:** Migration script reads old location and writes new file

## Files to Modify
- `plugin/migrations/lib/utils.sh` - Update `get_last_migrated_version()` and `set_last_migrated_version()` to
  read/write `.cat/VERSION` instead of cat-config.json
- `plugin/migrations/lib/utils.sh` - Remove backward compatibility wrappers `get_config_version()`/`set_config_version()`
- `.cat/cat-config.json` - Remove `last_migrated_version` field
- New migration script (e.g., `2.4.sh`) - Migrate existing installations: read value from cat-config.json, write to
  `.cat/VERSION`, remove field from cat-config.json
- `plugin/migrations/registry.json` - Register new migration

## Files to Delete
None

## Post-conditions
- [ ] `get_last_migrated_version()` reads from `.cat/VERSION` (plain text, single line)
- [ ] `set_last_migrated_version()` writes to `.cat/VERSION` (plain text, single line)
- [ ] `last_migrated_version` field removed from `cat-config.json`
- [ ] Migration script moves value from old location to new file
- [ ] Backward compatibility wrappers removed

## Sub-Agent Waves

### Wave 1
1. **Update `plugin/migrations/lib/utils.sh`:**
   - `get_last_migrated_version()`: Read from `.cat/VERSION` file. Return "0.0.0" if file doesn't exist.
   - `set_last_migrated_version()`: Write version string to `.cat/VERSION` file (plain text, no JSON).
   - Remove `get_config_version()` and `set_config_version()` wrappers.
2. **Create migration script** `plugin/migrations/2.4.sh`:
   - Read `last_migrated_version` from `.cat/cat-config.json` using grep/sed
   - Write value to `.cat/VERSION`
   - Remove `last_migrated_version` key from cat-config.json
3. **Register migration** in `plugin/migrations/registry.json`
4. **Remove `last_migrated_version`** from `.cat/cat-config.json`
5. **Run tests** to verify migration system still works
