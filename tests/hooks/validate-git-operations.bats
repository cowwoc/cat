#!/usr/bin/env bats
# Tests for validate-git-operations.sh - git command validation hook

load '../test_helper'

setup() {
    setup_test_dir
    HOOK="$HOOKS_DIR/validate-git-operations.sh"
}

teardown() {
    teardown_test_dir
}

# ============================================================================
# Safe Commands (should pass)
# ============================================================================

@test "allows: git status" {
    run "$HOOK" "git status"
    [ "$status" -eq 0 ]
}

@test "allows: git log" {
    run "$HOOK" "git log --oneline -10"
    [ "$status" -eq 0 ]
}

@test "allows: git diff" {
    run "$HOOK" "git diff HEAD~1"
    [ "$status" -eq 0 ]
}

@test "allows: git branch (list)" {
    run "$HOOK" "git branch -a"
    [ "$status" -eq 0 ]
}

@test "allows: git checkout feature-branch" {
    run "$HOOK" "git checkout feature-branch"
    [ "$status" -eq 0 ]
}

@test "allows: git push (normal)" {
    run "$HOOK" "git push origin feature-branch"
    [ "$status" -eq 0 ]
}

@test "allows: git push --force-with-lease" {
    run "$HOOK" "git push --force-with-lease origin feature-branch"
    [ "$status" -eq 0 ]
}

@test "allows: non-git commands" {
    run "$HOOK" "ls -la"
    [ "$status" -eq 0 ]
}

@test "allows: empty command" {
    run "$HOOK" ""
    [ "$status" -eq 0 ]
}

# ============================================================================
# Blocked Commands (exit 2)
# ============================================================================

@test "blocks: filter-branch --all" {
    run "$HOOK" "git filter-branch --all"
    [ "$status" -eq 2 ]
    [[ "$output" == *"BLOCKED"* ]]
    [[ "$output" == *"--all affects ALL branches"* ]]
}

@test "blocks: filter-branch with --all in middle" {
    run "$HOOK" "git filter-branch --env-filter 'cmd' --all -- HEAD"
    [ "$status" -eq 2 ]
}

@test "blocks: rebase --all" {
    run "$HOOK" "git rebase --all"
    [ "$status" -eq 2 ]
}

# ============================================================================
# Warned Commands (exit 0 with warning)
# ============================================================================

@test "warns: git push -f" {
    run "$HOOK" "git push -f origin main"
    [ "$status" -eq 0 ]
    [[ "$output" == *"WARNING"* ]]
    [[ "$output" == *"Force push"* ]]
}

@test "warns: git push --force" {
    run "$HOOK" "git push --force origin main"
    [ "$status" -eq 0 ]
    [[ "$output" == *"WARNING"* ]]
}

@test "warns: checkout protected branch pattern v1" {
    run "$HOOK" "git checkout v1"
    [ "$status" -eq 0 ]
    [[ "$output" == *"WARNING"* ]]
    [[ "$output" == *"protected branch"* ]]
}

@test "warns: checkout main" {
    run "$HOOK" "git checkout main"
    [ "$status" -eq 0 ]
    [[ "$output" == *"WARNING"* ]]
}

@test "warns: checkout master" {
    run "$HOOK" "git checkout master"
    [ "$status" -eq 0 ]
    [[ "$output" == *"WARNING"* ]]
}

@test "warns: branch -d main" {
    run "$HOOK" "git branch -d main"
    [ "$status" -eq 0 ]
    [[ "$output" == *"WARNING"* ]]
}

@test "warns: branch -D v2" {
    run "$HOOK" "git branch -D v2"
    [ "$status" -eq 0 ]
    [[ "$output" == *"WARNING"* ]]
}

@test "warns: rebase main" {
    run "$HOOK" "git rebase main"
    [ "$status" -eq 0 ]
    [[ "$output" == *"WARNING"* ]]
}

@test "warns: reset on protected branch" {
    run "$HOOK" "git reset --hard main"
    [ "$status" -eq 0 ]
    [[ "$output" == *"WARNING"* ]]
}

@test "warns: checkout release-1.0" {
    run "$HOOK" "git checkout release-1.0"
    [ "$status" -eq 0 ]
    [[ "$output" == *"WARNING"* ]]
}

# ============================================================================
# Edge Cases
# ============================================================================

@test "allows: --force-with-lease (safe alternative)" {
    run "$HOOK" "git push --force-with-lease origin feature"
    [ "$status" -eq 0 ]
    # Script allows --force-with-lease without blocking (exit 0)
    # Note: current implementation may still warn since it contains --force
}

@test "allows: force in branch name (not force push)" {
    run "$HOOK" "git checkout force-update-feature"
    [ "$status" -eq 0 ]
    [[ "$output" != *"Force push"* ]]
}

@test "allows: main in non-protected context" {
    run "$HOOK" "git log main..HEAD"
    [ "$status" -eq 0 ]
    # log is not a protected operation
    [[ "$output" != *"WARNING"* ]]
}

@test "handles: command with special characters" {
    run "$HOOK" "git commit -m 'Fix: handle --all flag'"
    [ "$status" -eq 0 ]
}

@test "blocks: filter-branch with spaces around --all" {
    run "$HOOK" "git filter-branch   --all"
    [ "$status" -eq 2 ]
}
