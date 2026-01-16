#!/bin/bash
set -euo pipefail

# Migration to 2.0: Add Research and Requirements sections to PLAN.md files
#
# This migration:
# 1. Adds ## Research section to PLAN.md files that don't have it
# 2. Adds ## Requirements section to Feature-type PLAN.md files
# 3. Adds ### Requirements Traceability to detailed specs
#
# Note: Sections are added as templates. Run /cat:research to populate with
# stakeholder findings, or fill manually.

# shellcheck source=lib/utils.sh
source "${CLAUDE_PLUGIN_ROOT}/migrations/lib/utils.sh"

log_migration "Starting 2.0 migration: Research & Requirements sections"

# Count files to process
plan_files=$(find .claude/cat/issues -name "PLAN.md" -type f 2>/dev/null || true)
total_count=$(echo "$plan_files" | grep -c "PLAN.md" || echo 0)

if [[ "$total_count" -eq 0 ]]; then
    log_migration "No PLAN.md files found - skipping"
    exit 0
fi

log_migration "Found $total_count PLAN.md files to check"

research_added=0
requirements_added=0
traceability_added=0

# Research section template
RESEARCH_TEMPLATE='## Research

*Populated by stakeholder research. Run `/cat:research` to fill, or add manually.*

### Stack
| Library | Purpose | Version | Rationale |
|---------|---------|---------|-----------|
| *TBD* | *TBD* | *TBD* | *TBD* |

### Architecture
- **Pattern:** *TBD*
- **Integration:** *TBD*

### Pitfalls
- *Run /cat:research to populate*

'

# Requirements section template
REQUIREMENTS_TEMPLATE='## Requirements

| ID | Requirement | Priority | Acceptance Criteria |
|----|-------------|----------|---------------------|
| REQ-001 | *Define requirement* | must-have | *How to verify* |

'

# Requirements traceability template
TRACEABILITY_TEMPLATE='### Requirements Traceability

| Requirement | Covered By | Status |
|-------------|------------|--------|
| REQ-001 | *Step N* | pending |

'

while IFS= read -r plan_file; do
    [[ -z "$plan_file" ]] && continue

    modified=false
    content=$(cat "$plan_file")

    # Check if Research section exists
    if ! grep -q "^## Research" "$plan_file"; then
        # Find insertion point (after ## Goal or ## Focus or ## Vision, before ## Approach)
        if grep -q "^## Goal\|^## Focus\|^## Vision" "$plan_file"; then
            # Insert after Goal/Focus/Vision section
            # Use awk for reliable multi-line insertion
            awk -v template="$RESEARCH_TEMPLATE" '
                /^## (Goal|Focus|Vision)/ { in_section=1 }
                in_section && /^## / && !/^## (Goal|Focus|Vision)/ {
                    print template
                    in_section=0
                }
                { print }
                END { if (in_section) print template }
            ' "$plan_file" > "${plan_file}.tmp" && mv "${plan_file}.tmp" "$plan_file"

            ((research_added++)) || true
            modified=true
            log_migration "  Added Research section to: $plan_file"
        fi
    fi

    # Check if this is a Feature-type plan (has ## Goal, not ## Problem)
    if grep -q "^## Goal" "$plan_file" && ! grep -q "^## Problem" "$plan_file"; then
        # Check if Requirements section exists
        if ! grep -q "^## Requirements" "$plan_file"; then
            # Insert after ## Goal, before ## Research or ## Approach
            awk -v template="$REQUIREMENTS_TEMPLATE" '
                /^## Goal/ { in_goal=1 }
                in_goal && /^## (Research|Approach)/ {
                    print template
                    in_goal=0
                }
                { print }
            ' "$plan_file" > "${plan_file}.tmp" && mv "${plan_file}.tmp" "$plan_file"

            ((requirements_added++)) || true
            modified=true
            log_migration "  Added Requirements section to: $plan_file"
        fi

        # Check if Requirements Traceability exists (for detailed specs)
        if grep -q "^### Acceptance Criteria\|^### Execution Steps" "$plan_file"; then
            if ! grep -q "^### Requirements Traceability" "$plan_file"; then
                # Insert before ### Execution Steps
                awk -v template="$TRACEABILITY_TEMPLATE" '
                    /^### Execution Steps/ {
                        print template
                    }
                    { print }
                ' "$plan_file" > "${plan_file}.tmp" && mv "${plan_file}.tmp" "$plan_file"

                ((traceability_added++)) || true
                modified=true
                log_migration "  Added Traceability section to: $plan_file"
            fi
        fi
    fi

done <<< "$plan_files"

log_success "PLAN.md migration complete:"
log_success "  - Research sections added: $research_added"
log_success "  - Requirements sections added: $requirements_added"
log_success "  - Traceability sections added: $traceability_added"

# Note about running research
if [[ $research_added -gt 0 ]]; then
    log_migration ""
    log_migration "To populate Research sections with stakeholder findings:"
    log_migration "  /cat:research [version or task path]"
fi

# =============================================================================
# Part 2: STATE.md Format Standardization (M224)
# =============================================================================
#
# Converts old YAML-style format to bullet+bold markdown format:
#   status: pending         -> - **Status:** pending
#   progress: 0%            -> - **Progress:** 0%
#   dependencies: []        -> - **Dependencies:** []
#   started: DATE           -> - **Started:** DATE
#   completed: DATE         -> - **Completed:** DATE

log_migration ""
log_migration "Starting STATE.md format standardization..."

state_files=$(find .claude/cat/issues -name "STATE.md" -type f 2>/dev/null || true)
state_count=$(echo "$state_files" | grep -c "STATE.md" 2>/dev/null || echo 0)

if [[ "$state_count" -eq 0 ]]; then
    log_migration "No STATE.md files found - skipping format migration"
else
    log_migration "Found $state_count STATE.md files to check"

    states_fixed=0

    while IFS= read -r state_file; do
        [[ -z "$state_file" ]] && continue

        # Check if file has old format (lowercase field: value at line start)
        if grep -qE "^(status|progress|started|completed|dependencies):" "$state_file" 2>/dev/null; then
            # Convert to new format
            sed -i \
                -e 's/^status: \(.*\)/- **Status:** \1/' \
                -e 's/^progress: \(.*\)/- **Progress:** \1/' \
                -e 's/^started: \(.*\)/- **Started:** \1/' \
                -e 's/^completed: \(.*\)/- **Completed:** \1/' \
                -e 's/^dependencies: \(.*\)/- **Dependencies:** \1/' \
                "$state_file"

            ((states_fixed++)) || true
            log_migration "  Fixed format: $state_file"
        fi
    done <<< "$state_files"

    log_success "STATE.md format migration complete:"
    log_success "  - Files converted: $states_fixed"
fi

exit 0
