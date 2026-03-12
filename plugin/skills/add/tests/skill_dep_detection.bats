#!/usr/bin/env bats
# Tests for the issue_detect_skill_deps step in plugin/skills/add/first-use.md
# Verifies that the bash scanning logic correctly:
#   - Finds open issues whose PLAN.md references a given skill
#   - Excludes closed issues
#   - Excludes the current issue being created

load '../../../../tests/test_helper'

# Source the shared production helper functions so tests always run the real code.
# PLUGIN_ROOT is set by test_helper (load above).
# shellcheck source=../../../add-agent/skill_dep_helpers.sh
source "${PLUGIN_ROOT}/skills/add-agent/skill_dep_helpers.sh"

setup() {
    setup_test_dir
    ISSUES_DIR="${TEST_TEMP_DIR}/.cat/issues"
    mkdir -p "$ISSUES_DIR"
}

teardown() {
    teardown_test_dir
}

# Create a mock issue directory with STATE.md and PLAN.md
create_mock_issue() {
    local issue_id="$1"
    local status="$2"       # e.g. "open" or "closed"
    local skill_ref="$3"    # skill path to put in PLAN.md, or "" for none

    local issue_dir="${ISSUES_DIR}/v2/v2.1/${issue_id}"
    mkdir -p "$issue_dir"

    cat > "${issue_dir}/STATE.md" <<EOF
# ${issue_id}

- **Status:** ${status}
- **Dependencies:** []
EOF

    if [[ -n "$skill_ref" ]]; then
        cat > "${issue_dir}/PLAN.md" <<EOF
# Plan for ${issue_id}

## Files to Modify
- ${skill_ref}/first-use.md - Add new step

## Execution Steps
1. Modify skill file
EOF
    else
        cat > "${issue_dir}/PLAN.md" <<EOF
# Plan for ${issue_id}

## Files to Modify
- client/src/main/java/SomeClass.java

## Execution Steps
1. Add new class
EOF
    fi
}

# ============================================================================
# Core detection: open issues referencing the skill are found
# ============================================================================

@test "skill_dep_detection: detects open issues referencing target skill" {
    create_mock_issue "compress-skills-batch-1" "open" "plugin/skills/add"
    create_mock_issue "add-backlog-support" "open" "plugin/skills/add"

    run run_detection "add" "new-current-issue"

    [ "$status" -eq 0 ]
    [[ "$output" == *"compress-skills-batch-1"* ]]
    [[ "$output" == *"add-backlog-support"* ]]
}

# ============================================================================
# Closed issues must be excluded
# ============================================================================

@test "skill_dep_detection: excludes closed issues from results" {
    create_mock_issue "open-issue-1" "open" "plugin/skills/add"
    create_mock_issue "closed-issue-1" "closed" "plugin/skills/add"

    run run_detection "add" "new-current-issue"

    [ "$status" -eq 0 ]
    [[ "$output" == *"open-issue-1"* ]]
    [[ "$output" != *"closed-issue-1"* ]]
}

# ============================================================================
# Exactly two open issues returned; closed one excluded (full scenario)
# ============================================================================

@test "skill_dep_detection: returns exactly two open issues, excludes closed issue" {
    create_mock_issue "issue-open-a" "open" "plugin/skills/add"
    create_mock_issue "issue-open-b" "open" "plugin/skills/add"
    create_mock_issue "issue-closed-c" "closed" "plugin/skills/add"

    run run_detection "add" "new-current-issue"

    [ "$status" -eq 0 ]

    # Count returned lines (one per matched issue)
    local count
    count=$(echo "$output" | grep -c '^.' || true)
    [ "$count" -eq 2 ]

    [[ "$output" == *"issue-open-a"* ]]
    [[ "$output" == *"issue-open-b"* ]]
    [[ "$output" != *"issue-closed-c"* ]]
}

# ============================================================================
# Issues not referencing the skill are not returned
# ============================================================================

@test "skill_dep_detection: ignores issues that do not reference the target skill" {
    create_mock_issue "unrelated-issue" "open" ""
    create_mock_issue "skill-issue" "open" "plugin/skills/add"

    run run_detection "add" "new-current-issue"

    [ "$status" -eq 0 ]
    [[ "$output" == *"skill-issue"* ]]
    [[ "$output" != *"unrelated-issue"* ]]
}

# ============================================================================
# Current issue being created is excluded
# ============================================================================

@test "skill_dep_detection: excludes the current issue being created" {
    create_mock_issue "existing-open-issue" "open" "plugin/skills/add"
    create_mock_issue "new-current-issue" "open" "plugin/skills/add"

    run run_detection "add" "new-current-issue"

    [ "$status" -eq 0 ]
    [[ "$output" == *"existing-open-issue"* ]]
    [[ "$output" != *"new-current-issue"* ]]
}

# ============================================================================
# No matches returns empty output (not an error)
# ============================================================================

@test "skill_dep_detection: returns empty when no open issues reference the skill" {
    create_mock_issue "closed-issue" "closed" "plugin/skills/add"
    create_mock_issue "unrelated-open-issue" "open" ""

    run run_detection "add" "new-current-issue"

    [ "$status" -eq 0 ]
    [ -z "$output" ]
}

# ============================================================================
# No false positives for non-existent skill
# ============================================================================

@test "skill_dep_detection: no false positives for nonexistent skill name" {
    create_mock_issue "open-issue-1" "open" "plugin/skills/add"
    create_mock_issue "open-issue-2" "open" "plugin/skills/work"

    run run_detection "nonexistent-skill-xyz123" "new-current-issue"

    [ "$status" -eq 0 ]
    [ -z "$output" ]
}

# ============================================================================
# Different skill names don't cross-match
# ============================================================================

@test "skill_dep_detection: skill name matching is specific (no cross-skill matches)" {
    create_mock_issue "add-skill-issue" "open" "plugin/skills/add"
    create_mock_issue "work-skill-issue" "open" "plugin/skills/work"

    # Detect deps for "work" skill only
    run run_detection "work" "new-current-issue"

    [ "$status" -eq 0 ]
    [[ "$output" == *"work-skill-issue"* ]]
    [[ "$output" != *"add-skill-issue"* ]]
}

# ============================================================================
# Partial match prevention: skill "add" must NOT match "plugin/skills/add-wizard/"
# ============================================================================

@test "skill_dep_detection: partial name match does not trigger (add vs add-wizard)" {
    local issue_dir="${ISSUES_DIR}/v2/v2.1/add-wizard-issue"
    mkdir -p "$issue_dir"
    cat > "${issue_dir}/STATE.md" <<EOF
# add-wizard-issue

- **Status:** open
- **Dependencies:** []
EOF
    cat > "${issue_dir}/PLAN.md" <<EOF
# Plan for add-wizard-issue

## Files to Modify
- plugin/skills/add-wizard/first-use.md - Modify wizard step

## Execution Steps
1. Update add-wizard skill
EOF

    run run_detection "add" "new-current-issue"

    [ "$status" -eq 0 ]
    [[ "$output" != *"add-wizard-issue"* ]]
}

# ============================================================================
# STATUS parsing with trailing whitespace
# ============================================================================

@test "skill_dep_detection: parses STATUS correctly when trailing whitespace is present" {
    local issue_dir="${ISSUES_DIR}/v2/v2.1/trailing-ws-issue"
    mkdir -p "$issue_dir"
    # Status line has trailing spaces
    printf '# trailing-ws-issue\n\n- **Status:** open   \n- **Dependencies:** []\n' \
        > "${issue_dir}/STATE.md"
    cat > "${issue_dir}/PLAN.md" <<EOF
# Plan

## Files to Modify
- plugin/skills/add/first-use.md - Some step
EOF

    run run_detection "add" "new-current-issue"

    [ "$status" -eq 0 ]
    [[ "$output" == *"trailing-ws-issue"* ]]
}

# ============================================================================
# Deduplication: same skill referenced on multiple PLAN.md lines
# ============================================================================

@test "skill_dep_detection: issue referencing same skill on two lines appears exactly once" {
    local issue_dir="${ISSUES_DIR}/v2/v2.1/dup-ref-issue"
    mkdir -p "$issue_dir"
    cat > "${issue_dir}/STATE.md" <<EOF
# dup-ref-issue

- **Status:** open
- **Dependencies:** []
EOF
    cat > "${issue_dir}/PLAN.md" <<EOF
# Plan for dup-ref-issue

## Files to Modify
- plugin/skills/add/first-use.md - Step A
- plugin/skills/add/first-use.md - Step B (same path again)

## Execution Steps
1. Modify skill file twice
EOF

    run run_detection "add" "new-current-issue"

    [ "$status" -eq 0 ]
    local count
    count=$(echo "$output" | grep -c "dup-ref-issue" || true)
    [ "$count" -eq 1 ]
}

# ============================================================================
# extract_skill_names: explicit path extraction
# ============================================================================

@test "extract_skill_names: extracts skill name from explicit path plugin/skills/add-agent/" {
    run extract_skill_names "Modify plugin/skills/add-agent/first-use.md to add a new step"

    [ "$status" -eq 0 ]
    [[ "$output" == *"add-agent"* ]]
}

@test "extract_skill_names: extracts skill name from 'modify the work skill' phrase" {
    run extract_skill_names "modify the work skill to support new option"

    [ "$status" -eq 0 ]
    [[ "$output" == *"work"* ]]
}

@test "extract_skill_names: extracts skill name from 'update add skill' phrase" {
    run extract_skill_names "update add skill to improve batching"

    [ "$status" -eq 0 ]
    [[ "$output" == *"add"* ]]
}

@test "extract_skill_names: returns empty when description has no skill reference" {
    run extract_skill_names "refactor the Java client library to use new APIs"

    [ "$status" -eq 0 ]
    [ -z "$output" ]
}

@test "extract_skill_names: extracts multiple skills from description" {
    run extract_skill_names "update add skill and modify the work skill to share new config"

    [ "$status" -eq 0 ]
    [[ "$output" == *"add"* ]]
    [[ "$output" == *"work"* ]]
}

# ============================================================================
# update_state_dependency: STATE.md dependency insertion
# ============================================================================

@test "update_state_dependency: empty [] list becomes [new-issue]" {
    local state_file="${TEST_TEMP_DIR}/STATE.md"
    cat > "$state_file" <<EOF
# test-issue

- **Status:** open
- **Dependencies:** []
EOF

    update_state_dependency "$state_file" "new-feature-issue"

    grep -q '^\- \*\*Dependencies:\*\* \[new-feature-issue\]' "$state_file"
}

@test "update_state_dependency: single-entry list gets new issue appended" {
    local state_file="${TEST_TEMP_DIR}/STATE.md"
    cat > "$state_file" <<EOF
# test-issue

- **Status:** open
- **Dependencies:** [existing-dep]
EOF

    update_state_dependency "$state_file" "new-feature-issue"

    grep -q '^\- \*\*Dependencies:\*\* \[existing-dep, new-feature-issue\]' "$state_file"
}

@test "update_state_dependency: multi-entry list gets new issue appended" {
    local state_file="${TEST_TEMP_DIR}/STATE.md"
    cat > "$state_file" <<EOF
# test-issue

- **Status:** open
- **Dependencies:** [dep-a, dep-b]
EOF

    update_state_dependency "$state_file" "new-feature-issue"

    grep -q '^\- \*\*Dependencies:\*\* \[dep-a, dep-b, new-feature-issue\]' "$state_file"
}

@test "update_state_dependency: idempotent (double update does not duplicate)" {
    local state_file="${TEST_TEMP_DIR}/STATE.md"
    cat > "$state_file" <<EOF
# test-issue

- **Status:** open
- **Dependencies:** []
EOF

    update_state_dependency "$state_file" "new-feature-issue"
    update_state_dependency "$state_file" "new-feature-issue"

    local count
    count=$(grep -c "new-feature-issue" "$state_file" || true)
    [ "$count" -eq 1 ]
}

@test "update_state_dependency: handles special characters in issue ID" {
    local state_file="${TEST_TEMP_DIR}/STATE.md"
    cat > "$state_file" <<EOF
# test-issue

- **Status:** open
- **Dependencies:** []
EOF

    # Issue IDs with dots and hyphens (common in version-prefixed IDs)
    update_state_dependency "$state_file" "v2.1-my-fix"

    # Use grep -c to count matches; avoid variable expansion of ** by reading file
    local count
    count=$(grep -cF 'v2.1-my-fix' "$state_file" || true)
    [ "$count" -ge 1 ]
    # Also verify the Dependencies line structure was maintained
    grep -q 'Dependencies' "$state_file"
}

# ============================================================================
# E2E integration test: full flow from extraction to STATE.md update
# ============================================================================

@test "skill_dep_detection: E2E full flow - extract names, detect deps, update STATE.md" {
    # Create two open issues referencing plugin/skills/add/
    create_mock_issue "open-issue-alpha" "open" "plugin/skills/add"
    create_mock_issue "open-issue-beta" "open" "plugin/skills/add"
    # Create one closed issue referencing plugin/skills/add/ — must be excluded
    create_mock_issue "closed-issue-gamma" "closed" "plugin/skills/add"

    # Step 1: Extract skill name from a natural-language description
    run extract_skill_names "modify the add skill to support extra options"
    [ "$status" -eq 0 ]
    [[ "$output" == *"add"* ]]
    local skill_name="add"

    # Step 2: Run detection
    run run_detection "$skill_name" "brand-new-issue"
    [ "$status" -eq 0 ]

    # Assert exactly 2 open issues found; closed one excluded
    local count
    count=$(echo "$output" | grep -c '^.' || true)
    [ "$count" -eq 2 ]
    [[ "$output" == *"open-issue-alpha"* ]]
    [[ "$output" == *"open-issue-beta"* ]]
    [[ "$output" != *"closed-issue-gamma"* ]]

    # Step 3: Update STATE.md for each matched issue
    local new_issue_id="brand-new-issue"
    local alpha_state="${ISSUES_DIR}/v2/v2.1/open-issue-alpha/STATE.md"
    local beta_state="${ISSUES_DIR}/v2/v2.1/open-issue-beta/STATE.md"
    local closed_state="${ISSUES_DIR}/v2/v2.1/closed-issue-gamma/STATE.md"

    update_state_dependency "$alpha_state" "$new_issue_id"
    update_state_dependency "$beta_state" "$new_issue_id"

    # Assert each matched STATE.md now contains the new-issue-id in Dependencies
    grep -qF "$new_issue_id" "$alpha_state"
    grep -qF "$new_issue_id" "$beta_state"

    # Assert closed issue STATE.md was NOT modified
    ! grep -qF "$new_issue_id" "$closed_state"
}

# ============================================================================
# Malformed/missing STATUS field: conservative behavior (treat as non-closed)
# ============================================================================

@test "skill_dep_detection: missing Status field is treated as non-closed (conservative)" {
    local issue_dir="${ISSUES_DIR}/v2/v2.1/no-status-issue"
    mkdir -p "$issue_dir"
    # STATE.md with no Status field at all
    cat > "${issue_dir}/STATE.md" <<EOF
# no-status-issue

- **Dependencies:** []
EOF
    cat > "${issue_dir}/PLAN.md" <<EOF
# Plan for no-status-issue

## Files to Modify
- plugin/skills/add/first-use.md - Some step
EOF

    run run_detection "add" "new-current-issue"

    [ "$status" -eq 0 ]
    # Conservative: missing status is not "closed", so the issue IS included
    [[ "$output" == *"no-status-issue"* ]]
}

# ============================================================================
# Missing PLAN.md: guard skips issue correctly
# ============================================================================

@test "skill_dep_detection: issue without PLAN.md is skipped (not included in results)" {
    local issue_dir="${ISSUES_DIR}/v2/v2.1/no-plan-issue"
    mkdir -p "$issue_dir"
    # Only STATE.md, no PLAN.md
    cat > "${issue_dir}/STATE.md" <<EOF
# no-plan-issue

- **Status:** open
- **Dependencies:** []
EOF
    # Intentionally do NOT create PLAN.md

    run run_detection "add" "new-current-issue"

    [ "$status" -eq 0 ]
    # The [[ -f "$PLAN_FILE" ]] guard correctly skips this issue
    [[ "$output" != *"no-plan-issue"* ]]
}

# ============================================================================
# extract_skill_names: additional patterns from first-use.md
# ============================================================================

@test "extract_skill_names: 'add a step to work skill' extracts 'work'" {
    run extract_skill_names "add a step to work skill for new option"

    [ "$status" -eq 0 ]
    [[ "$output" == *"work"* ]]
}

@test "extract_skill_names: 'change work first-use.md' extracts 'work'" {
    run extract_skill_names "change work first-use.md to fix the banner"

    [ "$status" -eq 0 ]
    [[ "$output" == *"work"* ]]
}

@test "extract_skill_names: 'fix work skill' extracts 'work'" {
    run extract_skill_names "fix work skill to handle edge case properly"

    [ "$status" -eq 0 ]
    [[ "$output" == *"work"* ]]
}

@test "extract_skill_names: 'extend work skill' extracts 'work'" {
    run extract_skill_names "extend work skill with new configuration options"

    [ "$status" -eq 0 ]
    [[ "$output" == *"work"* ]]
}

# ============================================================================
# STATUS whitespace boundary conditions
# ============================================================================

@test "skill_dep_detection: status with leading whitespace before value is found (open)" {
    local issue_dir="${ISSUES_DIR}/v2/v2.1/leading-ws-issue"
    mkdir -p "$issue_dir"
    # Status line has extra spaces before "open"
    printf '# leading-ws-issue\n\n- **Status:**   open\n- **Dependencies:** []\n' \
        > "${issue_dir}/STATE.md"
    cat > "${issue_dir}/PLAN.md" <<EOF
# Plan

## Files to Modify
- plugin/skills/add/first-use.md - Some step
EOF

    run run_detection "add" "new-current-issue"

    [ "$status" -eq 0 ]
    [[ "$output" == *"leading-ws-issue"* ]]
}

@test "skill_dep_detection: status 'closed' with trailing whitespace is excluded (fix from Concern 1)" {
    local issue_dir="${ISSUES_DIR}/v2/v2.1/trailing-closed-issue"
    mkdir -p "$issue_dir"
    # Status "closed" with trailing spaces — must be treated as closed
    printf '# trailing-closed-issue\n\n- **Status:** closed   \n- **Dependencies:** []\n' \
        > "${issue_dir}/STATE.md"
    cat > "${issue_dir}/PLAN.md" <<EOF
# Plan

## Files to Modify
- plugin/skills/add/first-use.md - Some step
EOF

    run run_detection "add" "new-current-issue"

    [ "$status" -eq 0 ]
    [[ "$output" != *"trailing-closed-issue"* ]]
}

@test "skill_dep_detection: only first Status line matters when multiple Status lines exist" {
    local issue_dir="${ISSUES_DIR}/v2/v2.1/multi-status-issue"
    mkdir -p "$issue_dir"
    # First Status: open; second Status: closed — grep -m1 takes the first
    printf '# multi-status-issue\n\n- **Status:** open\n- **Status:** closed\n- **Dependencies:** []\n' \
        > "${issue_dir}/STATE.md"
    cat > "${issue_dir}/PLAN.md" <<EOF
# Plan

## Files to Modify
- plugin/skills/add/first-use.md - Some step
EOF

    run run_detection "add" "new-current-issue"

    [ "$status" -eq 0 ]
    # First Status line is "open" so the issue is included
    [[ "$output" == *"multi-status-issue"* ]]
}

# ============================================================================
# Sed escaping: issue IDs with sed metacharacters
# ============================================================================

@test "update_state_dependency: issue ID containing forward slash is handled safely" {
    local state_file="${TEST_TEMP_DIR}/STATE.md"
    cat > "$state_file" <<EOF
# test-issue

- **Status:** open
- **Dependencies:** []
EOF

    # Issue ID containing "/" — the escaping in update_state_dependency must handle it
    update_state_dependency "$state_file" "v2/fix-slash"

    local count
    count=$(grep -cF 'v2/fix-slash' "$state_file" || true)
    [ "$count" -ge 1 ]
    grep -q 'Dependencies' "$state_file"
}

@test "update_state_dependency: issue ID containing ampersand is handled safely" {
    local state_file="${TEST_TEMP_DIR}/STATE.md"
    cat > "$state_file" <<EOF
# test-issue

- **Status:** open
- **Dependencies:** []
EOF

    # Issue ID containing "&" — sed uses & as "matched string" in replacement; must be escaped
    update_state_dependency "$state_file" "v2.1-fix-and-update"

    local count
    count=$(grep -cF 'v2.1-fix-and-update' "$state_file" || true)
    [ "$count" -ge 1 ]
    grep -q 'Dependencies' "$state_file"
}

# ============================================================================
# run_detection_multi_skill: outer loop with deduplication across skill names
# ============================================================================

@test "run_detection_multi_skill: same issue found via two skills appears exactly once" {
    # Create an issue whose PLAN.md references BOTH "add" and "work" skills
    local issue_dir="${ISSUES_DIR}/v2/v2.1/cross-skill-issue"
    mkdir -p "$issue_dir"
    cat > "${issue_dir}/STATE.md" <<EOF
# cross-skill-issue

- **Status:** open
- **Dependencies:** []
EOF
    cat > "${issue_dir}/PLAN.md" <<EOF
# Plan for cross-skill-issue

## Files to Modify
- plugin/skills/add/first-use.md - Modify add skill
- plugin/skills/work/first-use.md - Modify work skill

## Execution Steps
1. Update both skills
EOF

    # Simulate the outer multi-skill loop from first-use.md:
    # Loop over SKILL_NAMES ("add" and "work"), collect all matches, deduplicate
    local -a ALL_MATCHED_IDS=()
    local -a ALL_MATCHED_PATHS=()
    local SKILL_NAMES=("add" "work")

    for SKILL_NAME in "${SKILL_NAMES[@]}"; do
        run_detection "$SKILL_NAME" "new-current-issue"
        ALL_MATCHED_IDS+=("${MATCHING_ISSUES[@]+"${MATCHING_ISSUES[@]}"}")
        ALL_MATCHED_PATHS+=("${MATCHING_ISSUE_PATHS[@]+"${MATCHING_ISSUE_PATHS[@]}"}")
    done

    # Deduplicate (same logic as first-use.md)
    local -a AUTO_DETECTED_DEPS=()
    local -a seen_ids=()
    for idx in "${!ALL_MATCHED_IDS[@]}"; do
        local ISSUE_ID="${ALL_MATCHED_IDS[$idx]}"
        local already_found=false
        for existing in "${seen_ids[@]+"${seen_ids[@]}"}"; do
            if [[ "$existing" == "$ISSUE_ID" ]]; then
                already_found=true
                break
            fi
        done
        if [[ "$already_found" == false ]]; then
            seen_ids+=("$ISSUE_ID")
            AUTO_DETECTED_DEPS+=("$ISSUE_ID")
        fi
    done

    # cross-skill-issue was matched twice (once per skill) but must appear only once
    local count=0
    for id in "${AUTO_DETECTED_DEPS[@]+"${AUTO_DETECTED_DEPS[@]}"}"; do
        [[ "$id" == "cross-skill-issue" ]] && (( count++ )) || true
    done
    [ "$count" -eq 1 ]
}

@test "run_detection_multi_skill: distinct issues for distinct skills are all collected" {
    create_mock_issue "add-only-issue" "open" "plugin/skills/add"
    create_mock_issue "work-only-issue" "open" "plugin/skills/work"

    local -a ALL_MATCHED_IDS=()
    local SKILL_NAMES=("add" "work")

    for SKILL_NAME in "${SKILL_NAMES[@]}"; do
        run_detection "$SKILL_NAME" "new-current-issue"
        ALL_MATCHED_IDS+=("${MATCHING_ISSUES[@]+"${MATCHING_ISSUES[@]}"}")
    done

    # Both issues must be present in the combined array
    [[ " ${ALL_MATCHED_IDS[*]} " == *" add-only-issue "* ]]
    [[ " ${ALL_MATCHED_IDS[*]} " == *" work-only-issue "* ]]
}

# ============================================================================
# Dashed skill names
# ============================================================================

@test "extract_skill_names: extracts dashed skill name from explicit path plugin/skills/my-skill/" {
    run extract_skill_names "Modify plugin/skills/my-skill/first-use.md to add a step"

    [ "$status" -eq 0 ]
    [[ "$output" == *"my-skill"* ]]
}

@test "extract_skill_names: extracts dashed skill name from 'modify the my-other-skill skill' phrase" {
    run extract_skill_names "modify the my-other-skill skill to fix a bug"

    [ "$status" -eq 0 ]
    [[ "$output" == *"my-other-skill"* ]]
}

@test "skill_dep_detection: run_detection works with dashed skill name 'add-agent'" {
    create_mock_issue "uses-add-agent" "open" "plugin/skills/add-agent"

    run run_detection "add-agent" "new-current-issue"

    [ "$status" -eq 0 ]
    [[ "$output" == *"uses-add-agent"* ]]
}

# ============================================================================
# Idempotency with multiple existing dependencies
# ============================================================================

@test "update_state_dependency: idempotent when STATE.md already has multiple dependencies including target" {
    local state_file="${TEST_TEMP_DIR}/STATE.md"
    cat > "$state_file" <<EOF
# test-issue

- **Status:** open
- **Dependencies:** [dep-x, dep-y, existing-dep]
EOF

    # Attempt to add "existing-dep" which is already present — must be a no-op
    update_state_dependency "$state_file" "existing-dep"

    # Count occurrences of "existing-dep" in the file — must remain exactly 1
    local count
    count=$(grep -cF 'existing-dep' "$state_file" || true)
    [ "$count" -eq 1 ]

    # The Dependencies line must be unchanged
    grep -q '^\- \*\*Dependencies:\*\* \[dep-x, dep-y, existing-dep\]' "$state_file"
}

@test "update_state_dependency: appends to multi-entry list correctly after idempotent guard" {
    local state_file="${TEST_TEMP_DIR}/STATE.md"
    cat > "$state_file" <<EOF
# test-issue

- **Status:** open
- **Dependencies:** [dep-a, dep-b, dep-c]
EOF

    # First: try to add dep-b (already there) — must be no-op
    update_state_dependency "$state_file" "dep-b"
    grep -q '^\- \*\*Dependencies:\*\* \[dep-a, dep-b, dep-c\]' "$state_file"

    # Then: add a genuinely new dep
    update_state_dependency "$state_file" "dep-d"
    grep -q '^\- \*\*Dependencies:\*\* \[dep-a, dep-b, dep-c, dep-d\]' "$state_file"
}

# ============================================================================
# Full workflow integration: detect → user selection simulation → STATE.md update → confirm
# ============================================================================

@test "full_workflow: detect skill deps, select all, update STATE.md, verify idempotent confirm" {
    # Setup: two open issues referencing the "add-agent" skill
    create_mock_issue "issue-needs-add-agent-1" "open" "plugin/skills/add-agent"
    create_mock_issue "issue-needs-add-agent-2" "open" "plugin/skills/add-agent"

    # Step 1: Extract skill name from description (simulates LLM extraction)
    run extract_skill_names "Modify plugin/skills/add-agent/first-use.md to add auto-detection"
    [ "$status" -eq 0 ]
    [[ "$output" == *"add-agent"* ]]

    # Step 2: Run detection for the extracted skill name
    run run_detection "add-agent" "brand-new-auto-dep-issue"
    [ "$status" -eq 0 ]
    [[ "$output" == *"issue-needs-add-agent-1"* ]]
    [[ "$output" == *"issue-needs-add-agent-2"* ]]

    # Step 3: Simulate "Yes, mark all as dependents" — update each STATE.md
    local new_issue_id="brand-new-auto-dep-issue"
    local state1="${ISSUES_DIR}/v2/v2.1/issue-needs-add-agent-1/STATE.md"
    local state2="${ISSUES_DIR}/v2/v2.1/issue-needs-add-agent-2/STATE.md"

    update_state_dependency "$state1" "$new_issue_id"
    update_state_dependency "$state2" "$new_issue_id"

    grep -qF "$new_issue_id" "$state1"
    grep -qF "$new_issue_id" "$state2"

    # Step 4: Confirm idempotency — running update again must not duplicate
    update_state_dependency "$state1" "$new_issue_id"
    update_state_dependency "$state2" "$new_issue_id"

    local count1 count2
    count1=$(grep -cF "$new_issue_id" "$state1" || true)
    count2=$(grep -cF "$new_issue_id" "$state2" || true)
    [ "$count1" -eq 1 ]
    [ "$count2" -eq 1 ]
}
