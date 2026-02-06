#!/usr/bin/env bash
# Feature gate - checks if user's tier allows a feature
# Usage: feature-gate.sh <feature> [--json]
# Exit codes: 0=allowed, 1=blocked, 2=error

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FEATURE="${1:-}"
JSON_OUTPUT="${2:-}"

if [[ -z "$FEATURE" ]]; then
    echo "Usage: feature-gate.sh <feature> [--json]" >&2
    exit 2
fi

# Validate license and get tier
# Note: validate-license.py may exit non-zero but still output valid JSON
LICENSE_RESULT=$(python3 "${SCRIPT_DIR}/validate-license.py" 2>/dev/null) || true
if [[ -z "$LICENSE_RESULT" ]] || ! echo "$LICENSE_RESULT" | jq -e . >/dev/null 2>&1; then
    LICENSE_RESULT='{"tier":"indie","error":"validation failed"}'
fi
TIER=$(echo "$LICENSE_RESULT" | jq -r '.tier // "indie"')
WARNING=$(echo "$LICENSE_RESULT" | jq -r '.warning // empty')

# Check entitlement
if "${SCRIPT_DIR}/entitlements.sh" "$TIER" "$FEATURE" 2>/dev/null; then
    ALLOWED=true
    MESSAGE=""
else
    ALLOWED=false
    # Find required tier for this feature
    REQUIRED_TIER=$("${SCRIPT_DIR}/entitlements.sh" --required-tier "$FEATURE" 2>/dev/null)
    if [[ -z "$REQUIRED_TIER" ]]; then
        echo "ERROR: Could not determine required tier for feature: $FEATURE" >&2
        exit 1
    fi
    MESSAGE="Feature '$FEATURE' requires $REQUIRED_TIER tier. Current: $TIER. Upgrade at https://cat.example.com/pricing"
fi

if [[ "$JSON_OUTPUT" == "--json" ]]; then
    jq -n \
        --argjson allowed "$ALLOWED" \
        --arg tier "$TIER" \
        --arg feature "$FEATURE" \
        --arg message "$MESSAGE" \
        --arg warning "$WARNING" \
        '{allowed: $allowed, tier: $tier, feature: $feature, message: $message, warning: $warning}'
else
    if [[ "$ALLOWED" == "true" ]]; then
        [[ -n "$WARNING" ]] && echo "Warning: $WARNING" >&2
        exit 0
    else
        echo "Error: $MESSAGE" >&2
        exit 1
    fi
fi
