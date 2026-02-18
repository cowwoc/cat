#!/bin/bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
set -euo pipefail

# Migration to CAT 2.1
#
# Changes:
# - Remove "## Issues In Progress" section from version-level STATE.md files,
#   merging its entries into "## Issues Pending"

trap 'echo "ERROR in 2.1.sh at line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# shellcheck source=lib/utils.sh
source "${CLAUDE_PLUGIN_ROOT}/migrations/lib/utils.sh"

log_migration "Starting 2.1 migration: Remove In Progress section from version STATE.md files"

# Version-level STATE.md files live at .claude/cat/issues/v*/v*.*/STATE.md (depth 4 from issues/).
# Issue-level STATE.md files are one directory deeper (depth 5+) and must be excluded.
version_state_files=$(find .claude/cat/issues -path "*v*.*/*" -name "STATE.md" -mindepth 4 -maxdepth 4 -type f \
    2>/dev/null || true)

if [[ -z "$version_state_files" ]]; then
    log_migration "No version-level STATE.md files found - skipping"
    log_success "Migration to 2.1 completed (no files to migrate)"
    exit 0
fi

total_count=$(echo "$version_state_files" | wc -l | tr -d ' ')
log_migration "Found $total_count version-level STATE.md files to check"

migrated=0

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

    ((migrated++)) || true
    log_migration "    Done: $state_file"

done <<< "$version_state_files"

log_success "Migration to 2.1 completed:"
log_success "  - Files migrated: $migrated"

exit 0
