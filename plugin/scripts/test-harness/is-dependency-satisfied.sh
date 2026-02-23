#!/bin/bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Test harness for is_dependency_satisfied() unit testing.
# Exposes the function for isolated testing without invoking the full script entry point.
#
# Usage:
#   CAT_DIR=/path/to/issues bash is-dependency-satisfied.sh <dep_name>
#
# Outputs: "true" or "false"

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${SCRIPT_DIR}/lib/version-utils.sh"

# Check if all sub-issues of a decomposed parent are closed
all_subissues_closed() {
    local state_file="$1"

    if ! grep -q "^## Decomposed Into" "$state_file" 2>/dev/null; then
        return 0
    fi

    local subissue_names
    subissue_names=$(sed -n '/^## Decomposed Into/,/^##/p' "$state_file" | grep -E '^\- ' | sed 's/^\- //' | cut -d' ' -f1 | tr -d '()')

    if [[ -z "$subissue_names" ]]; then
        return 0
    fi

    local issue_dir parent_version_dir
    issue_dir=$(dirname "$state_file")
    parent_version_dir=$(dirname "$issue_dir")

    for subissue in $subissue_names; do
        local subissue_state="${parent_version_dir}/${subissue}/STATE.md"
        if [[ -f "$subissue_state" ]]; then
            local subissue_status
            subissue_status=$(grep -E "^\- \*\*Status:\*\*" "$subissue_state" 2>/dev/null | sed 's/.*\*\*Status:\*\* //' | tr -d ' ')
            if [[ "$subissue_status" != "closed" ]]; then
                return 1
            fi
        else
            return 1
        fi
    done

    return 0
}

# Parse status from STATE.md
get_issue_status() {
    local state_file="$1"

    if [[ ! -f "$state_file" ]]; then
        echo '{"error":"STATE.md not found","file":"'"$state_file"'"}' >&2
        return 1
    fi

    local status
    status=$(grep -E "^\- \*\*Status:\*\*" "$state_file" | sed 's/.*\*\*Status:\*\* //' | tr -d ' ')

    if [[ -z "$status" ]]; then
        echo '{"error":"Status field not found","file":"'"$state_file"'"}' >&2
        return 1
    fi

    case "$status" in
        pending)
            status="open"
            ;;
        completed|complete|done)
            status="closed"
            ;;
        in_progress|active)
            status="in-progress"
            ;;
    esac

    local valid_statuses="open in-progress closed blocked"
    local is_valid=false
    for valid in $valid_statuses; do
        if [[ "$status" == "$valid" ]]; then
            is_valid=true
            break
        fi
    done

    if [[ "$is_valid" == "false" ]]; then
        echo "ERROR: Unknown status '$status' in $state_file" >&2
        return 1
    fi

    if [[ "$status" == "closed" ]]; then
        if ! all_subissues_closed "$state_file"; then
            echo "ERROR: Decomposed parent issue marked 'closed' but sub-issues are not all closed in $state_file" >&2
            return 1
        fi
    fi

    echo "$status"
}

# Check if a dependency is satisfied (issue closed)
is_dependency_satisfied() {
    local dep_name="$1"

    local dep_state
    dep_state=$(find "$CAT_DIR" -path "*/$dep_name/STATE.md" 2>/dev/null | head -1)

    if [[ -z "$dep_state" && "$dep_name" =~ ^([0-9]+\.[0-9]+)-([a-zA-Z][a-zA-Z0-9_-]*)$ ]]; then
        local dep_version="${BASH_REMATCH[1]}"
        local bare_name="${BASH_REMATCH[2]}"
        local dep_dir
        dep_dir=$(get_issue_dir "$dep_version" "$bare_name" "$CAT_DIR")
        if [[ -f "$dep_dir/STATE.md" ]]; then
            dep_state="$dep_dir/STATE.md"
        fi
    fi

    if [[ -z "$dep_state" ]]; then
        echo "false"
        return
    fi

    local status
    if ! status=$(get_issue_status "$dep_state" 2>/dev/null); then
        echo "false"
        return
    fi

    if [[ "$status" == "closed" ]]; then
        echo "true"
    else
        echo "false"
    fi
}

is_dependency_satisfied "$1"
