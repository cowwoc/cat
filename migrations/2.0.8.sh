#!/bin/bash
set -euo pipefail

# Migration to CAT 1.0.8
#
# Changes:
# - Adds version field to cat-config.json (enables version tracking for future upgrades)
#
# This is the baseline migration that enables the upgrade system itself.

# Error handler
trap 'echo "ERROR in 1.0.8.sh at line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# shellcheck source=lib/utils.sh
source "${CLAUDE_PLUGIN_ROOT}/migrations/lib/utils.sh"

config_file=".claude/cat/cat-config.json"

if [[ ! -f "$config_file" ]]; then
    log_error "Config file not found: $config_file"
    exit 1
fi

# Check if version already exists with a valid version (not 0.0.0)
existing_version=$(jq -r '.version // "missing"' "$config_file")

if [[ "$existing_version" != "missing" && "$existing_version" != "null" && "$existing_version" != "0.0.0" ]]; then
    log_migration "version already exists ($existing_version), skipping"
    exit 0
fi

# Add version field (set to 1.8 since that's what this migration brings us to)
log_migration "Adding version field to config..."

tmp_file="${config_file}.tmp"
jq '. + {"version": "1.0.8"}' "$config_file" > "$tmp_file"
mv "$tmp_file" "$config_file"

log_success "Added version field to cat-config.json"
