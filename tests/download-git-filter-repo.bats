#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Integration tests for plugin/scripts/download-git-filter-repo.sh.
# All tests are self-contained: no network calls, all external dependencies stubbed.

SCRIPT_DIR="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"
DOWNLOAD_SCRIPT="${SCRIPT_DIR}/plugin/scripts/download-git-filter-repo.sh"

# Fake SHA256 values (64 lowercase hex chars) used in tests
FAKE_SHA256_LINUX_X64="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
FAKE_SHA256_LINUX_AARCH64="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
FAKE_SHA256_MACOS_X64="cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
FAKE_SHA256_MACOS_AARCH64="dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"

# Write a minimal release.conf into the fake plugin structure
write_release_conf() {
    local conf_dir="${FAKE_PLUGIN_ROOT}/.git-filter-repo-config"
    mkdir -p "${conf_dir}"
    cat > "${conf_dir}/release.conf" <<EOF
RELEASE_TAG="git-filter-repo-v2.38.0"
SOURCE_SHA256="69d2dae2d2331ce73b9c46d2a993046ec4bc26fd3c2328c2bcffb323b8338f8f"
PLATFORM_SHA256_linux_x64="${FAKE_SHA256_LINUX_X64}"
PLATFORM_SHA256_linux_aarch64="${FAKE_SHA256_LINUX_AARCH64}"
PLATFORM_SHA256_macos_x64="${FAKE_SHA256_MACOS_X64}"
PLATFORM_SHA256_macos_aarch64="${FAKE_SHA256_MACOS_AARCH64}"
EOF
}

# Write a stub binary that prints a fixed hash on stdout (simulates sha256sum)
write_sha256sum_stub() {
    local bin_dir="$1"
    local expected_hash="$2"
    mkdir -p "${bin_dir}"
    cat > "${bin_dir}/sha256sum" <<EOF
#!/usr/bin/env bash
printf '%s  %s\n' "${expected_hash}" "\$1"
EOF
    chmod +x "${bin_dir}/sha256sum"
}

# Write a stub python3 binary that always fails (exit 1)
write_python3_stub() {
    mkdir -p "${STUB_BIN_DIR}"
    cat > "${STUB_BIN_DIR}/python3" <<'EOF'
#!/usr/bin/env bash
exit 1
EOF
    chmod +x "${STUB_BIN_DIR}/python3"
}

setup() {
    FAKE_PLUGIN_ROOT="$(mktemp -d)"
    export CLAUDE_PLUGIN_ROOT="${FAKE_PLUGIN_ROOT}"
    export CLAUDE_SESSION_ID="test-session-$$"

    # Write the standard release.conf
    write_release_conf

    # Create a stub bin dir for PATH overrides
    STUB_BIN_DIR="$(mktemp -d)"
    export STUB_BIN_DIR

    # SAFE_PATH contains only standard system directories, excluding user-local paths
    # (e.g. ~/.local/bin) that may contain real binaries we want to stub or exclude.
    SAFE_PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
    export SAFE_PATH
}

teardown() {
    rm -rf "${FAKE_PLUGIN_ROOT:-}"
    rm -rf "${STUB_BIN_DIR:-}"
}

# ---------------------------------------------------------------------------
# python3 module path (resolution tier 1)
# ---------------------------------------------------------------------------

@test "returns 'python3 -m git_filter_repo' when python3 module is importable" {
    # Stub python3: exit 0 unconditionally (satisfies both `command -v` and `import git_filter_repo`)
    cat > "${STUB_BIN_DIR}/python3" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
    chmod +x "${STUB_BIN_DIR}/python3"

    # Put stub at front of PATH so the real python3 is shadowed
    run env PATH="${STUB_BIN_DIR}:${SAFE_PATH}" bash "${DOWNLOAD_SCRIPT}"

    [ "${status}" -eq 0 ]
    [ "${output}" = "python3 -m git_filter_repo" ]
}

# ---------------------------------------------------------------------------
# PATH binary path (resolution tier 2)
# ---------------------------------------------------------------------------

@test "returns path to git-filter-repo binary when it is on PATH (no python3 module)" {
    # Stub python3 that fails the import check (exit 1 for any invocation)
    write_python3_stub

    # Provide git-filter-repo binary on PATH
    cat > "${STUB_BIN_DIR}/git-filter-repo" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
    chmod +x "${STUB_BIN_DIR}/git-filter-repo"

    run env PATH="${STUB_BIN_DIR}:${SAFE_PATH}" bash "${DOWNLOAD_SCRIPT}"

    [ "${status}" -eq 0 ]
    [ "${output}" = "${STUB_BIN_DIR}/git-filter-repo" ]
}

# ---------------------------------------------------------------------------
# release.conf missing (error handling)
# ---------------------------------------------------------------------------

@test "fails with clear error when release.conf is missing" {
    rm -f "${FAKE_PLUGIN_ROOT}/.git-filter-repo-config/release.conf"

    run bash "${DOWNLOAD_SCRIPT}"

    [ "${status}" -ne 0 ]
    [[ "${output}" == *"ERROR"* ]] || [[ "${lines[*]}" == *"ERROR"* ]]
}

# ---------------------------------------------------------------------------
# cache hit (resolution tier 3, cached binary with correct SHA256)
# ---------------------------------------------------------------------------

@test "returns cached binary path when SHA256 matches without downloading" {
    # No python3, no git-filter-repo on PATH — force tier-3 resolution
    write_python3_stub

    # Detect the current platform to know which binary name to create
    LOCAL_OS=$(uname -s)
    LOCAL_ARCH=$(uname -m)
    case "${LOCAL_OS}" in
        Linux)
            case "${LOCAL_ARCH}" in
                x86_64)  BINARY_NAME="git-filter-repo-linux-x64"   ; EXPECTED_HASH="${FAKE_SHA256_LINUX_X64}" ;;
                aarch64) BINARY_NAME="git-filter-repo-linux-aarch64"; EXPECTED_HASH="${FAKE_SHA256_LINUX_AARCH64}" ;;
                arm64)   BINARY_NAME="git-filter-repo-linux-aarch64"; EXPECTED_HASH="${FAKE_SHA256_LINUX_AARCH64}" ;;
                *)       skip "Unsupported arch for cache-hit test: ${LOCAL_ARCH}" ;;
            esac ;;
        Darwin)
            case "${LOCAL_ARCH}" in
                x86_64)  BINARY_NAME="git-filter-repo-macos-x64"   ; EXPECTED_HASH="${FAKE_SHA256_MACOS_X64}" ;;
                arm64)   BINARY_NAME="git-filter-repo-macos-aarch64"; EXPECTED_HASH="${FAKE_SHA256_MACOS_AARCH64}" ;;
                *)       skip "Unsupported arch for cache-hit test: ${LOCAL_ARCH}" ;;
            esac ;;
        *)  skip "Unsupported OS for cache-hit test: ${LOCAL_OS}" ;;
    esac

    # Create a cached binary and a sha256sum stub returning the matching hash
    CACHE_DIR="${FAKE_PLUGIN_ROOT}/lib"
    mkdir -p "${CACHE_DIR}"
    echo "fake binary content" > "${CACHE_DIR}/${BINARY_NAME}"

    write_sha256sum_stub "${STUB_BIN_DIR}" "${EXPECTED_HASH}"

    run env PATH="${STUB_BIN_DIR}:${SAFE_PATH}" bash "${DOWNLOAD_SCRIPT}"

    [ "${status}" -eq 0 ]
    [ "${output}" = "${CACHE_DIR}/${BINARY_NAME}" ]
}

# ---------------------------------------------------------------------------
# release.conf missing RELEASE_TAG (error handling)
# ---------------------------------------------------------------------------

@test "fails with error when RELEASE_TAG is missing from release.conf" {
    write_python3_stub

    # Write a conf that omits RELEASE_TAG
    local conf_dir="${FAKE_PLUGIN_ROOT}/.git-filter-repo-config"
    mkdir -p "${conf_dir}"
    cat > "${conf_dir}/release.conf" <<EOF
PLATFORM_SHA256_linux_x64="${FAKE_SHA256_LINUX_X64}"
PLATFORM_SHA256_linux_aarch64="${FAKE_SHA256_LINUX_AARCH64}"
PLATFORM_SHA256_macos_x64="${FAKE_SHA256_MACOS_X64}"
PLATFORM_SHA256_macos_aarch64="${FAKE_SHA256_MACOS_AARCH64}"
EOF

    run env PATH="${STUB_BIN_DIR}:${SAFE_PATH}" bash "${DOWNLOAD_SCRIPT}"

    [ "${status}" -ne 0 ]
    [[ "${output}" == *"RELEASE_TAG"* ]] || [[ "${lines[*]}" == *"RELEASE_TAG"* ]]
}

# ---------------------------------------------------------------------------
# Invalid SHA256 format (error handling)
# ---------------------------------------------------------------------------

@test "fails with error when SHA256 value has invalid format (not 64 hex chars)" {
    # Stub python3 to fail so SHA256 parsing is reached; git-filter-repo excluded via SAFE_PATH
    write_python3_stub

    # Write a conf with a malformed SHA256 (non-hex chars — 'Z' is not valid hex)
    local conf_dir="${FAKE_PLUGIN_ROOT}/.git-filter-repo-config"
    mkdir -p "${conf_dir}"
    cat > "${conf_dir}/release.conf" <<EOF
RELEASE_TAG="git-filter-repo-v2.38.0"
SOURCE_SHA256="69d2dae2d2331ce73b9c46d2a993046ec4bc26fd3c2328c2bcffb323b8338f8f"
PLATFORM_SHA256_linux_x64="ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ"
PLATFORM_SHA256_linux_aarch64="${FAKE_SHA256_LINUX_AARCH64}"
PLATFORM_SHA256_macos_x64="${FAKE_SHA256_MACOS_X64}"
PLATFORM_SHA256_macos_aarch64="${FAKE_SHA256_MACOS_AARCH64}"
EOF

    run env PATH="${STUB_BIN_DIR}:${SAFE_PATH}" bash "${DOWNLOAD_SCRIPT}"

    [ "${status}" -ne 0 ]
    [[ "${output}" == *"Invalid SHA256"* ]] || [[ "${lines[*]}" == *"Invalid SHA256"* ]]
}

# ---------------------------------------------------------------------------
# Missing SHA256 field (error handling)
# ---------------------------------------------------------------------------

@test "fails with error when a PLATFORM_SHA256 field is missing from release.conf" {
    # Stub python3 to fail so SHA256 parsing is reached; git-filter-repo excluded via SAFE_PATH
    write_python3_stub

    # Write a conf that omits PLATFORM_SHA256_linux_x64
    local conf_dir="${FAKE_PLUGIN_ROOT}/.git-filter-repo-config"
    mkdir -p "${conf_dir}"
    cat > "${conf_dir}/release.conf" <<EOF
RELEASE_TAG="git-filter-repo-v2.38.0"
SOURCE_SHA256="69d2dae2d2331ce73b9c46d2a993046ec4bc26fd3c2328c2bcffb323b8338f8f"
PLATFORM_SHA256_linux_aarch64="${FAKE_SHA256_LINUX_AARCH64}"
PLATFORM_SHA256_macos_x64="${FAKE_SHA256_MACOS_X64}"
PLATFORM_SHA256_macos_aarch64="${FAKE_SHA256_MACOS_AARCH64}"
EOF

    run env PATH="${STUB_BIN_DIR}:${SAFE_PATH}" bash "${DOWNLOAD_SCRIPT}"

    [ "${status}" -ne 0 ]
    [[ "${output}" == *"PLATFORM_SHA256_linux_x64"* ]] || [[ "${lines[*]}" == *"PLATFORM_SHA256_linux_x64"* ]]
}

@test "reports all four platforms as missing when all PLATFORM_SHA256 fields are absent" {
    # Stub python3 to fail; git-filter-repo excluded via SAFE_PATH
    write_python3_stub

    local conf_dir="${FAKE_PLUGIN_ROOT}/.git-filter-repo-config"
    mkdir -p "${conf_dir}"
    cat > "${conf_dir}/release.conf" <<EOF
RELEASE_TAG="git-filter-repo-v2.38.0"
SOURCE_SHA256="69d2dae2d2331ce73b9c46d2a993046ec4bc26fd3c2328c2bcffb323b8338f8f"
EOF

    run env PATH="${STUB_BIN_DIR}:${SAFE_PATH}" bash "${DOWNLOAD_SCRIPT}"

    [ "${status}" -ne 0 ]
    [[ "${output}" == *"Missing: PLATFORM_SHA256_linux_x64"* ]] || [[ "${lines[*]}" == *"Missing: PLATFORM_SHA256_linux_x64"* ]]
    [[ "${output}" == *"Missing: PLATFORM_SHA256_linux_aarch64"* ]] || [[ "${lines[*]}" == *"Missing: PLATFORM_SHA256_linux_aarch64"* ]]
    [[ "${output}" == *"Missing: PLATFORM_SHA256_macos_x64"* ]] || [[ "${lines[*]}" == *"Missing: PLATFORM_SHA256_macos_x64"* ]]
    [[ "${output}" == *"Missing: PLATFORM_SHA256_macos_aarch64"* ]] || [[ "${lines[*]}" == *"Missing: PLATFORM_SHA256_macos_aarch64"* ]]
}

@test "reports both missing and malformed errors when SHA256 fields have mixed validity" {
    # Stub python3 to fail; git-filter-repo excluded via SAFE_PATH
    write_python3_stub

    local conf_dir="${FAKE_PLUGIN_ROOT}/.git-filter-repo-config"
    mkdir -p "${conf_dir}"
    cat > "${conf_dir}/release.conf" <<EOF
RELEASE_TAG="git-filter-repo-v2.38.0"
SOURCE_SHA256="69d2dae2d2331ce73b9c46d2a993046ec4bc26fd3c2328c2bcffb323b8338f8f"
PLATFORM_SHA256_linux_aarch64="ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ"
PLATFORM_SHA256_macos_x64="${FAKE_SHA256_MACOS_X64}"
PLATFORM_SHA256_macos_aarch64="${FAKE_SHA256_MACOS_AARCH64}"
EOF

    run env PATH="${STUB_BIN_DIR}:${SAFE_PATH}" bash "${DOWNLOAD_SCRIPT}"

    [ "${status}" -ne 0 ]
    [[ "${output}" == *"Missing: PLATFORM_SHA256_linux_x64"* ]] || [[ "${lines[*]}" == *"Missing: PLATFORM_SHA256_linux_x64"* ]]
    [[ "${output}" == *"Invalid SHA256 format for PLATFORM_SHA256_linux_aarch64"* ]] || [[ "${lines[*]}" == *"Invalid SHA256 format for PLATFORM_SHA256_linux_aarch64"* ]]
}

@test "reports errors for three invalid fields when exactly one PLATFORM_SHA256 field is valid" {
    # Stub python3 to fail; git-filter-repo excluded via SAFE_PATH
    write_python3_stub

    local conf_dir="${FAKE_PLUGIN_ROOT}/.git-filter-repo-config"
    mkdir -p "${conf_dir}"
    cat > "${conf_dir}/release.conf" <<EOF
RELEASE_TAG="git-filter-repo-v2.38.0"
SOURCE_SHA256="69d2dae2d2331ce73b9c46d2a993046ec4bc26fd3c2328c2bcffb323b8338f8f"
PLATFORM_SHA256_linux_x64="${FAKE_SHA256_LINUX_X64}"
PLATFORM_SHA256_macos_x64="invalid"
EOF

    run env PATH="${STUB_BIN_DIR}:${SAFE_PATH}" bash "${DOWNLOAD_SCRIPT}"

    [ "${status}" -ne 0 ]
    [[ "${output}" == *"Missing: PLATFORM_SHA256_linux_aarch64"* ]] || [[ "${lines[*]}" == *"Missing: PLATFORM_SHA256_linux_aarch64"* ]]
    [[ "${output}" == *"Invalid SHA256 format for PLATFORM_SHA256_macos_x64"* ]] || [[ "${lines[*]}" == *"Invalid SHA256 format for PLATFORM_SHA256_macos_x64"* ]]
    [[ "${output}" == *"Missing: PLATFORM_SHA256_macos_aarch64"* ]] || [[ "${lines[*]}" == *"Missing: PLATFORM_SHA256_macos_aarch64"* ]]
}

# ---------------------------------------------------------------------------
# Unsupported OS detection (error handling)
# ---------------------------------------------------------------------------

@test "fails with error naming supported platforms when OS is unsupported" {
    # Stub uname to return FreeBSD (unsupported OS)
    cat > "${STUB_BIN_DIR}/uname" <<'EOF'
#!/usr/bin/env bash
if [[ "$1" == "-s" ]]; then
  echo "FreeBSD"
elif [[ "$1" == "-m" ]]; then
  echo "x86_64"
fi
EOF
    chmod +x "${STUB_BIN_DIR}/uname"

    # Stub python3 to fail so we reach OS detection; git-filter-repo excluded via SAFE_PATH
    write_python3_stub

    run env PATH="${STUB_BIN_DIR}:${SAFE_PATH}" bash "${DOWNLOAD_SCRIPT}"

    [ "${status}" -ne 0 ]
    [[ "${output}" == *"FreeBSD"* ]] || [[ "${lines[*]}" == *"FreeBSD"* ]]
}

# ---------------------------------------------------------------------------
# Unsupported architecture detection (error handling)
# ---------------------------------------------------------------------------

@test "fails with error when architecture is unsupported" {
    # Stub uname to return Linux with unsupported arch
    cat > "${STUB_BIN_DIR}/uname" <<'EOF'
#!/usr/bin/env bash
if [[ "$1" == "-s" ]]; then
  echo "Linux"
elif [[ "$1" == "-m" ]]; then
  echo "i686"
fi
EOF
    chmod +x "${STUB_BIN_DIR}/uname"

    # Stub python3 to fail so we reach architecture detection; git-filter-repo excluded via SAFE_PATH
    write_python3_stub

    run env PATH="${STUB_BIN_DIR}:${SAFE_PATH}" bash "${DOWNLOAD_SCRIPT}"

    [ "${status}" -ne 0 ]
    [[ "${output}" == *"i686"* ]] || [[ "${lines[*]}" == *"i686"* ]]
}

# ---------------------------------------------------------------------------
# curl failure simulation (error handling)
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# stale cache by version mismatch (resolution tier 3, cached binary with wrong version)
# ---------------------------------------------------------------------------

@test "re-downloads when cached binary version file does not match RELEASE_TAG" {
    # No python3, no git-filter-repo on PATH — force tier-3 resolution
    write_python3_stub

    # Fix platform to linux-x64 for deterministic binary name
    cat > "${STUB_BIN_DIR}/uname" <<'EOF'
#!/usr/bin/env bash
if [[ "$1" == "-s" ]]; then
  echo "Linux"
elif [[ "$1" == "-m" ]]; then
  echo "x86_64"
fi
EOF
    chmod +x "${STUB_BIN_DIR}/uname"

    BINARY_NAME="git-filter-repo-linux-x64"
    CACHE_DIR="${FAKE_PLUGIN_ROOT}/lib"
    mkdir -p "${CACHE_DIR}"

    # Place a cached binary with a STALE version file (different tag)
    echo "fake binary content" > "${CACHE_DIR}/${BINARY_NAME}"
    echo "git-filter-repo-v1.0.0" > "${CACHE_DIR}/${BINARY_NAME}.version"

    # sha256sum stub that returns the expected hash (simulates a correct-hash binary)
    write_sha256sum_stub "${STUB_BIN_DIR}" "${FAKE_SHA256_LINUX_X64}"

    # curl stub: writes a valid fake binary and exits 0
    cat > "${STUB_BIN_DIR}/curl" <<EOF
#!/usr/bin/env bash
# Write content to the -o <output> argument
for ((i=1; i<=\$#; i++)); do
  if [[ "\${!i}" == "-o" ]]; then
    next=\$((i+1))
    echo "downloaded binary" > "\${!next}"
    break
  fi
done
exit 0
EOF
    chmod +x "${STUB_BIN_DIR}/curl"

    run env PATH="${STUB_BIN_DIR}:${SAFE_PATH}" bash "${DOWNLOAD_SCRIPT}"

    [ "${status}" -eq 0 ]
    # The script should have re-downloaded; verify that the version file now contains the correct tag
    [ -f "${CACHE_DIR}/${BINARY_NAME}.version" ]
    VERSION_IN_FILE=$(cat "${CACHE_DIR}/${BINARY_NAME}.version")
    [ "${VERSION_IN_FILE}" = "git-filter-repo-v2.38.0" ]
}

# ---------------------------------------------------------------------------

@test "fails with error when curl exits non-zero during download" {
    # Stub python3 to fail so we reach download phase; git-filter-repo excluded via SAFE_PATH
    write_python3_stub

    # Stub uname to return a known-good platform so platform detection succeeds
    cat > "${STUB_BIN_DIR}/uname" <<'EOF'
#!/usr/bin/env bash
if [[ "$1" == "-s" ]]; then
  echo "Linux"
elif [[ "$1" == "-m" ]]; then
  echo "x86_64"
fi
EOF
    chmod +x "${STUB_BIN_DIR}/uname"

    # Stub sha256sum so SHA256 verification of cache also fails gracefully (no cached binary exists)
    write_sha256sum_stub "${STUB_BIN_DIR}" "${FAKE_SHA256_LINUX_X64}"

    # Stub curl to return a connection failure exit code
    cat > "${STUB_BIN_DIR}/curl" <<'EOF'
#!/usr/bin/env bash
exit 6
EOF
    chmod +x "${STUB_BIN_DIR}/curl"

    run env PATH="${STUB_BIN_DIR}:${SAFE_PATH}" bash "${DOWNLOAD_SCRIPT}"

    [ "${status}" -ne 0 ]
    [[ "${output}" == *"ERROR"* ]] || [[ "${lines[*]}" == *"ERROR"* ]]
}
