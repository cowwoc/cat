#!/usr/bin/env bash
# java.sh - Intermediary script to invoke Java-based hooks
#
# This script serves as a bridge between Claude Code's hook system
# (which executes shell commands) and Java-based hook implementations.
#
# Usage:
#   java.sh <handler-class> [args...]
#
# Environment:
#   CAT_JAVA_HOME - Path to CAT's custom JDK runtime (required, set by session_start.sh)
#
# The script:
#   1. Locates the Java binary from CAT_JAVA_HOME (jlinked runtime required)
#   2. Sets up the classpath for hook handlers
#   3. Invokes the specified handler class
#   4. Passes stdin and captures stdout/stderr appropriately
#
# Example:
#   echo '{"tool":"Bash","input":"..."}' | java.sh BashPreToolHandler

set -euo pipefail

# --- Configuration ---

# Timeout for Java hook execution (seconds)
readonly JAVA_TIMEOUT="${CAT_JAVA_TIMEOUT:-30}"

# Memory limits for Java hooks
readonly JAVA_XMS="${CAT_JAVA_XMS:-16m}"
readonly JAVA_XMX="${CAT_JAVA_XMX:-64m}"

# --- Functions ---

find_java() {
    # Require CAT's jlinked runtime - no fallback to system JDK
    if [[ -z "${CAT_JAVA_HOME:-}" ]]; then
        echo "Error: CAT_JAVA_HOME not set. Run session_start.sh first." >&2
        return 1
    fi

    local java_bin="${CAT_JAVA_HOME}/bin/java"
    if [[ ! -x "$java_bin" ]]; then
        echo "Error: Java binary not found at ${java_bin}" >&2
        echo "CAT's jlinked JDK runtime may not be installed." >&2
        return 1
    fi

    echo "$java_bin"
}

find_hooks_jar() {
    local script_dir
    script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

    # Priority 1: Maven target directory (development)
    local jar_path="${script_dir}/java/target/cat-hooks.jar"
    if [[ -f "$jar_path" ]]; then
        echo "$jar_path"
        return 0
    fi

    # Priority 2: Installed location (production)
    jar_path="${script_dir}/java/cat-hooks.jar"
    if [[ -f "$jar_path" ]]; then
        echo "$jar_path"
        return 0
    fi

    # Priority 3: Using CLAUDE_PLUGIN_ROOT
    if [[ -n "${CLAUDE_PLUGIN_ROOT:-}" ]]; then
        jar_path="${CLAUDE_PLUGIN_ROOT}/hooks/java/target/cat-hooks.jar"
        if [[ -f "$jar_path" ]]; then
            echo "$jar_path"
            return 0
        fi
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
    local plugin_root="${CLAUDE_PLUGIN_ROOT:-${script_dir}/..}"
    local java_dir="${script_dir}/java"

    local classpath=""

    # Add the main hooks jar
    local hooks_jar
    if hooks_jar=$(find_hooks_jar); then
        classpath="$hooks_jar"
    fi

    # Priority 1: Maven-resolved dependencies (development)
    # Check if mvnw exists and use it to get classpath
    if [[ -x "${java_dir}/mvnw" && -f "${java_dir}/pom.xml" ]]; then
        local maven_cp
        maven_cp=$("${java_dir}/mvnw" -f "${java_dir}/pom.xml" dependency:build-classpath \
            -Dmdep.outputFile=/dev/stdout -q 2>/dev/null | tail -1) || true
        if [[ -n "$maven_cp" ]]; then
            [[ -n "$classpath" ]] && classpath+=":"
            classpath+="$maven_cp"
            echo "$classpath"
            return 0
        fi
    fi

    # Priority 2: Jackson jars from runtime lib directory (production)
    local lib_dir="${plugin_root}/runtime/jackson-libs"
    if [[ -d "$lib_dir" ]]; then
        for jar in "$lib_dir"/*.jar; do
            [[ -f "$jar" ]] || continue
            [[ -n "$classpath" ]] && classpath+=":"
            classpath+="$jar"
        done
    fi

    # Priority 3: Jackson jars bundled with custom JDK (jlinked runtime)
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
        echo '{"status":"error","message":"CAT jlinked JDK not found. Run session_start.sh to install."}' >&2
        return 1
    }

    local classpath
    classpath=$(build_classpath)

    if [[ -z "$classpath" ]]; then
        echo '{"status":"error","message":"Hook classpath not found. CAT may not be fully installed."}' >&2
        return 1
    fi

    # Determine full class name (support fully qualified names)
    local full_class
    if [[ "$handler_class" == *.* ]]; then
        full_class="$handler_class"
    else
        full_class="io.github.cowwoc.cat.hooks.${handler_class}"
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
        "$full_class" \
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

    # Validate handler class name (alphanumeric with optional dots for fully qualified names)
    if [[ ! "$handler_class" =~ ^[A-Za-z][A-Za-z0-9.]*$ ]]; then
        echo '{"status":"error","message":"Invalid handler class name"}' >&2
        exit 1
    fi

    run_handler "$handler_class" "$@"
}

main "$@"
