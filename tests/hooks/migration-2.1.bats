#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Tests for plugin/migrations/2.1.sh (consolidated migration)

load '../test_helper'

setup() {
    setup_test_dir
    mkdir -p "$TEST_TEMP_DIR/.claude/cat"
    export CLAUDE_PLUGIN_ROOT="$PLUGIN_ROOT"
}

teardown() {
    teardown_test_dir
}

# ─── Phase 1: Merge In Progress into Pending ─────────────────────────────────

@test "2.1.sh phase 1: skips when no version STATE.md files exist" {
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.claude/cat/cat-config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
}

# ─── Phase 2: Rename status values ───────────────────────────────────────────

@test "2.1.sh phase 2: renames pending to open in STATE.md" {
    mkdir -p "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.1/test-issue/STATE.md" <<'EOF'
# State

- **Status:** pending
- **Progress:** 0%
EOF
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.claude/cat/cat-config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep "Status.*open" ".claude/cat/issues/v2/v2.1/test-issue/STATE.md"
    [ "$status" -eq 0 ]
}

@test "2.1.sh phase 2: renames completed to closed in STATE.md" {
    mkdir -p "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.1/test-issue"
    cat > "$TEST_TEMP_DIR/.claude/cat/issues/v2/v2.1/test-issue/STATE.md" <<'EOF'
# State

- **Status:** completed
- **Progress:** 100%
EOF
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.claude/cat/cat-config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run grep "Status.*closed" ".claude/cat/issues/v2/v2.1/test-issue/STATE.md"
    [ "$status" -eq 0 ]
}

# ─── Phase 3: Move version to VERSION file ───────────────────────────────────

@test "2.1.sh phase 3: moves last_migrated_version from config to VERSION file" {
    echo '{"last_migrated_version": "2.0", "trust": "medium"}' > "$TEST_TEMP_DIR/.claude/cat/cat-config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ -f ".claude/cat/VERSION" ]
    [ "$(cat .claude/cat/VERSION | tr -d '[:space:]')" = "2.0" ]
}

@test "2.1.sh phase 3: moves old version field from config to VERSION file" {
    echo '{"version": "1.10", "trust": "medium"}' > "$TEST_TEMP_DIR/.claude/cat/cat-config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ -f ".claude/cat/VERSION" ]
    [ "$(cat .claude/cat/VERSION | tr -d '[:space:]')" = "1.10" ]
}

@test "2.1.sh phase 3: removes version field from cat-config.json after migration" {
    echo '{"last_migrated_version": "2.0", "trust": "medium"}' > "$TEST_TEMP_DIR/.claude/cat/cat-config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    run jq -e '.last_migrated_version' ".claude/cat/cat-config.json"
    [ "$status" -ne 0 ]
}

@test "2.1.sh phase 3: skips when VERSION file already exists" {
    echo '{"last_migrated_version": "2.0", "trust": "medium"}' > "$TEST_TEMP_DIR/.claude/cat/cat-config.json"
    echo "2.0" > "$TEST_TEMP_DIR/.claude/cat/VERSION"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ "$(cat .claude/cat/VERSION)" = "2.0" ]
}

@test "2.1.sh phase 3: skips when no version field in config" {
    echo '{"trust": "medium"}' > "$TEST_TEMP_DIR/.claude/cat/cat-config.json"

    cd "$TEST_TEMP_DIR"
    run bash "$CLAUDE_PLUGIN_ROOT/migrations/2.1.sh"
    [ "$status" -eq 0 ]
    [ ! -f ".claude/cat/VERSION" ]
}
