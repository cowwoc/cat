# Add Batch Error Scenario Tests for download-git-filter-repo.sh

## Goal
Extend `tests/download-git-filter-repo.bats` to cover scenarios where multiple SHA256 validation
errors occur simultaneously (batch failures), verifying that the script reports all errors at once
rather than stopping at the first failure.

## Background
The iteration-2 fix to `download-git-filter-repo.sh` changed SHA256 field validation to collect
all errors in `VALIDATION_ERRORS` and report them all at once instead of failing on the first
error. Tests added in the fix iteration cover single-field failures. Batch scenarios (multiple
missing/malformed fields simultaneously) are not yet covered.

## Changes Required

1. Add Bats test: all platform SHA256 fields missing simultaneously — expects error listing all
   missing platforms.
2. Add Bats test: mix of missing and malformed SHA256 fields — expects error listing both types of
   failures in a single report.
3. Add Bats test: exactly one valid field, rest invalid — verifies partial-valid input triggers
   batch error for the invalid ones.
4. Each test must use mktemp isolation and stub external commands via PATH override per the existing
   test pattern in `tests/download-git-filter-repo.bats`.

## Research Findings

The script (`plugin/scripts/download-git-filter-repo.sh` lines 54-71) collects validation errors in
`VALIDATION_ERRORS` and emits them in a single error message format:

```
ERROR: Invalid or missing SHA256 fields in ${CONF}:
  Missing: PLATFORM_SHA256_<name>
  Invalid SHA256 format for PLATFORM_SHA256_<name>: <value>
```

Error output goes to stderr; Bats `run` captures both stdout and stderr in `$output`.

Existing single-field tests cover:
- One field missing (`PLATFORM_SHA256_linux_x64` absent)
- One field malformed (`PLATFORM_SHA256_linux_x64` = 64 uppercase Zs)

Platforms iterated (in order): `linux_x64`, `linux_aarch64`, `macos_x64`, `macos_aarch64`.

## Files to Modify
- `tests/download-git-filter-repo.bats` — append 3 new test functions after the existing
  "Missing SHA256 field" section (after line 243)

## Sub-Agent Waves

### Wave 1
- Append 3 new Bats tests to `tests/download-git-filter-repo.bats`:

  **Test 1: "reports all four platforms as missing when all PLATFORM_SHA256 fields are absent"**
  - Write a release.conf containing only `RELEASE_TAG` and `SOURCE_SHA256` (no PLATFORM_SHA256_*
    fields). Write directly to `${FAKE_PLUGIN_ROOT}/.git-filter-repo-config/release.conf` using a
    heredoc, overriding the file written by `setup()`.
  - Stub python3 to exit 1 (bypass tiers 1 and 2) in `${STUB_BIN_DIR}/python3`.
  - Run: `run env PATH="${STUB_BIN_DIR}:${PATH}" bash "${DOWNLOAD_SCRIPT}"`
  - Assert: `[ "${status}" -ne 0 ]`
  - Assert all four missing messages appear in `"${output}"` or `"${lines[*]}"`:
    - `"Missing: PLATFORM_SHA256_linux_x64"`
    - `"Missing: PLATFORM_SHA256_linux_aarch64"`
    - `"Missing: PLATFORM_SHA256_macos_x64"`
    - `"Missing: PLATFORM_SHA256_macos_aarch64"`

  **Test 2: "reports both missing and malformed errors when SHA256 fields have mixed validity"**
  - Write a release.conf where:
    - `PLATFORM_SHA256_linux_x64` is absent (missing)
    - `PLATFORM_SHA256_linux_aarch64` = 64 uppercase Zs (malformed — not lowercase hex)
    - `PLATFORM_SHA256_macos_x64` = `${FAKE_SHA256_MACOS_X64}` (valid)
    - `PLATFORM_SHA256_macos_aarch64` = `${FAKE_SHA256_MACOS_AARCH64}` (valid)
  - Stub python3 to exit 1 in `${STUB_BIN_DIR}/python3`.
  - Run: `run env PATH="${STUB_BIN_DIR}:${PATH}" bash "${DOWNLOAD_SCRIPT}"`
  - Assert: `[ "${status}" -ne 0 ]`
  - Assert `"${output}"` or `"${lines[*]}"` contains `"Missing: PLATFORM_SHA256_linux_x64"`
  - Assert `"${output}"` or `"${lines[*]}"` contains `"Invalid SHA256 format for PLATFORM_SHA256_linux_aarch64"`

  **Test 3: "reports errors for three invalid fields when exactly one PLATFORM_SHA256 field is valid"**
  - Write a release.conf where:
    - `PLATFORM_SHA256_linux_x64` = `${FAKE_SHA256_LINUX_X64}` (valid — the only valid field)
    - `PLATFORM_SHA256_linux_aarch64` is absent (missing)
    - `PLATFORM_SHA256_macos_x64` = `"invalid"` (too short, not 64 hex chars)
    - `PLATFORM_SHA256_macos_aarch64` is absent (missing)
  - Stub python3 to exit 1 in `${STUB_BIN_DIR}/python3`.
  - Run: `run env PATH="${STUB_BIN_DIR}:${PATH}" bash "${DOWNLOAD_SCRIPT}"`
  - Assert: `[ "${status}" -ne 0 ]`
  - Assert `"${output}"` or `"${lines[*]}"` contains `"Missing: PLATFORM_SHA256_linux_aarch64"`
  - Assert `"${output}"` or `"${lines[*]}"` contains `"Invalid SHA256 format for PLATFORM_SHA256_macos_x64"`
  - Assert `"${output}"` or `"${lines[*]}"` contains `"Missing: PLATFORM_SHA256_macos_aarch64"`

- Run the full test suite after adding all 3 tests:
  ```bash
  bats tests/download-git-filter-repo.bats
  ```
  All tests must pass (exit code 0). Fix any test failures before updating index.json.

- Update `.cat/issues/v2/v2.1/add-batch-error-test-coverage/index.json` in the same commit:
  status=closed, progress=100%

## Post-conditions

- [ ] At least 3 new Bats tests covering batch SHA256 validation error scenarios
- [ ] Tests use mktemp isolation and PATH-based stubs consistent with existing test style
- [ ] All new and existing Bats tests pass
- [ ] No regressions introduced
