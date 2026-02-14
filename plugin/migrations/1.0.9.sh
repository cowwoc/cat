#!/bin/bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
set -euo pipefail

# Migration to CAT 1.0.9
#
# Changes:
# - Replaces approach/stakeholderReview/refactoring with trust/verify/curiosity/patience
# - Renames autoCleanupWorktrees to autoRemoveWorktrees
#
# Migration mapping:
#   approach: conservative -> trust: low, curiosity: low
#   approach: balanced -> trust: medium, curiosity: medium
#   approach: aggressive -> trust: medium, curiosity: high (NOT trust: high, reviews still run)
#
#   refactoring: avoid -> patience: high
#   refactoring: opportunistic -> patience: medium
#   refactoring: eager -> patience: low
#
# New defaults: trust: medium, verify: changed, curiosity: low, patience: high

# Error handler
trap 'echo "ERROR in 1.0.9.sh at line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# shellcheck source=lib/utils.sh
source "${CLAUDE_PLUGIN_ROOT}/migrations/lib/utils.sh"

config_file=".claude/cat/cat-config.json"

if [[ ! -f "$config_file" ]]; then
    log_error "Config file not found: $config_file"
    exit 1
fi

# Check if already migrated (has trust setting)
if jq -e '.trust' "$config_file" > /dev/null 2>&1; then
    log_migration "Already migrated (trust setting exists), skipping"
    exit 0
fi

# Skip if no old settings to migrate
if ! jq -e '.approach or .stakeholderReview or .refactoring' "$config_file" > /dev/null 2>&1; then
    log_migration "No old settings found (approach/stakeholderReview/refactoring), skipping"
    exit 0
fi

log_migration "Migrating configuration to new behavior settings..."

# Create backup before migration
backup_cat_dir "pre-1.0.9-migration"

# Read old values
approach=$(jq -r '.approach // "balanced"' "$config_file")
refactoring=$(jq -r '.refactoring // "opportunistic"' "$config_file")

log_migration "Old settings: approach=$approach, refactoring=$refactoring"

# Determine new values based on old settings
# Start with defaults
trust="medium"
verify="changed"
curiosity="low"
patience="high"

# Map approach -> trust and curiosity
case "$approach" in
    conservative)
        trust="low"
        curiosity="low"
        ;;
    balanced)
        trust="medium"
        curiosity="medium"
        ;;
    aggressive)
        # aggressive approach means auto-fix but still run reviews
        trust="medium"
        curiosity="high"
        log_migration "approach=aggressive -> trust=medium, curiosity=high"
        ;;
esac

# Map refactoring -> patience
case "$refactoring" in
    avoid)
        patience="high"
        ;;
    opportunistic)
        patience="medium"
        ;;
    eager)
        patience="low"
        ;;
esac

log_migration "New settings: trust=$trust, verify=$verify, curiosity=$curiosity, patience=$patience"

# Update config file - remove old settings, add new ones
tmp_file="${config_file}.tmp"
jq --arg trust "$trust" \
   --arg verify "$verify" \
   --arg curiosity "$curiosity" \
   --arg patience "$patience" \
   'del(.approach, .stakeholderReview, .refactoring) |
    # Rename autoCleanupWorktrees to autoRemoveWorktrees (preserve value if exists)
    (if has("autoCleanupWorktrees") then .autoRemoveWorktrees = .autoCleanupWorktrees | del(.autoCleanupWorktrees) else . end) |
    . + {
        "trust": $trust,
        "verify": $verify,
        "curiosity": $curiosity,
        "patience": $patience
    }' "$config_file" > "$tmp_file"
mv "$tmp_file" "$config_file"

log_success "Configuration migrated successfully"
log_migration "  trust: $trust (trust level for CAT decisions)"
log_migration "  verify: $verify (what verification runs before commits)"
log_migration "  curiosity: $curiosity (exploration beyond immediate task)"
log_migration "  patience: $patience (when to act on discoveries)"
