#!/usr/bin/env bash
# session_start.sh - SessionStart hook to bootstrap the CAT JDK runtime
#
# This hook runs at the start of each Claude Code session and ensures
# the custom JDK runtime is available for Java hooks.
#
# Behavior:
#   1. Check if cat-hooks-2.1.jar exists in the plugin cache
#   2. If missing, download pre-built bundle from release artifacts
#   3. Verify the runtime is functional
#   4. Export CAT_JAVA_HOME for use by hook.sh
#
# The hook is silent on success (only outputs JSON). On failure, it
# provides instructions for manual setup.

set -euo pipefail

# --- Configuration ---

# Where the hooks JAR lives (relative to plugin root)
readonly HOOKS_JAR_SUBPATH="hooks/cat-hooks-2.1.jar"

# Where the custom JDK runtime lives (relative to plugin root)
readonly JDK_SUBDIR="runtime/cat-jdk-25"

# Base URL for downloading pre-built runtime bundles
readonly DOWNLOAD_BASE_URL="https://github.com/anthropics/claude-code-cat/releases/download"

# Expected version tag for the runtime bundle
readonly RUNTIME_VERSION="v1.0.0"

# Debug log accumulator
DEBUG_LINES=""

# Platform detection
get_platform() {
    local os arch

    case "$(uname -s)" in
        Linux*)  os="linux" ;;
        Darwin*) os="macos" ;;
        MINGW*|MSYS*|CYGWIN*) os="windows" ;;
        *)
            echo "unknown"
            return 1
            ;;
    esac

    case "$(uname -m)" in
        x86_64|amd64) arch="x64" ;;
        aarch64|arm64) arch="aarch64" ;;
        *)
            echo "unknown"
            return 1
            ;;
    esac

    echo "${os}-${arch}"
}

# --- Functions ---

debug() {
    if [[ -n "$DEBUG_LINES" ]]; then
        DEBUG_LINES="${DEBUG_LINES}\\n$*"
    else
        DEBUG_LINES="$*"
    fi
}

log_json() {
    local status="$1"
    local message="$2"
    local additional_context="${3:-}"

    # Append debug trace to additionalContext
    if [[ -n "$DEBUG_LINES" ]]; then
        local debug_block="[session_start debug]\\n${DEBUG_LINES}"
        if [[ -n "$additional_context" ]]; then
            additional_context="${additional_context}\\n\\n${debug_block}"
        else
            additional_context="$debug_block"
        fi
    fi

    # Build JSON output for Claude Code hook system
    if [[ -n "$additional_context" ]]; then
        cat <<EOF
{
  "status": "$status",
  "message": "$message",
  "hookSpecificOutput": {
    "hookEventName": "SessionStart",
    "additionalContext": "$additional_context"
  }
}
EOF
    else
        cat <<EOF
{
  "status": "$status",
  "message": "$message"
}
EOF
    fi
}

check_existing_runtime() {
    local jdk_path="$1"

    if [[ ! -d "$jdk_path" ]]; then
        debug "JDK directory does not exist: $jdk_path"
        return 1
    fi

    local java_bin="${jdk_path}/bin/java"
    if [[ ! -x "$java_bin" ]]; then
        debug "java binary not executable or missing: $java_bin"
        return 1
    fi

    # Verify it can run
    if ! "$java_bin" -version &>/dev/null; then
        debug "java binary exists but failed to run: $java_bin"
        return 1
    fi

    debug "JDK runtime verified at: $jdk_path"
    return 0
}

download_runtime() {
    local target_dir="$1"
    local platform
    platform=$(get_platform) || {
        debug "ERROR: Unsupported platform"
        return 1
    }

    local archive_name="cat-jdk-25-${platform}.tar.gz"
    local download_url="${DOWNLOAD_BASE_URL}/${RUNTIME_VERSION}/${archive_name}"
    local temp_archive="/tmp/${archive_name}"

    debug "Downloading CAT JDK runtime for ${platform} from ${download_url}"

    if ! curl -sSfL -o "$temp_archive" "$download_url" 2>/dev/null; then
        debug "Failed to download runtime from $download_url"
        return 1
    fi

    # Create parent directory
    mkdir -p "$(dirname "$target_dir")"

    # Extract archive
    if ! tar -xzf "$temp_archive" -C "$(dirname "$target_dir")" 2>/dev/null; then
        debug "Failed to extract runtime archive"
        rm -f "$temp_archive"
        return 1
    fi

    rm -f "$temp_archive"

    debug "CAT JDK runtime installed to $target_dir"
}

build_runtime_locally() {
    local plugin_root="$1"
    local target_dir="$2"

    local build_script="${plugin_root}/hooks/jlink-config.sh"

    if [[ ! -x "$build_script" ]]; then
        debug "Build script not found or not executable: $build_script"
        return 1
    fi

    debug "Building CAT JDK runtime locally via $build_script"

    local runtime_parent
    runtime_parent="$(dirname "$target_dir")"
    mkdir -p "$runtime_parent"

    if ! "$build_script" build --output-dir "$runtime_parent" 2>&1; then
        debug "Failed to build runtime"
        return 1
    fi
}

# --- Main ---

main() {
    # Determine plugin root from script location
    local script_dir
    script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    local plugin_root="${script_dir}/.."
    debug "script_dir=$script_dir"
    debug "plugin_root (from script location)=$plugin_root"

    # Use CLAUDE_PLUGIN_ROOT if set (preferred)
    if [[ -n "${CLAUDE_PLUGIN_ROOT:-}" ]]; then
        plugin_root="$CLAUDE_PLUGIN_ROOT"
        debug "plugin_root (from CLAUDE_PLUGIN_ROOT)=$plugin_root"
    else
        debug "CLAUDE_PLUGIN_ROOT not set, using script-relative path"
    fi

    # Check if hooks JAR exists in the plugin cache
    local hooks_jar="${plugin_root}/${HOOKS_JAR_SUBPATH}"
    debug "Checking hooks JAR at: $hooks_jar"
    if [[ ! -f "$hooks_jar" ]]; then
        debug "JAR not found - exiting early with warning"
        log_json "warning" "Java hooks JAR not found at ${hooks_jar}. Run /build-hooks to build and install it."
        return 0
    fi
    debug "JAR found"

    local jdk_path="${plugin_root}/${JDK_SUBDIR}"
    debug "JDK path: $jdk_path"

    # Check if runtime already exists and is functional
    debug "Checking existing runtime..."
    if check_existing_runtime "$jdk_path"; then
        # Export for hook.sh
        export CAT_JAVA_HOME="$jdk_path"
        log_json "success" "CAT JDK runtime ready"
        return 0
    fi

    # Try to download pre-built runtime
    debug "Attempting download..."
    if download_runtime "$jdk_path"; then
        if check_existing_runtime "$jdk_path"; then
            export CAT_JAVA_HOME="$jdk_path"
            log_json "success" "CAT JDK runtime downloaded and ready"
            return 0
        fi
        debug "Download succeeded but runtime check failed"
    else
        debug "Download failed"
    fi

    # Fallback: Try to build locally (requires full JDK)
    debug "Attempting local build..."
    if build_runtime_locally "$plugin_root" "$jdk_path"; then
        if check_existing_runtime "$jdk_path"; then
            export CAT_JAVA_HOME="$jdk_path"
            log_json "success" "CAT JDK runtime built locally and ready"
            return 0
        fi
        debug "Local build succeeded but runtime check failed"
    else
        debug "Local build failed"
    fi

    # All methods failed - provide instructions
    debug "All methods failed - returning warning"
    local platform
    platform=$(get_platform 2>/dev/null) || platform="your-platform"

    log_json "warning" "CAT JDK runtime not available. Java hooks will use system Java if available." \
        "To enable optimized Java hooks, either:\\n1. Install JDK 25 and run: ${plugin_root}/hooks/jlink-config.sh build\\n2. Download pre-built runtime from GitHub releases"

    return 0  # Don't block session on missing runtime
}

main "$@"
