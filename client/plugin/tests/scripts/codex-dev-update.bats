#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.

SCRIPT_DIR="$(cd "$(dirname "$BATS_TEST_FILENAME")/../../../.." && pwd)"
UPDATE_SCRIPT="${SCRIPT_DIR}/client/plugin/scripts/codex-dev-update.sh"
JLINK_SCRIPT="${SCRIPT_DIR}/client/distribution/scripts/build-jlink-images.sh"

setup() {
  TEST_ROOT="$(mktemp -d)"
}

teardown() {
  rm -rf "${TEST_ROOT:-}"
}

@test "resolve_project_dir prefers the current CAT issue worktree" {
  local repo_dir="${TEST_ROOT}/repo"
  local worktree_dir="${TEST_ROOT}/issue-worktree"

  git -C "${TEST_ROOT}" init --quiet --initial-branch=main repo
  git -C "${repo_dir}" config user.email "test@test.com"
  git -C "${repo_dir}" config user.name "Test User"
  mkdir -p "${repo_dir}/client"
  touch "${repo_dir}/client/pom.xml"
  git -C "${repo_dir}" add client/pom.xml
  git -C "${repo_dir}" commit --quiet -m "Initial commit"
  git -C "${repo_dir}" worktree add -b issue-branch "${worktree_dir}" HEAD --quiet

  run bash -lc 'cd "$1" && source "$2" && resolve_project_dir' _ "${worktree_dir}" "${UPDATE_SCRIPT}"

  [ "${status}" -eq 0 ]
  [ "${output}" = "${worktree_dir}" ]
}

@test "jlink stamp requires codex-runner output" {
  run bash -lc 'source "$1" >/dev/null 2>&1 && jlink_required_outputs' _ "${JLINK_SCRIPT}"

  [ "${status}" -eq 0 ]
  [[ "${output}" == *"/codex/bin/codex-runner"* ]]
}
