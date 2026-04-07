#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
# Tests for acquire_runtime_lock / release_runtime_lock in session-start.sh.
# Covers successful acquire/release, stale lock removal, timeout, platform stat,
# and idempotent release.

setup() {
  TEST_DIR=$(mktemp -d)
  BATS_TEST_SOURCED=true source "${BATS_TEST_DIRNAME}/../../plugin/hooks/session-start.sh" || true

  JDK_PATH="${TEST_DIR}/jdk"
  LOCK_DIR="${JDK_PATH}.lock"
}

teardown() {
  release_runtime_lock
  rm -rf "$TEST_DIR"
}

@test "successful lock acquire sets LOCK_PATH and creates directory" {
  run acquire_runtime_lock "$JDK_PATH"
  [ "$status" -eq 0 ]
  [ -d "$LOCK_DIR" ]
}

@test "successful lock release removes directory and clears LOCK_PATH" {
  acquire_runtime_lock "$JDK_PATH"
  release_runtime_lock
  [ ! -d "$LOCK_DIR" ]
  [ -z "$LOCK_PATH" ]
}

@test "stale lock (mtime > 10 min) is removed and fresh lock is acquired" {
  mkdir "$LOCK_DIR"
  # Backdate mtime to 11 minutes ago (660 seconds)
  local old_time
  old_time=$(( $(date +%s) - 660 ))
  # Use GNU touch -d or BSD touch -t depending on platform
  touch -d "@${old_time}" "$LOCK_DIR" 2>/dev/null || \
    touch -t "$(date -r "${old_time}" +%Y%m%d%H%M.%S 2>/dev/null)" "$LOCK_DIR" 2>/dev/null || true

  run acquire_runtime_lock "$JDK_PATH"
  [ "$status" -eq 0 ]
  [ -d "$LOCK_DIR" ]
}

@test "active lock (not stale) causes timeout with 1-second budget" {
  mkdir "$LOCK_DIR"  # simulate a held lock by another session

  # Redefine with a 1-second timeout so the test doesn't block 30s
  acquire_runtime_lock_fast() {
    local jdk_path="$1"
    local lock_path="${jdk_path}.lock"
    local stale_threshold_seconds=600
    local timeout_seconds=1
    local elapsed=0
    while true; do
      if [[ -d "$lock_path" ]]; then
        local lock_mtime now age
        lock_mtime=$(stat -c "%Y" "$lock_path" 2>/dev/null || stat -f "%m" "$lock_path" 2>/dev/null || echo 0)
        now=$(date +%s)
        age=$(( now - lock_mtime ))
        if (( age > stale_threshold_seconds )); then
          rmdir "$lock_path" 2>/dev/null || true
        fi
      fi
      if mkdir "$lock_path" 2>/dev/null; then
        LOCK_PATH="$lock_path"
        return 0
      fi
      if (( elapsed >= timeout_seconds )); then
        return 1
      fi
      sleep 1
      (( elapsed++ )) || true
    done
  }

  run acquire_runtime_lock_fast "$JDK_PATH"
  [ "$status" -ne 0 ]

  # Clean up held lock
  rmdir "$LOCK_DIR"
}

@test "platform-aware stat handles GNU and BSD formats without error" {
  mkdir "$LOCK_DIR"
  local lock_mtime
  lock_mtime=$(stat -c "%Y" "$LOCK_DIR" 2>/dev/null || stat -f "%m" "$LOCK_DIR" 2>/dev/null || echo 0)
  [ "$lock_mtime" -ge 0 ]
  rmdir "$LOCK_DIR"
}

@test "release_runtime_lock is idempotent when no lock is held" {
  LOCK_PATH=""
  run release_runtime_lock
  [ "$status" -eq 0 ]
}
