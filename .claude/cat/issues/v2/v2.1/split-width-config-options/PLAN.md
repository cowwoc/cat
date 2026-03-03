# Plan: Split terminalWidth Into fileWidth and displayWidth

## Goal
Split the single `terminalWidth` configuration option into two distinct options: `fileWidth` for content
written to files, and `displayWidth` for content displayed in the terminal. This allows users to independently
tune wrapping for file output (e.g., markdown docs) vs. terminal rendering (e.g., diffs, status boxes).

## Satisfies
- None

## Rejected Approaches

### A: Hierarchical key `terminalWidth.file` / `terminalWidth.display`
- **Risk:** MEDIUM
- **Rationale for rejection:** Nested JSON key paths require different parsing in bash scripts; the
  migration script uses `sed`/`awk` without `jq`, making hierarchical key manipulation error-prone.
  Flat keys are simpler and consistent with the existing config style.

### B: Rename to `fileLineLength` / `displayLineLength`
- **Risk:** LOW
- **Rationale for rejection:** The existing name uses "Width" (terminalWidth). Keeping "Width" in the
  new keys maintains naming consistency and avoids user confusion when migrating.

### C: Single config key with both uses (no split)
- **Risk:** LOW
- **Rationale for rejection:** Does not address the use case where a narrow mobile terminal (50 cols)
  should still wrap files at 120 chars, or vice versa.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Tests reference `terminalWidth` in JSON; migration must handle partial configs
- **Mitigation:** Update all test fixtures; migration script handles missing keys gracefully

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/Config.java` - remove `terminalWidth` default,
  add `fileWidth: 120` and `displayWidth: 120` defaults
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetDiffOutput.java:410` - change
  `config.getInt("terminalWidth", 50)` to `config.getInt("displayWidth", 120)`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java:496` - change
  `config.getInt("terminalWidth", 120)` to `config.getInt("displayWidth", 120)`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetDiffOutputTest.java` - replace all
  `"terminalWidth"` keys in JSON fixtures with `"displayWidth"`
- `plugin/skills/config/first-use.md` - replace `terminalWidth` documentation with `fileWidth` and
  `displayWidth` entries
- `plugin/migrations/2.1.sh` - add Phase 9: migrate `terminalWidth` to `fileWidth` + `displayWidth`

## Edge Cases
- **Only `terminalWidth` present (pre-migration):** Migration sets both `fileWidth` and `displayWidth`
  to the same value as `terminalWidth`, then removes `terminalWidth`.
- **Neither key present:** Both fall back to default 120 (enforced in Config.java).
- **Only one of the new keys present:** The missing key falls back to its default 120.
- **`terminalWidth` still present after failed migration:** Java readers do NOT fall back to
  `terminalWidth`; users must re-run migration to get expected values.

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- Update `Config.java`: remove `terminalWidth` default, add `fileWidth: 120` and `displayWidth: 120`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/Config.java`
- Update `GetDiffOutput.java`: change `config.getInt("terminalWidth", 50)` to
  `config.getInt("displayWidth", 120)` on line 410; rename local variable `terminalWidth` to
  `displayWidth` and update the `DiffRenderer` constructor call on line 458 accordingly
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetDiffOutput.java`
- Update `GetStatusOutput.java`: change `config.getInt("terminalWidth", 120)` to
  `config.getInt("displayWidth", 120)` on line 496; update comment on line 495 and rename local
  variable `terminalWidth` to `displayWidth`; update all downstream uses in the same method
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java`

### Wave 2
- Update test fixtures: replace all `"terminalWidth"` keys with `"displayWidth"` in
  `GetDiffOutputTest.java` (lines 72, 107, 239, 288, 339, 391)
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetDiffOutputTest.java`
- Update config skill documentation: in `plugin/skills/config/first-use.md`, replace the
  `terminalWidth` option with separate `fileWidth` and `displayWidth` options; update
  mobile/desktop examples and the merge snippet
  - Files: `plugin/skills/config/first-use.md`

### Wave 3
- Add Phase 9 to `plugin/migrations/2.1.sh`: after Phase 8, add a phase that:
  1. Checks if `terminalWidth` exists in `cat-config.json`
  2. Reads its value, writes `fileWidth` and `displayWidth` with that value
  3. Removes `terminalWidth` using the same awk pattern as Phase 8
  4. Verifies `terminalWidth` is absent and both new keys present
  5. Logs each step with `log_migration`
  - Files: `plugin/migrations/2.1.sh`
- Run `mvn -f client/pom.xml test` and verify all tests pass

### Wave 4
- Update `README.md` to document both `fileWidth` and `displayWidth` config options where
  `terminalWidth` was previously referenced (if any), or add them to the configuration section
  - Files: `README.md`
- Update `/cat:config` skill output to display both `fileWidth` and `displayWidth` current values
  in the config display (e.g., `GetConfigOutput.java` if it renders width info)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetConfigOutput.java` (if applicable)

## Post-conditions
- [ ] `Config.java` has `fileWidth: 120` and `displayWidth: 120` defaults; no `terminalWidth`
- [ ] `GetDiffOutput.java` reads `displayWidth` from config (not `terminalWidth`)
- [ ] `GetStatusOutput.java` reads `displayWidth` from config (not `terminalWidth`)
- [ ] `GetDiffOutputTest.java` uses `displayWidth` in all JSON fixtures; no `terminalWidth` remaining
- [ ] `plugin/skills/config/first-use.md` documents `fileWidth` and `displayWidth`; no `terminalWidth`
- [ ] `plugin/migrations/2.1.sh` Phase 9 migrates `terminalWidth` to both new keys idempotently
- [ ] `README.md` documents both `fileWidth` and `displayWidth` config options
- [ ] `/cat:config` displays both `fileWidth` and `displayWidth` current values
- [ ] `mvn -f client/pom.xml test` passes with exit code 0
- [ ] E2E: set `displayWidth: 60` in `cat-config.json`, run `get-diff`, verify diff renders at 60 cols