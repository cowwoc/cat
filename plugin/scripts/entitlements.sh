#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# Entitlement resolver for CAT tier-based features
# Usage: entitlements.sh <tier> [feature]
#   With feature: returns 0 if entitled, 1 if not
#   Without feature: lists all entitled features

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TIERS_FILE="${SCRIPT_DIR}/../config/tiers.json"

if [[ ! -f "$TIERS_FILE" ]]; then
  echo "ERROR: Tier configuration not found: $TIERS_FILE" >&2
  exit 2
fi

# Get features for tier (including inherited)
# Defined early so it can be used by --required-tier
get_tier_features() {
  local tier="$1"
  local features=""

  # Get direct features
  features=$(jq -r ".tiers.${tier}.features // [] | .[]" "$TIERS_FILE" 2>/dev/null)

  # Get inherited tier
  local includes=$(jq -r ".tiers.${tier}.includes // empty" "$TIERS_FILE" 2>/dev/null)

  if [[ -n "$includes" ]]; then
    local inherited=$(get_tier_features "$includes")
    features="$features"$'\n'"$inherited"
  fi

  echo "$features" | sort -u | grep -v '^$'
}

TIER="${1:-}"
FEATURE="${2:-}"

if [[ -z "$TIER" ]]; then
  echo "Usage: entitlements.sh <tier> [feature]" >&2
  echo "       entitlements.sh --required-tier <feature>" >&2
  echo "Tiers: indie, team, enterprise" >&2
  exit 1
fi

# Handle --required-tier flag
if [[ "$TIER" == "--required-tier" ]]; then
  FEATURE="$2"
  if [[ -z "$FEATURE" ]]; then
    echo "Usage: entitlements.sh --required-tier <feature>" >&2
    exit 1
  fi

  # Check each tier from lowest to highest
  for check_tier in indie team enterprise; do
    if get_tier_features "$check_tier" | grep -qx "$FEATURE"; then
      echo "$check_tier"
      exit 0
    fi
  done

  echo "unknown"
  exit 1
fi

# Normalize tier to lowercase
TIER=$(echo "$TIER" | tr '[:upper:]' '[:lower:]')

# Check if tier is valid
if ! jq -e ".tiers.${TIER}" "$TIERS_FILE" > /dev/null 2>&1; then
  echo "ERROR: Unknown tier: $TIER" >&2
  echo "Valid tiers: $(jq -r '.tiers | keys | join(", ")' "$TIERS_FILE")" >&2
  exit 1
fi

# Get all features for the tier
ENTITLED_FEATURES=$(get_tier_features "$TIER")

if [[ -z "$FEATURE" ]]; then
  # List mode: output all entitled features
  echo "$ENTITLED_FEATURES"
else
  # Check mode: test if feature is entitled
  if echo "$ENTITLED_FEATURES" | grep -qx "$FEATURE"; then
    exit 0
  else
    exit 1
  fi
fi
