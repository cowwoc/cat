#!/usr/bin/env bash
#
# check-update-available.sh - Display update notice if newer CAT version available
#
# Runs at SessionStart after check-upgrade.sh. Non-blocking (always exit 0).
# Caches result for 24 hours to avoid repeated network requests.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../migrations/lib/utils.sh"

# Configuration
CACHE_DIR="${CLAUDE_PROJECT_DIR:-.}/.claude/cat/backups/update-check"
CACHE_FILE="${CACHE_DIR}/latest_version.json"
CACHE_MAX_AGE=$((24 * 60 * 60))  # 24 hours in seconds
GITHUB_API_URL="https://api.github.com/repos/cowwoc/cat/releases/latest"
TIMEOUT_SECONDS=5

# Create cache directory if needed
mkdir -p "$CACHE_DIR"

# Get current plugin version
CURRENT_VERSION=$(get_plugin_version)

# Check if cache is fresh
is_cache_fresh() {
    [[ -f "$CACHE_FILE" ]] || return 1

    local cache_time
    cache_time=$(stat -c %Y "$CACHE_FILE" 2>/dev/null || stat -f %m "$CACHE_FILE" 2>/dev/null || echo "0")
    local now
    now=$(date +%s)
    local age=$((now - cache_time))

    [[ $age -lt $CACHE_MAX_AGE ]]
}

# Fetch latest version from GitHub releases API
fetch_latest_version() {
    local response
    # Use timeout to ensure non-blocking
    response=$(curl -s --max-time "$TIMEOUT_SECONDS" "$GITHUB_API_URL" 2>/dev/null) || return 1

    # Parse tag_name from response (may be "v2.1.0" or "2.1.0")
    local version
    version=$(echo "$response" | jq -r '.tag_name // empty' 2>/dev/null) || return 1

    [[ -n "$version" ]] || return 1

    # Strip leading 'v' if present for consistent comparison
    version="${version#v}"
    echo "$version"
}

# Get latest version (from cache or network)
get_latest_version() {
    # Try cache first
    if is_cache_fresh; then
        jq -r '.version // empty' "$CACHE_FILE" 2>/dev/null && return 0
    fi

    # Fetch from network
    local latest
    latest=$(fetch_latest_version) || return 1

    # Update cache
    jq -n --arg v "$latest" --arg t "$(date +%s)" '{version: $v, checked: $t}' > "$CACHE_FILE" 2>/dev/null

    echo "$latest"
}

# Main logic
main() {
    # Get latest version (gracefully handle failures)
    local latest_version
    latest_version=$(get_latest_version 2>/dev/null) || {
        # Network/cache failure - silently exit (non-blocking)
        exit 0
    }

    # Compare versions
    local cmp
    cmp=$(version_compare "$CURRENT_VERSION" "$latest_version")

    # Only show notice if newer version available
    if [[ "$cmp" == "-1" ]]; then
        # Output to stderr (visible to user immediately)
        cat << EOF >&2

================================================================================
📦 CAT UPDATE AVAILABLE
================================================================================

Current version: $CURRENT_VERSION
Latest version:  $latest_version

Run: /plugin update cat

================================================================================

EOF

        # Also output to Claude context
        jq -n \
            --arg current "$CURRENT_VERSION" \
            --arg latest "$latest_version" \
            '{
                "hookSpecificOutput": {
                    "hookEventName": "SessionStart",
                    "additionalContext": ("CAT update available: " + $current + " → " + $latest + ". User has been notified.")
                }
            }'
    fi

    exit 0
}

main
