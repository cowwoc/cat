#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Testable shell functions extracted from work-implement-agent WAVES_COUNT detection.

# Detects the number of "### Wave " headers in a plan.md file.
# Arguments: $1 = path to plan.md
# Output: prints "WAVES_COUNT=<N>" to stdout
# Returns: 0 always
detect_waves_count() {
    local plan_md="$1"
    local count
    count=$(grep -c '^### Wave ' "$plan_md" || true)
    echo "WAVES_COUNT=${count}"
}

# Classifies wave execution mode based on wave count.
# Arguments: $1 = WAVES_COUNT integer
# Output: prints "single" or "parallel" to stdout
classify_wave_execution() {
    local waves_count="$1"
    if [[ "$waves_count" -le 1 ]]; then
        echo "single"
    else
        echo "parallel"
    fi
}

# Builds a compliant subagent prompt that passes PLAN_MD_PATH but does NOT embed WAVES_COUNT.
# Arguments: $1 = PLAN_MD_PATH, $2 = WAVES_COUNT
# Output: prints the prompt string to stdout
build_subagent_prompt() {
    local plan_md_path="$1"
    # $2 (WAVES_COUNT) is intentionally NOT embedded in the prompt.
    # Subagents must read plan.md directly via PLAN_MD_PATH.
    echo "PLAN_MD_PATH: ${plan_md_path}"
}
