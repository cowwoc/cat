#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Tests verifying that git worktree detection uses the git directory structure
# (git dir parent named "worktrees") rather than a sentinel file.

setup() {
    REPO_DIR="$(mktemp -d)"
    git -C "$REPO_DIR" init --quiet --initial-branch=main
    git -C "$REPO_DIR" config user.email "test@test.com"
    git -C "$REPO_DIR" config user.name "Test User"
    git -C "$REPO_DIR" config core.autocrlf false
    git -C "$REPO_DIR" config advice.detachedHead false
    # Create initial commit on main
    git -C "$REPO_DIR" commit --quiet --allow-empty -m "Initial commit"
}

teardown() {
    git -C "$REPO_DIR" worktree prune 2>/dev/null || true
    rm -rf "${REPO_DIR:-}"
}

# Returns 0 if the given directory is inside a git worktree (git dir parent == "worktrees"), 1 otherwise.
is_cat_worktree() {
    local dir="$1"
    local git_dir
    git_dir=$(git -C "$dir" rev-parse --git-dir 2>/dev/null) || return 1
    # Resolve relative path
    if [[ "$git_dir" != /* ]]; then
        git_dir="${dir}/${git_dir}"
    fi
    local parent_name
    parent_name=$(basename "$(dirname "$git_dir")")
    [ "$parent_name" = "worktrees" ]
}

@test "main workspace is not identified as a CAT worktree" {
    # The main repo has .git/ directly — parent of .git is the repo root, not "worktrees"
    run is_cat_worktree "$REPO_DIR"
    [ "$status" -ne 0 ]
}

@test "git worktree is identified as a CAT worktree" {
    local worktrees_parent
    worktrees_parent="$(mktemp -d)"
    local worktree_dir="${worktrees_parent}/test-issue"

    git -C "$REPO_DIR" worktree add -b test-issue "$worktree_dir" HEAD --quiet

    # The worktree's git dir is .git/worktrees/test-issue/ — parent is "worktrees"
    run is_cat_worktree "$worktree_dir"
    [ "$status" -eq 0 ]

    git -C "$REPO_DIR" worktree remove "$worktree_dir" --force 2>/dev/null || true
}

@test "worktree detection is stable after base branch advances" {
    local worktrees_parent
    worktrees_parent="$(mktemp -d)"
    local worktree_dir="${worktrees_parent}/stable-issue"

    git -C "$REPO_DIR" worktree add -b stable-issue "$worktree_dir" HEAD --quiet

    # Advance the base branch
    git -C "$REPO_DIR" commit --quiet --allow-empty -m "Base branch advances"

    # Worktree detection must still work after base branch moves
    run is_cat_worktree "$worktree_dir"
    [ "$status" -eq 0 ]

    git -C "$REPO_DIR" worktree remove "$worktree_dir" --force 2>/dev/null || true
}
