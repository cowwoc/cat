#!/bin/bash
set -euo pipefail

# Migration to CAT 1.0.10
#
# Changes:
# - Removes yoloMode in favor of trust: high
# - yoloMode: true -> trust: high (full autonomy, skip reviews)
# - yoloMode: false -> no change (trust already set by 1.0.9 migration)
#
# This completes the configuration consolidation started in 1.0.9.
# All autonomy/review behavior is now controlled by the single "trust" setting.

# Error handler
trap 'echo "ERROR in 1.0.10.sh at line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# shellcheck source=lib/utils.sh
source "${CLAUDE_PLUGIN_ROOT}/migrations/lib/utils.sh"

config_file=".claude/cat/cat-config.json"

if [[ ! -f "$config_file" ]]; then
    log_error "Config file not found: $config_file"
    exit 1
fi

# Check if yoloMode exists
if ! jq -e 'has("yoloMode")' "$config_file" > /dev/null 2>&1; then
    log_migration "No yoloMode setting found, skipping"
    exit 0
fi

# Read yoloMode value
yolo_mode=$(jq -r '.yoloMode // false' "$config_file")

log_migration "Found yoloMode=$yolo_mode, migrating to trust setting..."

# Create backup before migration
backup_cat_dir "pre-1.0.10-migration"

tmp_file="${config_file}.tmp"

if [[ "$yolo_mode" == "true" ]]; then
    # yoloMode: true -> trust: high (overrides any existing trust value)
    log_migration "yoloMode=true -> trust=high (autonomous mode, skip reviews)"
    jq 'del(.yoloMode) | .trust = "high"' "$config_file" > "$tmp_file"
else
    # yoloMode: false -> just remove yoloMode, keep existing trust
    log_migration "yoloMode=false -> removing yoloMode, keeping existing trust setting"
    jq 'del(.yoloMode)' "$config_file" > "$tmp_file"
fi

mv "$tmp_file" "$config_file"

log_success "yoloMode migration complete"
log_migration "Configuration now uses 'trust' setting for all autonomy/review behavior:"
log_migration "  trust: low    - Ask before decisions, run reviews, ask on rejection"
log_migration "  trust: medium - Make decisions, run reviews, auto-fix on rejection"
log_migration "  trust: high   - Full autonomy, skip reviews, auto-merge"
