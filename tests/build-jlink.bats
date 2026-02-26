#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Tests for client/build-jlink.sh AOT error reporting behavior.
# These tests use a purpose-built AOT test harness script to exercise the
# generate_startup_archives logic in isolation.

# The AOT harness is a minimal script that mirrors the error-reporting pattern
# used by generate_startup_archives in build-jlink.sh. Testing with the harness
# keeps tests fast and eliminates the need for a real jlink build.

HARNESS="$BATS_TEST_DIRNAME/aot-harness.sh"

setup() {
    FAKE_BIN_DIR="$(mktemp -d)"
}

teardown() {
    rm -rf "${FAKE_BIN_DIR:-}"
}

# Creates a fake java binary in FAKE_BIN_DIR/bin/java with configurable behavior.
#
# Parameters:
#   $1  exit code the fake java should return for -XX:AOTMode=record  (default 0)
#   $2  stderr message the fake java should emit  (default empty)
create_fake_java() {
    local record_exit="${1:-0}"
    local stderr_msg="${2:-}"
    local aot_conf_path="$FAKE_BIN_DIR/lib/server/aot-config.aotconf"

    mkdir -p "$FAKE_BIN_DIR/bin" "$FAKE_BIN_DIR/lib/server"

    cat > "$FAKE_BIN_DIR/bin/java" <<EOF
#!/bin/sh
for arg in "\$@"; do
    case "\$arg" in
        -XX:AOTMode=record)
            if [ -n "${stderr_msg}" ]; then
                echo "${stderr_msg}" >&2
            fi
            [ "${record_exit}" -eq 0 ] && touch "${aot_conf_path}"
            exit ${record_exit}
            ;;
        -XX:AOTMode=create)
            exit 0
            ;;
    esac
done
exit 0
EOF
    chmod +x "$FAKE_BIN_DIR/bin/java"
}

# ============================================================================
# AOT error output tests
# ============================================================================

@test "aot-harness: stderr from failing java appears in combined output" {
    create_fake_java 1 "CLAUDE_SESSION_ID is not set"

    run bash "$HARNESS" "$FAKE_BIN_DIR" 2>&1

    [[ "$output" == *"CLAUDE_SESSION_ID is not set"* ]] || \
        { echo "Expected JVM stderr in output. Got: $output"; false; }
}

@test "aot-harness: build fails when java AOT recording exits non-zero" {
    create_fake_java 1 "fatal JVM error"

    run bash "$HARNESS" "$FAKE_BIN_DIR"

    [ "$status" -ne 0 ]
}

@test "aot-harness: build reports script-level error when AOT recording fails" {
    create_fake_java 1 ""

    run bash "$HARNESS" "$FAKE_BIN_DIR" 2>&1

    [[ "$output" == *"Failed to record AOT"* ]] || \
        { echo "Expected 'Failed to record AOT' message. Got: $output"; false; }
}

@test "aot-harness: successful AOT recording does not fail the build" {
    create_fake_java 0 ""

    run bash "$HARNESS" "$FAKE_BIN_DIR"

    [ "$status" -eq 0 ]
}
