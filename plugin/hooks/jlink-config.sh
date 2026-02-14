#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# jlink-config.sh - Configuration and build script for CAT's custom JDK runtime
#
# Creates a minimal JDK 25 runtime with Jackson 3 modules using jlink.
# The resulting runtime is ~30-40MB vs ~300MB for full JDK.
#
# Usage:
#   ./jlink-config.sh build [--output-dir DIR]   Build the custom runtime
#   ./jlink-config.sh info                        Show configuration info
#
# Requirements:
#   - JDK 25 installed (JAVA_HOME set or java on PATH)
#   - Maven (for downloading Jackson jars)
#
# The custom runtime includes:
#   - Base JDK modules (java.base, java.logging, etc.)
#   - Jackson 3 core modules for JSON processing

set -euo pipefail

# --- Configuration ---

# JDK version to target
readonly JDK_VERSION="25"

# Jackson 3.x version (uses tools.jackson group/package)
readonly JACKSON_VERSION="3.0.3"

# Output directory name for the custom runtime
readonly RUNTIME_NAME="cat-jdk-${JDK_VERSION}"

# JDK modules required for CAT hooks
# Minimal set for JSON processing and basic I/O
readonly JDK_MODULES=(
    "java.base"          # Core classes (always required)
    "java.logging"       # java.util.logging for diagnostics
    "java.sql"           # JDBC types used by some Jackson modules
    "jdk.unsupported"    # sun.misc.Unsafe (Jackson optimization)
)

# Jackson 3 Maven coordinates (tools.jackson group)
readonly JACKSON_ARTIFACTS=(
    "tools.jackson.core:jackson-core:${JACKSON_VERSION}"
    "tools.jackson.core:jackson-databind:${JACKSON_VERSION}"
    "tools.jackson.core:jackson-annotations:${JACKSON_VERSION}"
)

# Jackson 3 module names (for --add-modules)
readonly JACKSON_MODULES=(
    "tools.jackson.core"
    "tools.jackson.databind"
    "tools.jackson.annotation"
)

# --- Functions ---

log_info() {
    echo "[INFO] $*"
}

log_error() {
    echo "[ERROR] $*" >&2
}

check_java_version() {
    local java_cmd="${JAVA_HOME:-}/bin/java"
    [[ -x "$java_cmd" ]] || java_cmd="java"

    if ! command -v "$java_cmd" &>/dev/null; then
        log_error "Java not found. Set JAVA_HOME or ensure java is on PATH."
        return 1
    fi

    local version
    version=$("$java_cmd" -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')

    if [[ "$version" -lt "$JDK_VERSION" ]]; then
        log_error "JDK $JDK_VERSION required, found version $version"
        return 1
    fi

    log_info "Found JDK version $version"
}

download_jackson_jars() {
    local lib_dir="$1"
    mkdir -p "$lib_dir"

    log_info "Downloading Jackson ${JACKSON_VERSION} modules..."

    for artifact in "${JACKSON_ARTIFACTS[@]}"; do
        local group="${artifact%%:*}"
        local rest="${artifact#*:}"
        local name="${rest%%:*}"
        local version="${rest#*:}"

        local group_path="${group//./\/}"
        local jar_name="${name}-${version}.jar"
        local jar_path="${lib_dir}/${jar_name}"

        if [[ -f "$jar_path" ]]; then
            log_info "  $jar_name (cached)"
            continue
        fi

        local url="https://repo1.maven.org/maven2/${group_path}/${name}/${version}/${jar_name}"
        log_info "  Downloading $jar_name..."

        if ! curl -sSfL -o "$jar_path" "$url"; then
            log_error "Failed to download $jar_name"
            return 1
        fi
    done

    log_info "Jackson modules downloaded to $lib_dir"
}

build_runtime() {
    local output_dir="${1:-.}"
    local runtime_path="${output_dir}/${RUNTIME_NAME}"
    local lib_dir="${output_dir}/jackson-libs"

    check_java_version || return 1

    # Download Jackson jars first
    download_jackson_jars "$lib_dir" || return 1

    # Build module path from Jackson jars
    local module_path=""
    for jar in "$lib_dir"/*.jar; do
        [[ -n "$module_path" ]] && module_path+=":"
        module_path+="$jar"
    done

    # Build comma-separated module list
    local add_modules=""
    for mod in "${JDK_MODULES[@]}" "${JACKSON_MODULES[@]}"; do
        [[ -n "$add_modules" ]] && add_modules+=","
        add_modules+="$mod"
    done

    local jlink_cmd="${JAVA_HOME:-}/bin/jlink"
    [[ -x "$jlink_cmd" ]] || jlink_cmd="jlink"

    if ! command -v "$jlink_cmd" &>/dev/null; then
        log_error "jlink not found. Ensure JDK (not JRE) is installed."
        return 1
    fi

    log_info "Creating custom runtime at $runtime_path..."

    # Remove existing runtime if present
    [[ -d "$runtime_path" ]] && rm -rf "$runtime_path"

    # Build the custom runtime
    "$jlink_cmd" \
        --module-path "$module_path" \
        --add-modules "$add_modules" \
        --output "$runtime_path" \
        --strip-debug \
        --no-man-pages \
        --no-header-files \
        --compress zip-6 \
        --vm server

    local size
    size=$(du -sh "$runtime_path" | cut -f1)

    log_info "Custom runtime created: $runtime_path ($size)"
    log_info "Java binary: ${runtime_path}/bin/java"
}

show_info() {
    echo "CAT JDK Runtime Configuration"
    echo "=============================="
    echo ""
    echo "Target JDK Version: $JDK_VERSION"
    echo "Jackson Version: $JACKSON_VERSION"
    echo "Runtime Name: $RUNTIME_NAME"
    echo ""
    echo "JDK Modules:"
    printf '  - %s\n' "${JDK_MODULES[@]}"
    echo ""
    echo "Jackson Modules:"
    printf '  - %s\n' "${JACKSON_MODULES[@]}"
    echo ""
    echo "Maven Artifacts:"
    printf '  - %s\n' "${JACKSON_ARTIFACTS[@]}"
}

# --- Main ---

case "${1:-info}" in
    build)
        shift
        output_dir="."
        while [[ $# -gt 0 ]]; do
            case "$1" in
                --output-dir)
                    output_dir="$2"
                    shift 2
                    ;;
                *)
                    log_error "Unknown option: $1"
                    exit 1
                    ;;
            esac
        done
        build_runtime "$output_dir"
        ;;
    info)
        show_info
        ;;
    *)
        echo "Usage: $0 {build|info} [options]"
        echo ""
        echo "Commands:"
        echo "  build [--output-dir DIR]  Build the custom JDK runtime"
        echo "  info                       Show configuration information"
        exit 1
        ;;
esac
