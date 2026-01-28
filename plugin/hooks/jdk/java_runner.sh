#!/usr/bin/env bash
# java_runner.sh - Intermediary script to invoke Java-based hooks
#
# This script serves as a bridge between Claude Code's hook system
# (which executes shell commands) and Java-based hook implementations.
#
# Usage:
#   java_runner.sh <handler-class> [args...]
#
# Environment:
#   CAT_JAVA_HOME - Path to CAT's custom JDK runtime (set by session_start.sh)
#   JAVA_HOME     - Fallback to system Java
#
# The script:
#   1. Locates the Java binary (CAT runtime > JAVA_HOME > PATH)
#   2. Sets up the classpath for hook handlers
#   3. Invokes the specified handler class
#   4. Passes stdin and captures stdout/stderr appropriately
#
# Example:
#   echo '{"tool":"Bash","input":"..."}' | java_runner.sh BashPreToolHandler

set -euo pipefail

# --- Configuration ---

# Timeout for Java hook execution (seconds)
readonly JAVA_TIMEOUT="${CAT_JAVA_TIMEOUT:-30}"

# Memory limits for Java hooks
readonly JAVA_XMS="${CAT_JAVA_XMS:-16m}"
readonly JAVA_XMX="${CAT_JAVA_XMX:-64m}"

# --- Functions ---

find_java() {
    local java_bin

    # Priority 1: CAT's custom runtime (smallest, fastest startup)
    if [[ -n "${CAT_JAVA_HOME:-}" && -x "${CAT_JAVA_HOME}/bin/java" ]]; then
        echo "${CAT_JAVA_HOME}/bin/java"
        return 0
    fi

    # Priority 2: System JAVA_HOME
    if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
        echo "${JAVA_HOME}/bin/java"
        return 0
    fi

    # Priority 3: java on PATH
    if command -v java &>/dev/null; then
        command -v java
        return 0
    fi

    return 1
}

find_hooks_jar() {
    local script_dir
    script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

    # Check relative to script location
    local jar_path="${script_dir}/../java/cat-hooks.jar"
    if [[ -f "$jar_path" ]]; then
        echo "$jar_path"
        return 0
    fi

    # Check using CLAUDE_PLUGIN_ROOT
    if [[ -n "${CLAUDE_PLUGIN_ROOT:-}" ]]; then
        jar_path="${CLAUDE_PLUGIN_ROOT}/hooks/java/cat-hooks.jar"
        if [[ -f "$jar_path" ]]; then
            echo "$jar_path"
            return 0
        fi
    fi

    return 1
}

build_classpath() {
    local script_dir
    script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    local plugin_root="${CLAUDE_PLUGIN_ROOT:-${script_dir}/../..}"

    local classpath=""

    # Add the main hooks jar
    local hooks_jar
    if hooks_jar=$(find_hooks_jar); then
        classpath="$hooks_jar"
    fi

    # Add Jackson jars from the runtime lib directory
    local lib_dir="${plugin_root}/runtime/jackson-libs"
    if [[ -d "$lib_dir" ]]; then
        for jar in "$lib_dir"/*.jar; do
            [[ -f "$jar" ]] || continue
            [[ -n "$classpath" ]] && classpath+=":"
            classpath+="$jar"
        done
    fi

    # Also check for jars bundled with the custom JDK
    if [[ -n "${CAT_JAVA_HOME:-}" ]]; then
        local jdk_lib="${CAT_JAVA_HOME}/lib/jackson"
        if [[ -d "$jdk_lib" ]]; then
            for jar in "$jdk_lib"/*.jar; do
                [[ -f "$jar" ]] || continue
                [[ -n "$classpath" ]] && classpath+=":"
                classpath+="$jar"
            done
        fi
    fi

    echo "$classpath"
}

run_handler() {
    local handler_class="$1"
    shift

    local java_bin
    java_bin=$(find_java) || {
        echo '{"status":"error","message":"Java not found. Install JDK 25 or run CAT setup."}' >&2
        return 1
    }

    local classpath
    classpath=$(build_classpath)

    if [[ -z "$classpath" ]]; then
        echo '{"status":"error","message":"Hook classpath not found. CAT may not be fully installed."}' >&2
        return 1
    fi

    # Build Java command line
    local java_opts=(
        "-Xms${JAVA_XMS}"
        "-Xmx${JAVA_XMX}"
        "-XX:+UseSerialGC"
        "-XX:TieredStopAtLevel=1"
        "-Djava.security.egd=file:/dev/./urandom"
    )

    # Run the handler with timeout
    timeout "${JAVA_TIMEOUT}" "$java_bin" \
        "${java_opts[@]}" \
        -classpath "$classpath" \
        "cat.hooks.${handler_class}" \
        "$@"
}

# --- Main ---

main() {
    if [[ $# -lt 1 ]]; then
        echo "Usage: $0 <handler-class> [args...]" >&2
        echo "" >&2
        echo "Handler classes:" >&2
        echo "  BashPreToolHandler   - Pre-tool hook for Bash commands" >&2
        echo "  BashPostToolHandler  - Post-tool hook for Bash results" >&2
        echo "  SkillHandler         - Skill invocation preprocessing" >&2
        echo "  ValidationHandler    - Commit/code validation" >&2
        exit 1
    fi

    local handler_class="$1"
    shift

    # Validate handler class name (alphanumeric only)
    if [[ ! "$handler_class" =~ ^[A-Za-z][A-Za-z0-9]*$ ]]; then
        echo '{"status":"error","message":"Invalid handler class name"}' >&2
        exit 1
    fi

    run_handler "$handler_class" "$@"
}

main "$@"
