#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
set -euo pipefail

# Validate the plan-builder review loop implementation by checking that all required structural
# elements are present in plugin/agents/plan-review-agent.md and
# plugin/skills/plan-builder-agent/first-use.md.
#
# Usage: validate-plan-builder-review-loop.sh [workspace_root]
#   workspace_root: optional path to workspace root (defaults to two levels above this script)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORKSPACE_ROOT="${1:-"${SCRIPT_DIR}/../.."}"

REVIEW_AGENT="${WORKSPACE_ROOT}/plugin/agents/plan-review-agent.md"
FIRST_USE="${WORKSPACE_ROOT}/plugin/skills/plan-builder-agent/first-use.md"

# --- Helper ---
fail() {
  echo "FAIL: $1"
  exit 1
}

# --- Check files exist ---
[[ -f "$REVIEW_AGENT" ]] || fail "plugin/agents/plan-review-agent.md does not exist at: $REVIEW_AGENT"
[[ -f "$FIRST_USE" ]] || fail "plugin/skills/plan-builder-agent/first-use.md does not exist at: $FIRST_USE"

# --- Validate plan-review-agent.md ---

grep -qF 'model: claude-sonnet-4-6' "$REVIEW_AGENT" \
  || fail "plan-review-agent.md is missing 'model: claude-sonnet-4-6' in frontmatter"

grep -qF '"verdict"' "$REVIEW_AGENT" \
  || fail "plan-review-agent.md is missing '\"verdict\"' JSON response field"

grep -qF '"gaps"' "$REVIEW_AGENT" \
  || fail "plan-review-agent.md is missing '\"gaps\"' JSON response field"

grep -qF '"YES"' "$REVIEW_AGENT" \
  || fail "plan-review-agent.md is missing '\"YES\"' verdict value"

grep -qF '"NO"' "$REVIEW_AGENT" \
  || fail "plan-review-agent.md is missing '\"NO\"' verdict value"

grep -q 'Haiku' "$REVIEW_AGENT" \
  || fail "plan-review-agent.md is missing 'Haiku' pass criterion reference"

# --- Validate first-use.md structural elements ---

grep -q 'Iterative Completeness Review' "$FIRST_USE" \
  || fail "first-use.md is missing 'Iterative Completeness Review' step heading"

grep -q '\blow\b' "$FIRST_USE" \
  || fail "first-use.md is missing effort gate keyword 'low'"

grep -q 'ITERATION' "$FIRST_USE" \
  || fail "first-use.md is missing 'ITERATION' loop counter variable"

grep -q 'PLAN_CONTENT' "$FIRST_USE" \
  || fail "first-use.md is missing 'PLAN_CONTENT' plan content variable"

grep -q 'ISSUE_GOAL' "$FIRST_USE" \
  || fail "first-use.md is missing 'ISSUE_GOAL' goal variable"

# Accept "3 iterations" or a digit 3 adjacent to the word "iteration"
(grep -qE '3 iterations' "$FIRST_USE" || grep -qE '(iteration[s]? [0-9]*3|3[^0-9]*iteration)' "$FIRST_USE") \
  || fail "first-use.md is missing cap value ('3 iterations' or equivalent)"

grep -q 'cap reached' "$FIRST_USE" \
  || fail "first-use.md is missing 'cap reached' cap warning text"

grep -q 'YES' "$FIRST_USE" \
  || fail "first-use.md is missing 'YES' exit-on-YES logic"

grep -q 'NO' "$FIRST_USE" \
  || fail "first-use.md is missing 'NO' gap-fix-on-NO logic"

# --- Verify ordering: review step appears BEFORE the write/commit step ---
#
# The workflow numbered list references the review loop before the final write step.
# Search for the line in the numbered list that references the review (contains
# "Iterative Completeness Review" or "review loop"), and the line that writes/commits
# the final PLAN.md (git commit, ## Commit, commit the PLAN.md, or Write the final PLAN.md).
# Use the first occurrence of each to handle the case where the detail section heading
# for Step 7 appears after the numbered list.

# First occurrence of a review-loop reference in the numbered workflow list
REVIEW_LINE=$(grep -n 'Iterative Completeness Review\|review loop' "$FIRST_USE" | head -1 | cut -d: -f1)

# First occurrence of a write/commit step
COMMIT_LINE=$(grep -n -E '(git commit|## Commit|commit the plan\.md|Write the final plan\.md|[Ww]rite.*plan\.md)' "$FIRST_USE" \
  | head -1 | cut -d: -f1)

if [[ -z "$REVIEW_LINE" ]]; then
  fail "Could not locate 'Iterative Completeness Review' or 'review loop' in first-use.md"
fi

if [[ -z "$COMMIT_LINE" ]]; then
  fail "Could not locate a write/commit step (git commit / ## Commit / commit the plan.md / Write the final plan.md) in first-use.md"
fi

if [[ "$REVIEW_LINE" -gt "$COMMIT_LINE" ]]; then
  fail "Review loop step appears after the commit step in first-use.md — insertion order is wrong."
fi

echo "PASS: plan-builder review loop structure validated."
