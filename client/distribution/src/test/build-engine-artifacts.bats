#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.

SCRIPT_DIR="$(cd "$BATS_TEST_DIRNAME/../../.." && pwd)"
BUILD_SCRIPT="${SCRIPT_DIR}/distribution/scripts/build-engine-artifacts.sh"
SHA256_PORTABLE="${SCRIPT_DIR}/plugin/scripts/sha256sum-portable.sh"
GIT_FILTER_REPO_RELEASE="${SCRIPT_DIR}/plugin/scripts/git-filter-repo-release.sh"

setup() {
    TEST_ROOT="$(mktemp -d)"
    CLIENT_DIR="${TEST_ROOT}/client"
    DISTRIBUTION_DIR="${CLIENT_DIR}/distribution"
    PLUGIN_DIR="${CLIENT_DIR}/plugin"
    STUB_BIN_DIR="$(mktemp -d)"
    BINARY_CONTENT="standalone git-filter-repo"
    EXPECTED_SHA256="$(printf '%s' "${BINARY_CONTENT}" | sha256sum | cut -d' ' -f1)"

    mkdir -p \
        "${DISTRIBUTION_DIR}/scripts" \
        "${DISTRIBUTION_DIR}/target/jlink/codex/bin" \
        "${PLUGIN_DIR}/scripts" \
        "${PLUGIN_DIR}/.git-filter-repo-config"
    cp "${BUILD_SCRIPT}" "${DISTRIBUTION_DIR}/scripts/build-engine-artifacts.sh"
    cp "${SHA256_PORTABLE}" "${PLUGIN_DIR}/scripts/sha256sum-portable.sh"
    cp "${GIT_FILTER_REPO_RELEASE}" "${PLUGIN_DIR}/scripts/git-filter-repo-release.sh"

    write_release_conf "${EXPECTED_SHA256}"
    write_linux_x64_uname_stub
    write_curl_stub 0
    write_builder_stub
}

teardown() {
    rm -rf "${TEST_ROOT:-}"
    rm -rf "${STUB_BIN_DIR:-}"
}

write_release_conf() {
    local linux_x64_sha="$1"
    cat > "${PLUGIN_DIR}/.git-filter-repo-config/release.conf" <<CONF_EOF
REPO_OWNER="cowwoc"
REPO_NAME="cat"
RELEASE_TAG="git-filter-repo-v2.38.0"
PLATFORM_SHA256_linux_x64="${linux_x64_sha}"
PLATFORM_SHA256_linux_aarch64="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
PLATFORM_SHA256_macos_x64="cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
PLATFORM_SHA256_macos_aarch64="dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
CONF_EOF
}

write_linux_x64_uname_stub() {
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

write_unsupported_uname_stub() {
    cat > "${STUB_BIN_DIR}/uname" <<'UNAME_EOF'
#!/usr/bin/env bash
if [[ "$1" == "-s" ]]; then
  echo "Plan9"
elif [[ "$1" == "-m" ]]; then
  echo "x86_64"
fi
UNAME_EOF
    chmod +x "${STUB_BIN_DIR}/uname"
}

write_curl_stub() {
    local exit_code="$1"
    cat > "${STUB_BIN_DIR}/curl" <<CURL_EOF
#!/usr/bin/env bash
output=""
while [[ \$# -gt 0 ]]; do
  case "\$1" in
    -o)
      output="\$2"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done
if [[ "${exit_code}" -ne 0 ]]; then
  exit "${exit_code}"
fi
printf '%s' "${BINARY_CONTENT}" > "\${output}"
CURL_EOF
    chmod +x "${STUB_BIN_DIR}/curl"
}

write_builder_stub() {
    cat > "${DISTRIBUTION_DIR}/target/jlink/codex/bin/build-engine-artifacts" <<'BUILDER_EOF'
#!/usr/bin/env bash
set -euo pipefail
plugin_dir="$1"
client_dir="$2"
target_dir="$3"
binary="${plugin_dir}/lib/git-filter-repo-linux-x64"
version_file="${binary}.version"
[[ -x "${binary}" ]] || { echo "bundled binary missing during builder execution" >&2; exit 1; }
[[ "$(cat "${version_file}")" == "git-filter-repo-v2.38.0" ]] || {
  echo "version file missing during builder execution" >&2
  exit 1
}
mkdir -p "${target_dir}"
printf '%s\n%s\n%s\n' "${plugin_dir}" "${client_dir}" "${target_dir}" > "${target_dir}/builder-args.txt"
BUILDER_EOF
    chmod +x "${DISTRIBUTION_DIR}/target/jlink/codex/bin/build-engine-artifacts"
}

run_build_script() {
    run env PATH="${STUB_BIN_DIR}:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin" \
        bash "${DISTRIBUTION_DIR}/scripts/build-engine-artifacts.sh"
}

@test "downloads verified git-filter-repo binary, invokes builder, and cleans generated source files" {
    run_build_script

    [ "${status}" -eq 0 ]
    [ -f "${DISTRIBUTION_DIR}/target/engine/builder-args.txt" ]
    [ ! -e "${PLUGIN_DIR}/lib/git-filter-repo-linux-x64" ]
    [ ! -e "${PLUGIN_DIR}/lib/git-filter-repo-linux-x64.version" ]
}

@test "fails fast when release tag is malformed" {
    sed -i 's/RELEASE_TAG="git-filter-repo-v2.38.0"/RELEASE_TAG="bad-tag"/' \
        "${PLUGIN_DIR}/.git-filter-repo-config/release.conf"

    run_build_script

    [ "${status}" -ne 0 ]
    [[ "${output}" == *"ERROR: RELEASE_TAG"* ]] || [[ "${lines[*]}" == *"ERROR: RELEASE_TAG"* ]]
}

@test "fails fast on unsupported platform before invoking builder" {
    write_unsupported_uname_stub

    run_build_script

    [ "${status}" -ne 0 ]
    [[ "${output}" == *"ERROR: Unsupported operating system for bundling"* ]] || \
        [[ "${lines[*]}" == *"ERROR: Unsupported operating system for bundling"* ]]
    [ ! -e "${DISTRIBUTION_DIR}/target/engine/builder-args.txt" ]
}

@test "fails fast when existing bundled binary is stale or not executable" {
    mkdir -p "${PLUGIN_DIR}/lib"
    printf 'stale' > "${PLUGIN_DIR}/lib/git-filter-repo-linux-x64"
    chmod +x "${PLUGIN_DIR}/lib/git-filter-repo-linux-x64"

    run_build_script

    [ "${status}" -ne 0 ]
    [[ "${output}" == *"ERROR: Existing bundled git-filter-repo-linux-x64 failed checksum"* ]] || \
        [[ "${lines[*]}" == *"ERROR: Existing bundled git-filter-repo-linux-x64 failed checksum"* ]]
}

@test "fails fast when release asset download fails" {
    write_curl_stub 22

    run_build_script

    [ "${status}" -ne 0 ]
    [[ "${output}" == *"ERROR: Failed to download git-filter-repo-linux-x64"* ]] || \
        [[ "${lines[*]}" == *"ERROR: Failed to download git-filter-repo-linux-x64"* ]]
}

@test "fails fast when downloaded binary checksum does not match release config" {
    write_release_conf "0000000000000000000000000000000000000000000000000000000000000000"

    run_build_script

    [ "${status}" -ne 0 ]
    [[ "${output}" == *"ERROR: SHA256 checksum mismatch for bundled git-filter-repo-linux-x64"* ]] || \
        [[ "${lines[*]}" == *"ERROR: SHA256 checksum mismatch for bundled git-filter-repo-linux-x64"* ]]
}
