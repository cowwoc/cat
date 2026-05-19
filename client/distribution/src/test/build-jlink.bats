#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Tests for client/distribution/scripts/build-jlink-images.sh AOT error reporting behavior.
# These tests use a purpose-built AOT test harness script to exercise the
# generate_startup_archives logic in isolation.

# The AOT harness is a minimal script that mirrors the error-reporting pattern
# used by generate_startup_archives in build-jlink-images.sh. Testing with the harness
# keeps tests fast and eliminates the need for a real jlink build.

HARNESS="$BATS_TEST_DIRNAME/aot-harness.sh"
BUILD_JLINK="$BATS_TEST_DIRNAME/../../scripts/build-jlink-images.sh"

setup() {
    FAKE_BIN_DIR="$(mktemp -d)"
    # Setup for launcher generation tests
    OUTPUT_DIR="$(mktemp -d)"
    mkdir -p "$OUTPUT_DIR/bin"
    MODULE_NAME="io.github.cowwoc.cat.client"
    HANDLERS=("test-launcher:PreToolUseHook")
    ENABLE_ASSERTIONS=false
}

teardown() {
    rm -rf "${FAKE_BIN_DIR:-}"
    rm -rf "${OUTPUT_DIR:-}"
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

# ============================================================================
# Launcher generation tests
# ============================================================================

@test "launcher always contains CAT_JVM_OPTS expansion" {
    local test_output_dir="$OUTPUT_DIR"
    source "$BUILD_JLINK"
    OUTPUT_DIR="$test_output_dir"
    HANDLERS=("test-launcher:PreToolUseHook")
    ENABLE_ASSERTIONS=false
    generate_launchers

    launcher="$OUTPUT_DIR/bin/test-launcher"
    grep -q 'CAT_JVM_OPTS' "$launcher" || \
        { echo "Expected CAT_JVM_OPTS in launcher. Got:"; cat "$launcher"; false; }
}

@test "launcher without --enable-assertions does not contain -ea" {
    local test_output_dir="$OUTPUT_DIR"
    source "$BUILD_JLINK"
    OUTPUT_DIR="$test_output_dir"
    HANDLERS=("test-launcher:PreToolUseHook")
    ENABLE_ASSERTIONS=false
    generate_launchers

    launcher="$OUTPUT_DIR/bin/test-launcher"
    ! grep -q '\-ea' "$launcher" || \
        { echo "Expected no -ea in launcher without assertions. Got:"; cat "$launcher"; false; }
}

@test "launcher with --enable-assertions contains -ea" {
    local test_output_dir="$OUTPUT_DIR"
    source "$BUILD_JLINK"
    OUTPUT_DIR="$test_output_dir"
    HANDLERS=("test-launcher:PreToolUseHook")
    ENABLE_ASSERTIONS=true
    generate_launchers

    launcher="$OUTPUT_DIR/bin/test-launcher"
    grep -q '\-ea' "$launcher" || \
        { echo "Expected -ea in launcher with assertions. Got:"; cat "$launcher"; false; }
}

@test "migrated shared launcher maps to neutral entrypoint" {
    local test_output_dir="$OUTPUT_DIR"
    source "$BUILD_JLINK"
    OUTPUT_DIR="$test_output_dir"
    HANDLERS=(
        "token-counter:io.github.cowwoc.cat.tool.TokenCounter"
        "get-output:io.github.cowwoc.cat.tool.skills.GetOutput"
        "get-add-output:io.github.cowwoc.cat.tool.skills.GetAddOutput"
        "create-issue:io.github.cowwoc.cat.tool.util.IssueCreator"
        "git-squash:io.github.cowwoc.cat.tool.util.GitSquash"
        "git-merge-linear:io.github.cowwoc.cat.tool.util.GitMergeLinear"
        "git-amend:io.github.cowwoc.cat.tool.util.GitAmend"
        "git-rebase:io.github.cowwoc.cat.tool.util.GitRebase"
        "issue-lock:io.github.cowwoc.cat.tool.util.IssueLock"
        "check-existing-work:io.github.cowwoc.cat.tool.util.ExistingWorkChecker"
        "wrap-markdown:io.github.cowwoc.cat.tool.util.MarkdownWrapper"
        "batch-read:io.github.cowwoc.cat.tool.util.BatchReader"
        "validate-status-alignment:io.github.cowwoc.cat.tool.util.StatusAlignmentValidator"
        "feedback:io.github.cowwoc.cat.tool.util.Feedback"
        "write-session-marker:io.github.cowwoc.cat.tool.util.WriteSessionMarker"
        "read-session-marker:io.github.cowwoc.cat.tool.util.ReadSessionMarker"
        "auto-close-index:io.github.cowwoc.cat.tool.util.AutoCloseIndexJson"
        "verify-defer-plan-generation:io.github.cowwoc.cat.tool.util.VerifyDeferPlanGeneration"
        "write-and-commit:io.github.cowwoc.cat.tool.util.WriteAndCommit"
    )
    ENABLE_ASSERTIONS=false
    generate_launchers

    for handler in "${HANDLERS[@]}"; do
        launcher_name="${handler%%:*}"
        entrypoint="${handler#*:}"
        launcher="$OUTPUT_DIR/bin/$launcher_name"
        grep -Fq "$entrypoint" "$launcher" || \
            { echo "Expected neutral $launcher_name entrypoint. Got:"; cat "$launcher"; false; }
    done
}

@test "migrated shared launcher preserves direct class name at engine" {
    local test_output_dir="$OUTPUT_DIR"
    source "$BUILD_JLINK"
    OUTPUT_DIR="$test_output_dir"
    HANDLERS=("write-session-marker:io.github.cowwoc.cat.tool.util.WriteSessionMarker")
    ENABLE_ASSERTIONS=false
    generate_launchers

    cat > "$OUTPUT_DIR/bin/java" <<'EOF'
#!/bin/sh
printf '%s\n' "$@" > "$OUTPUT_DIR/java-args.txt"
EOF
    chmod +x "$OUTPUT_DIR/bin/java"
    export OUTPUT_DIR

    "$OUTPUT_DIR/bin/write-session-marker" example

    grep -Fq 'io.github.cowwoc.cat.common.cli/io.github.cowwoc.cat.tool.util.WriteSessionMarker' \
        "$OUTPUT_DIR/java-args.txt" || \
        { echo "Expected direct class in java args. Got:"; cat "$OUTPUT_DIR/java-args.txt"; false; }
}

@test "engine handler selection keeps common and engine-only launchers separate" {
    source "$BUILD_JLINK"

    set_engine_handlers claude
    printf '%s\n' "${HANDLERS[@]}" | grep -Fq 'token-counter:io.github.cowwoc.cat.tool.TokenCounter' || \
        { echo "Expected common handler in Claude engine"; false; }
    printf '%s\n' "${HANDLERS[@]}" | grep -Fq 'claude-runner:io.github.cowwoc.cat.claude.engine.ClaudeRunner' || \
        { echo "Expected Claude-only handler"; false; }
    printf '%s\n' "${HANDLERS[@]}" | grep -Fq 'sprt-runner:io.github.cowwoc.cat.claude.engine.ClaudeSprtRunner' || \
        { echo "Expected Claude sprt-runner handler"; false; }
    ! printf '%s\n' "${HANDLERS[@]}" | grep -Fq 'codex-runner:io.github.cowwoc.cat.codex.engine.CodexRunner' || \
        { echo "Did not expect Codex-only handler in Claude engine"; false; }
    ! printf '%s\n' "${HANDLERS[@]}" | grep -Fq 'sprt-runner:io.github.cowwoc.cat.codex.engine.CodexSprtRunner' || \
        { echo "Did not expect Codex sprt-runner handler in Claude engine"; false; }

    set_engine_handlers codex
    printf '%s\n' "${HANDLERS[@]}" | grep -Fq 'token-counter:io.github.cowwoc.cat.tool.TokenCounter' || \
        { echo "Expected common handler in Codex engine"; false; }
    printf '%s\n' "${HANDLERS[@]}" | grep -Fq 'codex-runner:io.github.cowwoc.cat.codex.engine.CodexRunner' || \
        { echo "Expected Codex-only handler"; false; }
    printf '%s\n' "${HANDLERS[@]}" | grep -Fq 'sprt-runner:io.github.cowwoc.cat.codex.engine.CodexSprtRunner' || \
        { echo "Expected Codex sprt-runner handler"; false; }
    ! printf '%s\n' "${HANDLERS[@]}" | grep -Fq 'claude-runner:io.github.cowwoc.cat.claude.engine.ClaudeRunner' || \
        { echo "Did not expect Claude-only handler in Codex engine"; false; }
    ! printf '%s\n' "${HANDLERS[@]}" | grep -Fq 'sprt-runner:io.github.cowwoc.cat.claude.engine.ClaudeSprtRunner' || \
        { echo "Did not expect Claude sprt-runner handler in Codex engine"; false; }
}

@test "codex engine launcher registry avoids claude implementation entrypoints" {
    local test_output_dir="$OUTPUT_DIR"
    source "$BUILD_JLINK"
    OUTPUT_DIR="$test_output_dir"
    MODULE_NAME="io.github.cowwoc.cat.codex.cli"
    set_engine_handlers codex
    ENABLE_ASSERTIONS=false
    generate_launchers

    for launcher in "$OUTPUT_DIR"/bin/*; do
        [ -f "$launcher" ] || continue
        ! grep -q "io.github.cowwoc.cat.claude" "$launcher" || \
            { echo "Codex launcher references Claude implementation: $launcher"; cat "$launcher"; false; }
    done
}

@test "automatic module patching describes each peer jar once" {
    source "$BUILD_JLINK"
    STAGING_DIR="$(mktemp -d)"
    PATCH_DIR="$STAGING_DIR/patches"
    DESCRIBE_LOG="$STAGING_DIR/describes.log"
    JDEPS_LOG="$STAGING_DIR/jdeps.log"
    UPDATE_LOG="$STAGING_DIR/updates.log"
    touch "$STAGING_DIR/alpha-auto.jar" "$STAGING_DIR/beta-auto.jar" "$STAGING_DIR/gamma-named.jar"

    export DESCRIBE_LOG JDEPS_LOG UPDATE_LOG
    cat > "$FAKE_BIN_DIR/jar" <<'EOF'
#!/bin/sh
mode=""
file=""
while [ "$#" -gt 0 ]; do
    case "$1" in
        --list|--describe-module|--update) mode="$1" ;;
        --file=*) file="${1#--file=}" ;;
        --file) shift; file="$1" ;;
    esac
    shift || true
done

case "$mode" in
    --list)
        case "$(basename "$file")" in
            gamma-named.jar) printf '%s\n' "module-info.class" ;;
        esac
        ;;
    --describe-module)
        printf '%s\n' "$file" >> "$DESCRIBE_LOG"
        case "$(basename "$file")" in
            alpha-auto.jar|beta-auto.jar) printf '%s automatic\n' "${file%.jar}" ;;
            gamma-named.jar) printf '%s\n' "module gamma.named {}" ;;
        esac
        ;;
    --update)
        printf '%s\n' "$file" >> "$UPDATE_LOG"
        ;;
esac
EOF

    cat > "$FAKE_BIN_DIR/jdeps" <<'EOF'
#!/bin/sh
output_dir=""
module_path=""
target_jar=""
while [ "$#" -gt 0 ]; do
    case "$1" in
        --generate-module-info)
            shift
            output_dir="$1"
            ;;
        --module-path)
            shift
            module_path="$1"
            ;;
        *.jar)
            target_jar="$1"
            ;;
    esac
    shift || true
done
printf '%s|%s\n' "$(basename "$target_jar")" "$module_path" >> "$JDEPS_LOG"
module_dir="$output_dir/generated.module"
mkdir -p "$module_dir"
printf '%s\n' "module generated.module {" "}" > "$module_dir/module-info.java"
EOF

    cat > "$FAKE_BIN_DIR/javac" <<'EOF'
#!/bin/sh
classes_dir=""
while [ "$#" -gt 0 ]; do
    if [ "$1" = "-d" ]; then
        shift
        classes_dir="$1"
    fi
    shift || true
done
mkdir -p "$classes_dir"
touch "$classes_dir/module-info.class"
EOF
    chmod +x "$FAKE_BIN_DIR/jar" "$FAKE_BIN_DIR/jdeps" "$FAKE_BIN_DIR/javac"
    PATH="$FAKE_BIN_DIR:$PATH"

    patch_automatic_modules

    [ "$(grep -c 'alpha-auto.jar' "$DESCRIBE_LOG")" -eq 1 ]
    [ "$(grep -c 'beta-auto.jar' "$DESCRIBE_LOG")" -eq 1 ]
    [ "$(grep -c 'gamma-named.jar' "$DESCRIBE_LOG")" -eq 1 ]

    grep -q '^alpha-auto.jar|' "$JDEPS_LOG"
    grep -q '^beta-auto.jar|' "$JDEPS_LOG"
    ! grep -q '^gamma-named.jar|' "$JDEPS_LOG" || \
        { echo "Named module should not be patched. Got:"; cat "$JDEPS_LOG"; false; }

    local alpha_path beta_path
    alpha_path="$(grep '^alpha-auto.jar|' "$JDEPS_LOG" | cut -d '|' -f 2-)"
    beta_path="$(grep '^beta-auto.jar|' "$JDEPS_LOG" | cut -d '|' -f 2-)"
    [[ "$alpha_path" == *"beta-auto.jar"* ]]
    [[ "$alpha_path" == *"gamma-named.jar"* ]]
    [[ "$alpha_path" != *"alpha-auto.jar"* ]]
    [[ "$beta_path" == *"alpha-auto.jar"* ]]
    [[ "$beta_path" == *"gamma-named.jar"* ]]
    [[ "$beta_path" != *"beta-auto.jar"* ]]

    grep -q 'alpha-auto.jar' "$UPDATE_LOG"
    grep -q 'beta-auto.jar' "$UPDATE_LOG"
    ! grep -q 'gamma-named.jar' "$UPDATE_LOG" || \
        { echo "Named module should not be updated. Got:"; cat "$UPDATE_LOG"; false; }
}

@test "verify_image reports engine-specific smoke launchers" {
    local test_output_dir="$OUTPUT_DIR"
    source "$BUILD_JLINK"
    OUTPUT_DIR="$test_output_dir"
    PRE_BASH_LOG="$OUTPUT_DIR/pre-bash.log"
    SESSION_START_LOG="$OUTPUT_DIR/session-start.log"
    STATUS_LOG="$OUTPUT_DIR/status.log"
    UPDATE_BRANCH_LOG="$OUTPUT_DIR/update-branch.log"
    export PRE_BASH_LOG SESSION_START_LOG STATUS_LOG UPDATE_BRANCH_LOG

    cat > "$OUTPUT_DIR/bin/java" <<'EOF'
#!/bin/sh
exit 0
EOF
    cat > "$OUTPUT_DIR/bin/pre-bash" <<'EOF'
#!/bin/sh
cat >/dev/null
printf '%s\n' "pre-bash" >> "$PRE_BASH_LOG"
exit 0
EOF
    cat > "$OUTPUT_DIR/bin/session-start" <<'EOF'
#!/bin/sh
cat >/dev/null
printf '%s\n' "session-start" >> "$SESSION_START_LOG"
printf '%s\n' '{"hookSpecificOutput":"ok"}'
EOF
cat > "$OUTPUT_DIR/bin/get-status-output" <<'EOF'
#!/bin/sh
cat >/dev/null
printf '%s\n' "status" >> "$STATUS_LOG"
printf '%s\n' "No CAT project found. Initialize one first."
EOF
    cat > "$OUTPUT_DIR/bin/update-branch" <<'EOF'
#!/bin/sh
printf '%s\n' "update-branch" >> "$UPDATE_BRANCH_LOG"
printf '%s\n' "Usage: update-branch"
exit 1
EOF
    chmod +x "$OUTPUT_DIR/bin/java" "$OUTPUT_DIR/bin/pre-bash" "$OUTPUT_DIR/bin/session-start" \
        "$OUTPUT_DIR/bin/get-status-output" "$OUTPUT_DIR/bin/update-branch"

    run verify_image claude

    [ "$status" -eq 0 ]
    [[ "$output" == *"Testing claude pre-bash launcher"* ]]
    [[ "$output" == *"Testing get-status-output launcher"* ]]
    [[ "$output" == *"Testing update-branch launcher"* ]]
    [ "$(grep -c 'pre-bash' "$PRE_BASH_LOG")" -eq 1 ]
    [ "$(grep -c 'status' "$STATUS_LOG")" -eq 1 ]
    [ "$(grep -c 'update-branch' "$UPDATE_BRANCH_LOG")" -eq 1 ]
    [ ! -f "$SESSION_START_LOG" ]

    run verify_image codex

    [ "$status" -eq 0 ]
    [[ "$output" == *"Testing codex pre-bash launcher"* ]]
    [[ "$output" == *"Testing codex session-start launcher"* ]]
    [[ "$output" == *"Testing get-status-output launcher"* ]]
    [[ "$output" == *"Testing update-branch launcher"* ]]
    [ "$(grep -c 'pre-bash' "$PRE_BASH_LOG")" -eq 2 ]
    [ "$(grep -c 'session-start' "$SESSION_START_LOG")" -eq 1 ]
    [ "$(grep -c 'status' "$STATUS_LOG")" -eq 2 ]
    [ "$(grep -c 'update-branch' "$UPDATE_BRANCH_LOG")" -eq 2 ]
}

@test "generate_startup_archives uses engine-specific AOT entrypoints and hook environment" {
    local test_output_dir="$OUTPUT_DIR"
    source "$BUILD_JLINK"
    OUTPUT_DIR="$test_output_dir"
    mkdir -p "$OUTPUT_DIR/bin" "$OUTPUT_DIR/lib/server"
    AOT_LOG="$OUTPUT_DIR/aot.log"
    export AOT_LOG

    cat > "$OUTPUT_DIR/bin/java" <<'EOF'
#!/bin/sh
mode=""
configuration=""
cache=""
module=""
while [ "$#" -gt 0 ]; do
    case "$1" in
        -XX:AOTMode=*) mode="${1#-XX:AOTMode=}" ;;
        -XX:AOTConfiguration=*) configuration="${1#-XX:AOTConfiguration=}" ;;
        -XX:AOTCache=*) cache="${1#-XX:AOTCache=}" ;;
        -m)
            shift
            module="$1"
            ;;
    esac
    shift || true
done
cat >/dev/null
printf '%s|%s|%s|%s|%s|%s|%s|%s\n' \
    "$mode" "$module" "${CLAUDE_PROJECT_DIR:-}" "${CLAUDE_PLUGIN_ROOT:-}" "${CLAUDE_PLUGIN_DATA:-}" \
    "${CLAUDE_CONFIG_DIR:-}" "${CODEX_THREAD_ID:-}" "${CODEX_HOME:-}" >> "$AOT_LOG"
case "$mode" in
    record) touch "$configuration" ;;
    create) touch "$cache" ;;
esac
EOF
    chmod +x "$OUTPUT_DIR/bin/java"

    generate_startup_archives claude
    generate_startup_archives codex

    grep -q 'record|io.github.cowwoc.cat.claude.cli/io.github.cowwoc.cat.claude.hook.AotTraining|' "$AOT_LOG"
    grep -q 'create|io.github.cowwoc.cat.claude.cli/io.github.cowwoc.cat.claude.hook.PreToolUseHook|' "$AOT_LOG"
    grep -q 'record|io.github.cowwoc.cat.codex.cli/io.github.cowwoc.cat.codex.hook.CodexAotTraining|' "$AOT_LOG"
    grep -q 'create|io.github.cowwoc.cat.codex.cli/io.github.cowwoc.cat.codex.hook.PreBashHook|' "$AOT_LOG"

    while IFS='|' read -r mode module claude_project claude_root claude_data claude_config codex_thread codex_home; do
        [ -n "$mode" ]
        [ -n "$module" ]
        if [[ "$module" == *".claude."* ]]; then
            [ "$claude_project" = "$WORKSPACE_DIR" ]
            [[ "$claude_root" == */plugin ]]
            [[ "$claude_data" == */aot-plugin-data ]]
            [[ "$claude_config" == */aot-config-home ]]
            [ -z "$codex_thread" ]
            [ -z "$codex_home" ]
        else
            [ -z "$claude_project" ]
            [ -z "$claude_root" ]
            [ -z "$claude_data" ]
            [ -z "$claude_config" ]
            [ "$codex_thread" = "aot-training-session" ]
            [[ "$codex_home" == */aot-config-home ]]
        fi
    done < "$AOT_LOG"
}

@test "generate_startup_archives reports engine-specific AOT cache creation failures" {
    local test_output_dir="$OUTPUT_DIR"
    source "$BUILD_JLINK"
    OUTPUT_DIR="$test_output_dir"
    mkdir -p "$OUTPUT_DIR/bin" "$OUTPUT_DIR/lib/server"

    cat > "$OUTPUT_DIR/bin/java" <<'EOF'
#!/bin/sh
mode=""
configuration=""
while [ "$#" -gt 0 ]; do
    case "$1" in
        -XX:AOTMode=*) mode="${1#-XX:AOTMode=}" ;;
        -XX:AOTConfiguration=*) configuration="${1#-XX:AOTConfiguration=}" ;;
    esac
    shift || true
done
cat >/dev/null
case "$mode" in
    record)
        touch "$configuration"
        exit 0
        ;;
    create)
        printf '%s\n' "cache creation failed" >&2
        exit 1
        ;;
esac
exit 0
EOF
    chmod +x "$OUTPUT_DIR/bin/java"

    run generate_startup_archives claude 2>&1

    [ "$status" -ne 0 ]
    [[ "$output" == *"cache creation failed"* ]] || \
        { echo "Expected JVM create error in output. Got: $output"; false; }
    [[ "$output" == *"Failed to create claude AOT cache"* ]] || \
        { echo "Expected claude cache creation failure. Got: $output"; false; }

    run generate_startup_archives codex 2>&1

    [ "$status" -ne 0 ]
    [[ "$output" == *"cache creation failed"* ]] || \
        { echo "Expected JVM create error in output. Got: $output"; false; }
    [[ "$output" == *"Failed to create codex AOT cache"* ]] || \
        { echo "Expected codex cache creation failure. Got: $output"; false; }
}

@test "build-jlink-images.sh exits non-zero on unknown argument" {
    run bash "$BUILD_JLINK" --unknown-flag

    [ "$status" -ne 0 ]
}

@test "engine descriptor writer creates codex plugin descriptor with version" {
    local test_output_root
    test_output_root="$(mktemp -d)"
    source "$BUILD_JLINK"
    OUTPUT_ROOT="$test_output_root"

    write_engine_plugin_descriptors "2.1"

    [ -f "$OUTPUT_ROOT/codex/.codex-plugin/plugin.json" ]
    grep -q '"version":"2.1"' "$OUTPUT_ROOT/codex/.codex-plugin/plugin.json"
}

@test "engine descriptor writer creates claude plugin descriptor with version" {
    local test_output_root
    test_output_root="$(mktemp -d)"
    source "$BUILD_JLINK"
    OUTPUT_ROOT="$test_output_root"

    write_engine_plugin_descriptors "2.1"

    [ -f "$OUTPUT_ROOT/claude/.claude-plugin/plugin.json" ]
    grep -q '"version":"2.1"' "$OUTPUT_ROOT/claude/.claude-plugin/plugin.json"
}
