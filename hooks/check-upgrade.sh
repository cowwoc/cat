#!/bin/bash
set -euo pipefail

# Error handler
trap 'echo "ERROR in check-upgrade.sh at line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# Check for CAT version upgrades on session start
#
# TRIGGER: SessionStart
#
# BEHAVIOR:
# - Compares plugin version to cat-config.json version
# - If upgrade detected: backs up, runs migrations, updates version
# - If downgrade detected: warns user (no auto-migration)
# - Outputs migration status via additionalContext

# shellcheck source=../migrations/lib/utils.sh
source "${CLAUDE_PLUGIN_ROOT}/migrations/lib/utils.sh"

# Check if CAT is initialized
if ! is_cat_initialized; then
    # Not initialized - nothing to migrate
    exit 0
fi

config_version=$(get_config_version)
plugin_version=$(get_plugin_version)

# Compare versions
cmp_result=$(version_compare "$config_version" "$plugin_version")

if [[ "$cmp_result" == "0" ]]; then
    # Same version - nothing to do
    exit 0
fi

if [[ "$cmp_result" == "1" ]]; then
    # Config version > plugin version = downgrade
    message="CAT VERSION MISMATCH DETECTED

Your config has version $config_version but the plugin is version $plugin_version.
This appears to be a downgrade.

**Action Required**: If this is intentional, manually update version in .claude/cat/cat-config.json.
Automatic downgrade migration is not supported to prevent data loss."

    jq -n --arg msg "$message" '{
        "hookSpecificOutput": {
            "hookEventName": "SessionStart",
            "additionalContext": $msg
        }
    }'
    exit 0
fi

# Upgrade detected (cmp_result == "-1")
# Get list of migrations to run
migrations=$(get_pending_migrations "$config_version" "$plugin_version")

if [[ -z "$migrations" ]]; then
    # No migrations needed, just update version
    set_config_version "$plugin_version"

    message="CAT upgraded: $config_version → $plugin_version (no migrations required)"

    jq -n --arg msg "$message" '{
        "hookSpecificOutput": {
            "hookEventName": "SessionStart",
            "additionalContext": $msg
        }
    }'
    exit 0
fi

# Create backup before migration
backup_path=$(backup_cat_dir "pre-upgrade-${plugin_version}")

# Run migrations in order
migration_log=""
migration_failed=false

while IFS='|' read -r ver script; do
    if run_migration "$ver" "$script"; then
        migration_log="${migration_log}\n- $ver: success"
    else
        migration_log="${migration_log}\n- $ver: FAILED"
        migration_failed=true
        break
    fi
done <<< "$migrations"

if [[ "$migration_failed" == "true" ]]; then
    message="CAT UPGRADE FAILED

Attempted upgrade: $config_version → $plugin_version

Migration log:$migration_log

**Backup preserved at**: $backup_path

Please review the error and try again, or restore from backup."

    jq -n --arg msg "$message" '{
        "hookSpecificOutput": {
            "hookEventName": "SessionStart",
            "additionalContext": $msg
        }
    }'
    exit 1
fi

# Update config version after successful migration
set_config_version "$plugin_version"

# Check for PLAN.md files missing Research section
missing_research=0
missing_research_files=""
if [[ -d ".claude/cat" ]]; then
    while IFS= read -r plan_file; do
        [[ -z "$plan_file" ]] && continue
        if ! grep -q "^## Research" "$plan_file" 2>/dev/null; then
            ((missing_research++)) || true
            missing_research_files="${missing_research_files}\n  - ${plan_file}"
        fi
    done < <(find .claude/cat -name "PLAN.md" -type f 2>/dev/null)
fi


# Output directly to user (visible in terminal)
echo ""
echo "CAT UPGRADED from version $config_version to $plugin_version"
if [[ $missing_research -gt 0 ]]; then
    echo ""
    echo "**Action Required:** $missing_research PLAN.md file(s) are missing Research sections."
    echo "To populate with stakeholder research, run:"
    echo "    /cat:research [version]"
fi
echo ""

# Also send to Claude via additionalContext
message="CAT upgraded from $config_version to $plugin_version. Backup at: $backup_path"

jq -n --arg msg "$message" '{
    "hookSpecificOutput": {
        "hookEventName": "SessionStart",
        "additionalContext": $msg
    }
}'
