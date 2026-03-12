#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Tests for plugin/migrations/lib/utils.sh utility functions

load '../test_helper'

setup() {
    setup_test_dir
    export CLAUDE_PLUGIN_ROOT="$PLUGIN_ROOT"
    # Source the utils so we can call functions directly
    # shellcheck source=../../plugin/migrations/lib/utils.sh
    source "$CLAUDE_PLUGIN_ROOT/migrations/lib/utils.sh"
}

teardown() {
    teardown_test_dir
}

# ─── resolve_cat_dir ──────────────────────────────────────────────────────────

@test "resolve_cat_dir: returns .cat when .cat exists" {
    mkdir -p "$TEST_TEMP_DIR/.cat"
    cd "$TEST_TEMP_DIR"
    result=$(resolve_cat_dir)
    [ "$result" = ".cat" ]
}

@test "resolve_cat_dir: returns .claude/cat when .cat does not exist" {
    # No .cat directory created
    cd "$TEST_TEMP_DIR"
    result=$(resolve_cat_dir)
    [ "$result" = ".claude/cat" ]
}

@test "resolve_cat_dir: returns .cat even when both directories exist" {
    mkdir -p "$TEST_TEMP_DIR/.cat"
    mkdir -p "$TEST_TEMP_DIR/.claude/cat"
    cd "$TEST_TEMP_DIR"
    result=$(resolve_cat_dir)
    [ "$result" = ".cat" ]
}

# ─── get_last_migrated_version ────────────────────────────────────────────────

@test "get_last_migrated_version: returns 0.0.0 when no VERSION file" {
    mkdir -p "$TEST_TEMP_DIR/.cat"
    cd "$TEST_TEMP_DIR"
    result=$(get_last_migrated_version)
    [ "$result" = "0.0.0" ]
}

@test "get_last_migrated_version: returns 0.0.0 when VERSION file is empty" {
    mkdir -p "$TEST_TEMP_DIR/.cat"
    touch "$TEST_TEMP_DIR/.cat/VERSION"
    cd "$TEST_TEMP_DIR"
    result=$(get_last_migrated_version)
    [ "$result" = "0.0.0" ]
}

@test "get_last_migrated_version: reads version from .cat/VERSION" {
    mkdir -p "$TEST_TEMP_DIR/.cat"
    printf '2.1\n' > "$TEST_TEMP_DIR/.cat/VERSION"
    cd "$TEST_TEMP_DIR"
    result=$(get_last_migrated_version)
    [ "$result" = "2.1" ]
}

@test "get_last_migrated_version: reads version from .claude/cat/VERSION when .cat absent" {
    mkdir -p "$TEST_TEMP_DIR/.claude/cat"
    printf '2.0\n' > "$TEST_TEMP_DIR/.claude/cat/VERSION"
    cd "$TEST_TEMP_DIR"
    result=$(get_last_migrated_version)
    [ "$result" = "2.0" ]
}

@test "get_last_migrated_version: strips whitespace from version" {
    mkdir -p "$TEST_TEMP_DIR/.cat"
    printf '  2.1  \n' > "$TEST_TEMP_DIR/.cat/VERSION"
    cd "$TEST_TEMP_DIR"
    result=$(get_last_migrated_version)
    [ "$result" = "2.1" ]
}

# ─── set_last_migrated_version ────────────────────────────────────────────────

@test "set_last_migrated_version: writes VERSION file to .cat" {
    mkdir -p "$TEST_TEMP_DIR/.cat"
    cd "$TEST_TEMP_DIR"
    set_last_migrated_version "2.2"
    [ -f ".cat/VERSION" ]
    [ "$(tr -d '[:space:]' < .cat/VERSION)" = "2.2" ]
}

@test "set_last_migrated_version: creates .cat if it does not exist" {
    cd "$TEST_TEMP_DIR"
    set_last_migrated_version "2.2"
    [ -d ".cat" ]
    [ -f ".cat/VERSION" ]
}

@test "set_last_migrated_version: overwrites existing VERSION file" {
    mkdir -p "$TEST_TEMP_DIR/.cat"
    printf '2.1\n' > "$TEST_TEMP_DIR/.cat/VERSION"
    cd "$TEST_TEMP_DIR"
    set_last_migrated_version "2.2"
    [ "$(tr -d '[:space:]' < .cat/VERSION)" = "2.2" ]
}
