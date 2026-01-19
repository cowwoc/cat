#!/bin/bash
# Validate status display alignment before rendering
# Usage: validate-status-alignment.sh < proposed_output
# Returns 0 if valid, 1 if alignment issues found
#
# Created as prevention for M137 (recurrence of M136)

set -euo pipefail

# Read input
input=$(cat)

# Check if we have box borders
if ! echo "$input" | grep -q "╭─"; then
    echo "ERROR: No box structure found"
    exit 1
fi

# Extract lines between top and bottom borders
in_box=false
errors=()
line_num=0

while IFS= read -r line; do
    ((line_num++))

    # Start of outer box
    if [[ "$line" =~ ^╭─ ]]; then
        in_box=true
        continue
    fi

    # End of outer box
    if [[ "$line" =~ ^╰─ ]]; then
        in_box=false
        continue
    fi

    # Skip divider lines
    if [[ "$line" =~ ^├─ ]]; then
        continue
    fi

    if $in_box; then
        # Check line starts with │
        if [[ ! "$line" =~ ^│ ]]; then
            errors+=("Line $line_num: Missing left border │")
        fi

        # Check line ends with │ (allowing trailing whitespace)
        if [[ ! "$line" =~ │[[:space:]]*$ ]]; then
            errors+=("Line $line_num: Missing right border │ - got: '${line: -10}'")
        fi
    fi
done <<< "$input"

if [ ${#errors[@]} -gt 0 ]; then
    echo "ALIGNMENT ERRORS DETECTED:"
    printf '%s\n' "${errors[@]}"
    exit 1
fi

echo "PASS: Alignment validation successful"
exit 0
