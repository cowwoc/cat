#!/bin/bash
# Hook: detect-failures.sh
# Trigger: PostToolUse for Bash
# Purpose: Detect failures and suggest learning from mistakes

set -euo pipefail

# Configuration
FAILURE_PATTERNS="BUILD FAILED|FAILED|ERROR:|error:|Exception|FATAL|fatal:"
SUGGEST_SKILL=true

# Read exit code and output
EXIT_CODE="${1:-0}"
OUTPUT="${2:-}"

# Skip if successful
if [[ "$EXIT_CODE" == "0" ]]; then
    exit 0
fi

# Check for failure patterns in output
if echo "$OUTPUT" | grep -qE "$FAILURE_PATTERNS"; then
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "⚠️  Failure detected (exit code: $EXIT_CODE)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""

    if $SUGGEST_SKILL; then
        echo "Consider:"
        echo "1. Fix the immediate issue"
        echo "2. If this could recur, use learn-from-mistakes skill"
        echo "   to implement prevention"
        echo ""
        echo "See: .claude/skills/learn-from-mistakes/SKILL.md"
    fi
fi

exit 0
