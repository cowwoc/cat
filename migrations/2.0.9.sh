#!/bin/bash
set -euo pipefail

# Migration to CAT 1.0.9
#
# Changes:
# - Replaces approach/stakeholderReview/refactoring with leash/caution/curiosity/patience
# - Stakeholder review is now automatically triggered based on task characteristics
#
# Migration mapping:
#   approach: conservative -> leash: short, curiosity: low
#   approach: balanced -> leash: medium, curiosity: medium
#   approach: aggressive -> leash: long, curiosity: high
#
#   stakeholderReview: always -> leash: short (if not set by approach)
#   stakeholderReview: high-risk-only -> leash: medium (if not set by approach)
#   stakeholderReview: never -> leash: long (if not set by approach)
#
#   refactoring: avoid -> curiosity: low, patience: high
#   refactoring: opportunistic -> curiosity: medium, patience: medium
#   refactoring: eager -> curiosity: high, patience: low
#
# New defaults: leash: medium, caution: moderate, curiosity: low, patience: high

# Error handler
trap 'echo "ERROR in 1.0.9.sh at line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# shellcheck source=lib/utils.sh
source "${CLAUDE_PLUGIN_ROOT}/migrations/lib/utils.sh"

config_file=".claude/cat/cat-config.json"

if [[ ! -f "$config_file" ]]; then
    log_error "Config file not found: $config_file"
    exit 1
fi

# Check if already migrated (has leash setting)
if jq -e '.leash' "$config_file" > /dev/null 2>&1; then
    log_migration "Already migrated (leash setting exists), skipping"
    exit 0
fi

log_migration "Migrating configuration to new behavior settings..."

# Create backup before migration
backup_cat_dir "pre-1.0.9-migration"

# Read old values
approach=$(jq -r '.approach // "balanced"' "$config_file")
stakeholder_review=$(jq -r '.stakeholderReview // "high-risk-only"' "$config_file")
refactoring=$(jq -r '.refactoring // "opportunistic"' "$config_file")

log_migration "Old settings: approach=$approach, stakeholderReview=$stakeholder_review, refactoring=$refactoring"

# Determine new values based on old settings
# Start with defaults
leash="medium"
caution="moderate"
curiosity="low"
patience="high"

# Map approach -> leash + curiosity
case "$approach" in
    conservative)
        leash="short"
        curiosity="low"
        ;;
    balanced)
        leash="medium"
        curiosity="medium"
        ;;
    aggressive)
        leash="long"
        curiosity="high"
        ;;
esac

# stakeholderReview influences leash if approach didn't set it strongly
# Use the more conservative (shorter) leash if there's a conflict
case "$stakeholder_review" in
    always)
        # If approach was aggressive but reviews were always, use medium leash
        if [[ "$leash" == "long" ]]; then
            leash="medium"
        fi
        ;;
    never)
        # If approach was conservative but reviews were never, stay conservative
        # (conservative wins for safety)
        ;;
esac

# Map refactoring -> curiosity + patience
case "$refactoring" in
    avoid)
        # More conservative curiosity setting wins
        if [[ "$curiosity" != "low" ]]; then
            curiosity="low"
        fi
        patience="high"
        ;;
    opportunistic)
        # Keep curiosity from approach, set patience to medium
        patience="medium"
        ;;
    eager)
        # More aggressive curiosity setting wins
        if [[ "$curiosity" == "low" ]]; then
            curiosity="medium"
        fi
        patience="low"
        ;;
esac

log_migration "New settings: leash=$leash, caution=$caution, curiosity=$curiosity, patience=$patience"

# Update config file
tmp_file="${config_file}.tmp"
jq --arg leash "$leash" \
   --arg caution "$caution" \
   --arg curiosity "$curiosity" \
   --arg patience "$patience" \
   'del(.approach, .stakeholderReview, .refactoring) |
    . + {
        "leash": $leash,
        "caution": $caution,
        "curiosity": $curiosity,
        "patience": $patience
    }' "$config_file" > "$tmp_file"
mv "$tmp_file" "$config_file"

log_success "Configuration migrated successfully"
log_migration "  leash: $leash (trust level for CAT decisions)"
log_migration "  caution: $caution (verification depth before commits)"
log_migration "  curiosity: $curiosity (exploration beyond immediate task)"
log_migration "  patience: $patience (when to act on discoveries)"
