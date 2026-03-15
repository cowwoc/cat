#!/bin/bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
# Test helper for CAT plugin tests
# Source this file in all test files

# Get the directory containing the test file
TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$TEST_DIR/.." && pwd)"
PLUGIN_ROOT="$PROJECT_ROOT/plugin"

# Export paths for scripts under test
export SCRIPTS_DIR="$PLUGIN_ROOT/scripts"
export HOOKS_DIR="$PLUGIN_ROOT/hooks"
export HOOKS_LIB_DIR="$HOOKS_DIR/lib"

# Create temporary test directory
setup_test_dir() {
    TEST_TEMP_DIR=$(mktemp -d)
    export TEST_TEMP_DIR
    export CLAUDE_PROJECT_DIR="$TEST_TEMP_DIR"

    # Create required directories
    mkdir -p "$TEST_TEMP_DIR/.cat/work/locks"
    mkdir -p "$TEST_TEMP_DIR/.git"
}

# Initialize a mock git repository in /tmp (allowed by hook exception)
setup_git_repo() {
    # Create fresh temp directory
    TEST_TEMP_DIR=$(mktemp -d)
    export TEST_TEMP_DIR
    export CLAUDE_PROJECT_DIR="$TEST_TEMP_DIR"

    # Initialize git repo in /tmp (allowed by hook)
    cd "$TEST_TEMP_DIR" || return 1
    git init --quiet --initial-branch=main
    git config user.email "test@test.com"
    git config user.name "Test User"
    git config core.autocrlf false
    git config advice.detachedHead false

    # Create initial commit
    echo "initial" > file.txt
    git add file.txt
    git commit --quiet -m "Initial commit"

    # Create required CAT directories
    mkdir -p "$TEST_TEMP_DIR/.cat/work/locks"
}

# Clean up temporary test directory
teardown_test_dir() {
    # Return to safe directory
    cd /workspace || true

    # Clean up temp directory
    if [[ -n "${TEST_TEMP_DIR:-}" && -d "$TEST_TEMP_DIR" ]]; then
        rm -rf "$TEST_TEMP_DIR" 2>/dev/null || true
    fi
}

# Create a test branch with commits
create_test_branch() {
    local branch_name="${1:-test-branch}"
    local num_commits="${2:-3}"

    git checkout --quiet -b "$branch_name"
    for i in $(seq 1 "$num_commits"); do
        echo "change $i" >> file.txt
        git add file.txt
        git commit --quiet -m "Commit $i on $branch_name"
    done
}

# Assert that a file contains expected content
assert_file_contains() {
    local file="$1"
    local expected="$2"

    if ! grep -q "$expected" "$file"; then
        echo "Expected file '$file' to contain: $expected"
        echo "Actual content:"
        cat "$file"
        return 1
    fi
}

# Set up config fixture with default trust level
setup_config_fixture() {
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.cat/cat-config.json"
}

# Run a script with timeout
run_with_timeout() {
    local timeout_seconds="${1:-5}"
    shift
    timeout "$timeout_seconds" "$@"
}

