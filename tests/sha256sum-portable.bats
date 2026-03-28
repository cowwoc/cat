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
