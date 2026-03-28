# Extract sha256sum_portable into Shared Helper

## Goal
Extract the `sha256sum_portable()` function from `plugin/scripts/download-git-filter-repo.sh` into
a shared helper script in `plugin/scripts/` to eliminate duplication and ensure consistent
cross-platform SHA256 computation across all scripts.

## Background
The `sha256sum_portable()` function handles platform differences between `sha256sum` (Linux) and
`shasum -a 256` (macOS). This function may need to be used in other scripts. Having it duplicated
violates DRY and creates divergence risk when the function needs updating.

## Research Findings

`sha256sum_portable()` is defined only in `plugin/scripts/download-git-filter-repo.sh`. The other
scripts in `plugin/scripts/` (`cat-env.sh`, `measure-emoji-widths.sh`,
`validate-plan-builder-review-loop.sh`) do not compute SHA256. `plugin/hooks/session-start.sh`
uses `sha256sum` directly without a portable wrapper, but this is outside the scope of this issue
(the post-condition covers only duplication of the `sha256sum_portable` function body, not all SHA256
usage).

The sourced-helper pattern already exists: `plugin/scripts/cat-env.sh` uses a shebang and license
header. Because `download-git-filter-repo.sh` already runs with `set -euo pipefail`, having the helper
set the same options is idempotent for the current caller and makes the helper self-consistent.

Bats is not installed in the development environment; tests must be committed so CI can run them.

## Changes Required

1. Create `plugin/scripts/sha256sum-portable.sh` containing only the `sha256sum_portable` function
   with proper license header and `set -euo pipefail`.
2. Update `plugin/scripts/download-git-filter-repo.sh` to source the shared helper instead of
   defining the function inline.
3. Add `tests/sha256sum-portable.bats` with Bats unit tests for the helper.

## Jobs

### Job 1

- Create `plugin/scripts/sha256sum-portable.sh` (see Execution Details below)
- Update `plugin/scripts/download-git-filter-repo.sh` to remove the inline function and source the
  helper (see Execution Details below)
- Create `tests/sha256sum-portable.bats` (see Execution Details below)
- Update `${ISSUE_PATH}/index.json`: set `"status": "closed"`, `"progress": 100` in the final commit

### Job 2 — Verify Bats test files exist and have valid syntax

- Verify that `tests/download-git-filter-repo.bats` exists: `test -f tests/download-git-filter-repo.bats`
- Verify that `tests/sha256sum-portable.bats` exists: `test -f tests/sha256sum-portable.bats`
- Verify both files pass Bash syntax check:
  `bash -n tests/download-git-filter-repo.bats && bash -n tests/sha256sum-portable.bats`
- Verify `tests/sha256sum-portable.bats` contains the expected test names (sha256sum, shasum, neither):
  `grep -c '@test' tests/sha256sum-portable.bats` must output 4
- Verify `plugin/scripts/sha256sum-portable.sh` passes syntax check: `bash -n plugin/scripts/sha256sum-portable.sh`
- Verify `plugin/scripts/download-git-filter-repo.sh` passes syntax check and contains the source directive:
  `bash -n plugin/scripts/download-git-filter-repo.sh &&
  grep -q 'source.*sha256sum-portable.sh' plugin/scripts/download-git-filter-repo.sh`

## Execution Details

### File: plugin/scripts/sha256sum-portable.sh (CREATE)

Create this file with the following exact content:

```bash
#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
set -euo pipefail

# Portable SHA256 computation across Linux (sha256sum) and macOS (shasum -a 256).
# Usage: sha256sum_portable <file>
# Outputs: 64-character lowercase hex SHA256 hash on stdout
# Exits non-zero if neither sha256sum nor shasum is found.
sha256sum_portable() {
  local file="$1"
  if command -v sha256sum &>/dev/null; then
    sha256sum "${file}" 2>/dev/null | awk '{print $1}'
  elif command -v shasum &>/dev/null; then
    shasum -a 256 "${file}" 2>/dev/null | awk '{print $1}'
  else
    echo "ERROR: Neither sha256sum nor shasum found in PATH" >&2
    return 1
  fi
}
```

### File: plugin/scripts/download-git-filter-repo.sh (MODIFY)

Remove the following block (the portable SHA256 function definition and its leading comment):

```
# Portable SHA256 computation across Linux (sha256sum) and macOS (shasum -a 256)
sha256sum_portable() {
  local file="$1"
  if command -v sha256sum &>/dev/null; then
    sha256sum "${file}" 2>/dev/null | awk '{print $1}'
  elif command -v shasum &>/dev/null; then
    shasum -a 256 "${file}" 2>/dev/null | awk '{print $1}'
  else
    echo "ERROR: Neither sha256sum nor shasum found in PATH" >&2
    return 1
  fi
}
```

In place of the removed block, add the following two lines (preserve the blank line before `# --- Detect platform ---`):

```bash
# shellcheck source=plugin/scripts/sha256sum-portable.sh
source "${CLAUDE_PLUGIN_ROOT}/scripts/sha256sum-portable.sh"
```

The result around the splice point should be:

```bash
# shellcheck source=plugin/scripts/sha256sum-portable.sh
source "${CLAUDE_PLUGIN_ROOT}/scripts/sha256sum-portable.sh"

# --- Detect platform ---
```

### File: tests/sha256sum-portable.bats (CREATE)

Create this file with the following exact content:

```bash
#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Unit tests for plugin/scripts/sha256sum-portable.sh.
# Tests verify sha256sum_portable() delegates to sha256sum (Linux) or shasum -a 256 (macOS),
# and fails gracefully when neither is available.

SCRIPT_DIR="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"
HELPER_SCRIPT="${SCRIPT_DIR}/plugin/scripts/sha256sum-portable.sh"

setup() {
    STUB_BIN_DIR="$(mktemp -d)"
    SAFE_PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
}

teardown() {
    rm -rf "${STUB_BIN_DIR:-}"
}

@test "sha256sum_portable uses sha256sum when available" {
    cat > "${STUB_BIN_DIR}/sha256sum" <<'EOF'
#!/usr/bin/env bash
echo "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890  $1"
EOF
    chmod +x "${STUB_BIN_DIR}/sha256sum"

    run env PATH="${STUB_BIN_DIR}:${SAFE_PATH}" bash -c "
source '${HELPER_SCRIPT}'
sha256sum_portable /dev/null
"

    [ "${status}" -eq 0 ]
    [ "${output}" = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890" ]
}

@test "sha256sum_portable falls back to shasum when sha256sum is absent" {
    cat > "${STUB_BIN_DIR}/shasum" <<'EOF'
#!/usr/bin/env bash
echo "1111111111111111111111111111111111111111111111111111111111111111  $1"
EOF
    chmod +x "${STUB_BIN_DIR}/shasum"

    run env PATH="${STUB_BIN_DIR}:${SAFE_PATH}" bash -c "
source '${HELPER_SCRIPT}'
sha256sum_portable /dev/null
"

    [ "${status}" -eq 0 ]
    [ "${output}" = "1111111111111111111111111111111111111111111111111111111111111111" ]
}

@test "sha256sum_portable exits non-zero when neither sha256sum nor shasum is available" {
    run env PATH="${STUB_BIN_DIR}:${SAFE_PATH}" bash -c "
source '${HELPER_SCRIPT}'
sha256sum_portable /dev/null
"

    [ "${status}" -ne 0 ]
    [[ "${output}" == *"ERROR"* ]] || [[ "${lines[*]}" == *"ERROR"* ]]
}

@test "sha256sum_portable prefers sha256sum over shasum when both are available" {
    cat > "${STUB_BIN_DIR}/sha256sum" <<'EOF'
#!/usr/bin/env bash
echo "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa  $1"
EOF
    chmod +x "${STUB_BIN_DIR}/sha256sum"

    cat > "${STUB_BIN_DIR}/shasum" <<'EOF'
#!/usr/bin/env bash
echo "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb  $1"
EOF
    chmod +x "${STUB_BIN_DIR}/shasum"

    run env PATH="${STUB_BIN_DIR}:${SAFE_PATH}" bash -c "
source '${HELPER_SCRIPT}'
sha256sum_portable /dev/null
"

    [ "${status}" -eq 0 ]
    [ "${output}" = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" ]
}
```

## Post-conditions

- [ ] `sha256sum_portable()` is defined in exactly one place: `plugin/scripts/sha256sum-portable.sh`
- [ ] `download-git-filter-repo.sh` sources the shared helper instead of defining it inline
- [ ] No other script duplicates the sha256sum_portable logic
- [ ] Existing Bats tests for `download-git-filter-repo.bats` continue to pass (verify with:
      `bats tests/download-git-filter-repo.bats`)
- [ ] No regressions introduced
