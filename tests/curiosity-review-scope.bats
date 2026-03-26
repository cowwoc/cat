#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Tests for the CURIOSITY extraction and branching logic used in:
#   - plugin/skills/work-review-agent/first-use.md (lines 204-208, 211, 229)
#   - plugin/skills/stakeholder-review-agent/first-use.md (lines 367-380)
#
# The grep/sed extraction pattern under test:
#   CURIOSITY=$(echo "$EFFECTIVE_CONFIG" | grep -o '"curiosity"[[:space:]]*:[[:space:]]*"[^"]*"' \
#       | sed 's/.*"curiosity"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
#   if [[ -z "$CURIOSITY" ]]; then
#       CURIOSITY="medium"
#   fi

# ---------------------------------------------------------------------------
# Helper: extract CURIOSITY from a JSON string using the exact pattern from
# work-review-agent/first-use.md and stakeholder-review-agent/first-use.md.
# ---------------------------------------------------------------------------
extract_curiosity() {
    local json="$1"
    local result
    result=$(echo "$json" | grep -o '"curiosity"[[:space:]]*:[[:space:]]*"[^"]*"' \
        | sed 's/.*"curiosity"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
    if [[ -z "$result" ]]; then
        result="medium"
    fi
    echo "$result"
}

# ---------------------------------------------------------------------------
# Helper: compute REVIEW_SCOPE from CURIOSITY using the case block from
# stakeholder-review-agent/first-use.md (lines 373-380).
# ---------------------------------------------------------------------------
get_review_scope() {
    local CURIOSITY="$1"
    local REVIEW_SCOPE
    case "$CURIOSITY" in
        low)    REVIEW_SCOPE="Review changed lines only. Flag obvious issues visible in the diff." ;;
        medium) REVIEW_SCOPE="Review changed lines and their surrounding context (functions, classes containing the change). Flag issues that arise from the interaction between new and existing code." ;;
        high)   REVIEW_SCOPE="Review the broader system context. For each changed file, read the surrounding code that references or depends on it. Consider: (1) how this change interacts with other open issues in the same version, (2) architectural patterns in the rest of the codebase this change should follow or might inadvertently break, (3) cross-cutting concerns (security, performance, accessibility) beyond immediately changed files. Flag pre-existing issues in any file you read. Consider downstream impact on consumers of changed APIs or interfaces." ;;
        *)      CURIOSITY="medium"
                REVIEW_SCOPE="Review changed lines and their surrounding context (functions, classes containing the change). Flag issues that arise from the interaction between new and existing code." ;;
    esac
    echo "$REVIEW_SCOPE"
}

# ===========================================================================
# 1. CURIOSITY JSON extraction tests
# ===========================================================================

@test "extract curiosity: 'low' from JSON config" {
    local json='{"trust": "medium", "curiosity": "low", "effort": "medium"}'
    local result
    result=$(extract_curiosity "$json")
    [ "$result" = "low" ]
}

@test "extract curiosity: 'medium' from JSON config" {
    local json='{"trust": "medium", "curiosity": "medium", "effort": "medium"}'
    local result
    result=$(extract_curiosity "$json")
    [ "$result" = "medium" ]
}

@test "extract curiosity: 'high' from JSON config" {
    local json='{"trust": "high", "curiosity": "high", "effort": "high"}'
    local result
    result=$(extract_curiosity "$json")
    [ "$result" = "high" ]
}

@test "extract curiosity: handles whitespace around colon in JSON" {
    local json='{"curiosity" : "high"}'
    local result
    result=$(extract_curiosity "$json")
    [ "$result" = "high" ]
}

@test "extract curiosity: handles multiple spaces around colon" {
    local json='{"curiosity"  :  "low"}'
    local result
    result=$(extract_curiosity "$json")
    [ "$result" = "low" ]
}

# ===========================================================================
# 2. Empty-field default tests
# ===========================================================================

@test "extract curiosity: missing field defaults to 'medium'" {
    local json='{"trust": "medium", "effort": "medium"}'
    local result
    result=$(extract_curiosity "$json")
    [ "$result" = "medium" ]
}

@test "extract curiosity: empty JSON object defaults to 'medium'" {
    local json='{}'
    local result
    result=$(extract_curiosity "$json")
    [ "$result" = "medium" ]
}

@test "extract curiosity: empty string defaults to 'medium'" {
    local json=''
    local result
    result=$(extract_curiosity "$json")
    [ "$result" = "medium" ]
}

# ===========================================================================
# 3. Skip-branch condition tests (curiosity == "low")
# ===========================================================================

@test "skip condition: true when CURIOSITY is 'low'" {
    local CURIOSITY="low"
    [[ "$CURIOSITY" == "low" ]]
}

@test "skip condition: false when CURIOSITY is 'medium'" {
    local CURIOSITY="medium"
    ! [[ "$CURIOSITY" == "low" ]]
}

@test "skip condition: false when CURIOSITY is 'high'" {
    local CURIOSITY="high"
    ! [[ "$CURIOSITY" == "low" ]]
}

# ===========================================================================
# 4. Research-branch condition tests (curiosity == "high")
# ===========================================================================

@test "research condition: true when CURIOSITY is 'high'" {
    local CURIOSITY="high"
    [[ "$CURIOSITY" == "high" ]]
}

@test "research condition: false when CURIOSITY is 'low'" {
    local CURIOSITY="low"
    ! [[ "$CURIOSITY" == "high" ]]
}

@test "research condition: false when CURIOSITY is 'medium'" {
    local CURIOSITY="medium"
    ! [[ "$CURIOSITY" == "high" ]]
}

# ===========================================================================
# 5. REVIEW_SCOPE assignment tests
# ===========================================================================

@test "REVIEW_SCOPE: 'low' assigns changed-lines-only scope" {
    local result
    result=$(get_review_scope "low")
    [ "$result" = "Review changed lines only. Flag obvious issues visible in the diff." ]
}

@test "REVIEW_SCOPE: 'medium' assigns surrounding-context scope" {
    local result
    result=$(get_review_scope "medium")
    [ "$result" = "Review changed lines and their surrounding context (functions, classes containing the change). Flag issues that arise from the interaction between new and existing code." ]
}

@test "REVIEW_SCOPE: 'high' assigns broader-system-context scope" {
    local result
    result=$(get_review_scope "high")
    # Verify key phrases from the high scope definition
    echo "$result" | grep -q "Review the broader system context"
    echo "$result" | grep -q "downstream impact"
}

@test "REVIEW_SCOPE: low scope differs from medium scope" {
    local low_scope medium_scope
    low_scope=$(get_review_scope "low")
    medium_scope=$(get_review_scope "medium")
    [ "$low_scope" != "$medium_scope" ]
}

@test "REVIEW_SCOPE: medium scope differs from high scope" {
    local medium_scope high_scope
    medium_scope=$(get_review_scope "medium")
    high_scope=$(get_review_scope "high")
    [ "$medium_scope" != "$high_scope" ]
}

@test "REVIEW_SCOPE: low scope differs from high scope" {
    local low_scope high_scope
    low_scope=$(get_review_scope "low")
    high_scope=$(get_review_scope "high")
    [ "$low_scope" != "$high_scope" ]
}

# ===========================================================================
# 6. End-to-end extraction + routing tests
# ===========================================================================

@test "end-to-end: low config → skip review, no research" {
    local json='{"curiosity": "low"}'
    local CURIOSITY
    CURIOSITY=$(extract_curiosity "$json")
    [ "$CURIOSITY" = "low" ]
    # Skip condition fires
    [[ "$CURIOSITY" == "low" ]]
    # Research condition does not fire
    ! [[ "$CURIOSITY" == "high" ]]
}

@test "end-to-end: medium config → no skip, no research, scoped review" {
    local json='{"curiosity": "medium"}'
    local CURIOSITY
    CURIOSITY=$(extract_curiosity "$json")
    [ "$CURIOSITY" = "medium" ]
    # Skip condition does not fire
    ! [[ "$CURIOSITY" == "low" ]]
    # Research condition does not fire
    ! [[ "$CURIOSITY" == "high" ]]
    # REVIEW_SCOPE is medium
    local scope
    scope=$(get_review_scope "$CURIOSITY")
    echo "$scope" | grep -q "surrounding context"
}

@test "end-to-end: high config → no skip, research triggered, holistic review" {
    local json='{"curiosity": "high"}'
    local CURIOSITY
    CURIOSITY=$(extract_curiosity "$json")
    [ "$CURIOSITY" = "high" ]
    # Skip condition does not fire
    ! [[ "$CURIOSITY" == "low" ]]
    # Research condition fires
    [[ "$CURIOSITY" == "high" ]]
    # REVIEW_SCOPE is holistic
    local scope
    scope=$(get_review_scope "$CURIOSITY")
    echo "$scope" | grep -q "broader system context"
}

@test "end-to-end: missing field → medium default → scoped review" {
    local json='{"trust": "high"}'
    local CURIOSITY
    CURIOSITY=$(extract_curiosity "$json")
    [ "$CURIOSITY" = "medium" ]
    ! [[ "$CURIOSITY" == "low" ]]
    ! [[ "$CURIOSITY" == "high" ]]
    local scope
    scope=$(get_review_scope "$CURIOSITY")
    echo "$scope" | grep -q "surrounding context"
}
