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
#    pending → open, completed/complete/done → closed
# 3. Move version tracking from cat-config.json to .claude/cat/VERSION plain text file
#    (handles both old "version" field and renamed "last_migrated_version" field)
# 4. Rename sections in PLAN.md files:
#    "## Acceptance Criteria" → "## Post-conditions"
#    "## Success Criteria" → merged into "## Post-conditions"
#    "## Gates" / "### Entry" / "### Exit" → "## Pre-conditions" / "## Post-conditions"
#    "## Entry Gate" / "## Exit Gate" → "## Pre-conditions" / "## Post-conditions"
#    "## Exit Gate Tasks" → "## Post-conditions"
# 5. Rename "curiosity" config key to "effort" in existing cat-config.json files
# 6. Create .claude/cat/.gitignore with patterns for temporary files if missing;
#    if it exists, add any missing patterns (/worktrees/, /locks/, /verify/)

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

        if grep -q '\*\*Status:\*\* done' "$state_file" 2>/dev/null; then
            sed -i 's/\*\*Status:\*\* done/**Status:** closed/' "$state_file"
            changed=true
        fi

        if grep -q '\*\*Status:\*\* in_progress' "$state_file" 2>/dev/null; then
            sed -i 's/\*\*Status:\*\* in_progress/**Status:** in-progress/' "$state_file"
            changed=true
        fi

        if grep -q '\*\*Status:\*\* active' "$state_file" 2>/dev/null; then
            sed -i 's/\*\*Status:\*\* active/**Status:** in-progress/' "$state_file"
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

    if grep -q '"last_migrated_version"[[:space:]]*:' "$config_file" 2>/dev/null; then
        migrated_version=$(sed -n 's/.*"last_migrated_version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$config_file")
        field_to_remove="last_migrated_version"
    elif grep -q '"version"[[:space:]]*:' "$config_file" 2>/dev/null; then
        migrated_version=$(sed -n 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$config_file")
        field_to_remove="version"
    fi

    if [[ -n "$migrated_version" && -n "$field_to_remove" ]]; then
        log_migration "Moving $field_to_remove ($migrated_version) to VERSION file..."

        # Write to VERSION file
        printf '%s\n' "$migrated_version" > "$version_file"

        # Remove the field from cat-config.json using awk (no jq required)
        # Handles field at start/middle (followed by comma) and at end (preceded by comma)
        tmp_file="${config_file}.tmp"
        awk -v field="$field_to_remove" '{
            # Remove "field": "value", pattern (field followed by comma)
            gsub("\"" field "\"[[:space:]]*:[[:space:]]*\"[^\"]*\"[[:space:]]*,[[:space:]]*", "")
            # Remove , "field": "value" pattern (field preceded by comma)
            gsub("[[:space:]]*,[[:space:]]*\"" field "\"[[:space:]]*:[[:space:]]*\"[^\"]*\"", "")
            # Remove "field": "value" pattern (sole field, no adjacent comma)
            gsub("\"" field "\"[[:space:]]*:[[:space:]]*\"[^\"]*\"", "")
            print
        }' "$config_file" > "$tmp_file"
        mv "$tmp_file" "$config_file"

        log_migration "Phase 3 complete: moved to VERSION file and removed from config"
    else
        log_migration "No version field found in config - skipping phase 3"
    fi
fi

# ──────────────────────────────────────────────────────────────────────────────
# Phase 4: Rename PLAN.md sections to pre-conditions / post-conditions
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 4: Rename PLAN.md sections to pre-conditions/post-conditions"

all_plan_files=$(find .claude/cat/issues -name "PLAN.md" -type f 2>/dev/null || true)
all_plan_count=$(echo "$all_plan_files" | grep -c "PLAN.md" || echo 0)

if [[ "$all_plan_count" -eq 0 ]]; then
    log_migration "No PLAN.md files found - skipping phase 4"
else
    log_migration "Found $all_plan_count PLAN.md files to check"

    phase4_changed=0

    while IFS= read -r plan_file; do
        [[ -z "$plan_file" ]] && continue

        changed=false

        # Rename "## Acceptance Criteria" → "## Post-conditions"
        if grep -q "^## Acceptance Criteria" "$plan_file" 2>/dev/null; then
            sed -i 's/^## Acceptance Criteria$/## Post-conditions/' "$plan_file"
            changed=true
            log_migration "  Renamed Acceptance Criteria → Post-conditions: $plan_file"
        fi

        # Rename "## Success Criteria" → "## Post-conditions" (merge)
        # If both "## Post-conditions" and "## Success Criteria" exist, remove Success Criteria section
        # If only "## Success Criteria" exists, rename it
        if grep -q "^## Success Criteria" "$plan_file" 2>/dev/null; then
            if grep -q "^## Post-conditions" "$plan_file" 2>/dev/null; then
                # Both sections exist - merge Success Criteria content into Post-conditions
                # Extract Success Criteria items
                success_items=$(awk '
                    /^## Success Criteria/ { in_section=1; next }
                    in_section && /^## / { in_section=0 }
                    in_section && /^- / { print }
                ' "$plan_file")

                # Remove Success Criteria section
                awk '
                    /^## Success Criteria/ { skip=1; next }
                    skip && /^## / { skip=0; print; next }
                    skip { next }
                    { print }
                ' "$plan_file" > "${plan_file}.tmp" && mv "${plan_file}.tmp" "$plan_file"

                # Append Success Criteria items to Post-conditions section
                if [[ -n "$success_items" ]]; then
                    awk -v items="$success_items" '
                        /^## Post-conditions/ { print; in_section=1; next }
                        in_section && /^## / {
                            # End of post-conditions section - insert items before next heading
                            n = split(items, arr, "\n")
                            for (i = 1; i <= n; i++) {
                                if (arr[i] != "") print arr[i]
                            }
                            print ""
                            in_section=0
                            print
                            next
                        }
                        in_section { print; next }
                        { print }
                        END {
                            if (in_section) {
                                n = split(items, arr, "\n")
                                for (i = 1; i <= n; i++) {
                                    if (arr[i] != "") print arr[i]
                                }
                            }
                        }
                    ' "$plan_file" > "${plan_file}.tmp" && mv "${plan_file}.tmp" "$plan_file"
                fi
            else
                # Only Success Criteria - rename it to Post-conditions
                sed -i 's/^## Success Criteria$/## Post-conditions/' "$plan_file"
            fi
            changed=true
            log_migration "  Merged/renamed Success Criteria → Post-conditions: $plan_file"
        fi

        # Handle "## Gates" with "### Entry" and "### Exit" subsections
        if grep -q "^## Gates" "$plan_file" 2>/dev/null; then
            # Extract Entry section content
            entry_content=$(awk '
                /^### Entry/ { in_section=1; next }
                in_section && /^### / { in_section=0 }
                in_section && /^## / { in_section=0 }
                in_section { print }
            ' "$plan_file")

            # Extract Exit section content
            exit_content=$(awk '
                /^### Exit/ { in_section=1; next }
                in_section && /^### / { in_section=0 }
                in_section && /^## / { in_section=0 }
                in_section { print }
            ' "$plan_file")

            # Replace ## Gates section in-place with ## Pre-conditions / ## Post-conditions
            awk -v entry="$entry_content" -v exit_cond="$exit_content" '
                /^## Gates/ { skip=1; next }
                skip && /^### / { next }
                skip && /^## / {
                    skip=0
                    if (!last_blank) print ""
                    print "## Pre-conditions"
                    if (entry != "") print entry; else print "- Previous version complete (or no prerequisites)"
                    print ""
                    print "## Post-conditions"
                    if (exit_cond != "") print exit_cond; else print "- All issues complete"
                    print ""
                    print
                    last_blank=0
                    next
                }
                skip { next }
                { print; last_blank=($0 == "") }
                END {
                    if (skip) {
                        if (!last_blank) print ""
                        print "## Pre-conditions"
                        if (entry != "") print entry; else print "- Previous version complete (or no prerequisites)"
                        print ""
                        print "## Post-conditions"
                        if (exit_cond != "") print exit_cond; else print "- All issues complete"
                    }
                }
            ' "$plan_file" > "${plan_file}.tmp" && mv "${plan_file}.tmp" "$plan_file"

            changed=true
            log_migration "  Converted Gates → Pre/Post-conditions in-place: $plan_file"
        fi

        # Handle standalone "## Entry Gate" section
        if grep -q "^## Entry Gate$" "$plan_file" 2>/dev/null; then
            sed -i 's/^## Entry Gate$/## Pre-conditions/' "$plan_file"
            changed=true
            log_migration "  Renamed Entry Gate → Pre-conditions: $plan_file"
        fi

        # Handle standalone "## Exit Gate" or "## Exit Gate Tasks" section
        if grep -q "^## Exit Gate" "$plan_file" 2>/dev/null; then
            sed -i 's/^## Exit Gate Tasks$/## Post-conditions/' "$plan_file"
            sed -i 's/^## Exit Gate$/## Post-conditions/' "$plan_file"
            changed=true
            log_migration "  Renamed Exit Gate → Post-conditions: $plan_file"
        fi

        if [[ "$changed" == "true" ]]; then
            ((phase4_changed++)) || true
        fi

    done <<< "$all_plan_files"

    log_migration "Phase 4 complete: $phase4_changed files changed"
fi

# ──────────────────────────────────────────────────────────────────────────────
# Phase 5: Rename "curiosity" to "effort" in cat-config.json
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 5: Rename curiosity → effort in cat-config.json"

config_file=".claude/cat/cat-config.json"

if [[ ! -f "$config_file" ]]; then
    log_migration "No config file found - skipping phase 5"
else
    if grep -q '"curiosity"' "$config_file" 2>/dev/null; then
        log_migration "Found curiosity key - renaming to effort"
        sed -i 's/"curiosity"/"effort"/g' "$config_file"
        log_migration "Phase 5 complete: renamed curiosity → effort"
    else
        log_migration "No curiosity key found - skipping phase 5"
    fi
fi

# ──────────────────────────────────────────────────────────────────────────────
# Phase 6: Create or update .claude/cat/.gitignore with patterns for temp files
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 6: Create or update .claude/cat/.gitignore"

gitignore_file=".claude/cat/.gitignore"
gitignore_template="${CLAUDE_PLUGIN_ROOT}/templates/gitignore"

if [[ ! -f "$gitignore_template" ]]; then
    echo "ERROR: .gitignore template not found: $gitignore_template" >&2
    echo "Solution: Verify plugin installation is complete." >&2
    exit 1
fi

if [[ ! -f "$gitignore_file" ]]; then
    log_migration "No .gitignore found - copying template"
    cp "$gitignore_template" "$gitignore_file"
    log_migration "Phase 5 complete: created $gitignore_file"
else
    log_migration ".gitignore exists - checking for missing patterns"
    phase6_changed=0

    # Extract patterns from template (non-comment, non-empty lines)
    patterns=$(grep -v '^#' "$gitignore_template" | grep -v '^[[:space:]]*$' | sed 's/#.*//' | sed 's/[[:space:]]*$//')

    while IFS= read -r pattern; do
        [[ -z "$pattern" ]] && continue
        if ! grep -qF "$pattern" "$gitignore_file" 2>/dev/null; then
            printf '%s\n' "$pattern" >> "$gitignore_file"
            log_migration "  Added missing pattern: $pattern"
            ((phase6_changed++)) || true
        fi
    done <<< "$patterns"

    if [[ "$phase6_changed" -eq 0 ]]; then
        log_migration "Phase 6 complete: all patterns already present"
    else
        log_migration "Phase 6 complete: added $phase6_changed missing patterns"
    fi
fi

log_success "Migration to 2.1 completed"
exit 0
