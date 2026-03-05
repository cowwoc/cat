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
# 6. Create .claude/cat/.gitignore with patterns for local config files if missing;
#    if it exists, add any missing patterns and remove stale ones (/worktrees/, /locks/, /verify/)
#    including their associated comment and blank lines
# 7. Migrate ## Execution Steps → ## Execution Waves in PLAN.md files
#    (numbered steps become bullet items under ### Wave 1)
# 8. Remove reviewThreshold from cat-config.json
# 9. Remove legacy worktree-locks directory
# 10. Migrate cross-session dirs (locks/, worktrees/) to external storage; delete stale
#     session-scoped dirs (sessions/, verify/, e2e-config-test/)
# 11. Migrate terminalWidth to fileWidth + displayWidth in cat-config.json
# 12. Remove deprecated Last Updated and Completed fields from open issue-level STATE.md files
#     (closed issues are not modified)

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
            # No "## Issues Pending" section: create one before "## Issues Closed"
            # (or at end of file if no Closed section), then remove In Progress section.
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

                /^## Issues Closed/ && !inserted {
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
# Phase 6: Create or update .claude/cat/.gitignore with patterns for local config
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
    log_migration "Phase 6 complete: created $gitignore_file"
else
    log_migration ".gitignore exists - checking for missing/stale patterns"
    phase6_changed=0

    # Remove stale patterns and their associated comment+blank lines above them.
    # We build a set of stale pattern strings, then use awk to walk the file once:
    #   - Buffer each comment line (lines starting with #) and blank lines
    #   - When a stale pattern line is found, discard the buffer (comment block) and the pattern line
    #   - When a non-stale non-comment non-blank line is found, flush the buffer before printing it
    stale_patterns=("/worktrees/" "/locks/" "/verify/")
    stale_comments=(
        "# Temporary worktrees created for issue isolation"
        "# Lock files used to prevent concurrent access"
        "# Verification output from audit and verify commands"
    )
    stale_set=""
    for stale in "${stale_patterns[@]}"; do
        if grep -qF "$stale" "$gitignore_file" 2>/dev/null; then
            stale_set="${stale_set}${stale}\n"
            ((phase6_changed++)) || true
        fi
    done

    if [[ -n "$stale_set" ]]; then
        awk -v stale_set="$stale_set" '
            BEGIN {
                n = split(stale_set, stale_arr, "\n")
                for (i = 1; i <= n; i++) {
                    if (stale_arr[i] != "") stale_map[stale_arr[i]] = 1
                }
                buf_len = 0
            }
            # Lines that begin with # or are blank go into a lookahead buffer
            /^[[:space:]]*#/ || /^[[:space:]]*$/ {
                buf[buf_len++] = $0
                next
            }
            # Non-comment, non-blank line: check if it is a stale pattern
            {
                is_stale = 0
                for (p in stale_map) {
                    if (index($0, p) > 0) { is_stale = 1; break }
                }
                if (is_stale) {
                    # Discard the buffered comment/blank block and the pattern line itself
                    buf_len = 0
                } else {
                    # Flush the buffer, then print the current line
                    for (i = 0; i < buf_len; i++) print buf[i]
                    buf_len = 0
                    print
                }
            }
            END {
                # Flush any trailing comments/blanks
                for (i = 0; i < buf_len; i++) print buf[i]
            }
        ' "$gitignore_file" > "${gitignore_file}.tmp" && mv "${gitignore_file}.tmp" "$gitignore_file"
        log_migration "  Removed stale patterns and associated comments: $(printf '%s' "$stale_set" | tr '\n' ' ')"
    fi

    # Remove orphaned stale comment lines that remain when patterns were already removed
    # by a prior migration run (awk above only removes comments paired with their pattern).
    for stale_comment in "${stale_comments[@]}"; do
        if grep -qF "$stale_comment" "$gitignore_file" 2>/dev/null; then
            grep -vF "$stale_comment" "$gitignore_file" > "${gitignore_file}.tmp" \
                && mv "${gitignore_file}.tmp" "$gitignore_file"
            log_migration "  Removed orphaned stale comment: $stale_comment"
            ((phase6_changed++)) || true
        fi
    done

    # Collapse consecutive blank lines and strip leading/trailing blank lines
    # left behind after comment removal.
    awk '
        /^[[:space:]]*$/ { blank++; next }
        { if (blank > 0 && NR > 1 && printed > 0) print ""; blank = 0; print; printed++ }
        END { if (printed == 0 && blank > 0) { } }
    ' "$gitignore_file" > "${gitignore_file}.tmp" && mv "${gitignore_file}.tmp" "$gitignore_file"

    # Add any missing patterns from template
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
        log_migration "Phase 6 complete: all patterns up to date"
    else
        log_migration "Phase 6 complete: updated $phase6_changed patterns"
    fi
fi

# ──────────────────────────────────────────────────────────────────────────────
# Phase 7: Migrate ## Execution Steps to ## Execution Waves in PLAN.md files
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 7: Migrate Execution Steps → Execution Waves in PLAN.md files"

all_plan_files=$(find .claude/cat/issues -name "PLAN.md" -type f 2>/dev/null || true)

if [[ -z "$all_plan_files" ]]; then
    log_migration "No PLAN.md files found - skipping phase 7"
else
    phase7_migrated=0

    while IFS= read -r plan_file; do
        [[ -z "$plan_file" ]] && continue

        # Skip if already migrated (has Execution Waves)
        if grep -q "^## Execution Waves" "$plan_file" 2>/dev/null; then
            continue
        fi

        # Skip if no old format exists
        if ! grep -q "^## Execution Steps" "$plan_file" 2>/dev/null; then
            continue
        fi

        log_migration "  Migrating: $plan_file"

        # Use awk to preserve all content while renaming the section heading and inserting Wave 1
        # The awk command:
        # 1. Matches "^## Execution Steps" and replaces with "## Execution Waves\n\n### Wave 1"
        # 2. Preserves all subsequent lines unchanged until EOF or next "^## " heading
        # 3. Passes through lines before the section unchanged
        awk '
        /^## Execution Steps/ {
            print "## Execution Waves"
            print ""
            print "### Wave 1"
            in_section = 1
            last_blank=0
            next
        }
        in_section && /^## / {
            in_section=0
            if (!last_blank) print ""
            print
            next
        }
        { print; last_blank=($0 == "") }
        ' "$plan_file" > "${plan_file}.tmp" && mv "${plan_file}.tmp" "$plan_file"

        ((phase7_migrated++)) || true
        log_migration "    Done: $plan_file"

    done <<< "$all_plan_files"

    log_migration "Phase 7 complete: $phase7_migrated files migrated"
fi

# ──────────────────────────────────────────────────────────────────────────────
# Phase 8: Remove reviewThreshold from cat-config.json
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 8: Remove reviewThreshold from cat-config.json"

config_file=".claude/cat/cat-config.json"

if [[ ! -f "$config_file" ]]; then
    log_migration "No config file found - skipping phase 8"
else
    if grep -q '"reviewThreshold"' "$config_file" 2>/dev/null; then
        log_migration "Found reviewThreshold key - removing"

        # Remove "reviewThreshold": "value" with various formatting.
        # Four cases (processed in order by a single awk pass):
        #   1. Sole field on its own line:      "reviewThreshold": "value"
        #   2. First/middle field (followed by trailing comma on same line):
        #                                       "reviewThreshold": "value",
        #   3. Last field (preceded by comma on a previous line): drop the line entirely
        #      and remove the trailing comma from the preceding field line.
        #   4. First field with comma on next line: handled by blank-line cleanup below.
        tmp_file="${config_file}.tmp"
        awk '
        {
            # Case 2 & 1: field followed by comma on same line, or sole field
            if (/[[:space:]]*"reviewThreshold"[[:space:]]*:[[:space:]]*"[^"]*"[[:space:]]*,?[[:space:]]*$/) {
                next
            }
            print
        }
        ' "$config_file" > "$tmp_file"

        # Case 3: after removing a last field, the preceding line may have a trailing comma.
        # Remove trailing commas before closing brace (handles ", \n }" -> "\n }").
        awk '
        {
            lines[NR] = $0
        }
        END {
            for (i = 1; i <= NR; i++) {
                line = lines[i]
                # Remove trailing comma if next non-empty line is a closing brace
                if (line ~ /,[[:space:]]*$/) {
                    j = i + 1
                    while (j <= NR && lines[j] ~ /^[[:space:]]*$/) j++
                    if (j <= NR && lines[j] ~ /^[[:space:]]*\}[[:space:]]*$/) {
                        sub(/,[[:space:]]*$/, "", line)
                    }
                }
                print line
            }
        }
        ' "$tmp_file" > "${tmp_file}.2"
        mv "${tmp_file}.2" "$config_file"
        rm -f "$tmp_file"

        # Post-removal validation: verify reviewThreshold is gone
        if grep -q '"reviewThreshold"' "$config_file" 2>/dev/null; then
            log_migration "WARNING: reviewThreshold still present after removal attempt"
        else
            log_migration "Phase 8 complete: removed reviewThreshold from config"
        fi
    else
        log_migration "No reviewThreshold key found - skipping phase 8"
    fi
fi

# ──────────────────────────────────────────────────────────────────────────────
# Phase 9: Remove legacy worktree-locks directory
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 9: Remove legacy worktree-locks directory"

# Old in-project location
old_wt_locks=".claude/cat/worktree-locks"
if [[ -d "$old_wt_locks" ]]; then
    rm -rf "$old_wt_locks"
    log_migration "  Removed legacy worktree-locks from: $old_wt_locks"
else
    log_migration "  No legacy worktree-locks at: $old_wt_locks"
fi

# External storage location
if [[ -z "${CLAUDE_CONFIG_DIR:-}" ]]; then
    log_migration "  CLAUDE_CONFIG_DIR not set - skipping external storage cleanup"
else
    encoded_project=$(pwd | sed 's/[\/.]/-/g')
    ext_wt_locks="${CLAUDE_CONFIG_DIR}/projects/${encoded_project}/cat/worktree-locks"
    if [[ -d "$ext_wt_locks" ]]; then
        rm -rf "$ext_wt_locks"
        log_migration "  Removed legacy worktree-locks from external storage: $ext_wt_locks"
    else
        log_migration "  No legacy worktree-locks in external storage"
    fi
fi

log_migration "Phase 9 complete: legacy worktree-locks cleanup done"

# ──────────────────────────────────────────────────────────────────────────────
# Phase 10: Migrate or remove directories that moved to external storage
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 10: Migrate cross-session directories and delete stale session-scoped dirs"

config_dir="${CLAUDE_CONFIG_DIR:-${HOME}/.config/claude}"
encoded_project=$(echo "${CLAUDE_PROJECT_DIR:-$(pwd)}" | sed 's/[\/.]/-/g')
project_cat_dir="${config_dir}/projects/${encoded_project}/cat"

# Move cross-session directories to external storage
for dir_name in locks worktrees; do
    src_dir=".claude/cat/${dir_name}"
    dst_dir="${project_cat_dir}/${dir_name}"

    if [[ -d "$src_dir" ]]; then
        log_migration "  Moving ${src_dir} → ${dst_dir}"
        mkdir -p "$dst_dir"
        # Move all contents including dotfiles; find does nothing if directory is empty
        find "$src_dir" -maxdepth 1 -mindepth 1 -exec mv -t "$dst_dir/" {} +
        rm -rf "$src_dir"
        log_migration "  Done: ${dir_name} moved to external storage"
    else
        log_migration "  ${src_dir} not present - skipping"
    fi
done

# Delete session-scoped directories (always stale after migration)
for dir_name in sessions verify e2e-config-test; do
    src_dir=".claude/cat/${dir_name}"
    if [[ -d "$src_dir" ]]; then
        rm -rf "$src_dir"
        log_migration "  Deleted stale session-scoped dir: ${src_dir}"
    else
        log_migration "  ${src_dir} not present - skipping"
    fi
done

log_migration "Phase 10 complete"

# ──────────────────────────────────────────────────────────────────────────────
# Phase 11: Migrate terminalWidth to fileWidth + displayWidth in cat-config.json
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 11: Migrate terminalWidth to fileWidth + displayWidth in cat-config.json"

config_file=".claude/cat/cat-config.json"

if [[ ! -f "$config_file" ]]; then
    log_migration "No config file found - skipping phase 11"
else
    if ! grep -q '"terminalWidth"' "$config_file" 2>/dev/null; then
        log_migration "No terminalWidth key found - skipping phase 11 (already migrated or not set)"
    else
        log_migration "Found terminalWidth key - migrating to fileWidth and displayWidth"

        # Read the terminalWidth value
        terminal_width=$(sed -n 's/.*"terminalWidth"[[:space:]]*:[[:space:]]*\([0-9]*\).*/\1/p' "$config_file" | head -1)

        if [[ -z "$terminal_width" ]]; then
            log_migration "WARNING: Could not extract terminalWidth value - skipping phase 11"
        else
            log_migration "  terminalWidth value: $terminal_width"

            # Add fileWidth and displayWidth with the same value as terminalWidth.
            # Insert them before the terminalWidth line, then remove terminalWidth.
            tmp_file="${config_file}.tmp"
            awk -v width="$terminal_width" '
            {
                # Before removing terminalWidth line, insert fileWidth and displayWidth if not present
                if (/[[:space:]]*"terminalWidth"[[:space:]]*:/) {
                    # Insert fileWidth and displayWidth entries with trailing comma to maintain valid JSON
                    print "  \"fileWidth\": " width ","
                    print "  \"displayWidth\": " width ","
                    # Skip the terminalWidth line itself
                    next
                }
                print
            }
            ' "$config_file" > "$tmp_file"

            # Remove trailing commas before closing brace (handles last-field case)
            awk '
            {
                lines[NR] = $0
            }
            END {
                for (i = 1; i <= NR; i++) {
                    line = lines[i]
                    if (line ~ /,[[:space:]]*$/) {
                        j = i + 1
                        while (j <= NR && lines[j] ~ /^[[:space:]]*$/) j++
                        if (j <= NR && lines[j] ~ /^[[:space:]]*\}[[:space:]]*$/) {
                            sub(/,[[:space:]]*$/, "", line)
                        }
                    }
                    print line
                }
            }
            ' "$tmp_file" > "${tmp_file}.2"
            mv "${tmp_file}.2" "$config_file"
            rm -f "$tmp_file"

            log_migration "  Wrote fileWidth: $terminal_width and displayWidth: $terminal_width"
            log_migration "  Removed terminalWidth"

            # Post-migration validation
            if grep -q '"terminalWidth"' "$config_file" 2>/dev/null; then
                log_migration "WARNING: terminalWidth still present after migration"
            else
                log_migration "  Verified: terminalWidth absent"
            fi

            if grep -q '"fileWidth"' "$config_file" 2>/dev/null; then
                log_migration "  Verified: fileWidth present"
            else
                log_migration "WARNING: fileWidth not found after migration"
            fi

            if grep -q '"displayWidth"' "$config_file" 2>/dev/null; then
                log_migration "  Verified: displayWidth present"
            else
                log_migration "WARNING: displayWidth not found after migration"
            fi

            log_migration "Phase 11 complete: migrated terminalWidth to fileWidth and displayWidth"
        fi
    fi
fi

# Phase 12: Remove deprecated Last Updated, Completed, and Closed fields from open issue-level STATE.md
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 12: Remove deprecated Last Updated, Completed, and Closed fields from open issue-level STATE.md"

# Issue-level STATE.md files live at .claude/cat/issues/v*/v*.*/issue-name/STATE.md (depth 5 from issues/).
issue_state_files=$(find .claude/cat/issues -path "*v*.*/*" -name "STATE.md" -mindepth 5 -maxdepth 5 -type f \
    2>/dev/null || true)

if [[ -z "$issue_state_files" ]]; then
    log_migration "No issue-level STATE.md files found - skipping phase 12"
else
    total_count=$(echo "$issue_state_files" | wc -l | tr -d ' ')
    log_migration "Found $total_count issue-level STATE.md files to check"

    phase12_changed=0
    phase12_skipped=0

    while IFS= read -r state_file; do
        [[ -z "$state_file" ]] && continue

        # Skip closed issues
        if grep -q '^\*\*Status:\*\* closed' "$state_file" 2>/dev/null || \
           grep -q '^- \*\*Status:\*\* closed' "$state_file" 2>/dev/null; then
            ((phase12_skipped++)) || true
            continue
        fi

        changed=false

        if grep -q '^- \*\*Last Updated:\*\*' "$state_file" 2>/dev/null; then
            sed -i '/^- \*\*Last Updated:\*\*/d' "$state_file"
            changed=true
        fi

        if grep -q '^- \*\*Completed:\*\*' "$state_file" 2>/dev/null; then
            sed -i '/^- \*\*Completed:\*\*/d' "$state_file"
            changed=true
        fi

        if grep -q '^- \*\*Closed:\*\*' "$state_file" 2>/dev/null; then
            sed -i '/^- \*\*Closed:\*\*/d' "$state_file"
            changed=true
        fi

        if [[ "$changed" == "true" ]]; then
            ((phase12_changed++)) || true
            log_migration "  Updated: $state_file"
        fi

    done <<< "$issue_state_files"

    log_migration "Phase 12 complete: $phase12_changed files changed, $phase12_skipped closed issues skipped"
fi

log_success "Migration to 2.1 completed"
exit 0
