# Plan: omit-default-config-values

## Problem

`cat:config` writes selected settings through `client/bin/update-config`, which currently persists all provided
`key=value` pairs into `.cat/config.json` even when a value equals CAT's built-in default. This causes config files to
accumulate default-valued entries instead of storing only meaningful overrides.

## Parent Requirements

None

## Reproduction Code

```java
Path project = Files.createTempDirectory("cfg");
try (TestClaudeTool scope = new TestClaudeTool(project, project))
{
  UpdateConfig.run(scope, new String[]{"trust=high"}, out);   // non-default persisted
  UpdateConfig.run(scope, new String[]{"trust=medium"}, out); // default selected
  String raw = Files.readString(project.resolve(".cat/config.json"));
  // Actual today: raw still contains "trust":"medium"
}
```

## Expected vs Actual

- **Expected:** When a value equals its built-in default, that key is removed from `.cat/config.json` (or omitted when
  writing a new file). Non-default keys remain persisted.
- **Actual:** Default-valued keys are written and retained in `.cat/config.json`.

## Root Cause

`UpdateConfig.mergeAndWrite()` performs a simple `existing.putAll(updates)` followed by JSON serialization. It has no
default-pruning phase and no API that exposes canonical default values for key-by-key comparison at write time.

## Approach Analysis (High Curiosity)

### Chosen Approach: prune before write using Config-owned defaults

- Add `public static Map<String, Object> defaultValues()` to `Config` (returning immutable defaults map), then use it
  in `UpdateConfig` to remove keys whose merged values are equal to defaults before serialization.
- Keep validation behavior unchanged; only adjust final persistence representation.
- Compare values as typed objects (`Integer` for widths, `String` for enum/string keys) to avoid string-format drift.

### Rejected Alternative A: duplicate defaults inside UpdateConfig

- Rejected because it creates a second source of truth and high drift risk whenever defaults evolve in `Config`.

### Rejected Alternative B: keep defaults in file and filter only in UI/output

- Rejected because it does not satisfy the requirement to remove default-valued entries from `.cat/config.json` and
  keeps noisy persisted config state.

## Risk Assessment

- **Risk Level:** MEDIUM
- **Regression Risk:** Incorrect default comparison could delete non-default user overrides, especially for typed values
  (`fileWidth`, `displayWidth`) and empty-string `license`.
- **Mitigation:** Test-first coverage for prune-on-default, preserve-non-default, and create-from-default-only flows;
  run full suite with `mvn -f client/pom.xml verify -e`.

## Files to Modify

- `client/common-cli/src/main/java/io/github/cowwoc/cat/agent/Config.java`
  - Expose canonical default values for write-time comparison (immutable view).
- `client/common-cli/src/main/java/io/github/cowwoc/cat/tool/util/UpdateConfig.java`
  - Add pruning logic after merge and before write; remove entries matching defaults.
- `client/claude-cli/src/test/java/io/github/cowwoc/cat/client/test/UpdateConfigTest.java`
  - Add/adjust tests that fail on current behavior and pass after pruning implementation.

## Test Cases

- [x] Updating an existing non-default key back to its default removes the key from raw `.cat/config.json`.
- [x] Writing only default values into a missing config produces raw `.cat/config.json` content exactly `{}`
- [x] Non-default values are still persisted exactly as before.
- [x] Effective config behavior still resolves to defaults when keys are absent from raw file.
- [x] Removing a default-valued key preserves unrelated non-default overrides.
- [x] Width validation covers accepted boundary values and rejected out-of-bound values.
- [x] Blank-key validation returns an error.

## Pre-conditions

- [x] All dependent issues are closed

## Jobs

### Job 1

- Add failing tests in `UpdateConfigTest` first:
  - `defaultValueUpdateRemovesExistingOverride`: run `trust=high` then `trust=medium`; assert parsed JSON map from
    `.cat/config.json` does not contain `trust`.
  - `defaultOnlyBatchWritesEmptyObject`: on missing config run all writable default values, including string,
    integer, and empty-string defaults; assert raw `.cat/config.json` string is exactly `{}`.
  - `nonDefaultValuesStillPersist`: run `trust=low` and `fileWidth=100`; assert parsed JSON contains
    `trust=low` and `fileWidth=100`.
- Run targeted tests to confirm failure before implementation:
  - `mvn -f client/pom.xml -Dtest=UpdateConfigTest test -e`
- Implement default-pruning support:
  - add `public static Map<String, Object> defaultValues()` in `Config.java` returning `DEFAULTS`.
  - update `UpdateConfig.mergeAndWrite()` to prune entries whose merged values equal Config defaults.
  - preserve atomic write path (randomized same-directory temp file + `ATOMIC_MOVE`).
- Re-run targeted tests until green:
  - `mvn -f client/pom.xml -Dtest=UpdateConfigTest test -e`
- Run full verification:
  - `mvn -f client/pom.xml verify -e`
- Update issue status in `.cat/issues/v2/v2.1/omit-default-config-values/index.json` to closed in the same
  implementation commit.

## Post-conditions

- [x] `cat:config` removes a key from `.cat/config.json` when the selected value equals that key's built-in default
- [x] `cat:config` omits default-valued keys when creating or rewriting `.cat/config.json`
- [x] Non-default configuration overrides are preserved and written normally
- [x] Effective configuration output remains unchanged after default-valued entries are pruned
- [x] Regression tests cover updating a key to its default, creating config from default selections, and preserving
  non-default values
- [x] Tests passing: `mvn -f client/pom.xml verify -e` exits 0
