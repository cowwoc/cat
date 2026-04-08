#!/usr/bin/env bats
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Tests for JOBS_COUNT boundary detection in work-implement-agent.
#
# The grep command under test (from plugin/skills/work-implement-agent/first-use.md):
#   JOBS_COUNT=$(grep -c '^### Job ' "$PLAN_MD") && echo "JOBS_COUNT=${JOBS_COUNT}"

setup() {
    TMPDIR="$(mktemp -d)"
    PLAN_MD="${TMPDIR}/plan.md"
    HELPER_DIR="$(cd "$(dirname "$BATS_TEST_FILENAME")" && pwd)"
    source "${HELPER_DIR}/jobs-count-helper.bash"
}

teardown() {
    rm -rf "${TMPDIR:-}"
}

# --- detect_jobs_count tests ---

@test "detect_jobs_count returns 0 for empty plan.md" {
    > "${PLAN_MD}"
    result=$(detect_jobs_count "${PLAN_MD}")
    [ "$result" = "JOBS_COUNT=0" ]
}

@test "detect_jobs_count returns 0 for plan with no jobs section" {
    printf '## Goal\nSome goal text\n\n## Post-conditions\nSome conditions\n' > "${PLAN_MD}"
    result=$(detect_jobs_count "${PLAN_MD}")
    [ "$result" = "JOBS_COUNT=0" ]
}

@test "detect_jobs_count returns 1 for plan with one job" {
    printf '## Jobs\n\n### Job 1\nStep 1 content\n' > "${PLAN_MD}"
    result=$(detect_jobs_count "${PLAN_MD}")
    [ "$result" = "JOBS_COUNT=1" ]
}

@test "detect_jobs_count returns 2 for plan with two jobs" {
    printf '## Jobs\n\n### Job 1\nContent\n\n### Job 2\nContent\n' > "${PLAN_MD}"
    result=$(detect_jobs_count "${PLAN_MD}")
    [ "$result" = "JOBS_COUNT=2" ]
}

@test "detect_jobs_count returns 5 for plan with five jobs" {
    printf '## Jobs\n\n### Job 1\n\n### Job 2\n\n### Job 3\n\n### Job 4\n\n### Job 5\n' > "${PLAN_MD}"
    result=$(detect_jobs_count "${PLAN_MD}")
    [ "$result" = "JOBS_COUNT=5" ]
}

@test "detect_jobs_count returns 0 for wrong header level (## Job 1)" {
    printf '## Job 1\nContent\n' > "${PLAN_MD}"
    result=$(detect_jobs_count "${PLAN_MD}")
    [ "$result" = "JOBS_COUNT=0" ]
}

@test "detect_jobs_count returns 0 for wrong header level (#### Job 1)" {
    printf '#### Job 1\nContent\n' > "${PLAN_MD}"
    result=$(detect_jobs_count "${PLAN_MD}")
    [ "$result" = "JOBS_COUNT=0" ]
}

@test "detect_jobs_count returns 0 for leading whitespace before ### Job" {
    printf ' ### Job 1\nContent\n' > "${PLAN_MD}"
    result=$(detect_jobs_count "${PLAN_MD}")
    [ "$result" = "JOBS_COUNT=0" ]
}

@test "detect_jobs_count returns 0 for missing space after ### Job" {
    printf '### Job1\nContent\n' > "${PLAN_MD}"
    result=$(detect_jobs_count "${PLAN_MD}")
    [ "$result" = "JOBS_COUNT=0" ]
}

@test "detect_jobs_count counts only ### Job headers, ignores other ### headers" {
    printf '### Job 1\n\n### Implementation Notes\n\n### Job 2\n' > "${PLAN_MD}"
    result=$(detect_jobs_count "${PLAN_MD}")
    [ "$result" = "JOBS_COUNT=2" ]
}

@test "detect_jobs_count returns 0 for whitespace-only file" {
    printf '   \n\t\n  \n' > "${PLAN_MD}"
    result=$(detect_jobs_count "${PLAN_MD}")
    [ "$result" = "JOBS_COUNT=0" ]
}

@test "detect_jobs_count handles plan with Execution Steps instead of jobs" {
    printf '## Execution Steps\n\n### Step 1\nDo something\n\n### Step 2\nDo more\n' > "${PLAN_MD}"
    result=$(detect_jobs_count "${PLAN_MD}")
    [ "$result" = "JOBS_COUNT=0" ]
}

# --- classify_job_execution tests ---

@test "classify_job_execution returns single for 0 jobs" {
    result=$(classify_job_execution 0)
    [ "$result" = "single" ]
}

@test "classify_job_execution returns single for 1 job" {
    result=$(classify_job_execution 1)
    [ "$result" = "single" ]
}

@test "classify_job_execution returns parallel for 2 jobs" {
    result=$(classify_job_execution 2)
    [ "$result" = "parallel" ]
}

@test "classify_job_execution returns parallel for 10 jobs" {
    result=$(classify_job_execution 10)
    [ "$result" = "parallel" ]
}

# --- build_subagent_prompt relay prohibition tests ---

@test "build_subagent_prompt includes PLAN_MD_PATH" {
    result=$(build_subagent_prompt "/tmp/test/plan.md" 3)
    [[ "$result" == *"PLAN_MD_PATH: /tmp/test/plan.md"* ]]
}

@test "build_subagent_prompt does not embed JOBS_COUNT value" {
    result=$(build_subagent_prompt "/tmp/test/plan.md" 3)
    [[ "$result" != *"JOBS_COUNT="* ]]
    [[ "$result" != *" 3 "* ]]
    [[ "$result" != *" 3"  ]]
}

# --- E2E tests (detect + classify combined) ---

@test "E2E: empty plan yields single execution" {
    > "${PLAN_MD}"
    output=$(detect_jobs_count "${PLAN_MD}")
    count="${output#JOBS_COUNT=}"
    result=$(classify_job_execution "$count")
    [ "$result" = "single" ]
}

@test "E2E: one-job plan yields single execution" {
    printf '## Jobs\n\n### Job 1\nContent\n' > "${PLAN_MD}"
    output=$(detect_jobs_count "${PLAN_MD}")
    count="${output#JOBS_COUNT=}"
    result=$(classify_job_execution "$count")
    [ "$result" = "single" ]
}

@test "E2E: two-job plan yields parallel execution" {
    printf '## Jobs\n\n### Job 1\nContent\n\n### Job 2\nContent\n' > "${PLAN_MD}"
    output=$(detect_jobs_count "${PLAN_MD}")
    count="${output#JOBS_COUNT=}"
    result=$(classify_job_execution "$count")
    [ "$result" = "parallel" ]
}

@test "E2E: malformed job headers yield single execution" {
    printf '## Job 1\nContent\n\n#### Job 2\nContent\n' > "${PLAN_MD}"
    output=$(detect_jobs_count "${PLAN_MD}")
    count="${output#JOBS_COUNT=}"
    result=$(classify_job_execution "$count")
    [ "$result" = "single" ]
}
