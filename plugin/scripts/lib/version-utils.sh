#!/bin/bash
# Version utilities for flexible version schema support
# Supports: MAJOR (1), MAJOR.MINOR (1.0), MAJOR.MINOR.PATCH (1.0.0)

# Detect version schema depth from version string
# Returns: "major", "minor", "patch", or "invalid"
detect_version_depth() {
    local version_str="$1"
    version_str="${version_str#v}"

    # Use same validation pattern as validate_version
    if [[ "$version_str" =~ ^[0-9]+$ ]]; then
        echo "major"
    elif [[ "$version_str" =~ ^[0-9]+\.[0-9]+$ ]]; then
        echo "minor"
    elif [[ "$version_str" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        echo "patch"
    else
        echo "invalid"
    fi
}

# Parse version string into components
# Sets: VERSION_MAJOR, VERSION_MINOR, VERSION_PATCH
parse_version() {
    local version_str="$1"
    version_str="${version_str#v}"

    VERSION_MAJOR="${version_str%%.*}"
    local rest="${version_str#*.}"
    if [[ "$rest" == "$version_str" ]]; then
        VERSION_MINOR=""
        VERSION_PATCH=""
    elif [[ "$rest" == *"."* ]]; then
        VERSION_MINOR="${rest%%.*}"
        VERSION_PATCH="${rest#*.}"
    else
        VERSION_MINOR="$rest"
        VERSION_PATCH=""
    fi
}

# Get task directory path based on version format
get_task_dir() {
    local version="$1"
    local task_name="$2"
    # Fail-fast: require cat_dir argument
    if [[ -z "${3:-}" ]]; then
        echo "ERROR: get_task_dir requires cat_dir as third argument" >&2
        return 1
    fi
    local cat_dir="$3"

    parse_version "$version"

    if [[ -z "$VERSION_MINOR" ]]; then
        echo "${cat_dir}/v${VERSION_MAJOR}/${task_name}"
    elif [[ -z "$VERSION_PATCH" ]]; then
        echo "${cat_dir}/v${VERSION_MAJOR}/v${VERSION_MAJOR}.${VERSION_MINOR}/${task_name}"
    else
        echo "${cat_dir}/v${VERSION_MAJOR}/v${VERSION_MAJOR}.${VERSION_MINOR}/v${VERSION_MAJOR}.${VERSION_MINOR}.${VERSION_PATCH}/${task_name}"
    fi
}

# Get version directory path (without task)
get_version_dir() {
    local version="$1"
    # Fail-fast: require cat_dir argument
    if [[ -z "${2:-}" ]]; then
        echo "ERROR: get_version_dir requires cat_dir as second argument" >&2
        return 1
    fi
    local cat_dir="$2"

    parse_version "$version"

    if [[ -z "$VERSION_MINOR" ]]; then
        echo "${cat_dir}/v${VERSION_MAJOR}"
    elif [[ -z "$VERSION_PATCH" ]]; then
        echo "${cat_dir}/v${VERSION_MAJOR}/v${VERSION_MAJOR}.${VERSION_MINOR}"
    else
        echo "${cat_dir}/v${VERSION_MAJOR}/v${VERSION_MAJOR}.${VERSION_MINOR}/v${VERSION_MAJOR}.${VERSION_MINOR}.${VERSION_PATCH}"
    fi
}

# Format branch name from version and task
format_branch_name() {
    local version="$1"
    local task_name="$2"
    version="${version#v}"
    echo "${version}-${task_name}"
}

# Format Task ID for commit messages
format_task_id() {
    local version="$1"
    local task_name="$2"
    version="${version#v}"
    echo "v${version}-${task_name}"
}

# Validate version format (delegates to detect_version_depth)
validate_version() {
    [[ "$(detect_version_depth "$1")" != "invalid" ]]
}
