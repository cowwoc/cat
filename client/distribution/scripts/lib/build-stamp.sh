#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
set -euo pipefail

BUILD_STAMP_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../../../plugin/scripts/sha256sum-portable.sh
source "${BUILD_STAMP_LIB_DIR}/../../../plugin/scripts/sha256sum-portable.sh"

build_stamp_compute() {
  local manifest
  manifest="$(mktemp)"

  local path
  for path in "$@"; do
    if [[ -d "${path}" ]]; then
      printf 'dir\t%s\n' "${path}" >> "${manifest}"
      while IFS= read -r -d '' file; do
        local rel="${file#${path}/}"
        printf 'file\t%s\t%s\t%s\n' "${path}" "${rel}" "$(sha256sum_portable "${file}")" >> "${manifest}"
      done < <(find "${path}" -type f -print0 | sort -z)
    elif [[ -f "${path}" ]]; then
      printf 'file\t%s\t%s\n' "${path}" "$(sha256sum_portable "${path}")" >> "${manifest}"
    else
      printf 'missing\t%s\n' "${path}" >> "${manifest}"
    fi
  done

  local digest
  digest="$(sha256sum_portable "${manifest}")"
  rm -f "${manifest}"
  echo "${digest}"
}

build_stamp_matches() {
  local stamp_file="$1"
  shift
  [[ -f "${stamp_file}" ]] || return 1
  local expected actual
  expected="$(<"${stamp_file}")"
  actual="$(build_stamp_compute "$@")"
  [[ "${expected}" == "${actual}" ]]
}

build_stamp_write() {
  local stamp_file="$1"
  shift
  mkdir -p "$(dirname "${stamp_file}")"
  local tmp
  tmp="${stamp_file}.tmp.$$.$RANDOM"
  build_stamp_compute "$@" > "${tmp}"
  mv "${tmp}" "${stamp_file}"
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  if [[ $# -lt 2 ]]; then
    echo "Usage: build-stamp.sh compute <path>... | matches <stamp-file> <path>... | write <stamp-file> <path>..." >&2
    exit 1
  fi
  case "$1" in
    compute) shift; build_stamp_compute "$@" ;;
    matches)
      stamp_file="$2"
      shift 2
      if build_stamp_matches "${stamp_file}" "$@"; then
        exit 0
      fi
      exit 1
      ;;
    write)
      stamp_file="$2"
      shift 2
      build_stamp_write "${stamp_file}" "$@"
      ;;
    *)
      echo "Unknown command: $1" >&2
      exit 1
      ;;
  esac
fi
