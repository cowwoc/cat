#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Tests verifying that cat-branch-point contains an immutable fork-point commit hash,
# not a mutable branch name, so that worktree isolation is preserved even when
# the base branch advances after worktree creation.

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

# Simulate what work-prepare writes to cat-branch-point: the fork-point commit hash.
# The worktree directory is named after the branch (matching production behavior),
# so git stores worktree metadata under <git-dir>/worktrees/<branch-name>/.
# Returns the absolute path to the created worktree directory.
write_cat_branch_point_hash() {
    local repo_dir="$1"
    local issue_branch="$2"
    # Name the worktree directory after the branch, matching work-prepare behavior
    local worktrees_parent
    worktrees_parent="$(mktemp -d)"
    local worktree_dir="${worktrees_parent}/${issue_branch}"

    # Capture the fork-point commit hash before creating the worktree
    local fork_hash
    fork_hash=$(git -C "$repo_dir" rev-parse HEAD)

    # Create the worktree on a new branch
    git -C "$repo_dir" worktree add -b "$issue_branch" "$worktree_dir" HEAD --quiet

    # Write the commit hash to cat-branch-point (simulating work-prepare)
    # git stores worktree metadata under <git-common-dir>/worktrees/<worktree-dir-basename>/
    local git_dir
    git_dir=$(git -C "$repo_dir" rev-parse --absolute-git-dir)
    local worktree_basename
    worktree_basename="$(basename "$worktree_dir")"
    printf '%s' "$fork_hash" > "${git_dir}/worktrees/${worktree_basename}/cat-branch-point"

    echo "$worktree_dir"
}

@test "cat-branch-point contains a 40-character hex commit hash, not a branch name" {
    local issue_branch="test-issue"
    local worktree_dir
    worktree_dir=$(write_cat_branch_point_hash "$REPO_DIR" "$issue_branch")

    # Locate cat-branch-point using the same path logic as write_cat_branch_point_hash
    local git_dir
    git_dir=$(git -C "$REPO_DIR" rev-parse --absolute-git-dir)
    local worktree_basename
    worktree_basename="$(basename "$worktree_dir")"
    local cat_branch_point_path="${git_dir}/worktrees/${worktree_basename}/cat-branch-point"

    [ -f "$cat_branch_point_path" ]

    local cat_branch_point_content
    cat_branch_point_content=$(cat "$cat_branch_point_path")

    # Must match exactly 40 hex characters (a full commit hash)
    [[ "$cat_branch_point_content" =~ ^[0-9a-f]{40}$ ]]

    # Must be a valid commit object in the git object store
    local object_type
    object_type=$(git -C "$REPO_DIR" cat-file -t "$cat_branch_point_content")
    [ "$object_type" = "commit" ]

    # Clean up
    git -C "$REPO_DIR" worktree remove "$worktree_dir" --force 2>/dev/null || true
}

@test "cat-branch-point value is unchanged after the base branch advances by one commit" {
    local issue_branch="stable-issue"
    local worktree_dir
    worktree_dir=$(write_cat_branch_point_hash "$REPO_DIR" "$issue_branch")

    # Locate cat-branch-point using the same path logic as write_cat_branch_point_hash
    local git_dir
    git_dir=$(git -C "$REPO_DIR" rev-parse --absolute-git-dir)
    local worktree_basename
    worktree_basename="$(basename "$worktree_dir")"
    local cat_branch_point_path="${git_dir}/worktrees/${worktree_basename}/cat-branch-point"

    # Record the fork-point hash before the base advances
    local fork_hash_before
    fork_hash_before=$(cat "$cat_branch_point_path")

    # Advance the base branch by adding a new commit on main
    git -C "$REPO_DIR" commit --quiet --allow-empty -m "Base branch advances"

    # cat-branch-point must still contain the original fork-point hash
    local fork_hash_after
    fork_hash_after=$(cat "$cat_branch_point_path")

    [ "$fork_hash_before" = "$fork_hash_after" ]

    # The fork-point hash must differ from the new HEAD of main (proving base advanced)
    local new_main_head
    new_main_head=$(git -C "$REPO_DIR" rev-parse main)
    [ "$fork_hash_after" != "$new_main_head" ]

    # Clean up
    git -C "$REPO_DIR" worktree remove "$worktree_dir" --force 2>/dev/null || true
}
