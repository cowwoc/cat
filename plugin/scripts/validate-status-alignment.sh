#!/bin/bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Validate status display alignment before rendering
# Usage: validate-status-alignment.sh < proposed_output
# Returns 0 if valid, 1 if alignment issues found
#
# Created as prevention for M137 (recurrence of M136)
# Enhanced for M140 with display width validation

set -euo pipefail

if [[ $# -gt 0 ]]; then
  echo "ERROR: $(basename "$0") accepts no arguments, got $#" >&2
  exit 1
fi

# Read input
input=$(cat)

# Check if we have box borders
if ! echo "$input" | grep -q "╭─"; then
    echo "ERROR: No box structure found"
    exit 1
fi

# Track state and errors
in_box=false
errors=()
line_num=0
content_lines=0

while IFS= read -r line || [[ -n "$line" ]]; do
    ((line_num++)) || true

    # Top border line
    if [[ "$line" =~ ^╭─ ]]; then
        in_box=true
        continue
    fi

    # Bottom border line
    if [[ "$line" =~ ^╰─ ]]; then
        in_box=false
        continue
    fi

    # Skip divider lines
    if [[ "$line" =~ ^├─ ]]; then
        continue
    fi

    if $in_box; then
        ((content_lines++)) || true

        # Check line starts with │
        if [[ ! "$line" =~ ^│ ]]; then
            errors+=("Line $line_num: Missing left border │")
        fi

        # Strip trailing whitespace for right border check
        trimmed="${line%"${line##*[![:space:]]}"}"
        if [[ -z "$trimmed" ]]; then
            trimmed="$line"
        fi

        # Check line ends with │
        if [[ ! "$trimmed" =~ │$ ]]; then
            last_chars="${trimmed: -10}"
            errors+=("Line $line_num: Missing right border │ - ends with: '$last_chars'")
        fi

        # Check inner box structure (M140 key check)
        # If line starts with │ followed by spaces then │ or ╭ or ╰, it's an inner box line
        if [[ "$line" =~ ^│[[:space:]]+│ ]]; then
            # Inner box content line - must end with │ (spaces) │ pattern
            if [[ ! "$trimmed" =~ │[[:space:]]+│$ ]]; then
                errors+=("Line $line_num: Inner box content line missing outer right border │ - should end with │...│")
            fi
        elif [[ "$line" =~ ^│[[:space:]]+╭ ]]; then
            # Inner box top border - must end with ╮ (spaces) │
            if [[ ! "$trimmed" =~ ╮[[:space:]]+│$ ]]; then
                errors+=("Line $line_num: Inner box top border missing outer right border │ - should end with ╮...│")
            fi
        elif [[ "$line" =~ ^│[[:space:]]+╰ ]]; then
            # Inner box bottom border - must end with ╯ (spaces) │
            if [[ ! "$trimmed" =~ ╯[[:space:]]+│$ ]]; then
                errors+=("Line $line_num: Inner box bottom border missing outer right border │ - should end with ╯...│")
            fi
        fi
    fi
done <<< "$input"

if [[ ${#errors[@]} -gt 0 ]]; then
    echo "ALIGNMENT ERRORS DETECTED (${#errors[@]} issues):"
    printf '%s\n' "${errors[@]}"
    exit 1
fi

echo "PASS: Alignment validation successful"
echo "  - Validated $content_lines content lines"
exit 0
