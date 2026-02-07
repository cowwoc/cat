#!/usr/bin/env bats
# Tests for bash_handlers/block_merge_commits.py - block merge commits to enforce linear history

load '../test_helper'

setup() {
    setup_test_dir
}

teardown() {
    teardown_test_dir
}

# Helper to create hook input JSON for Bash tool
create_bash_hook_json() {
    local command="$1"
    # Escape backslashes and quotes for JSON
    command=$(echo "$command" | sed 's/\\/\\\\/g; s/"/\\"/g')
    echo "{\"hook_event_name\": \"PreToolUse\", \"tool_name\": \"Bash\", \"tool_input\": {\"command\": \"$command\"}}"
}

# Helper to run hook with JSON input via Python dispatcher
run_hook_with_command() {
    local command="$1"
    local json=$(create_bash_hook_json "$command")
    echo "$json" | python3 "$HOOKS_DIR/get-bash-pretool-output.py"
}

# ============================================================================
# BLOCKED Commands (from PLAN.md test scenarios)
# ============================================================================

@test "blocks: git merge branch (no --ff-only)" {
    run run_hook_with_command "git merge feature-branch"
    [ "$status" -eq 0 ]
    [[ "$output" == *"block"* ]]
    [[ "$output" == *"without --ff-only"* ]] || [[ "$output" == *"BLOCKED"* ]]
}

@test "blocks: git merge --no-ff branch" {
    run run_hook_with_command "git merge --no-ff feature-branch"
    [ "$status" -eq 0 ]
    [[ "$output" == *"block"* ]]
    [[ "$output" == *"--no-ff"* ]] || [[ "$output" == *"BLOCKED"* ]]
}

# ============================================================================
# ALLOWED Commands (from PLAN.md test scenarios)
# ============================================================================

@test "allows: git merge --ff-only branch" {
    run run_hook_with_command "git merge --ff-only feature-branch"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

@test "allows: git merge --squash branch" {
    run run_hook_with_command "git merge --squash feature-branch"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

@test "allows: git merge-base branch1 branch2 (regression test for M467)" {
    run run_hook_with_command "git merge-base main feature-branch"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

@test "allows: git merge-tree branch1 branch2" {
    run run_hook_with_command "git merge-tree main feature-branch"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

@test "allows: git merge-file file1 file2 file3" {
    run run_hook_with_command "git merge-file base.txt current.txt other.txt"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

@test "allows: cmd && git merge-base x y (with preceding command)" {
    run run_hook_with_command "git status && git merge-base main feature-branch"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

# ============================================================================
# Edge Cases
# ============================================================================

@test "allows: non-git commands" {
    run run_hook_with_command "ls -la"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]]
}

@test "allows: empty command" {
    run run_hook_with_command ""
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]]
}

@test "allows: git commands without merge" {
    run run_hook_with_command "git status"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]]
}

@test "blocks: git merge with --no-ff after branch name" {
    run run_hook_with_command "git merge feature-branch --no-ff"
    [ "$status" -eq 0 ]
    [[ "$output" == *"block"* ]]
}

@test "allows: merge in branch name (not merge command)" {
    run run_hook_with_command "git checkout merge-request-123"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

@test "allows: git merge-base in piped command" {
    run run_hook_with_command "git merge-base main HEAD | xargs git log"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

@test "allows: git merge-base with ; separator" {
    run run_hook_with_command "echo test; git merge-base main HEAD"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}
