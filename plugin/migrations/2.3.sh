#!/bin/bash
set -euo pipefail

# Migration to CAT 2.3
#
# Changes:
# - Renames version field to last_migrated_version in cat-config.json for clarity
#
# The version field in cat-config.json tracks which version the installation has been migrated to,
# NOT the current plugin version. This rename clarifies its purpose.

# Error handler
trap 'echo "ERROR in 2.3.sh at line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# shellcheck source=lib/utils.sh
source "${CLAUDE_PLUGIN_ROOT}/migrations/lib/utils.sh"

config_file=".claude/cat/cat-config.json"

if [[ ! -f "$config_file" ]]; then
    log_error "Config file not found: $config_file"
    exit 1
fi

# Check if already migrated (last_migrated_version exists)
if jq -e '.last_migrated_version' "$config_file" >/dev/null 2>&1; then
    log_migration "Field last_migrated_version already exists, skipping"
    exit 0
fi

# Check if old version field exists
if ! jq -e '.version' "$config_file" >/dev/null 2>&1; then
    log_migration "No version field to migrate, skipping"
    exit 0
fi

# Get the current version value
old_version=$(jq -r '.version' "$config_file")
log_migration "Renaming version field to last_migrated_version (value: $old_version)..."

# Rename the field
tmp_file="${config_file}.tmp"
jq 'with_entries(if .key == "version" then .key = "last_migrated_version" else . end)' "$config_file" > "$tmp_file"
mv "$tmp_file" "$config_file"

log_success "Renamed version to last_migrated_version in cat-config.json"
