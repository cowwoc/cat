#!/usr/bin/env bats
# Tests for bash_handlers/validate_git_operations.py - git command validation hook

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
# Safe Commands (should pass)
# ============================================================================

@test "allows: git status" {
    run run_hook_with_command "git status"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

@test "allows: git log" {
    run run_hook_with_command "git log --oneline -10"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

@test "allows: git diff" {
    run run_hook_with_command "git diff HEAD~1"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

@test "allows: git branch (list)" {
    run run_hook_with_command "git branch -a"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

@test "allows: git push (normal)" {
    run run_hook_with_command "git push origin feature-branch"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

@test "allows: git push --force-with-lease" {
    run run_hook_with_command "git push --force-with-lease origin feature-branch"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

@test "allows: non-git commands" {
    run run_hook_with_command "ls -la"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

@test "allows: empty command" {
    run run_hook_with_command ""
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]]
}

# ============================================================================
# Blocked Commands
# ============================================================================

@test "blocks: git push --force to main" {
    run run_hook_with_command "git push --force origin main"
    [ "$status" -eq 0 ]
    [[ "$output" == *"block"* ]]
    [[ "$output" == *"Force push"* ]] || [[ "$output" == *"BLOCKED"* ]]
}

@test "blocks: git push --force to master" {
    run run_hook_with_command "git push --force origin master"
    [ "$status" -eq 0 ]
    [[ "$output" == *"block"* ]]
}

@test "blocks: git reset --hard without acknowledgment" {
    run run_hook_with_command "git reset --hard HEAD~1"
    [ "$status" -eq 0 ]
    [[ "$output" == *"block"* ]]
    [[ "$output" == *"BLOCKED"* ]]
}

@test "blocks: git reset --hard HEAD" {
    run run_hook_with_command "git reset --hard HEAD"
    [ "$status" -eq 0 ]
    [[ "$output" == *"block"* ]]
}

# ============================================================================
# Allowed with Acknowledgment
# ============================================================================

@test "allows: git reset --hard with ACKNOWLEDGED comment" {
    run run_hook_with_command "git reset --hard HEAD~1 # ACKNOWLEDGED"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

@test "allows: git reset --hard in worktrees directory" {
    run run_hook_with_command "cd /workspace/.worktrees/task && git reset --hard HEAD"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

# ============================================================================
# Edge Cases
# ============================================================================

@test "allows: --force-with-lease (safe alternative)" {
    run run_hook_with_command "git push --force-with-lease origin feature"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

@test "allows: force in branch name (not force push)" {
    run run_hook_with_command "git checkout force-update-feature"
    [ "$status" -eq 0 ]
    [[ "$output" != *"Force push"* ]]
}

@test "allows: main in non-protected context" {
    run run_hook_with_command "git log main..HEAD"
    [ "$status" -eq 0 ]
    [[ "$output" != *"block"* ]]
}

@test "handles: command with special characters" {
    run run_hook_with_command "git commit -m 'bugfix: handle --all flag'"
    [ "$status" -eq 0 ]
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}

@test "allows: git push --force to feature branch" {
    run run_hook_with_command "git push --force origin feature-branch"
    [ "$status" -eq 0 ]
    # Only blocks force push to main/master
    [[ "$output" == "{}" ]] || [[ "$output" != *"block"* ]]
}
