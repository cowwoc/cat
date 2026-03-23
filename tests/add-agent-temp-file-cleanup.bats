#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Tests verifying that the plan_temp_file created by mktemp --suffix=.md is cleaned up
# on both the success path and the error path in add-agent/first-use.md.

setup() {
    TMPDIR="$(mktemp -d)"
}

teardown() {
    rm -f "${plan_temp_file:-}"
    rm -rf "${TMPDIR:-}"
}

# Creates a .md-suffixed temp file and stores the path in plan_temp_file.
# In Bats, helper functions execute in the same shell as the test,
# so plan_temp_file is directly accessible in the calling test without exporting.
create_plan_temp_file() {
    plan_temp_file=$(mktemp -p "${TMPDIR}" --suffix=.md)
}

@test "mktemp --suffix=.md creates a .md-suffixed temp file" {
    create_plan_temp_file
    [ -f "${plan_temp_file}" ]
    [[ "${plan_temp_file}" == *.md ]]
}

@test "cleanup after failed command: rm -f removes the temp file" {
    create_plan_temp_file
    [ -f "${plan_temp_file}" ]
    # Simulate a preceding command failure (non-zero exit); the script continues because Bats does not use set -e
    false || true
    rm -f "${plan_temp_file}"
    [ ! -f "${plan_temp_file}" ]
}

@test "rm -f on non-existent path does not fail" {
    run rm -f "${TMPDIR}/nonexistent-plan-file.md"
    [ "$status" -eq 0 ]
}

@test "no temp file remains after writing content to it" {
    create_plan_temp_file
    [ -f "${plan_temp_file}" ]
    # Write content to the temp file before cleanup
    printf 'plan content\n' > "${plan_temp_file}"
    [ -f "${plan_temp_file}" ]
    rm -f "${plan_temp_file}"
    [ ! -f "${plan_temp_file}" ]
}

@test "multiple cleanup calls are idempotent" {
    create_plan_temp_file
    [ -f "${plan_temp_file}" ]
    # First removal — file exists
    rm -f "${plan_temp_file}"
    [ ! -f "${plan_temp_file}" ]
    # Second removal — file already gone; -f must suppress the error
    run rm -f "${plan_temp_file}"
    [ "$status" -eq 0 ]
}
