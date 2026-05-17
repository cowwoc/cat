#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Testable shell functions extracted from work-implement-agent JOBS_COUNT detection.

# Detects the number of "### Job " headers in a plan.md file.
# Arguments: $1 = path to plan.md
# Output: prints "JOBS_COUNT=<N>" to stdout
# Returns: 0 always
detect_jobs_count() {
    local plan_md="$1"
    local count
    count=$(grep -c '^### Job ' "$plan_md" || true)
    echo "JOBS_COUNT=${count}"
}

# Classifies job execution mode based on job count.
# Arguments: $1 = JOBS_COUNT integer
# Output: prints "single" or "parallel" to stdout
classify_job_execution() {
    local jobs_count="$1"
    if [[ "$jobs_count" -le 1 ]]; then
        echo "single"
    else
        echo "parallel"
    fi
}

# Builds a compliant subagent prompt that passes PLAN_MD_PATH but does NOT embed JOBS_COUNT.
# Arguments: $1 = PLAN_MD_PATH, $2 = JOBS_COUNT
# Output: prints the prompt string to stdout
build_subagent_prompt() {
    local plan_md_path="$1"
    # $2 (JOBS_COUNT) is intentionally NOT embedded in the prompt.
    # Subagents must read plan.md directly via PLAN_MD_PATH.
    echo "PLAN_MD_PATH: ${plan_md_path}"
}
