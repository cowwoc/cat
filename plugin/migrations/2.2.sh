#!/bin/bash
set -euo pipefail

# Migration to 2.2: Rename issue status values
#
# This migration renames canonical status values in STATE.md files:
#   - "pending"   -> "open"
#   - "completed" -> "closed"
#   - "complete"  -> "closed"  (non-canonical form)
#
# The values "in-progress" and "blocked" remain unchanged.

# shellcheck source=lib/utils.sh
source "${CLAUDE_PLUGIN_ROOT}/migrations/lib/utils.sh"

log_migration "Starting 2.2 migration: Rename issue status values"

# Find all STATE.md files under .claude/cat/issues/
state_files=$(find .claude/cat/issues -name "STATE.md" -type f 2>/dev/null || true)
total_count=$(echo "$state_files" | grep -c "STATE.md" || echo 0)

if [[ "$total_count" -eq 0 ]]; then
    log_migration "No STATE.md files found - skipping"
    exit 0
fi

log_migration "Found $total_count STATE.md files to check"

files_changed=0

while IFS= read -r state_file; do
    [[ -z "$state_file" ]] && continue

    changed=false

    # Check for status values that need renaming
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
        ((files_changed++)) || true
        log_migration "  Updated: $state_file"
    fi

done <<< "$state_files"

log_success "Status value migration complete:"
log_success "  - Files changed: $files_changed"

exit 0
