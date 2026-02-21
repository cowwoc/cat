#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Tests for is_dependency_satisfied() in plugin/scripts/get-available-issues.sh

load '../test_helper'

HARNESS="$PLUGIN_ROOT/scripts/test-harness/is-dependency-satisfied.sh"

setup() {
    setup_test_dir
    mkdir -p "$TEST_TEMP_DIR/.claude/cat/issues"
    export CAT_DIR="$TEST_TEMP_DIR/.claude/cat/issues"
}

teardown() {
    teardown_test_dir
}

# Helper to create a STATE.md with given status
create_state_md() {
    local dir="$1"
    local status="$2"
    mkdir -p "$dir"
    cat > "$dir/STATE.md" <<EOF
# State

- **Status:** $status
- **Progress:** 100%
EOF
}

# ─── Version-qualified name resolution ────────────────────────────────────────

@test "is_dependency_satisfied: version-qualified name resolves when issue is closed" {
    local issue_dir="$CAT_DIR/v2/v2.1/port-lock-and-worktree"
    create_state_md "$issue_dir" "closed"

    run bash "$HARNESS" "2.1-port-lock-and-worktree"
    [ "$status" -eq 0 ]
    [ "$output" = "true" ]
}

@test "is_dependency_satisfied: version-qualified name returns false when issue is open" {
    local issue_dir="$CAT_DIR/v2/v2.1/port-lock-and-worktree"
    create_state_md "$issue_dir" "open"

    run bash "$HARNESS" "2.1-port-lock-and-worktree"
    [ "$status" -eq 0 ]
    [ "$output" = "false" ]
}

@test "is_dependency_satisfied: version-qualified name returns false when issue is in-progress" {
    local issue_dir="$CAT_DIR/v2/v2.1/port-lock-and-worktree"
    create_state_md "$issue_dir" "in-progress"

    run bash "$HARNESS" "2.1-port-lock-and-worktree"
    [ "$status" -eq 0 ]
    [ "$output" = "false" ]
}

@test "is_dependency_satisfied: version-qualified name returns false when issue is blocked" {
    local issue_dir="$CAT_DIR/v2/v2.1/port-lock-and-worktree"
    create_state_md "$issue_dir" "blocked"

    run bash "$HARNESS" "2.1-port-lock-and-worktree"
    [ "$status" -eq 0 ]
    [ "$output" = "false" ]
}

@test "is_dependency_satisfied: version-qualified name not found returns false" {
    run bash "$HARNESS" "2.1-nonexistent-issue"
    [ "$status" -eq 0 ]
    [ "$output" = "false" ]
}

# ─── Bare name compatibility ───────────────────────────────────────────────────

@test "is_dependency_satisfied: bare name resolves when issue is closed" {
    local issue_dir="$CAT_DIR/v2/v2.1/my-issue"
    create_state_md "$issue_dir" "closed"

    run bash "$HARNESS" "my-issue"
    [ "$status" -eq 0 ]
    [ "$output" = "true" ]
}

@test "is_dependency_satisfied: bare name not found returns false" {
    run bash "$HARNESS" "nonexistent-bare-issue"
    [ "$status" -eq 0 ]
    [ "$output" = "false" ]
}

@test "is_dependency_satisfied: bare name returns false when issue is open" {
    local issue_dir="$CAT_DIR/v2/v2.1/my-open-issue"
    create_state_md "$issue_dir" "open"

    run bash "$HARNESS" "my-open-issue"
    [ "$status" -eq 0 ]
    [ "$output" = "false" ]
}

# ─── Regex boundary conditions ────────────────────────────────────────────────

@test "is_dependency_satisfied: trailing hyphen does not match version-qualified regex" {
    # "2.1-issue-" ends with a hyphen which is not in [a-zA-Z][a-zA-Z0-9_-]*
    # It falls through to bare name search and returns false (not found)
    run bash "$HARNESS" "2.1-issue-"
    [ "$status" -eq 0 ]
    [ "$output" = "false" ]
}

@test "is_dependency_satisfied: major-only version does not match version-qualified regex" {
    # "1-issue" has no minor version so does not match major.minor-name pattern
    # It falls through to bare name search and returns false (not found)
    run bash "$HARNESS" "1-issue"
    [ "$status" -eq 0 ]
    [ "$output" = "false" ]
}

@test "is_dependency_satisfied: patch version does not match version-qualified regex" {
    # "2.1.1-issue" has a patch segment which does not match major.minor-name pattern
    # It falls through to bare name search and returns false (not found)
    run bash "$HARNESS" "2.1.1-issue"
    [ "$status" -eq 0 ]
    [ "$output" = "false" ]
}

# ─── Security: path traversal rejected ────────────────────────────────────────

@test "is_dependency_satisfied: path traversal in dep name does not match version-qualified regex" {
    # "2.1-../../../etc/passwd" must NOT match the safe character regex
    # It falls through to bare name search and returns false (not found)
    run bash "$HARNESS" "2.1-../../../etc/passwd"
    [ "$status" -eq 0 ]
    [ "$output" = "false" ]
}

# ─── Error handling: malformed STATE.md ───────────────────────────────────────

@test "is_dependency_satisfied: malformed STATE.md with no Status field returns false" {
    local issue_dir="$CAT_DIR/v2/v2.1/broken-issue"
    mkdir -p "$issue_dir"
    printf '# State\n\n- **Progress:** 50%%\n' > "$issue_dir/STATE.md"

    run bash "$HARNESS" "broken-issue"
    [ "$status" -eq 0 ]
    [ "$output" = "false" ]
}
