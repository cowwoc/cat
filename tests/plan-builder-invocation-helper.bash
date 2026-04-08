#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.

# Testable shell functions extracted from work-implement-agent first-use.md Step 4.
# These functions encapsulate the plan-builder invocation conditional logic.

# Detects whether plan.md contains Jobs or Execution Steps sections.
# $1 - path to plan.md
# Prints "true" or "false" to stdout.
detect_has_steps() {
    grep -qE '^## (Jobs|Execution Steps)' "$1" && echo "true" || echo "false"
}

# Extracts the effort field from get-config-output JSON.
# $1 - raw JSON string (output of get-config-output effective)
# Prints the effort value to stdout. Prints empty string if field not found.
extract_effort() {
    echo "$1" | grep -o '"effort"[[:space:]]*:[[:space:]]*"[^"]*"' \
      | sed 's/.*"effort"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/'
}

# Constructs the full args string for plan-builder-agent invocation.
# $1 - CAT_AGENT_ID
# $2 - EFFORT
# $3 - ISSUE_PATH
# Prints the args string to stdout.
build_plan_builder_args() {
    echo "$1 $2 revise $3 Generate full implementation steps for this lightweight plan. Add Jobs or Execution Steps section with detailed step-by-step implementation guidance."
}

# Orchestrates the full conditional flow for plan-builder invocation.
# $1 - path to plan.md
# $2 - path to get-config-output binary
# $3 - CAT_AGENT_ID
# $4 - ISSUE_PATH
# Prints "SKIP" if hasSteps is true, or "INVOKE:<args>" if plan-builder should be called.
# Returns 1 on config read failure.
should_invoke_plan_builder() {
    local has_steps
    has_steps=$(detect_has_steps "$1")
    if [[ "$has_steps" == "true" ]]; then
        echo "SKIP"
        return 0
    fi

    local config
    if ! config=$("$2" effective); then
        echo "ERROR: Failed to read effective config" >&2
        return 1
    fi

    local effort
    effort=$(extract_effort "$config")

    local args
    args=$(build_plan_builder_args "$3" "$effort" "$4")
    echo "INVOKE:${args}"
}
