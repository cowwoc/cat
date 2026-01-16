#!/usr/bin/env bats
# Tests for scripts/batch-read.sh - Batch file search and read

load '../test_helper'

setup() {
    setup_test_dir
    # Create test directories first
    mkdir -p "$TEST_TEMP_DIR/src"
    mkdir -p "$TEST_TEMP_DIR/docs"
    # Create test files with various content
    echo "function parseData() { return data; }" > "$TEST_TEMP_DIR/src/parser.java"
    echo "function formatData() { return formatted; }" > "$TEST_TEMP_DIR/src/formatter.java"
    echo "function validateData() { return valid; }" > "$TEST_TEMP_DIR/src/validator.java"
    echo "export const utils = { parseData };" > "$TEST_TEMP_DIR/src/utils.js"
    echo "# parseData usage guide" > "$TEST_TEMP_DIR/docs/README.md"
    cd "$TEST_TEMP_DIR"
}

teardown() {
    teardown_test_dir
}

# ============================================================================
# Basic Pattern Matching Tests
# ============================================================================

@test "batch-read: finds files matching pattern" {
    run "$SCRIPTS_DIR/batch-read.sh" "parseData"
    [ "$status" -eq 0 ]
    [[ "$output" == *'"status": "success"'* ]]
    [[ "$output" == *"parser.java"* ]]
}

@test "batch-read: returns error for pattern with no matches" {
    run "$SCRIPTS_DIR/batch-read.sh" "nonexistent_pattern_xyz123"
    [ "$status" -eq 1 ]
    [[ "$output" == *'"status": "error"'* ]]
    [[ "$output" == *"No files found"* ]]
}

@test "batch-read: requires pattern argument" {
    run "$SCRIPTS_DIR/batch-read.sh"
    [ "$status" -eq 1 ]
    [[ "$output" == *"ERROR"* ]] || [[ "$output" == *"PATTERN required"* ]]
}

# ============================================================================
# --max-files Option Tests
# ============================================================================

@test "batch-read: respects --max-files limit" {
    run "$SCRIPTS_DIR/batch-read.sh" "function" --max-files 2
    [ "$status" -eq 0 ]
    assert_json_field "$output" ".files_read" "2"
}

@test "batch-read: --max-files 1 returns single file" {
    run "$SCRIPTS_DIR/batch-read.sh" "function" --max-files 1
    [ "$status" -eq 0 ]
    assert_json_field "$output" ".files_read" "1"
}

# ============================================================================
# --context-lines Option Tests
# ============================================================================

@test "batch-read: --context-lines limits output per file" {
    # Create a file with many lines
    for i in {1..50}; do echo "line $i content"; done > "$TEST_TEMP_DIR/src/large.java"
    echo "function target()" >> "$TEST_TEMP_DIR/src/large.java"

    run "$SCRIPTS_DIR/batch-read.sh" "target" --context-lines 10
    [ "$status" -eq 0 ]
    [[ "$output" == *"truncated"* ]] || [[ "$output" == *"showing 10"* ]]
}

@test "batch-read: --context-lines 0 reads entire file" {
    # Create a file with known content
    for i in {1..20}; do echo "line $i"; done > "$TEST_TEMP_DIR/src/full.java"
    echo "function searchMe()" >> "$TEST_TEMP_DIR/src/full.java"

    run "$SCRIPTS_DIR/batch-read.sh" "searchMe" --context-lines 0
    [ "$status" -eq 0 ]
    # Should contain content but no truncation message
    [[ "$output" != *"truncated"* ]] || [[ "$output" == *"line 20"* ]]
}

# ============================================================================
# --type Option Tests
# ============================================================================

@test "batch-read: --type filters by file extension" {
    run "$SCRIPTS_DIR/batch-read.sh" "function" --type java
    [ "$status" -eq 0 ]
    # Should find .java files
    [[ "$output" == *".java"* ]]
    # Should not include .js files in this filtered result
}

@test "batch-read: --type md filters markdown files" {
    run "$SCRIPTS_DIR/batch-read.sh" "parseData" --type md
    [ "$status" -eq 0 ]
    [[ "$output" == *".md"* ]] || [[ "$output" == *"README"* ]]
}

# ============================================================================
# Unknown Option Handling
# ============================================================================

@test "batch-read: rejects unknown options" {
    run "$SCRIPTS_DIR/batch-read.sh" "pattern" --invalid-option value
    [ "$status" -eq 1 ]
    [[ "$output" == *"Unknown option"* ]]
}

# ============================================================================
# JSON Output Structure Tests
# ============================================================================

@test "batch-read: JSON output has required fields" {
    run "$SCRIPTS_DIR/batch-read.sh" "parseData"
    [ "$status" -eq 0 ]

    # Check required JSON fields exist
    assert_json_field "$output" ".status" "success"
    [[ "$output" == *'"pattern":'* ]]
    [[ "$output" == *'"files_found":'* ]]
    [[ "$output" == *'"files_read":'* ]]
    [[ "$output" == *'"duration_seconds":'* ]]
}

@test "batch-read: JSON output includes working directory" {
    run "$SCRIPTS_DIR/batch-read.sh" "parseData"
    [ "$status" -eq 0 ]
    [[ "$output" == *'"working_directory":'* ]]
}

# ============================================================================
# File Content Output Tests
# ============================================================================

@test "batch-read: output includes file content with headers" {
    run "$SCRIPTS_DIR/batch-read.sh" "parseData"
    [ "$status" -eq 0 ]
    [[ "$output" == *"FILE CONTENTS"* ]]
    [[ "$output" == *"FILE:"* ]]
}

@test "batch-read: output includes actual file content" {
    run "$SCRIPTS_DIR/batch-read.sh" "parseData"
    [ "$status" -eq 0 ]
    # Should contain the actual content from parser.java
    [[ "$output" == *"return data"* ]]
}

# ============================================================================
# Combined Option Tests
# ============================================================================

@test "batch-read: combines --max-files and --type" {
    run "$SCRIPTS_DIR/batch-read.sh" "function" --max-files 2 --type java
    [ "$status" -eq 0 ]
    assert_json_field "$output" ".files_read" "2"
}

@test "batch-read: combines all options" {
    run "$SCRIPTS_DIR/batch-read.sh" "function" --max-files 1 --context-lines 5 --type java
    [ "$status" -eq 0 ]
    assert_json_field "$output" ".files_read" "1"
}
