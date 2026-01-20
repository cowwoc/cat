#!/bin/bash
set -euo pipefail

# Migration to CAT 1.0.10
#
# Changes:
# - Removes yoloMode in favor of trust: high
# - yoloMode: true -> trust: high (full autonomy, skip reviews)
# - yoloMode: false -> no change (trust already set by 1.0.9 migration)
# - Removes contextLimit and targetContextUsage (now fixed values in agent-architecture.md)
# - Migrates deprecated task/ subdirectory structure:
#   Old: .claude/cat/v1/v1/task/<task-name>/PLAN.md
#   New: .claude/cat/v1/v1/<task-name>/PLAN.md
#
# This completes the configuration consolidation started in 1.0.9.
# All autonomy/review behavior is now controlled by the single "trust" setting.
# Context limits are now fixed architectural values, not user-configurable.

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

# Remove contextLimit and targetContextUsage (now fixed values)
if jq -e 'has("contextLimit") or has("targetContextUsage")' "$config_file" > /dev/null 2>&1; then
    log_migration "Removing contextLimit and targetContextUsage (now fixed in agent-architecture.md)..."
    jq 'del(.contextLimit, .targetContextUsage)' "$config_file" > "$tmp_file"
    mv "$tmp_file" "$config_file"
    log_success "Context limit settings removed"
fi

log_migration "Configuration now uses 'trust' setting for all autonomy/review behavior:"
log_migration "  trust: low    - Ask before decisions, run reviews, ask on rejection"
log_migration "  trust: medium - Make decisions, run reviews, auto-fix on rejection"
log_migration "  trust: high   - Full autonomy, skip reviews, auto-merge"
log_migration "Context limits are now fixed (see agent-architecture.md § Context Limit Constants)"

# Migrate deprecated task/ subdirectory structure
# Old: .claude/cat/v1/v1/task/<task-name>/PLAN.md
# New: .claude/cat/v1/v1/<task-name>/PLAN.md
cat_dir=".claude/cat"

if [[ -d "$cat_dir" ]]; then
    # Find all task/ subdirectories under minor versions
    task_dirs_found=false

    for minor_dir in "$cat_dir"/v[0-9]*/v[0-9]*.[0-9]*/; do
        [[ -d "$minor_dir" ]] || continue
        task_subdir="${minor_dir}task"

        if [[ -d "$task_subdir" ]]; then
            # Check if it contains actual task directories (with PLAN.md or STATE.md)
            has_tasks=false
            for potential_task in "$task_subdir"/*/; do
                [[ -d "$potential_task" ]] || continue
                if [[ -f "${potential_task}PLAN.md" ]] || [[ -f "${potential_task}STATE.md" ]]; then
                    has_tasks=true
                    break
                fi
            done

            if [[ "$has_tasks" == "true" ]]; then
                if [[ "$task_dirs_found" == "false" ]]; then
                    log_migration "Found deprecated task/ subdirectory structure, migrating..."
                    task_dirs_found=true
                fi

                log_migration "  Migrating tasks from ${task_subdir}/ to ${minor_dir}"

                # Move each task directory up one level
                for task_dir in "$task_subdir"/*/; do
                    [[ -d "$task_dir" ]] || continue
                    task_name=$(basename "$task_dir")

                    # Check for conflict
                    if [[ -d "${minor_dir}${task_name}" ]]; then
                        log_error "Cannot migrate: ${minor_dir}${task_name} already exists"
                        exit 1
                    fi

                    if ! mv "$task_dir" "${minor_dir}${task_name}"; then
                        log_error "Failed to move ${task_dir} to ${minor_dir}${task_name}"
                        exit 1
                    fi
                    log_migration "    Moved: $task_name"
                done

                # Remove empty task/ directory - must succeed after moving all tasks
                if ! rmdir "$task_subdir"; then
                    log_error "Failed to remove ${task_subdir} - directory not empty after migration"
                    exit 1
                fi
            fi
        fi
    done

    if [[ "$task_dirs_found" == "true" ]]; then
        log_success "Task directory structure migration complete"
        log_migration "Tasks are now directly under minor version directories:"
        log_migration "  .claude/cat/v1/v1/<task-name>/PLAN.md"
    fi
fi
