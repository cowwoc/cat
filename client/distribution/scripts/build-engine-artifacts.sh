#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DISTRIBUTION_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
CLIENT_DIR="$(cd "${DISTRIBUTION_DIR}/.." && pwd)"
PLUGIN_DIR="${CLIENT_DIR}/plugin"
TARGET_DIR="${DISTRIBUTION_DIR}/target/engine"
STAMP_DIR="${DISTRIBUTION_DIR}/target/.stamps"
CONF="${PLUGIN_DIR}/.git-filter-repo-config/release.conf"
GENERATED_BUNDLE_FILES=()

# shellcheck source=../../plugin/scripts/sha256sum-portable.sh
source "${PLUGIN_DIR}/scripts/sha256sum-portable.sh"
# shellcheck source=../../plugin/scripts/git-filter-repo-release.sh
source "${PLUGIN_DIR}/scripts/git-filter-repo-release.sh"

cleanup_generated_bundle_files() {
  if [[ ${#GENERATED_BUNDLE_FILES[@]} -gt 0 ]]; then
    rm -f "${GENERATED_BUNDLE_FILES[@]}"
    rmdir "${PLUGIN_DIR}/lib" 2>/dev/null || true
  fi
}

COMMON_JAR="${CLIENT_DIR}/common-cli/target/client-common-cli-2.1.jar"

build_stamp_cli() {
  if [[ -n "${BUILD_STAMP_CLI:-}" ]]; then
    if [[ -x "${BUILD_STAMP_CLI}" ]]; then
      "${BUILD_STAMP_CLI}" "$@"
    else
      bash "${BUILD_STAMP_CLI}" "$@"
    fi
  else
    java -cp "${COMMON_JAR}" io.github.cowwoc.cat.tool.util.BuildStamp "$@"
  fi
}

engine_artifact_stamp_file() {
  echo "${STAMP_DIR}/engine-artifacts.sha256"
}

engine_artifact_stamp_inputs() {
  printf '%s\n' \
    "${DISTRIBUTION_DIR}/scripts/build-engine-artifacts.sh" \
    "${DISTRIBUTION_DIR}/scripts/lib/build-stamp.sh" \
    "${PLUGIN_DIR}/scripts/sha256sum-portable.sh" \
    "${PLUGIN_DIR}/scripts/git-filter-repo-release.sh" \
    "${CONF}" \
    "${BUILDER}" \
    "${PLUGIN_DIR}"
}

engine_artifact_required_outputs() {
  printf '%s\n' \
    "${TARGET_DIR}/claude/.claude-plugin/plugin.json" \
    "${TARGET_DIR}/claude/client/VERSION" \
    "${TARGET_DIR}/codex/.codex-plugin/plugin.json" \
    "${TARGET_DIR}/codex/client/VERSION"
}

engine_artifact_outputs_ready() {
  local output
  while IFS= read -r output; do
    [[ -e "${output}" ]] || return 1
  done < <(engine_artifact_required_outputs)
}

engine_artifact_stamp_current() {
  engine_artifact_outputs_ready || return 1
  mapfile -t inputs < <(engine_artifact_stamp_inputs)
  build_stamp_cli matches "$(engine_artifact_stamp_file)" "${inputs[@]}"
}

write_engine_artifact_stamp() {
  mapfile -t inputs < <(engine_artifact_stamp_inputs)
  build_stamp_cli write "$(engine_artifact_stamp_file)" "${inputs[@]}"
}

ensure_bundled_git_filter_repo() {
  local release_tag
  git_filter_repo_require_conf "${CONF}"
  release_tag="$(git_filter_repo_require_release_tag "${CONF}")"

  local os arch platform platform_var expected_sha256
  platform="$(git_filter_repo_detect_platform " for bundling")"
  expected_sha256="$(git_filter_repo_expected_sha256 "${CONF}" "${platform}")"

  local binary_name bundled_binary version_file tmp_binary actual_sha256 download_url repo_owner repo_name
  local version_file_existed
  binary_name="git-filter-repo-${platform}"
  bundled_binary="${PLUGIN_DIR}/lib/${binary_name}"
  version_file="${bundled_binary}.version"
  tmp_binary="${bundled_binary}.tmp.$$.$RANDOM"
  version_file_existed=false
  if [[ -e "${version_file}" ]]; then
    version_file_existed=true
  fi
  repo_owner="$(git_filter_repo_conf_value "${CONF}" REPO_OWNER)"
  repo_name="$(git_filter_repo_conf_value "${CONF}" REPO_NAME)"
  if [[ -z "${repo_owner}" ]]; then
    repo_owner="cowwoc"
  fi
  if [[ -z "${repo_name}" ]]; then
    repo_name="cat"
  fi
  download_url="https://github.com/${repo_owner}/${repo_name}/releases/download/${release_tag}/${binary_name}"

  mkdir -p "${PLUGIN_DIR}/lib"
  trap "rm -f '${tmp_binary}'" EXIT

  if [[ -f "${bundled_binary}" ]]; then
    actual_sha256="$(sha256sum_portable "${bundled_binary}")"
    if [[ "${actual_sha256}" == "${expected_sha256}" ]] && [[ -x "${bundled_binary}" ]]; then
      printf '%s\n' "${release_tag}" > "${version_file}"
      if [[ "${version_file_existed}" == "false" ]]; then
        GENERATED_BUNDLE_FILES+=("${version_file}")
      fi
      trap - EXIT
      return
    fi
    echo "ERROR: Existing bundled ${binary_name} failed checksum or executable validation: ${bundled_binary}" >&2
    echo "Remove the stale file before rebuilding engine artifacts." >&2
    exit 1
  fi

  echo "Bundling ${binary_name} from ${download_url}" >&2
  if ! curl -fsSL --max-time 120 -o "${tmp_binary}" "${download_url}"; then
    echo "ERROR: Failed to download ${binary_name} from ${download_url}" >&2
    exit 1
  fi

  actual_sha256="$(sha256sum_portable "${tmp_binary}")"
  if [[ -z "${actual_sha256}" ]]; then
    echo "ERROR: Failed to compute SHA256 checksum for ${tmp_binary}" >&2
    exit 1
  fi
  if [[ "${actual_sha256}" != "${expected_sha256}" ]]; then
    echo "ERROR: SHA256 checksum mismatch for bundled ${binary_name}" >&2
    echo "Expected: ${expected_sha256}" >&2
    echo "Actual:   ${actual_sha256}" >&2
    exit 1
  fi

  mv "${tmp_binary}" "${bundled_binary}"
  chmod +x "${bundled_binary}"
  printf '%s\n' "${release_tag}" > "${version_file}"
  GENERATED_BUNDLE_FILES+=("${bundled_binary}" "${version_file}")
  trap - EXIT
}

BUILDER=""
for engine in codex claude; do
  candidate="${DISTRIBUTION_DIR}/target/jlink/${engine}/bin/build-engine-artifacts"
  if [[ -x "$candidate" ]]; then
    BUILDER="$candidate"
    break
  fi
done

if [[ -z "$BUILDER" ]]; then
  echo "ERROR: build-engine-artifacts launcher not found in any engine image under ${DISTRIBUTION_DIR}/target/jlink" >&2
  echo "Run: ${CLIENT_DIR}/mvnw -f ${CLIENT_DIR}/pom.xml -pl distribution -am package -e" >&2
  exit 1
fi

FORCE_REBUILD=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --force) FORCE_REBUILD=true; shift ;;
    *) echo "ERROR: Unknown argument: $1" >&2; exit 1 ;;
  esac
done

if [[ "${FORCE_REBUILD}" == "false" ]] && engine_artifact_stamp_current; then
  echo "Skipping engine artifact rebuild because inputs and required outputs are unchanged" >&2
  exit 0
fi

ensure_bundled_git_filter_repo
trap cleanup_generated_bundle_files EXIT

"$BUILDER" "$PLUGIN_DIR" "$CLIENT_DIR" "$TARGET_DIR"
cleanup_generated_bundle_files
write_engine_artifact_stamp
