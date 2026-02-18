#!/bin/bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
set -euo pipefail

# Migration to CAT 2.1
#
# Changes (consolidated chronologically):
# 1. Remove "## Issues In Progress" section from version-level STATE.md files,
#    merging its entries into "## Issues Pending"
# 2. Rename issue status values in STATE.md files:
#    pending → open, completed/complete → closed
# 3. Move version tracking from cat-config.json to .claude/cat/VERSION plain text file
#    (handles both old "version" field and renamed "last_migrated_version" field)

trap 'echo "ERROR in 2.1.sh at line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# shellcheck source=lib/utils.sh
source "${CLAUDE_PLUGIN_ROOT}/migrations/lib/utils.sh"

# ──────────────────────────────────────────────────────────────────────────────
# Phase 1: Merge "Issues In Progress" into "Issues Pending" in version STATE.md
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 1: Remove In Progress section from version STATE.md files"

# Version-level STATE.md files live at .claude/cat/issues/v*/v*.*/STATE.md (depth 4 from issues/).
# Issue-level STATE.md files are one directory deeper (depth 5+) and must be excluded.
version_state_files=$(find .claude/cat/issues -path "*v*.*/*" -name "STATE.md" -mindepth 4 -maxdepth 4 -type f \
    2>/dev/null || true)

if [[ -z "$version_state_files" ]]; then
    log_migration "No version-level STATE.md files found - skipping phase 1"
else
    total_count=$(echo "$version_state_files" | wc -l | tr -d ' ')
    log_migration "Found $total_count version-level STATE.md files to check"

    phase1_migrated=0

    while IFS= read -r state_file; do
        [[ -z "$state_file" ]] && continue

        # Only process files that contain "## Issues In Progress"
        if ! grep -q "^## Issues In Progress" "$state_file" 2>/dev/null; then
            continue
        fi

        log_migration "  Migrating: $state_file"

        # Extract entries (lines starting with "- ") from the "## Issues In Progress" section
        in_progress_entries=$(awk '
            /^## Issues In Progress/ { in_section=1; next }
            in_section && /^## / { in_section=0 }
            in_section && /^- / { print }
        ' "$state_file")

        entry_count=$(echo "$in_progress_entries" | grep -c "^- " || echo 0)
        log_migration "    Moving $entry_count In Progress entries to Issues Pending"

        if grep -q "^## Issues Pending" "$state_file" 2>/dev/null; then
            # Append in_progress entries to the existing "## Issues Pending" section,
            # then remove the "## Issues In Progress" section entirely.
            awk -v entries="$in_progress_entries" '
                BEGIN {
                    n = split(entries, entry_arr, "\n")
                    entry_count = 0
                    for (i = 1; i <= n; i++) {
                        if (entry_arr[i] != "") entry_count++
                    }
                }

                /^## Issues In Progress/ { skip=1; next }
                skip && /^## / { skip=0 }
                skip { next }

                /^## Issues Pending/ { print; in_pending=1; next }
                in_pending && /^## / {
                    # End of pending section: insert in_progress entries, blank line, then next heading
                    if (entry_count > 0) {
                        for (i = 1; i <= n; i++) {
                            if (entry_arr[i] != "") print entry_arr[i]
                        }
                    }
                    print ""
                    in_pending=0
                    print
                    next
                }
                in_pending && /^$/ { next }
                in_pending { print; next }

                { print }

                END {
                    if (in_pending && entry_count > 0) {
                        for (i = 1; i <= n; i++) {
                            if (entry_arr[i] != "") print entry_arr[i]
                        }
                    }
                }
            ' "$state_file" > "${state_file}.tmp" && mv "${state_file}.tmp" "$state_file"
        else
            # No "## Issues Pending" section: create one before "## Issues Completed"
            # (or at end of file if no Completed section), then remove In Progress section.
            awk -v entries="$in_progress_entries" '
                BEGIN {
                    n = split(entries, entry_arr, "\n")
                    entry_count = 0
                    for (i = 1; i <= n; i++) {
                        if (entry_arr[i] != "") entry_count++
                    }
                    inserted=0
                }

                /^## Issues In Progress/ { skip=1; next }
                skip && /^## / { skip=0 }
                skip { next }

                /^## Issues Completed/ && !inserted {
                    if (entry_count > 0) {
                        print "## Issues Pending"
                        for (i = 1; i <= n; i++) {
                            if (entry_arr[i] != "") print entry_arr[i]
                        }
                        print ""
                    }
                    inserted=1
                    print
                    next
                }

                { print }

                END {
                    if (!inserted && entry_count > 0) {
                        print ""
                        print "## Issues Pending"
                        for (i = 1; i <= n; i++) {
                            if (entry_arr[i] != "") print entry_arr[i]
                        }
                    }
                }
            ' "$state_file" > "${state_file}.tmp" && mv "${state_file}.tmp" "$state_file"
        fi

        ((phase1_migrated++)) || true
        log_migration "    Done: $state_file"

    done <<< "$version_state_files"

    log_migration "Phase 1 complete: $phase1_migrated files migrated"
fi

# ──────────────────────────────────────────────────────────────────────────────
# Phase 2: Rename issue status values in STATE.md files
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 2: Rename issue status values (pending→open, completed→closed)"

# Find all STATE.md files under .claude/cat/issues/
all_state_files=$(find .claude/cat/issues -name "STATE.md" -type f 2>/dev/null || true)
all_state_count=$(echo "$all_state_files" | grep -c "STATE.md" || echo 0)

if [[ "$all_state_count" -eq 0 ]]; then
    log_migration "No STATE.md files found - skipping phase 2"
else
    log_migration "Found $all_state_count STATE.md files to check"

    phase2_changed=0

    while IFS= read -r state_file; do
        [[ -z "$state_file" ]] && continue

        changed=false

        if grep -q '\*\*Status:\*\* pending' "$state_file" 2>/dev/null; then
            sed -i 's/\*\*Status:\*\* pending/**Status:** open/' "$state_file"
            changed=true
        fi

        if grep -q '\*\*Status:\*\* completed' "$state_file" 2>/dev/null; then
            sed -i 's/\*\*Status:\*\* completed/**Status:** closed/' "$state_file"
            changed=true
        fi

        if grep -q '\*\*Status:\*\* complete' "$state_file" 2>/dev/null; then
            sed -i 's/\*\*Status:\*\* complete/**Status:** closed/' "$state_file"
            changed=true
        fi

        if [[ "$changed" == "true" ]]; then
            ((phase2_changed++)) || true
            log_migration "  Updated: $state_file"
        fi

    done <<< "$all_state_files"

    log_migration "Phase 2 complete: $phase2_changed files changed"
fi

# ──────────────────────────────────────────────────────────────────────────────
# Phase 3: Move version tracking to .claude/cat/VERSION plain text file
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 3: Move version tracking from cat-config.json to VERSION file"

config_file=".claude/cat/cat-config.json"
version_file=".claude/cat/VERSION"

if [[ ! -f "$config_file" ]]; then
    log_migration "No config file found - skipping phase 3"
elif [[ -f "$version_file" ]]; then
    log_migration "VERSION file already exists - skipping phase 3"
else
    # Try last_migrated_version first (renamed in earlier development), then fall back to version
    migrated_version=""
    field_to_remove=""

    if jq -e '.last_migrated_version' "$config_file" >/dev/null 2>&1; then
        migrated_version=$(jq -r '.last_migrated_version' "$config_file")
        field_to_remove="last_migrated_version"
    elif jq -e '.version' "$config_file" >/dev/null 2>&1; then
        migrated_version=$(jq -r '.version' "$config_file")
        field_to_remove="version"
    fi

    if [[ -n "$migrated_version" && -n "$field_to_remove" ]]; then
        log_migration "Moving $field_to_remove ($migrated_version) to VERSION file..."

        # Write to VERSION file
        printf '%s\n' "$migrated_version" > "$version_file"

        # Remove the field from cat-config.json
        tmp_file="${config_file}.tmp"
        jq "del(.${field_to_remove})" "$config_file" > "$tmp_file"
        mv "$tmp_file" "$config_file"

        log_migration "Phase 3 complete: moved to VERSION file and removed from config"
    else
        log_migration "No version field found in config - skipping phase 3"
    fi
fi

log_success "Migration to 2.1 completed"
exit 0
