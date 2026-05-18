#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.

SCRIPT_DIR="$(cd "$(dirname "$BATS_TEST_FILENAME")/../../../.." && pwd)"
DOWNLOAD_SCRIPT="${SCRIPT_DIR}/client/plugin/scripts/download-git-filter-repo.sh"

FAKE_SHA256_LINUX_X64="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
FAKE_SHA256_LINUX_AARCH64="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
FAKE_SHA256_MACOS_X64="cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
FAKE_SHA256_MACOS_AARCH64="dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"

write_release_conf() {
    local conf_dir="${FAKE_PLUGIN_ROOT}/.git-filter-repo-config"
    mkdir -p "${conf_dir}"
    cat > "${conf_dir}/release.conf" <<RELEASE_EOF
RELEASE_TAG="git-filter-repo-v2.38.0"
SOURCE_SHA256="69d2dae2d2331ce73b9c46d2a993046ec4bc26fd3c2328c2bcffb323b8338f8f"
PLATFORM_SHA256_linux_x64="${FAKE_SHA256_LINUX_X64}"
PLATFORM_SHA256_linux_aarch64="${FAKE_SHA256_LINUX_AARCH64}"
PLATFORM_SHA256_macos_x64="${FAKE_SHA256_MACOS_X64}"
PLATFORM_SHA256_macos_aarch64="${FAKE_SHA256_MACOS_AARCH64}"
RELEASE_EOF
}

write_sha256sum_stub() {
    local expected_hash="$1"
    cat > "${STUB_BIN_DIR}/sha256sum" <<SHA_EOF
#!/usr/bin/env bash
printf '%s  %s\n' "${expected_hash}" "\$1"
SHA_EOF
    chmod +x "${STUB_BIN_DIR}/sha256sum"
}

write_uname_linux_x64_stub() {
    cat > "${STUB_BIN_DIR}/uname" <<'UNAME_EOF'
#!/usr/bin/env bash
if [[ "$1" == "-s" ]]; then
  echo "Linux"
elif [[ "$1" == "-m" ]]; then
  echo "x86_64"
fi
UNAME_EOF
    chmod +x "${STUB_BIN_DIR}/uname"
}

setup() {
    FAKE_PLUGIN_ROOT="$(mktemp -d)"
    export CLAUDE_PLUGIN_ROOT="${FAKE_PLUGIN_ROOT}"

    write_release_conf

    mkdir -p "${FAKE_PLUGIN_ROOT}/scripts"
    cp "${SCRIPT_DIR}/client/plugin/scripts/sha256sum-portable.sh" "${FAKE_PLUGIN_ROOT}/scripts/"
    cp "${SCRIPT_DIR}/client/plugin/scripts/git-filter-repo-release.sh" "${FAKE_PLUGIN_ROOT}/scripts/"

    STUB_BIN_DIR="$(mktemp -d)"
    export STUB_BIN_DIR

    SAFE_PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
    export SAFE_PATH
}

teardown() {
    rm -rf "${FAKE_PLUGIN_ROOT:-}"
    rm -rf "${STUB_BIN_DIR:-}"
}

@test "fails fast when bundled executable is missing even if git-filter-repo is on PATH" {
    cat > "${STUB_BIN_DIR}/git-filter-repo" <<'PATH_EOF'
#!/usr/bin/env bash
exit 0
PATH_EOF
    chmod +x "${STUB_BIN_DIR}/git-filter-repo"

    run env PATH="${STUB_BIN_DIR}:${SAFE_PATH}" bash "${DOWNLOAD_SCRIPT}"

    [ "${status}" -ne 0 ]
    [[ "${output}" == *"ERROR: Bundled git-filter-repo executable not found"* ]] || [[ "${lines[*]}" == *"ERROR: Bundled git-filter-repo executable not found"* ]]
    [[ "${output}" != *"${STUB_BIN_DIR}/git-filter-repo"* ]] || [[ "${lines[*]}" != *"${STUB_BIN_DIR}/git-filter-repo"* ]]
}

@test "returns bundled binary path when bundled executable exists and checksum matches" {
    write_uname_linux_x64_stub

    local cache_dir="${FAKE_PLUGIN_ROOT}/lib"
    local bundled_binary="${cache_dir}/git-filter-repo-linux-x64"
    mkdir -p "${cache_dir}"
    echo "bundled" > "${bundled_binary}"
    chmod +x "${bundled_binary}"

    write_sha256sum_stub "${FAKE_SHA256_LINUX_X64}"

    run env PATH="${STUB_BIN_DIR}:${SAFE_PATH}" bash "${DOWNLOAD_SCRIPT}"

    [ "${status}" -eq 0 ]
    [ "${output}" = "${bundled_binary}" ]
}

@test "fails fast when bundled executable is missing" {
    write_uname_linux_x64_stub

    run env PATH="${STUB_BIN_DIR}:${SAFE_PATH}" bash "${DOWNLOAD_SCRIPT}"

    [ "${status}" -ne 0 ]
    [[ "${output}" == *"ERROR: Bundled git-filter-repo executable not found"* ]] || [[ "${lines[*]}" == *"ERROR: Bundled git-filter-repo executable not found"* ]]
    [[ "${output}" == *"Expected bundled path:"* ]] || [[ "${lines[*]}" == *"Expected bundled path:"* ]]
    [[ "${output}" == *"Reinstall CAT engine artifact"* ]] || [[ "${lines[*]}" == *"Reinstall CAT engine artifact"* ]]
}

@test "fails fast when bundled executable is not executable" {
    write_uname_linux_x64_stub

    local cache_dir="${FAKE_PLUGIN_ROOT}/lib"
    local bundled_binary="${cache_dir}/git-filter-repo-linux-x64"
    mkdir -p "${cache_dir}"
    echo "bundled" > "${bundled_binary}"

    run env PATH="${STUB_BIN_DIR}:${SAFE_PATH}" bash "${DOWNLOAD_SCRIPT}"

    [ "${status}" -ne 0 ]
    [[ "${output}" == *"ERROR: Bundled git-filter-repo is not executable"* ]] || [[ "${lines[*]}" == *"ERROR: Bundled git-filter-repo is not executable"* ]]
}

@test "fails fast when bundled executable checksum does not match release config" {
    write_uname_linux_x64_stub

    local cache_dir="${FAKE_PLUGIN_ROOT}/lib"
    local bundled_binary="${cache_dir}/git-filter-repo-linux-x64"
    mkdir -p "${cache_dir}"
    echo "bundled" > "${bundled_binary}"
    chmod +x "${bundled_binary}"

    write_sha256sum_stub "0000000000000000000000000000000000000000000000000000000000000000"

    run env PATH="${STUB_BIN_DIR}:${SAFE_PATH}" bash "${DOWNLOAD_SCRIPT}"

    [ "${status}" -ne 0 ]
    [[ "${output}" == *"ERROR: Bundled git-filter-repo checksum mismatch"* ]] || [[ "${lines[*]}" == *"ERROR: Bundled git-filter-repo checksum mismatch"* ]]
}
