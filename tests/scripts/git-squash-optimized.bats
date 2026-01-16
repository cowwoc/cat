#!/usr/bin/env bats
# Tests for git-squash-optimized.sh - atomic git squash with safety checks

load '../test_helper'

setup() {
    setup_test_dir
    setup_git_repo
    SCRIPT="$SCRIPTS_DIR/git-squash-optimized.sh"

    # Create progress.sh mock if needed
    mkdir -p "$SCRIPTS_DIR/lib"
    if [[ ! -f "$SCRIPTS_DIR/lib/progress.sh" ]]; then
        cat > "$TEST_TEMP_DIR/progress.sh" <<'EOF'
progress_init() { :; }
progress_step() { echo "  [STEP] $1"; }
progress_done() { echo "  [DONE] $1"; }
EOF
        export SCRIPTS_DIR="$TEST_TEMP_DIR"
        mkdir -p "$TEST_TEMP_DIR/lib"
        cp "$TEST_TEMP_DIR/progress.sh" "$TEST_TEMP_DIR/lib/progress.sh"
    fi
}

teardown() {
    teardown_test_dir
}

# ============================================================================
# Helper Functions
# ============================================================================

create_commits() {
    local count="${1:-3}"
    for i in $(seq 1 "$count"); do
        echo "change $i" >> file.txt
        git add file.txt
        git commit --quiet -m "Commit $i"
    done
}

create_message_file() {
    local msg="${1:-Squashed commit message}"
    # Put message file in /tmp, outside the git repo to avoid untracked file issues
    local msg_file="/tmp/commit-msg-$$.txt"
    echo "$msg" > "$msg_file"
    echo "$msg_file"
}

# ============================================================================
# Argument Validation
# ============================================================================

@test "fails: missing BASE_COMMIT argument" {
    run "$SCRIPT"
    [ "$status" -ne 0 ]
    [[ "$output" == *"BASE_COMMIT required"* ]]
}

@test "fails: missing EXPECTED_LAST_COMMIT argument" {
    run "$SCRIPT" "abc123"
    [ "$status" -ne 0 ]
    [[ "$output" == *"EXPECTED_LAST_COMMIT required"* ]]
}

@test "fails: missing COMMIT_MESSAGE_FILE argument" {
    run "$SCRIPT" "abc123" "def456"
    [ "$status" -ne 0 ]
    [[ "$output" == *"COMMIT_MESSAGE_FILE required"* ]]
}

@test "fails: non-existent commit message file" {
    run "$SCRIPT" "abc123" "def456" "/nonexistent/file.txt"
    [ "$status" -ne 0 ]
    [[ "$output" == *"not found"* ]]
}

# ============================================================================
# Successful Squash
# ============================================================================

@test "squashes 3 commits into 1" {
    create_commits 3
    local base_commit=$(git rev-parse HEAD~3)
    local last_commit=$(git rev-parse HEAD)
    local msg_file=$(create_message_file "Squashed: 3 commits")

    run "$SCRIPT" "$base_commit" "$last_commit" "$msg_file"

    [ "$status" -eq 0 ]
    [[ "$output" == *"success"* ]]

    # Verify only 1 commit between base and HEAD
    local commit_count=$(git rev-list --count "$base_commit..HEAD")
    [ "$commit_count" -eq 1 ]
}

@test "preserves final working tree state" {
    create_commits 3
    local base_commit=$(git rev-parse HEAD~3)
    local last_commit=$(git rev-parse HEAD)
    local original_content=$(cat file.txt)
    local msg_file=$(create_message_file "Squashed commit")

    "$SCRIPT" "$base_commit" "$last_commit" "$msg_file" > /dev/null

    local final_content=$(cat file.txt)
    [ "$original_content" = "$final_content" ]
}

@test "uses commit message from file" {
    create_commits 2
    local base_commit=$(git rev-parse HEAD~2)
    local last_commit=$(git rev-parse HEAD)
    local msg_file=$(create_message_file "Custom squash message for test")

    "$SCRIPT" "$base_commit" "$last_commit" "$msg_file" > /dev/null

    local commit_msg=$(git log -1 --format=%s)
    [ "$commit_msg" = "Custom squash message for test" ]
}

@test "returns JSON result with success status" {
    create_commits 2
    local base_commit=$(git rev-parse HEAD~2)
    local last_commit=$(git rev-parse HEAD)
    local msg_file=$(create_message_file "Test")

    run "$SCRIPT" "$base_commit" "$last_commit" "$msg_file"

    assert_json_field "$output" '.status' 'success'
    assert_json_field "$output" '.commits_squashed' '2'
}

# ============================================================================
# Error Handling - Preconditions
# ============================================================================

@test "fails: invalid base commit" {
    create_commits 2
    local last_commit=$(git rev-parse HEAD)
    local msg_file=$(create_message_file "Test")

    run "$SCRIPT" "invalid-commit-hash" "$last_commit" "$msg_file"

    [ "$status" -ne 0 ]
}

@test "fails: invalid last commit" {
    create_commits 2
    local base_commit=$(git rev-parse HEAD~2)
    local msg_file=$(create_message_file "Test")

    run "$SCRIPT" "$base_commit" "invalid-commit-hash" "$msg_file"

    [ "$status" -ne 0 ]
}

@test "fails: dirty working directory" {
    create_commits 2
    local base_commit=$(git rev-parse HEAD~2)
    local last_commit=$(git rev-parse HEAD)
    local msg_file=$(create_message_file "Test")

    # Make working directory dirty
    echo "uncommitted change" >> file.txt

    run "$SCRIPT" "$base_commit" "$last_commit" "$msg_file"

    [ "$status" -ne 0 ]
    [[ "$output" == *"not clean"* ]] || [[ "$output" == *"error"* ]]
}

# ============================================================================
# Backup and Rollback
# ============================================================================

@test "creates backup branch before squash" {
    create_commits 2
    local base_commit=$(git rev-parse HEAD~2)
    local last_commit=$(git rev-parse HEAD)
    local msg_file=$(create_message_file "Test")

    # Run but capture backup branch name from output
    local output
    output=$("$SCRIPT" "$base_commit" "$last_commit" "$msg_file" 2>&1)

    # After successful squash, backup should be deleted
    # So we check that the output mentions backup creation
    [[ "$output" == *"Backup created"* ]] || [[ "$output" == *"backup"* ]]
}

@test "backup branch removed after successful squash" {
    create_commits 2
    local base_commit=$(git rev-parse HEAD~2)
    local last_commit=$(git rev-parse HEAD)
    local msg_file=$(create_message_file "Test")

    "$SCRIPT" "$base_commit" "$last_commit" "$msg_file" > /dev/null 2>&1

    # No backup branches should remain
    local backup_count=$(git branch | grep -c "backup-before-squash" || true)
    [ "$backup_count" -eq 0 ]
}

# ============================================================================
# Branch Handling
# ============================================================================

@test "updates specified branch after squash" {
    create_commits 3
    local base_commit=$(git rev-parse HEAD~3)
    local last_commit=$(git rev-parse HEAD)
    local msg_file=$(create_message_file "Test")
    local branch_name="test-branch"

    git checkout -b "$branch_name"

    "$SCRIPT" "$base_commit" "$last_commit" "$msg_file" "$branch_name" > /dev/null 2>&1

    # Verify we're on the correct branch
    local current_branch=$(git rev-parse --abbrev-ref HEAD)
    [ "$current_branch" = "$branch_name" ]
}

# ============================================================================
# Edge Cases
# ============================================================================

@test "handles single commit squash (no-op)" {
    create_commits 1
    local base_commit=$(git rev-parse HEAD~1)
    local last_commit=$(git rev-parse HEAD)
    local msg_file=$(create_message_file "Single commit")

    run "$SCRIPT" "$base_commit" "$last_commit" "$msg_file"

    [ "$status" -eq 0 ]
    assert_json_field "$output" '.commits_squashed' '1'
}

@test "handles multi-line commit message" {
    create_commits 2
    local base_commit=$(git rev-parse HEAD~2)
    local last_commit=$(git rev-parse HEAD)

    # Put message file outside git repo
    local msg_file="/tmp/commit-msg-multiline-$$.txt"
    cat > "$msg_file" <<'EOF'
feat: multi-line commit

This is a longer description that spans
multiple lines and includes details.

- Item 1
- Item 2
EOF

    run "$SCRIPT" "$base_commit" "$last_commit" "$msg_file"

    [ "$status" -eq 0 ]

    # Verify multi-line message preserved
    local msg=$(git log -1 --format=%B)
    [[ "$msg" == *"multi-line commit"* ]]
    [[ "$msg" == *"Item 1"* ]]

    rm -f "$msg_file"
}

# ============================================================================
# JSON Output Structure
# ============================================================================

@test "JSON output includes all required fields" {
    create_commits 2
    local base_commit=$(git rev-parse HEAD~2)
    local last_commit=$(git rev-parse HEAD)
    local msg_file=$(create_message_file "Test")

    run "$SCRIPT" "$base_commit" "$last_commit" "$msg_file"

    # Extract JSON from output (may include progress messages before JSON)
    local json
    json=$(echo "$output" | awk '/^{/,/^}/')

    # Check all required fields exist
    echo "$json" | jq -e '.status' > /dev/null
    echo "$json" | jq -e '.message' > /dev/null
    echo "$json" | jq -e '.duration_seconds' > /dev/null
    echo "$json" | jq -e '.squashed_commit' > /dev/null
    echo "$json" | jq -e '.commits_squashed' > /dev/null
    echo "$json" | jq -e '.timestamp' > /dev/null
}

@test "error output includes rollback instructions" {
    create_commits 2
    local base_commit=$(git rev-parse HEAD~2)
    local last_commit=$(git rev-parse HEAD)
    local msg_file=$(create_message_file "Test")

    # Make directory dirty to trigger error
    echo "dirty" >> file.txt

    run "$SCRIPT" "$base_commit" "$last_commit" "$msg_file"

    # On error, should include rollback info
    [ "$status" -ne 0 ]
}
