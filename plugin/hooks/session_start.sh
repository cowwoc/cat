#!/usr/bin/env bash
# session_start.sh - SessionStart hook to bootstrap the CAT JDK runtime
#
# This hook runs at the start of each Claude Code session and ensures
# the custom JDK runtime is available for Java hooks.
#
# Behavior:
#   1. Check if custom JDK exists in the expected location
#   2. If missing, download pre-built bundle from release artifacts
#   3. Verify the runtime is functional
#   4. Export CAT_JAVA_HOME for use by java.sh
#
# The hook is silent on success (only outputs JSON). On failure, it
# provides instructions for manual setup.

set -euo pipefail

# --- Configuration ---

# Where the custom JDK runtime lives (relative to plugin root)
readonly JDK_SUBDIR="runtime/cat-jdk-25"

# Base URL for downloading pre-built runtime bundles
readonly DOWNLOAD_BASE_URL="https://github.com/anthropics/claude-code-cat/releases/download"

# Expected version tag for the runtime bundle
readonly RUNTIME_VERSION="v1.0.0"

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

log_json() {
    local status="$1"
    local message="$2"
    local additional_context="${3:-}"

    # Build JSON output for Claude Code hook system
    cat <<EOF
{
  "status": "$status",
  "message": "$message"$([ -n "$additional_context" ] && echo ",
  \"additionalContext\": \"$additional_context\"")
}
EOF
}

check_existing_runtime() {
    local jdk_path="$1"

    if [[ ! -d "$jdk_path" ]]; then
        return 1
    fi

    local java_bin="${jdk_path}/bin/java"
    if [[ ! -x "$java_bin" ]]; then
        return 1
    fi

    # Verify it can run
    if ! "$java_bin" -version &>/dev/null; then
        return 1
    fi

    return 0
}

download_runtime() {
    local target_dir="$1"
    local platform
    platform=$(get_platform) || {
        echo "ERROR: Unsupported platform" >&2
        return 1
    }

    local archive_name="cat-jdk-25-${platform}.tar.gz"
    local download_url="${DOWNLOAD_BASE_URL}/${RUNTIME_VERSION}/${archive_name}"
    local temp_archive="/tmp/${archive_name}"

    echo "Downloading CAT JDK runtime for ${platform}..." >&2

    if ! curl -sSfL -o "$temp_archive" "$download_url" 2>/dev/null; then
        echo "ERROR: Failed to download runtime from $download_url" >&2
        return 1
    fi

    # Create parent directory
    mkdir -p "$(dirname "$target_dir")"

    # Extract archive
    if ! tar -xzf "$temp_archive" -C "$(dirname "$target_dir")" 2>/dev/null; then
        echo "ERROR: Failed to extract runtime archive" >&2
        rm -f "$temp_archive"
        return 1
    fi

    rm -f "$temp_archive"

    echo "CAT JDK runtime installed to $target_dir" >&2
}

build_runtime_locally() {
    local plugin_root="$1"
    local target_dir="$2"

    local build_script="${plugin_root}/hooks/jlink-config.sh"

    if [[ ! -x "$build_script" ]]; then
        echo "ERROR: Build script not found: $build_script" >&2
        return 1
    fi

    echo "Building CAT JDK runtime locally..." >&2

    local runtime_parent
    runtime_parent="$(dirname "$target_dir")"
    mkdir -p "$runtime_parent"

    if ! "$build_script" build --output-dir "$runtime_parent" 2>&1; then
        echo "ERROR: Failed to build runtime" >&2
        return 1
    fi
}

# --- Main ---

main() {
    # Determine plugin root from script location
    local script_dir
    script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    local plugin_root="${script_dir}/.."

    # Use CLAUDE_PLUGIN_ROOT if set (preferred)
    if [[ -n "${CLAUDE_PLUGIN_ROOT:-}" ]]; then
        plugin_root="$CLAUDE_PLUGIN_ROOT"
    fi

    local jdk_path="${plugin_root}/${JDK_SUBDIR}"

    # Check if runtime already exists and is functional
    if check_existing_runtime "$jdk_path"; then
        # Export for java.sh
        export CAT_JAVA_HOME="$jdk_path"
        log_json "success" "CAT JDK runtime ready"
        return 0
    fi

    # Try to download pre-built runtime
    if download_runtime "$jdk_path"; then
        if check_existing_runtime "$jdk_path"; then
            export CAT_JAVA_HOME="$jdk_path"
            log_json "success" "CAT JDK runtime downloaded and ready"
            return 0
        fi
    fi

    # Fallback: Try to build locally (requires full JDK)
    if build_runtime_locally "$plugin_root" "$jdk_path"; then
        if check_existing_runtime "$jdk_path"; then
            export CAT_JAVA_HOME="$jdk_path"
            log_json "success" "CAT JDK runtime built locally and ready"
            return 0
        fi
    fi

    # All methods failed - provide instructions
    local platform
    platform=$(get_platform 2>/dev/null) || platform="your-platform"

    log_json "warning" "CAT JDK runtime not available. Java hooks will use system Java if available." \
        "To enable optimized Java hooks, either:\\n1. Install JDK 25 and run: ${plugin_root}/hooks/jlink-config.sh build\\n2. Download pre-built runtime from GitHub releases"

    return 0  # Don't block session on missing runtime
}

main "$@"
