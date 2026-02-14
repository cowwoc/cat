#!/bin/bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Migration utility functions for CAT version upgrades
#
# Usage: source this file in migration scripts
#
# Functions:
#   version_compare <v1> <v2>         - Returns: -1 (v1<v2), 0 (equal), 1 (v1>v2)
#   backup_cat_dir <reason>           - Creates timestamped backup of .claude/cat/
#   get_last_migrated_version         - Returns last_migrated_version from config (or "0.0.0")
#   get_plugin_version                - Returns version from plugin.json
#   set_last_migrated_version <ver>   - Updates last_migrated_version in config
#   log_migration <message>           - Logs migration progress

set -euo pipefail

# Colors for output (disabled if not a terminal)
if [[ -t 1 ]]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[0;33m'
    BLUE='\033[0;34m'
    NC='\033[0m'
else
    RED=''
    GREEN=''
    YELLOW=''
    BLUE=''
    NC=''
fi

# Compare two semantic versions
# Returns: -1 if v1 < v2, 0 if equal, 1 if v1 > v2
# Usage: version_compare "1.7" "2.0"
version_compare() {
    local v1="${1:-0.0.0}"
    local v2="${2:-0.0.0}"

    # Handle empty versions
    [[ -z "$v1" ]] && v1="0.0.0"
    [[ -z "$v2" ]] && v2="0.0.0"

    # Use sort -V for version comparison (portable)
    # If v1 sorts before v2, v1 < v2
    local sorted
    sorted=$(printf '%s\n%s\n' "$v1" "$v2" | sort -V | head -n1)

    if [[ "$v1" == "$v2" ]]; then
        echo "0"
    elif [[ "$sorted" == "$v1" ]]; then
        echo "-1"
    else
        echo "1"
    fi
}

# Create a backup of the .claude/cat/ directory
# Usage: backup_cat_dir "pre-migration-2.0"
backup_cat_dir() {
    local reason="${1:-backup}"
    local timestamp
    timestamp=$(date +%Y%m%d-%H%M%S)
    local backup_dir=".claude/cat/backups/${timestamp}-${reason}"

    if [[ ! -d ".claude/cat" ]]; then
        log_migration "No .claude/cat directory to backup"
        return 0
    fi

    mkdir -p ".claude/cat/backups"

    # Copy everything except the backups directory itself
    mkdir -p "$backup_dir"
    find .claude/cat -maxdepth 1 -mindepth 1 ! -name "backups" -exec cp -r {} "$backup_dir/" \;

    log_migration "Backup created: $backup_dir"
    echo "$backup_dir"
}

# Get the last migrated version from cat-config.json
# Returns "0.0.0" if not found or file doesn't exist
get_last_migrated_version() {
    local config_file=".claude/cat/cat-config.json"

    if [[ ! -f "$config_file" ]]; then
        echo "0.0.0"
        return
    fi

    local version
    version=$(jq -r '.last_migrated_version // "0.0.0"' "$config_file" 2>/dev/null || echo "0.0.0")

    # Handle null or empty
    [[ "$version" == "null" || -z "$version" ]] && version="0.0.0"

    echo "$version"
}

# Backward compatibility wrapper
get_config_version() {
    get_last_migrated_version
}

# Get the version from plugin.json
get_plugin_version() {
    local plugin_file="${CLAUDE_PLUGIN_ROOT:?CLAUDE_PLUGIN_ROOT must be set}/plugin.json"

    # Fallback to .claude-plugin subdirectory layout
    if [[ ! -f "$plugin_file" ]]; then
        plugin_file="${CLAUDE_PLUGIN_ROOT}/.claude-plugin/plugin.json"
    fi

    if [[ ! -f "$plugin_file" ]]; then
        log_migration "ERROR: plugin.json not found at $plugin_file"
        echo "ERROR: plugin.json not found - plugin installation may be broken" >&2
        exit 1
    fi

    local version
    version=$(jq -r '.version // empty' "$plugin_file")

    if [[ -z "$version" || "$version" == "null" ]]; then
        log_migration "ERROR: No version field in $plugin_file"
        echo "ERROR: No version field in plugin.json" >&2
        exit 1
    fi

    echo "$version"
}

# Update last migrated version in cat-config.json
# Usage: set_last_migrated_version "2.0"
set_last_migrated_version() {
    local new_version="$1"
    local config_file=".claude/cat/cat-config.json"

    if [[ ! -f "$config_file" ]]; then
        log_migration "ERROR: Config file not found: $config_file"
        return 1
    fi

    # Use temp file for atomic update
    local tmp_file="${config_file}.tmp"
    jq --arg v "$new_version" '.last_migrated_version = $v' "$config_file" > "$tmp_file"
    mv "$tmp_file" "$config_file"

    log_migration "Updated last_migrated_version to $new_version"
}

# Backward compatibility wrapper
set_config_version() {
    set_last_migrated_version "$@"
}

# Log migration progress (to stderr so it doesn't interfere with return values)
# Usage: log_migration "Migrating task state files..."
log_migration() {
    local message="$1"
    echo -e "${BLUE}[CAT Migration]${NC} $message" >&2
}

# Log migration warning
log_warning() {
    local message="$1"
    echo -e "${YELLOW}[CAT Migration WARNING]${NC} $message" >&2
}

# Log migration error
log_error() {
    local message="$1"
    echo -e "${RED}[CAT Migration ERROR]${NC} $message" >&2
}

# Log migration success
log_success() {
    local message="$1"
    echo -e "${GREEN}[CAT Migration]${NC} $message" >&2
}

# Check if CAT is initialized in current directory
is_cat_initialized() {
    [[ -f ".claude/cat/cat-config.json" ]]
}

# Get list of migrations to run between two versions
# Usage: get_pending_migrations "1.6" "2.0"
# Output: newline-separated list of migration scripts to run
get_pending_migrations() {
    local from_version="$1"
    local to_version="$2"
    local registry_file="${CLAUDE_PLUGIN_ROOT:?CLAUDE_PLUGIN_ROOT must be set}/migrations/registry.json"

    if [[ ! -f "$registry_file" ]]; then
        return
    fi

    # Get all migrations where version > from_version AND version <= to_version
    jq -r --arg from "$from_version" --arg to "$to_version" '
        .migrations
        | sort_by(.version)
        | .[]
        | select(.version != null and .script != null)
        | "\(.version)|\(.script)"
    ' "$registry_file" | while IFS='|' read -r ver script; do
        local cmp_from
        local cmp_to
        cmp_from=$(version_compare "$ver" "$from_version")
        cmp_to=$(version_compare "$ver" "$to_version")

        # Include if version > from AND version <= to
        if [[ "$cmp_from" == "1" && ("$cmp_to" == "-1" || "$cmp_to" == "0") ]]; then
            echo "$ver|$script"
        fi
    done
}

# Run a single migration script
# Usage: run_migration "2.0" "2.0.sh"
run_migration() {
    local version="$1"
    local script="$2"
    local script_path="${CLAUDE_PLUGIN_ROOT:?CLAUDE_PLUGIN_ROOT must be set}/migrations/$script"

    if [[ ! -f "$script_path" ]]; then
        log_warning "Migration script not found: $script_path (skipping)"
        return 0
    fi

    if [[ ! -x "$script_path" ]]; then
        chmod +x "$script_path"
    fi

    log_migration "Running migration to $version..."

    if "$script_path"; then
        log_success "Migration to $version completed"
        return 0
    else
        log_error "Migration to $version failed"
        return 1
    fi
}
