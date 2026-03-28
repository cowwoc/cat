#!/bin/bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
set -euo pipefail

# Migration to CAT 2.1
#
# Changes (consolidated chronologically):
# 1. Move .claude/cat/ directory to .cat/ at workspace root
# 2. Remove "## Issues In Progress" section from version-level STATE.md files,
#    merging its entries into "## Issues Pending"
# 3. Rename issue status values in STATE.md files:
#    pending → open, completed/complete/done → closed
# 4. Move version tracking from cat-config.json to .cat/VERSION plain text file
#    (handles both old "version" field and renamed "last_migrated_version" field)
# 5. Rename sections in PLAN.md files:
#    "## Acceptance Criteria" → "## Post-conditions"
#    "## Success Criteria" → merged into "## Post-conditions"
#    "## Gates" / "### Entry" / "### Exit" → "## Pre-conditions" / "## Post-conditions"
#    "## Entry Gate" / "## Exit Gate" → "## Pre-conditions" / "## Post-conditions"
#    "## Exit Gate Tasks" → "## Post-conditions"
# 6. Rename config keys: verify→caution, effort→curiosity, patience→perfection (with value inversion)
# 7. Create .cat/.gitignore with patterns for local config files if missing;
#    if it exists, add any missing patterns and remove stale ones (/worktrees/, /locks/, /verify/)
#    including their associated comment and blank lines
# 8. Migrate ## Execution Steps → ## Execution Waves in PLAN.md files
#    (numbered steps become bullet items under ### Wave 1)
# 9. Remove reviewThreshold from cat-config.json
# 10. Remove legacy worktree-locks directory
# 11. Migrate cross-session dirs (locks/, worktrees/, verify/) to .cat/work/ inside project workspace
# 12. Migrate terminalWidth to fileWidth + displayWidth in cat-config.json
# 13. Remove deprecated Last Updated and Completed fields from issue-level STATE.md files
# 14. Rename ## Satisfies → ## Parent Requirements in issue-level PLAN.md files
# 15. Rename ## Execution Waves → ## Sub-Agent Waves in PLAN.md files
#     (all issues, including closed ones)
# 16. Rename cat-config.json → config.json and cat-config.local.json → config.local.json
# 17. Convert bare sub-issue names to qualified names in "Decomposed Into" sections
# 18. Rename PLAN.md → plan.md in all issue directories under .cat/issues/
# 19. Convert STATE.md → index.json in all directories under .cat/issues/,
#     extracting: status, resolution, target_branch, dependencies, blocks, parent, decomposedInto
#     Strips Progress, Last Updated, Completed, and any narrative content.
# 20. Rename targetBranch → target_branch in all index.json files under .cat/issues/
# 21. Rename caution config values: none→low, changed→medium, all→high

trap 'echo "ERROR in 2.1.sh at line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# shellcheck source=lib/utils.sh
source "${CLAUDE_PLUGIN_ROOT}/migrations/lib/utils.sh"

# ──────────────────────────────────────────────────────────────────────────────
# Phase 1: Move .claude/cat/ directory to .cat/ at workspace root
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 1: Move .claude/cat/ directory to .cat/"

if [[ -d ".cat" ]]; then
    log_migration ".cat/ already exists - skipping phase 1 (already migrated)"
elif [[ -d ".claude/cat" ]]; then
    log_migration "Moving .claude/cat/ → .cat/"
    mv .claude/cat .cat
    # Clean up empty .claude/ directory if nothing else remains
    if [[ -d ".claude" ]] && [[ -z "$(ls -A .claude 2>/dev/null)" ]]; then
        rmdir .claude
        log_migration "  Removed empty .claude/ directory"
    fi
    log_migration "Phase 1 complete: moved .claude/cat/ to .cat/"
else
    log_migration "Neither .claude/cat/ nor .cat/ found - skipping phase 1"
fi

# ──────────────────────────────────────────────────────────────────────────────
# Phase 2: Merge "Issues In Progress" into "Issues Pending" in version STATE.md
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 2: Remove In Progress section from version STATE.md files"

# Version-level STATE.md files live at .cat/issues/v*/v*.*/STATE.md (depth 4 from issues/).
# Issue-level STATE.md files are one directory deeper (depth 5+) and must be excluded.
version_state_files=$(find .cat/issues -path "*v*.*/*" -name "STATE.md" -mindepth 4 -maxdepth 4 -type f \
    2>/dev/null || true)

if [[ -z "$version_state_files" ]]; then
    log_migration "No version-level STATE.md files found - skipping phase 2"
else
    totalCount=$(echo "$version_state_files" | wc -l | tr -d ' ')
    log_migration "Found $totalCount version-level STATE.md files to check"

    phase2_migrated=0

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

        ((phase2_migrated++)) || true
        log_migration "    Done: $state_file"

    done <<< "$version_state_files"

    log_migration "Phase 2 complete: $phase2_migrated files migrated"
fi

# ──────────────────────────────────────────────────────────────────────────────
# Phase 3: Rename issue status values in STATE.md files
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 3: Rename issue status values (pending→open, completed→closed)"

# Find all STATE.md files under .cat/issues/
all_state_files=$(find .cat/issues -name "STATE.md" -type f 2>/dev/null || true)
all_state_count=$(echo "$all_state_files" | grep -c "STATE.md" || echo 0)

if [[ "$all_state_count" -eq 0 ]]; then
    log_migration "No STATE.md files found - skipping phase 3"
else
    log_migration "Found $all_state_count STATE.md files to check"

    phase3_changed=0

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
            ((phase3_changed++)) || true
            log_migration "  Updated: $state_file"
        fi

    done <<< "$all_state_files"

    log_migration "Phase 3 complete: $phase3_changed files changed"
fi

# ──────────────────────────────────────────────────────────────────────────────
# Phase 4: Move version tracking to .cat/VERSION plain text file
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 4: Move version tracking from cat-config.json to VERSION file"

config_file=".cat/cat-config.json"
version_file=".cat/VERSION"

if [[ ! -f "$config_file" ]]; then
    log_migration "No config file found - skipping phase 4"
elif [[ -f "$version_file" ]]; then
    log_migration "VERSION file already exists - skipping phase 4"
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

        log_migration "Phase 4 complete: moved to VERSION file and removed from config"
    else
        log_migration "No version field found in config - skipping phase 4"
    fi
fi

# ──────────────────────────────────────────────────────────────────────────────
# Phase 5: Rename PLAN.md sections to pre-conditions / post-conditions
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 5: Rename PLAN.md sections to pre-conditions/post-conditions"

all_plan_files=$(find .cat/issues -name "PLAN.md" -type f 2>/dev/null || true)
all_plan_count=$(echo "$all_plan_files" | grep -c "PLAN.md" || echo 0)

if [[ "$all_plan_count" -eq 0 ]]; then
    log_migration "No PLAN.md files found - skipping phase 5"
else
    log_migration "Found $all_plan_count PLAN.md files to check"

    phase5_changed=0

    while IFS= read -r planFile; do
        [[ -z "$planFile" ]] && continue

        changed=false

        # Rename "## Acceptance Criteria" → "## Post-conditions"
        if grep -q "^## Acceptance Criteria" "$planFile" 2>/dev/null; then
            sed -i 's/^## Acceptance Criteria$/## Post-conditions/' "$planFile"
            changed=true
            log_migration "  Renamed Acceptance Criteria → Post-conditions: $planFile"
        fi

        # Rename "## Success Criteria" → "## Post-conditions" (merge)
        # If both "## Post-conditions" and "## Success Criteria" exist, remove Success Criteria section
        # If only "## Success Criteria" exists, rename it
        if grep -q "^## Success Criteria" "$planFile" 2>/dev/null; then
            if grep -q "^## Post-conditions" "$planFile" 2>/dev/null; then
                # Both sections exist - merge Success Criteria content into Post-conditions
                # Extract Success Criteria items
                success_items=$(awk '
                    /^## Success Criteria/ { in_section=1; next }
                    in_section && /^## / { in_section=0 }
                    in_section && /^- / { print }
                ' "$planFile")

                # Remove Success Criteria section
                awk '
                    /^## Success Criteria/ { skip=1; next }
                    skip && /^## / { skip=0; print; next }
                    skip { next }
                    { print }
                ' "$planFile" > "${planFile}.tmp" && mv "${planFile}.tmp" "$planFile"

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
                    ' "$planFile" > "${planFile}.tmp" && mv "${planFile}.tmp" "$planFile"
                fi
            else
                # Only Success Criteria - rename it to Post-conditions
                sed -i 's/^## Success Criteria$/## Post-conditions/' "$planFile"
            fi
            changed=true
            log_migration "  Merged/renamed Success Criteria → Post-conditions: $planFile"
        fi

        # Handle "## Gates" with "### Entry" and "### Exit" subsections
        if grep -q "^## Gates" "$planFile" 2>/dev/null; then
            # Extract Entry section content
            entry_content=$(awk '
                /^### Entry/ { in_section=1; next }
                in_section && /^### / { in_section=0 }
                in_section && /^## / { in_section=0 }
                in_section { print }
            ' "$planFile")

            # Extract Exit section content
            exit_content=$(awk '
                /^### Exit/ { in_section=1; next }
                in_section && /^### / { in_section=0 }
                in_section && /^## / { in_section=0 }
                in_section { print }
            ' "$planFile")

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
            ' "$planFile" > "${planFile}.tmp" && mv "${planFile}.tmp" "$planFile"

            changed=true
            log_migration "  Converted Gates → Pre/Post-conditions in-place: $planFile"
        fi

        # Handle standalone "## Entry Gate" section
        if grep -q "^## Entry Gate$" "$planFile" 2>/dev/null; then
            sed -i 's/^## Entry Gate$/## Pre-conditions/' "$planFile"
            changed=true
            log_migration "  Renamed Entry Gate → Pre-conditions: $planFile"
        fi

        # Handle standalone "## Exit Gate" or "## Exit Gate Tasks" section
        if grep -q "^## Exit Gate" "$planFile" 2>/dev/null; then
            sed -i 's/^## Exit Gate Tasks$/## Post-conditions/' "$planFile"
            sed -i 's/^## Exit Gate$/## Post-conditions/' "$planFile"
            changed=true
            log_migration "  Renamed Exit Gate → Post-conditions: $planFile"
        fi

        if [[ "$changed" == "true" ]]; then
            ((phase5_changed++)) || true
        fi

    done <<< "$all_plan_files"

    log_migration "Phase 5 complete: $phase5_changed files changed"
fi

# ──────────────────────────────────────────────────────────────────────────────
# Phase 6: Rename config keys verify→caution, effort→curiosity, patience→perfection
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 6: Rename config keys verify→caution, effort→curiosity, patience→perfection"

config_file=".cat/config.json"

if [[ ! -f "$config_file" ]]; then
    log_migration "No config file found - skipping phase 6"
else
    phase6_changed=false

    # Rename "verify" → "caution" (idempotent: skip if already renamed)
    if grep -q '"verify"' "$config_file" 2>/dev/null && ! grep -q '"caution"' "$config_file" 2>/dev/null; then
        sed -i 's/"verify"/"caution"/g' "$config_file"
        log_migration "  Renamed verify → caution"
        phase6_changed=true
    fi

    # Rename "effort" → "curiosity" (idempotent: skip if already renamed)
    if grep -q '"effort"' "$config_file" 2>/dev/null && ! grep -q '"curiosity"' "$config_file" 2>/dev/null; then
        sed -i 's/"effort"/"curiosity"/g' "$config_file"
        log_migration "  Renamed effort → curiosity"
        phase6_changed=true
    fi

    # Rename "patience" → "perfection" with value inversion (idempotent: skip if already renamed)
    if grep -q '"patience"' "$config_file" 2>/dev/null && ! grep -q '"perfection"' "$config_file" 2>/dev/null; then
        # Extract current patience value before renaming
        patience_value=$(grep -o '"patience"[[:space:]]*:[[:space:]]*"[^"]*"' "$config_file" | head -1 | \
            sed 's/.*"patience"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
        # Invert the scale: high↔low, medium stays medium
        case "$patience_value" in
            high) inverted_value="low" ;;
            low)  inverted_value="high" ;;
            *)    inverted_value="$patience_value" ;;
        esac
        sed -i "s/\"patience\"[[:space:]]*:[[:space:]]*\"[^\"]*\"/\"perfection\": \"${inverted_value}\"/" "$config_file"
        log_migration "  Renamed patience → perfection (value: ${patience_value} → ${inverted_value})"
        phase6_changed=true
    fi

    if [[ "$phase6_changed" == "true" ]]; then
        log_migration "Phase 6 complete: config keys renamed"
    else
        log_migration "Phase 6: no old keys found - skipping"
    fi
fi

# ──────────────────────────────────────────────────────────────────────────────
# Phase 7: Create or update .cat/.gitignore with patterns for local config
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 7: Create or update .cat/.gitignore"

gitignore_file=".cat/.gitignore"
gitignore_template="${CLAUDE_PLUGIN_ROOT}/templates/gitignore"

if [[ ! -f "$gitignore_template" ]]; then
    echo "ERROR: .gitignore template not found: $gitignore_template" >&2
    echo "Solution: Verify plugin installation is complete." >&2
    exit 1
fi

if [[ ! -f "$gitignore_file" ]]; then
    log_migration "No .gitignore found - copying template"
    cp "$gitignore_template" "$gitignore_file"
    # work/ contains runtime data (locks/, worktrees/, verify/) and must not be committed
    if ! grep -qF "work/" "$gitignore_file" 2>/dev/null; then
        printf '%s\n' "work/" >> "$gitignore_file"
    fi
    log_migration "Phase 7 complete: created $gitignore_file"
else
    log_migration ".gitignore exists - checking for missing/stale patterns"
    phase7_changed=0

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
            ((phase7_changed++)) || true
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
            ((phase7_changed++)) || true
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
            ((phase7_changed++)) || true
        fi
    done <<< "$patterns"

    # Ensure work/ is present (runtime data: locks/, worktrees/, verify/)
    if ! grep -qF "work/" "$gitignore_file" 2>/dev/null; then
        printf '%s\n' "work/" >> "$gitignore_file"
        log_migration "  Added missing pattern: work/"
        ((phase7_changed++)) || true
    fi

    if [[ "$phase7_changed" -eq 0 ]]; then
        log_migration "Phase 7 complete: all patterns up to date"
    else
        log_migration "Phase 7 complete: updated $phase7_changed patterns"
    fi
fi

# ──────────────────────────────────────────────────────────────────────────────
# Phase 8: Migrate ## Execution Steps to ## Execution Waves in PLAN.md files
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 8: Migrate Execution Steps → Execution Waves in PLAN.md files"

all_plan_files=$(find .cat/issues -name "PLAN.md" -type f 2>/dev/null || true)

if [[ -z "$all_plan_files" ]]; then
    log_migration "No PLAN.md files found - skipping phase 8"
else
    phase8_migrated=0

    while IFS= read -r planFile; do
        [[ -z "$planFile" ]] && continue

        # Skip if already migrated (has Execution Waves)
        if grep -q "^## Execution Waves" "$planFile" 2>/dev/null; then
            continue
        fi

        # Skip if no old format exists
        if ! grep -q "^## Execution Steps" "$planFile" 2>/dev/null; then
            continue
        fi

        log_migration "  Migrating: $planFile"

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
        ' "$planFile" > "${planFile}.tmp" && mv "${planFile}.tmp" "$planFile"

        ((phase8_migrated++)) || true
        log_migration "    Done: $planFile"

    done <<< "$all_plan_files"

    log_migration "Phase 8 complete: $phase8_migrated files migrated"
fi

# ──────────────────────────────────────────────────────────────────────────────
# Phase 9: Remove reviewThreshold from cat-config.json
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 9: Remove reviewThreshold from cat-config.json"

config_file=".cat/cat-config.json"

if [[ ! -f "$config_file" ]]; then
    log_migration "No config file found - skipping phase 9"
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
            log_migration "Phase 9 complete: removed reviewThreshold from config"
        fi
    else
        log_migration "No reviewThreshold key found - skipping phase 9"
    fi
fi

# ──────────────────────────────────────────────────────────────────────────────
# Phase 10: Remove legacy worktree-locks directory
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 10: Remove legacy worktree-locks directory"

# Old in-project location
old_wt_locks=".cat/worktree-locks"
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

log_migration "Phase 10 complete: legacy worktree-locks cleanup done"

# ──────────────────────────────────────────────────────────────────────────────
# Phase 11: Migrate cross-session dirs (locks/, worktrees/, verify/) to .cat/work/
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 11: Migrate cross-session directories to .cat/work/"

config_dir="${CLAUDE_CONFIG_DIR:-${HOME}/.config/claude}"
project_dir="${CLAUDE_PROJECT_DIR:-$(pwd)}"
ENCODED_PROJECT_DIR=$(echo "${project_dir}" | tr '/.' '-')
ext_project_cat_dir="${config_dir}/projects/${ENCODED_PROJECT_DIR}/cat"

new_locks_dir=".cat/work/locks"
new_worktrees_dir=".cat/work/worktrees"
new_verify_base=".cat/work/verify"

# Idempotency: if .cat/work/locks already exists and old locations are absent, skip
phase11_needs_work=false
if [[ ! -d "$new_locks_dir" ]]; then
    phase11_needs_work=true
elif [[ -d ".cat/locks" ]] || [[ -d "${ext_project_cat_dir}/locks" ]]; then
    phase11_needs_work=true
fi

if [[ "$phase11_needs_work" == "false" ]]; then
    log_migration "Phase 11: .cat/work/locks/ already exists and no old locations found — skipping (already migrated)"
else

    # Check for active worktrees before moving
    for wt_src in ".cat/worktrees" "${ext_project_cat_dir}/worktrees"; do
        if [[ -d "$wt_src" ]]; then
            active_worktrees=$(find "$wt_src" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | head -5 || true)
            if [[ -n "$active_worktrees" ]]; then
                echo "ERROR: Active worktrees found under ${wt_src}/:" >&2
                echo "$active_worktrees" >&2
                echo "Migration aborted. Remove or complete all active worktrees before migrating." >&2
                echo "Run: /cat:cleanup to remove abandoned worktrees" >&2
                exit 1
            fi
        fi
    done

    # Check for active lock files before moving
    for lock_src in ".cat/locks" "${ext_project_cat_dir}/locks"; do
        if [[ -d "$lock_src" ]]; then
            active_locks=$(find "$lock_src" -mindepth 1 -maxdepth 1 -name "*.lock" 2>/dev/null | head -5 || true)
            if [[ -n "$active_locks" ]]; then
                echo "ERROR: Active lock files found under ${lock_src}/:" >&2
                echo "$active_locks" >&2
                echo "Migration aborted. Remove active lock files before migrating." >&2
                echo "Run: /cat:cleanup to remove stale locks" >&2
                exit 1
            fi
        fi
    done

    # Move locks from .cat/locks/ (users who ran Phase 1 but not old Phase 11)
    if [[ -d ".cat/locks" ]]; then
        log_migration "  Moving .cat/locks/ → ${new_locks_dir}"
        mkdir -p "$new_locks_dir"
        find ".cat/locks" -maxdepth 1 -mindepth 1 -exec mv -t "${new_locks_dir}/" {} +
        rm -rf ".cat/locks"
        log_migration "  Done: locks moved from .cat/locks/"
    fi

    # Move locks from external storage (users who ran old Phase 11)
    if [[ -d "${ext_project_cat_dir}/locks" ]]; then
        log_migration "  Moving ${ext_project_cat_dir}/locks/ → ${new_locks_dir}"
        mkdir -p "$new_locks_dir"
        find "${ext_project_cat_dir}/locks" -maxdepth 1 -mindepth 1 -exec mv -t "${new_locks_dir}/" {} +
        rmdir "${ext_project_cat_dir}/locks" 2>/dev/null || true
        log_migration "  Done: locks moved from external storage"
    fi

    # Ensure new locks dir exists even if no old data was present
    mkdir -p "$new_locks_dir"

    # Move worktrees from .cat/worktrees/ (users who ran Phase 1 but not old Phase 11)
    if [[ -d ".cat/worktrees" ]]; then
        log_migration "  Moving .cat/worktrees/ → ${new_worktrees_dir}"
        mkdir -p "$new_worktrees_dir"
        find ".cat/worktrees" -maxdepth 1 -mindepth 1 -exec mv -t "${new_worktrees_dir}/" {} +
        rm -rf ".cat/worktrees"
        if [[ -d "$new_worktrees_dir" ]]; then
            git -C "${project_dir}" worktree repair "${new_worktrees_dir}" 2>/dev/null || true
            log_migration "  Repaired git worktree registry for new paths"
        fi
        log_migration "  Done: worktrees moved from .cat/worktrees/"
    fi

    # Move worktrees from external storage (users who ran old Phase 11)
    if [[ -d "${ext_project_cat_dir}/worktrees" ]]; then
        log_migration "  Moving ${ext_project_cat_dir}/worktrees/ → ${new_worktrees_dir}"
        mkdir -p "$new_worktrees_dir"
        find "${ext_project_cat_dir}/worktrees" -maxdepth 1 -mindepth 1 -exec mv -t "${new_worktrees_dir}/" {} +
        rmdir "${ext_project_cat_dir}/worktrees" 2>/dev/null || true
        if [[ -d "$new_worktrees_dir" ]]; then
            git -C "${project_dir}" worktree repair "${new_worktrees_dir}" 2>/dev/null || true
            log_migration "  Repaired git worktree registry for new paths"
        fi
        log_migration "  Done: worktrees moved from external storage"
    fi

    # Ensure new worktrees dir exists even if no old data was present
    mkdir -p "$new_worktrees_dir"

    # Migrate session-scoped verify dirs from external storage
    # Path structure: <config_dir>/projects/<encoded>/<session-uuid>/cat/verify
    ext_session_base="${config_dir}/projects/${ENCODED_PROJECT_DIR}"
    if [[ -d "${ext_session_base}" ]]; then
        while IFS= read -r verify_dir; do
            session_dir=$(dirname "$(dirname "$verify_dir")")
            SESSION_ID=$(basename "$session_dir")
            if [[ ! "${SESSION_ID}" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]]; then
                log_migration "  Skipping verify dir with non-UUID name: ${SESSION_ID}"
                continue
            fi
            new_verify_dir="${new_verify_base}/${SESSION_ID}"
            mkdir -p "${new_verify_dir}"
            find "$verify_dir" -mindepth 1 -maxdepth 1 -exec mv -t "${new_verify_dir}/" {} +
            rmdir "$verify_dir" 2>/dev/null || true
            log_migration "  Moved verify dir for session ${SESSION_ID}"
        done < <(find "${ext_session_base}" -mindepth 3 -maxdepth 3 -name "verify" -type d 2>/dev/null || true)
    fi

    # Update worktree path references in STATE.md files (from both old locations)
    old_dot_cat_worktrees_prefix="${project_dir}/.cat/worktrees/"
    old_ext_worktrees_prefix="${ext_project_cat_dir}/worktrees/"
    new_worktrees_prefix="${project_dir}/.cat/work/worktrees/"
    if [[ -d ".cat/issues" ]]; then
        while IFS= read -r state_file; do
            changed=false
            if grep -qF "${old_dot_cat_worktrees_prefix}" "$state_file" 2>/dev/null; then
                tmp=$(mktemp)
                sed "s|${old_dot_cat_worktrees_prefix}|${new_worktrees_prefix}|g" "$state_file" > "$tmp" \
                    && mv "$tmp" "$state_file"
                log_migration "  Updated .cat/worktrees/ refs in: $state_file"
                changed=true
            fi
            if grep -qF "${old_ext_worktrees_prefix}" "$state_file" 2>/dev/null; then
                tmp=$(mktemp)
                sed "s|${old_ext_worktrees_prefix}|${new_worktrees_prefix}|g" "$state_file" > "$tmp" \
                    && mv "$tmp" "$state_file"
                log_migration "  Updated external storage refs in: $state_file"
                changed=true
            fi
        done < <(find ".cat/issues" -name "STATE.md" -type f 2>/dev/null || true)
    fi

    # Clean up empty old external directory
    rmdir "${ext_project_cat_dir}" 2>/dev/null || true

    # Delete stale session-scoped directories that are no longer used
    for dir_name in sessions e2e-config-test; do
        src_dir=".cat/${dir_name}"
        if [[ -d "$src_dir" ]]; then
            rm -rf "$src_dir"
            log_migration "  Deleted stale session-scoped dir: ${src_dir}"
        fi
    done

fi

log_migration "Phase 11 complete"

# ──────────────────────────────────────────────────────────────────────────────
# Phase 12: Migrate terminalWidth to fileWidth + displayWidth in cat-config.json
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 12: Migrate terminalWidth to fileWidth + displayWidth in cat-config.json"

config_file=".cat/cat-config.json"

if [[ ! -f "$config_file" ]]; then
    log_migration "No config file found - skipping phase 12"
else
    if ! grep -q '"terminalWidth"' "$config_file" 2>/dev/null; then
        log_migration "No terminalWidth key found - skipping phase 12 (already migrated or not set)"
    else
        log_migration "Found terminalWidth key - migrating to fileWidth and displayWidth"

        # Read the terminalWidth value
        terminal_width=$(sed -n 's/.*"terminalWidth"[[:space:]]*:[[:space:]]*\([0-9]*\).*/\1/p' "$config_file" | head -1)

        if [[ -z "$terminal_width" ]]; then
            log_migration "WARNING: Could not extract terminalWidth value - skipping phase 12"
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

            log_migration "Phase 12 complete: migrated terminalWidth to fileWidth and displayWidth"
        fi
    fi
fi

# Phase 13: Remove deprecated Last Updated, Completed, and Closed fields from issue-level STATE.md
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 13: Remove deprecated Last Updated, Completed, and Closed fields from issue-level STATE.md"

# Issue-level STATE.md files live at .cat/issues/v*/v*.*/issue-name/STATE.md (depth 5 from issues/).
issue_state_files=$(find .cat/issues -path "*v*.*/*" -name "STATE.md" -mindepth 5 -maxdepth 5 -type f \
    2>/dev/null || true)

if [[ -z "$issue_state_files" ]]; then
    log_migration "No issue-level STATE.md files found - skipping phase 13"
else
    totalCount=$(echo "$issue_state_files" | wc -l | tr -d ' ')
    log_migration "Found $totalCount issue-level STATE.md files to check"

    phase13_changed=0

    while IFS= read -r state_file; do
        [[ -z "$state_file" ]] && continue

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
            ((phase13_changed++)) || true
            log_migration "  Updated: $state_file"
        fi

    done <<< "$issue_state_files"

    log_migration "Phase 13 complete: $phase13_changed files changed"
fi

# ──────────────────────────────────────────────────────────────────────────────
# Phase 14: Rename ## Satisfies → ## Parent Requirements in issue PLAN.md files
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 14: Rename ## Satisfies → ## Parent Requirements in issue PLAN.md files"

# Issue-level PLAN.md files live at .cat/issues/v*/v*.*/issue-name/PLAN.md (depth 4 from issues/).
issue_plan_files=$(find .cat/issues -path "*v*.*/*" -name "PLAN.md" -mindepth 4 -maxdepth 4 -type f \
    2>/dev/null || true)

if [[ -z "$issue_plan_files" ]]; then
    log_migration "No issue-level PLAN.md files found - skipping phase 14"
else
    totalCount=$(echo "$issue_plan_files" | wc -l | tr -d ' ')
    log_migration "Found $totalCount issue-level PLAN.md files to check"

    phase14_changed=0

    while IFS= read -r planFile; do
        [[ -z "$planFile" ]] && continue

        # Skip if already migrated (idempotency)
        if ! grep -q "^## Satisfies" "$planFile" 2>/dev/null; then
            continue
        fi

        sed -i 's/^## Satisfies$/## Parent Requirements/' "$planFile"
        ((phase14_changed++)) || true
        log_migration "  Updated: $planFile"

    done <<< "$issue_plan_files"

    log_migration "Phase 14 complete: $phase14_changed files changed"
fi

# ──────────────────────────────────────────────────────────────────────────────
# Phase 15: Rename ## Execution Waves → ## Sub-Agent Waves in PLAN.md files
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 15: Rename ## Execution Waves → ## Sub-Agent Waves in PLAN.md files"

all_plan_files=$(find .cat/issues -name "PLAN.md" -type f 2>/dev/null || true)

if [[ -z "$all_plan_files" ]]; then
    log_migration "No PLAN.md files found - skipping phase 15"
else
    phase15_migrated=0
    phase15_skipped=0

    while IFS= read -r planFile; do
        [[ -z "$planFile" ]] && continue

        # Idempotency: skip files that already use the new section name
        if grep -q "^## Sub-Agent Waves" "$planFile" 2>/dev/null; then
            ((phase15_skipped++)) || true
            continue
        fi

        # Skip files with no old section to rename
        if ! grep -q "^## Execution Waves" "$planFile" 2>/dev/null; then
            continue
        fi

        sed -i 's/^## Execution Waves$/## Sub-Agent Waves/' "$planFile"
        ((phase15_migrated++)) || true
        log_migration "  Updated: $planFile"

    done <<< "$all_plan_files"

    log_migration "Phase 15 complete: $phase15_migrated files migrated, $phase15_skipped already up to date"
fi

# ──────────────────────────────────────────────────────────────────────────────
# Phase 16: Rename cat-config.json → config.json and cat-config.local.json → config.local.json
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 16: Rename cat-config.json → config.json and cat-config.local.json → config.local.json"

cat_dir=".cat"

if [[ ! -d "$cat_dir" ]]; then
    log_migration "No .cat/ directory found - skipping phase 16"
else
    phase16_changed=0

    if [[ -f "${cat_dir}/cat-config.json" ]] && [[ ! -f "${cat_dir}/config.json" ]]; then
        mv "${cat_dir}/cat-config.json" "${cat_dir}/config.json"
        ((phase16_changed++)) || true
        log_migration "  Renamed: ${cat_dir}/cat-config.json → ${cat_dir}/config.json"
    elif [[ -f "${cat_dir}/cat-config.json" ]] && [[ -f "${cat_dir}/config.json" ]]; then
        log_migration "  Skipped: both ${cat_dir}/cat-config.json and ${cat_dir}/config.json exist - manual resolution required"
    else
        log_migration "  Skipped: ${cat_dir}/cat-config.json already absent"
    fi

    if [[ -f "${cat_dir}/cat-config.local.json" ]] && [[ ! -f "${cat_dir}/config.local.json" ]]; then
        mv "${cat_dir}/cat-config.local.json" "${cat_dir}/config.local.json"
        ((phase16_changed++)) || true
        log_migration "  Renamed: ${cat_dir}/cat-config.local.json → ${cat_dir}/config.local.json"
    elif [[ -f "${cat_dir}/cat-config.local.json" ]] && [[ -f "${cat_dir}/config.local.json" ]]; then
        log_migration "  Skipped: both ${cat_dir}/cat-config.local.json and ${cat_dir}/config.local.json exist - manual resolution required"
    else
        log_migration "  Skipped: ${cat_dir}/cat-config.local.json already absent"
    fi

    # Update .gitignore to replace cat-config.local.json pattern with config.local.json
    gitignore_path="${cat_dir}/.gitignore"
    if [[ -f "$gitignore_path" ]] && grep -qF "cat-config.local.json" "$gitignore_path" 2>/dev/null; then
        sed -i 's/cat-config\.local\.json/config.local.json/g' "$gitignore_path"
        ((phase16_changed++)) || true
        log_migration "  Updated: ${gitignore_path} — replaced cat-config.local.json with config.local.json"
    fi

    log_migration "Phase 16 complete: $phase16_changed files changed"
fi

# ──────────────────────────────────────────────────────────────────────────────
# Phase 17: Convert bare sub-issue names to qualified names in "Decomposed Into" sections
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 17: Convert bare sub-issue names to qualified names in Decomposed Into sections"

# Issue-level STATE.md files live at .cat/issues/v*/v*.*/issue-name/STATE.md (depth 4 from issues/).
issue_state_files=$(find .cat/issues -path "*v*.*/*" -name "STATE.md" -mindepth 4 -maxdepth 4 -type f \
    2>/dev/null || true)

if [[ -z "$issue_state_files" ]]; then
    log_migration "No issue-level STATE.md files found - skipping phase 17"
else
    totalCount=$(echo "$issue_state_files" | wc -l | tr -d ' ')
    log_migration "Found $totalCount issue-level STATE.md files to check"

    phase17_changed=0
    phase17_skipped=0

    while IFS= read -r state_file; do
        [[ -z "$state_file" ]] && continue

        # Skip files without a "Decomposed Into" section
        if ! grep -q "^## Decomposed Into" "$state_file" 2>/dev/null; then
            continue
        fi

        # Extract version prefix from path (e.g., v2.1 -> 2.1-)
        version_dir=$(echo "$state_file" | sed 's|.*/v[0-9]*/\(v[0-9]*\.[0-9]*\)/.*|\1|')
        version_prefix="${version_dir#v}-"

        # Validate we got a proper version prefix (digits.digits-)
        if ! [[ "$version_prefix" =~ ^[0-9]+\.[0-9]+-$ ]]; then
            log_migration "  Warning: Could not extract version prefix from $state_file (got '$version_prefix') - skipping"
            ((phase17_skipped++)) || true
            continue
        fi

        # Use awk to transform bare names in the Decomposed Into section only
        # A "bare name" is a list item (^- ) that does NOT start with a digit (not yet qualified)
        new_content=$(awk -v prefix="$version_prefix" '
            /^## Decomposed Into/ { in_section=1; print; next }
            in_section && /^## / { in_section=0 }
            in_section && /^- / {
                # Check if line starts with "- " followed by a version prefix (digit.digit-)
                if ($0 ~ /^- [0-9]+\.[0-9]+-/) {
                    print  # already qualified
                } else {
                    # Prefix the bare name with version prefix
                    sub(/^- /, "- " prefix)
                    print
                }
                next
            }
            { print }
        ' "$state_file")

        if [[ "$new_content" != "$(cat "$state_file")" ]]; then
            printf '%s\n' "$new_content" > "$state_file"
            ((phase17_changed++)) || true
            log_migration "  Updated: $state_file (prefix: $version_prefix)"
        fi

    done <<< "$issue_state_files"

    log_migration "Phase 17 complete: $phase17_changed files changed, $phase17_skipped skipped"
fi

# ──────────────────────────────────────────────────────────────────────────────
# Phase 18: Rename PLAN.md → plan.md in all directories under .cat/issues/
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 18: Rename PLAN.md → plan.md under .cat/issues/"

issues_dir=".cat/issues"

if [[ ! -d "$issues_dir" ]]; then
    log_migration "No .cat/issues/ directory found - skipping phase 18"
else
    phase18_migrated=0
    phase18_skipped=0

    while IFS= read -r planFile; do
        [[ -z "$planFile" ]] && continue
        dir=$(dirname "$planFile")
        target="${dir}/plan.md"

        # Idempotency: skip if plan.md already exists
        if [[ -f "$target" ]]; then
            ((phase18_skipped++)) || true
            continue
        fi

        mv "$planFile" "$target"
        ((phase18_migrated++)) || true
        log_migration "  Renamed: $planFile → $target"

    done < <(find "$issues_dir" -name "PLAN.md" -type f 2>/dev/null || true)

    log_migration "Phase 18 complete: $phase18_migrated files renamed, $phase18_skipped already up to date"
fi

# ──────────────────────────────────────────────────────────────────────────────
# Phase 19: Convert STATE.md → index.json under .cat/issues/
#
# Extracts structured fields from STATE.md markdown bullet list format into JSON:
#   status, resolution, targetBranch, dependencies, blocks, parent, decomposedInto
# Strips: Progress, Last Updated, Completed, and any narrative/markdown content.
# ──────────────────────────────────────────────────────────────────────────────

log_migration "Phase 19: Convert STATE.md → index.json under .cat/issues/"

issues_dir=".cat/issues"

if [[ ! -d "$issues_dir" ]]; then
    log_migration "No .cat/issues/ directory found - skipping phase 19"
else
    phase19_migrated=0
    phase19_skipped=0

    # Helper: extract a bullet field value from STATE.md content
    # Usage: extract_field "Status" "$content"
    extract_field() {
        local field="$1"
        local content="$2"
        # Match: - **FieldName:** value (rest of line)
        # Use grep || true to avoid exit 1 when field is absent (set -e compatibility)
        echo "$content" | (grep -m1 "^\- \*\*${field}:\*\*" || true) | sed "s/^\- \*\*${field}:\*\* *//" | sed 's/[[:space:]]*$//'
    }

    # Helper: convert a bracket list like [a, b, c] to JSON array ["a","b","c"]
    # Empty list [] returns ""
    bracket_list_to_json() {
        local raw="$1"
        # Strip surrounding brackets and whitespace
        local inner
        inner=$(echo "$raw" | sed 's/^[[:space:]]*\[//' | sed 's/\][[:space:]]*$//')
        # Empty list
        if [[ -z "$inner" ]]; then
            echo ""
            return
        fi
        # Split on comma, trim, wrap in quotes
        local result="["
        local first=1
        while IFS= read -r item; do
            item=$(echo "$item" | sed 's/^[[:space:]]*//' | sed 's/[[:space:]]*$//')
            [[ -z "$item" ]] && continue
            # Escape double quotes in item
            item="${item//\"/\\\"}"
            if [[ $first -eq 1 ]]; then
                result="${result}\"${item}\""
                first=0
            else
                result="${result}, \"${item}\""
            fi
        done < <(echo "$inner" | tr ',' '\n')
        result="${result}]"
        echo "$result"
    }

    # Helper: extract decomposedInto list from ## Decomposed Into section
    # Returns newline-separated list of items (bare names extracted from "- item (description)" lines)
    extract_decomposedInto() {
        local content="$1"
        # Find the ## Decomposed Into section and extract bullet items until next ## section
        echo "$content" | awk '
            /^## Decomposed Into/ { in_section=1; next }
            /^## / && in_section { exit }
            in_section && /^- / {
                # Extract the item: first word after "- " up to space or parenthesis
                line=$0
                sub(/^- /, "", line)
                # Take content up to first " (" or end of line
                if (match(line, / \(/) > 0) {
                    print substr(line, 1, RSTART - 1)
                } else {
                    print line
                }
            }
        ' | sed 's/[[:space:]]*$//'
    }

    # Helper: build JSON from extracted fields
    build_index_json() {
        local state_file="$1"
        local content
        content=$(cat "$state_file")

        local status resolution targetBranch deps blocks parent

        status=$(extract_field "Status" "$content")
        resolution=$(extract_field "Resolution" "$content")
        targetBranch=$(extract_field "Target Branch" "$content")
        deps=$(extract_field "Dependencies" "$content")
        blocks=$(extract_field "Blocks" "$content")
        parent=$(extract_field "Parent" "$content")

        local json="{"

        # status is always required
        if [[ -n "$status" ]]; then
            status="${status//\"/\\\"}"
            json="${json}
  \"status\": \"${status}\""
        fi

        # resolution only if non-empty
        if [[ -n "$resolution" ]]; then
            resolution="${resolution//\"/\\\"}"
            json="${json},
  \"resolution\": \"${resolution}\""
        fi

        # target_branch only if non-empty
        if [[ -n "$targetBranch" ]]; then
            targetBranch="${targetBranch//\"/\\\"}"
            json="${json},
  \"target_branch\": \"${targetBranch}\""
        fi

        # dependencies only if non-empty list
        if [[ -n "$deps" ]] && [[ "$deps" != "[]" ]]; then
            local deps_json
            deps_json=$(bracket_list_to_json "$deps")
            if [[ -n "$deps_json" ]]; then
                json="${json},
  \"dependencies\": ${deps_json}"
            fi
        fi

        # blocks only if non-empty list
        if [[ -n "$blocks" ]] && [[ "$blocks" != "[]" ]]; then
            local blocks_json
            blocks_json=$(bracket_list_to_json "$blocks")
            if [[ -n "$blocks_json" ]]; then
                json="${json},
  \"blocks\": ${blocks_json}"
            fi
        fi

        # parent only if non-empty
        if [[ -n "$parent" ]]; then
            parent="${parent//\"/\\\"}"
            json="${json},
  \"parent\": \"${parent}\""
        fi

        # decomposedInto: extract from ## Decomposed Into section
        local decomposed_items
        decomposed_items=$(extract_decomposedInto "$content")
        if [[ -n "$decomposed_items" ]]; then
            local decomposed_json="["
            local dfirst=1
            while IFS= read -r ditem; do
                [[ -z "$ditem" ]] && continue
                ditem="${ditem//\"/\\\"}"
                if [[ $dfirst -eq 1 ]]; then
                    decomposed_json="${decomposed_json}\"${ditem}\""
                    dfirst=0
                else
                    decomposed_json="${decomposed_json}, \"${ditem}\""
                fi
            done <<< "$decomposed_items"
            decomposed_json="${decomposed_json}]"
            json="${json},
  \"decomposedInto\": ${decomposed_json}"
        fi

        json="${json}
}"
        echo "$json"
    }

    while IFS= read -r state_file; do
        [[ -z "$state_file" ]] && continue
        dir=$(dirname "$state_file")
        target="${dir}/index.json"

        # Idempotency: skip if index.json already exists
        if [[ -f "$target" ]]; then
            ((phase19_skipped++)) || true
            continue
        fi

        build_index_json "$state_file" > "$target"
        rm "$state_file"
        ((phase19_migrated++)) || true
        log_migration "  Converted: $state_file → $target"

    done < <(find "$issues_dir" -name "STATE.md" -type f 2>/dev/null || true)

    log_migration "Phase 19 complete: $phase19_migrated files converted, $phase19_skipped already up to date"
fi

# Phase 20: Rename targetBranch → target_branch in index.json files
# Idempotent: only renames if targetBranch key is present (not already target_branch)
phase20_migrated=0
phase20_skipped=0

while IFS= read -r index_file; do
    [[ -z "$index_file" ]] && continue
    if grep -q '"targetBranch"' "$index_file" 2>/dev/null; then
        sed -i 's/"targetBranch"/"target_branch"/g' "$index_file"
        ((phase20_migrated++)) || true
        log_migration "  Renamed targetBranch → target_branch in: $index_file"
    else
        ((phase20_skipped++)) || true
    fi
done < <(find "$issues_dir" -name "index.json" -type f 2>/dev/null || true)

log_migration "Phase 20 complete: $phase20_migrated files updated, $phase20_skipped already up to date"

# Phase 21: Rename caution config values: none→low, changed→medium, all→high
# Idempotent: only updates config files that contain old caution values
phase21_migrated=0
phase21_skipped=0

for config_file in "${project_root}/.cat/config.json" "${project_root}/.cat/config.local.json"; do
    [[ -f "$config_file" ]] || continue
    if grep -q '"caution"' "$config_file" 2>/dev/null; then
        caution_value=$(grep -o '"caution"[[:space:]]*:[[:space:]]*"[^"]*"' "$config_file" | \
            sed 's/.*"caution"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
        case "$caution_value" in
            none)
                sed -i 's/"caution"[[:space:]]*:[[:space:]]*"none"/"caution": "low"/g' "$config_file"
                log_migration "  Renamed caution none → low in: $config_file"
                ((phase21_migrated++)) || true
                ;;
            changed)
                sed -i 's/"caution"[[:space:]]*:[[:space:]]*"changed"/"caution": "medium"/g' "$config_file"
                log_migration "  Renamed caution changed → medium in: $config_file"
                ((phase21_migrated++)) || true
                ;;
            all)
                sed -i 's/"caution"[[:space:]]*:[[:space:]]*"all"/"caution": "high"/g' "$config_file"
                log_migration "  Renamed caution all → high in: $config_file"
                ((phase21_migrated++)) || true
                ;;
            *)
                ((phase21_skipped++)) || true
                ;;
        esac
    else
        ((phase21_skipped++)) || true
    fi
done

log_migration "Phase 21 complete: $phase21_migrated files updated, $phase21_skipped already up to date"

log_success "Migration to 2.1 completed"
exit 0
