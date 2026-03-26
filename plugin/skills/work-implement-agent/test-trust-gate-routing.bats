#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.

# Tests for trust-level gate routing logic extracted from:
# - plugin/skills/work-implement-agent/first-use.md (Step 4: pre-implementation gate)
# - plugin/skills/work-merge-agent/first-use.md (Step 12: approval gate)

setup() {
  TMPDIR=$(mktemp -d)
  PLAN_MD="${TMPDIR}/plan.md"
  REVIEW_RESULT_FILE="${TMPDIR}/review-result.json"
}

teardown() {
  rm -rf "${TMPDIR}"
}

# ---------------------------------------------------------------------------
# Test 1: trust=low — plan.md Goal extraction via sed (Step 4 logic)
# ---------------------------------------------------------------------------
@test "trust=low: sed extracts Goal section from plan.md" {
  cat > "${PLAN_MD}" <<'EOF'
## Goal
Implement trust approval gates for the work workflow.

## Post-conditions
- trust=low shows pre-implementation gate
- trust=medium skips pre-implementation gate
- trust=high auto-merges on clean review

## Execution Plan
### Wave 1
...
EOF

  ISSUE_GOAL=$(sed -n '/^## Goal/{n;:loop;/^## /b;p;n;b loop}' "${PLAN_MD}" \
    | sed '/^[[:space:]]*$/d' | head -20)

  [[ -n "${ISSUE_GOAL}" ]]
  [[ "${ISSUE_GOAL}" == *"Implement trust approval gates"* ]]
}

# ---------------------------------------------------------------------------
# Test 2: trust=medium — TRUST != 'low' conditional skip (Step 4 logic)
# ---------------------------------------------------------------------------
@test "trust=medium: TRUST != low means pre-implementation gate is skipped" {
  TRUST="medium"

  # Simulate: "If TRUST != 'low', skip this step entirely and proceed to Step 5."
  GATE_SKIPPED="false"
  if [[ "${TRUST}" != "low" ]]; then
    GATE_SKIPPED="true"
  fi

  [[ "${GATE_SKIPPED}" == "true" ]]
}

# ---------------------------------------------------------------------------
# Test 3: trust=high — clean review (REVIEW_PASSED + has_high_or_critical=false)
#         sets HIGH_TRUST_PAUSE=false (Step 12 logic)
# ---------------------------------------------------------------------------
@test "trust=high: REVIEW_PASSED with has_high_or_critical=false sets HIGH_TRUST_PAUSE=false" {
  cat > "${REVIEW_RESULT_FILE}" <<'EOF'
{
  "status": "REVIEW_PASSED",
  "has_high_or_critical": false
}
EOF

  HIGH_TRUST_PAUSE="false"
  if [[ -f "${REVIEW_RESULT_FILE}" ]]; then
    PERSISTED_STATUS=$(grep -o '"status"[[:space:]]*:[[:space:]]*"[^"]*"' "${REVIEW_RESULT_FILE}" | \
      head -1 | sed 's/.*"status"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
    PERSISTED_HAS_HIGH=$(grep -o '"has_high_or_critical"[[:space:]]*:[[:space:]]*[^,}]*' "${REVIEW_RESULT_FILE}" | \
      sed 's/.*"has_high_or_critical"[[:space:]]*:[[:space:]]*\([^,}]*\)/\1/' | tr -d ' ')

    if [[ "${PERSISTED_STATUS}" == "CONCERNS_FOUND" || "${PERSISTED_HAS_HIGH}" == "true" ]]; then
      HIGH_TRUST_PAUSE="true"
    fi
  fi

  [[ "${HIGH_TRUST_PAUSE}" == "false" ]]
}

# ---------------------------------------------------------------------------
# Test 4: trust=high — has_high_or_critical=true sets HIGH_TRUST_PAUSE=true (Step 12 logic)
# ---------------------------------------------------------------------------
@test "trust=high: has_high_or_critical=true sets HIGH_TRUST_PAUSE=true" {
  cat > "${REVIEW_RESULT_FILE}" <<'EOF'
{
  "status": "REVIEW_PASSED",
  "has_high_or_critical": true
}
EOF

  HIGH_TRUST_PAUSE="false"
  if [[ -f "${REVIEW_RESULT_FILE}" ]]; then
    PERSISTED_STATUS=$(grep -o '"status"[[:space:]]*:[[:space:]]*"[^"]*"' "${REVIEW_RESULT_FILE}" | \
      head -1 | sed 's/.*"status"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
    PERSISTED_HAS_HIGH=$(grep -o '"has_high_or_critical"[[:space:]]*:[[:space:]]*[^,}]*' "${REVIEW_RESULT_FILE}" | \
      sed 's/.*"has_high_or_critical"[[:space:]]*:[[:space:]]*\([^,}]*\)/\1/' | tr -d ' ')

    if [[ "${PERSISTED_STATUS}" == "CONCERNS_FOUND" || "${PERSISTED_HAS_HIGH}" == "true" ]]; then
      HIGH_TRUST_PAUSE="true"
    fi
  fi

  [[ "${HIGH_TRUST_PAUSE}" == "true" ]]
}

# ---------------------------------------------------------------------------
# Test 5: trust=high — CONCERNS_FOUND status sets HIGH_TRUST_PAUSE=true (Step 12 logic)
# ---------------------------------------------------------------------------
@test "trust=high: CONCERNS_FOUND status sets HIGH_TRUST_PAUSE=true" {
  cat > "${REVIEW_RESULT_FILE}" <<'EOF'
{
  "status": "CONCERNS_FOUND",
  "has_high_or_critical": false
}
EOF

  HIGH_TRUST_PAUSE="false"
  if [[ -f "${REVIEW_RESULT_FILE}" ]]; then
    PERSISTED_STATUS=$(grep -o '"status"[[:space:]]*:[[:space:]]*"[^"]*"' "${REVIEW_RESULT_FILE}" | \
      head -1 | sed 's/.*"status"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
    PERSISTED_HAS_HIGH=$(grep -o '"has_high_or_critical"[[:space:]]*:[[:space:]]*[^,}]*' "${REVIEW_RESULT_FILE}" | \
      sed 's/.*"has_high_or_critical"[[:space:]]*:[[:space:]]*\([^,}]*\)/\1/' | tr -d ' ')

    if [[ "${PERSISTED_STATUS}" == "CONCERNS_FOUND" || "${PERSISTED_HAS_HIGH}" == "true" ]]; then
      HIGH_TRUST_PAUSE="true"
    fi
  fi

  [[ "${HIGH_TRUST_PAUSE}" == "true" ]]
}
