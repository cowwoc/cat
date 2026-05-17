#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.

GIT_FILTER_REPO_PLATFORMS=(linux_x64 linux_aarch64 macos_x64 macos_aarch64)

git_filter_repo_conf_value() {
  local conf="$1"
  local key="$2"
  grep -E "^${key}=" "${conf}" | sed "s/^${key}=\"\(.*\)\"$/\1/" | head -1 || true
}

git_filter_repo_require_conf() {
  local conf="$1"
  if [[ ! -f "${conf}" ]]; then
    echo "ERROR: Release config not found: ${conf}" >&2
    exit 1
  fi
}

git_filter_repo_require_release_tag() {
  local conf="$1"
  local release_tag
  release_tag="$(git_filter_repo_conf_value "${conf}" RELEASE_TAG)"
  if [[ -z "${release_tag}" ]]; then
    echo "ERROR: RELEASE_TAG not found in ${conf}" >&2
    exit 1
  fi
  if ! echo "${release_tag}" | grep -qE '^git-filter-repo-v[0-9]+\.[0-9]+\.[0-9]+$'; then
    echo "ERROR: RELEASE_TAG in ${conf} has unexpected format: ${release_tag}" >&2
    echo "Expected format: git-filter-repo-vX.Y.Z" >&2
    exit 1
  fi
  echo "${release_tag}"
}

git_filter_repo_detect_platform() {
  local context="$1"
  local os arch
  os="$(uname -s)"
  arch="$(uname -m)"
  case "${os}" in
    Linux)
      case "${arch}" in
        x86_64) echo "linux-x64" ;;
        aarch64|arm64) echo "linux-aarch64" ;;
        *)
          echo "ERROR: Unsupported Linux architecture${context}: ${arch}" >&2
          echo "Supported: x86_64, aarch64" >&2
          exit 1
          ;;
      esac
      ;;
    Darwin)
      case "${arch}" in
        x86_64) echo "macos-x64" ;;
        arm64) echo "macos-aarch64" ;;
        *)
          echo "ERROR: Unsupported macOS architecture${context}: ${arch}" >&2
          echo "Supported: x86_64, arm64" >&2
          exit 1
          ;;
      esac
      ;;
    *)
      echo "ERROR: Unsupported operating system${context}: ${os}" >&2
      exit 1
      ;;
  esac
}

git_filter_repo_validate_sha256_fields() {
  local conf="$1"
  local validation_errors=""
  local platform_name var_name hash
  for platform_name in "${GIT_FILTER_REPO_PLATFORMS[@]}"; do
    var_name="PLATFORM_SHA256_${platform_name}"
    hash="$(git_filter_repo_conf_value "${conf}" "${var_name}")"
    if [[ -z "${hash}" ]]; then
      validation_errors="${validation_errors}\n  Missing: ${var_name}"
    elif ! echo "${hash}" | grep -qE '^[a-f0-9]{64}$'; then
      validation_errors="${validation_errors}\n  Invalid SHA256 format for ${var_name}: ${hash}"
    fi
  done
  if [[ -n "${validation_errors}" ]]; then
    echo "ERROR: Invalid or missing SHA256 fields in ${conf}:${validation_errors}" >&2
    exit 1
  fi
}

git_filter_repo_expected_sha256() {
  local conf="$1"
  local platform="$2"
  local platform_var expected_sha256
  git_filter_repo_validate_sha256_fields "${conf}"
  platform_var="PLATFORM_SHA256_${platform//-/_}"
  expected_sha256="$(git_filter_repo_conf_value "${conf}" "${platform_var}")"
  if [[ -z "${expected_sha256}" ]]; then
    echo "ERROR: No SHA256 configured for platform '${platform}' in ${conf}" >&2
    echo "Supported platforms: linux-x64, linux-aarch64, macos-x64, macos-aarch64" >&2
    exit 1
  fi
  echo "${expected_sha256}"
}
