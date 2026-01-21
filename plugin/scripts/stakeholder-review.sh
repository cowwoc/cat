#!/bin/bash
set -euo pipefail

# Stakeholder Review Output Renderer
#
# Renders compact, scannable stakeholder review output using box-drawing
# characters and tree structure.
#
# Usage: stakeholder-review.sh BOX_TYPE [ARGS...]
#
# Box types:
#   summary TASK_NAME JSON_DATA        - Summary box with tree structure
#   concerns SEVERITY JSON_ARRAY       - Severity-grouped concern box
#   full TASK_NAME JSON_DATA [--verbose] - Full review output

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/box.sh"

# Stakeholder review boxes use width 60 (58 interior + 2 borders)
box_init 60

# Status icons mapping
get_status_icon() {
    local status="$1"
    local critical="${2:-0}"
    local high="${3:-0}"

    case "$status" in
        APPROVED)
            echo "✓"
            ;;
        CONCERNS)
            if [ "$high" -gt 0 ]; then
                echo "⚠ ${high} HIGH"
            else
                echo "⚠"
            fi
            ;;
        REJECTED)
            if [ "$critical" -gt 0 ]; then
                echo "✗ ${critical} CRITICAL"
            elif [ "$high" -gt 0 ]; then
                echo "✗ ${high} HIGH"
            else
                echo "✗"
            fi
            ;;
        *)
            echo "?"
            ;;
    esac
}

# Default empty JSON object
EMPTY_JSON='{}'

# Render the summary box with tree-structured stakeholder list
box_summary() {
    local task_name="${1:-task}"
    local json_data="${2:-$EMPTY_JSON}"

    local review_status critical_count high_count
    review_status=$(echo "$json_data" | jq -r '.review_status // "UNKNOWN"')
    critical_count=$(echo "$json_data" | jq -r '.critical_count // 0')
    high_count=$(echo "$json_data" | jq -r '.high_count // 0')

    local stakeholders="requirements architect security quality tester performance ux sales marketing legal"

    box_top "STAKEHOLDER REVIEW"
    box_empty
    box_line "  Task: ${task_name}"
    box_empty
    box_divider
    box_line "  Spawning reviewers..."

    local count=0
    local total=0
    # Count stakeholders with results
    for stakeholder in $stakeholders; do
        local result
        result=$(echo "$json_data" | jq -r ".stakeholder_results.${stakeholder} // null")
        if [ "$result" != "null" ]; then
            total=$((total + 1))
        fi
    done

    for stakeholder in $stakeholders; do
        local result stakeholder_status s_critical s_high icon
        result=$(echo "$json_data" | jq -r ".stakeholder_results.${stakeholder} // null")

        if [ "$result" = "null" ]; then
            continue
        fi

        count=$((count + 1))

        stakeholder_status=$(echo "$result" | jq -r '.status // "UNKNOWN"')
        s_critical=$(echo "$result" | jq -r '[.concerns[]? | select(.severity == "CRITICAL")] | length')
        s_high=$(echo "$result" | jq -r '[.concerns[]? | select(.severity == "HIGH")] | length')

        icon=$(get_status_icon "$stakeholder_status" "$s_critical" "$s_high")

        local connector
        if [ "$count" -eq "$total" ]; then
            connector="└──"
        else
            connector="├──"
        fi

        box_line "  ${connector} ${stakeholder} ${icon}"
    done

    box_divider

    local result_text
    if [ "$review_status" = "APPROVED" ]; then
        result_text="Result: APPROVED"
    elif [ "$review_status" = "CONCERNS" ]; then
        result_text="Result: CONCERNS (${high_count} high)"
    else
        result_text="Result: REJECTED (${critical_count} critical, ${high_count} high)"
    fi
    box_line "  ${result_text}"

    box_empty
    box_bottom
}

# Default empty JSON array
EMPTY_ARRAY='[]'

# Render a concern box for a specific severity
box_concerns() {
    local severity="${1:-CRITICAL}"
    local json_array="${2:-$EMPTY_ARRAY}"

    local count
    count=$(echo "$json_array" | jq 'length')

    if [ "$count" -eq 0 ]; then
        return
    fi

    # Render box with severity as title
    printf '┌─ %s ' "$severity"
    local title_len=$((3 + ${#severity} + 1))
    local dash_count=$((60 - 2 - title_len))
    for ((i=0; i<dash_count; i++)); do printf '─'; done
    printf '┐\n'

    echo "$json_array" | jq -c '.[]' | while IFS= read -r concern; do
        local stakeholder issue location
        stakeholder=$(echo "$concern" | jq -r '.stakeholder // "unknown"')
        issue=$(echo "$concern" | jq -r '.issue // "Unknown issue"')
        location=$(echo "$concern" | jq -r '.location // ""')

        if [ ${#issue} -gt 48 ]; then
            issue="${issue:0:45}..."
        fi

        # Capitalize first letter of stakeholder
        stakeholder="$(echo "${stakeholder:0:1}" | tr '[:lower:]' '[:upper:]')${stakeholder:1}"

        local line1="[${stakeholder}] ${issue}"
        local padded1
        padded1=$(pad "$line1" 58)
        printf '│ %s│\n' "$padded1"

        if [ -n "$location" ]; then
            local line2="└─ ${location}"
            local padded2
            padded2=$(pad "$line2" 58)
            printf '│ %s│\n' "$padded2"
        fi

        printf '│%58s│\n' ""
    done

    printf '└'
    for ((i=0; i<58; i++)); do printf '─'; done
    printf '┘\n'
}

# Render full review output (summary + concern boxes)
box_full() {
    local task_name="${1:-task}"
    local json_data="${2:-$EMPTY_JSON}"
    local verbose="${3:-}"

    box_summary "$task_name" "$json_data"
    echo ""

    local critical_concerns high_concerns medium_concerns
    critical_concerns=$(echo "$json_data" | jq -c '.aggregated_concerns.critical // []')
    high_concerns=$(echo "$json_data" | jq -c '.aggregated_concerns.high // []')
    medium_concerns=$(echo "$json_data" | jq -c '.aggregated_concerns.medium // []')

    if [ "$(echo "$critical_concerns" | jq 'length')" -gt 0 ]; then
        box_concerns "CRITICAL" "$critical_concerns"
        echo ""
    fi

    if [ "$(echo "$high_concerns" | jq 'length')" -gt 0 ]; then
        box_concerns "HIGH" "$high_concerns"
        echo ""
    fi

    if [ "$verbose" = "--verbose" ] && [ "$(echo "$medium_concerns" | jq 'length')" -gt 0 ]; then
        box_concerns "MEDIUM" "$medium_concerns"
        echo ""
    fi

    if [ "$verbose" != "--verbose" ] && [ "$(echo "$medium_concerns" | jq 'length')" -gt 0 ]; then
        echo "Details: Run with --verbose for full recommendations"
    fi
}

# Main dispatcher
BOX_TYPE="${1:-}"
shift || true

case "$BOX_TYPE" in
    summary)
        box_summary "$@"
        ;;
    concerns)
        box_concerns "$@"
        ;;
    full)
        box_full "$@"
        ;;
    *)
        echo "Usage: stakeholder-review.sh BOX_TYPE [ARGS...]" >&2
        echo "" >&2
        echo "Box types:" >&2
        echo "  summary TASK_NAME JSON_DATA        - Summary box with tree structure" >&2
        echo "  concerns SEVERITY JSON_ARRAY       - Severity-grouped concern box" >&2
        echo "  full TASK_NAME JSON_DATA [--verbose] - Full review output" >&2
        exit 1
        ;;
esac
