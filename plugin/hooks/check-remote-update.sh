#!/usr/bin/env bash
# Check for CAT plugin updates from npm registry (non-blocking)

set -euo pipefail

# Get plugin info
PLUGIN_DIR="${CLAUDE_PLUGIN_ROOT:-$(dirname "$(dirname "$0")")}"
PLUGIN_JSON="$PLUGIN_DIR/plugin.json"

# Current version
CURRENT_VERSION=$(jq -r '.version // "0.0"' "$PLUGIN_JSON" 2>/dev/null || echo "0.0")

# Cache file (in user's home config)
CACHE_DIR="${HOME}/.config/claude-code-cat"
CACHE_FILE="$CACHE_DIR/update-check-cache.json"
CACHE_TTL=86400  # 24 hours in seconds

# Output helper
output_message() {
    local msg="$1"
    jq -n --arg msg "$msg" '{
        "hookSpecificOutput": {
            "hookEventName": "SessionStart",
            "additionalContext": $msg
        }
    }'
}

# Check if cache is valid
is_cache_valid() {
    if [[ ! -f "$CACHE_FILE" ]]; then
        return 1
    fi

    local cache_time
    cache_time=$(jq -r '.timestamp // 0' "$CACHE_FILE" 2>/dev/null || echo "0")
    local current_time
    current_time=$(date +%s)

    if (( current_time - cache_time < CACHE_TTL )); then
        return 0
    fi
    return 1
}

# Get cached version
get_cached_version() {
    jq -r '.latest_version // ""' "$CACHE_FILE" 2>/dev/null || echo ""
}

# Save to cache
save_cache() {
    local latest="$1"
    mkdir -p "$CACHE_DIR"
    jq -n --arg ver "$latest" --arg ts "$(date +%s)" '{
        "latest_version": $ver,
        "timestamp": ($ts | tonumber),
        "checked_at": (now | strftime("%Y-%m-%d %H:%M:%S"))
    }' > "$CACHE_FILE"
}

# Compare versions (returns 0 if v1 < v2)
version_lt() {
    local v1="$1" v2="$2"
    # Use sort -V for semantic version comparison
    [[ "$(printf '%s\n%s' "$v1" "$v2" | sort -V | head -1)" == "$v1" ]] && [[ "$v1" != "$v2" ]]
}

# Fetch latest version from npm (with timeout)
fetch_latest_version() {
    # Try npm registry first (claude-code-cat package)
    local npm_response
    npm_response=$(curl -sf --max-time 3 "https://registry.npmjs.org/claude-code-cat/latest" 2>/dev/null || echo "")

    if [[ -n "$npm_response" ]]; then
        local version
        version=$(echo "$npm_response" | jq -r '.version // ""' 2>/dev/null || echo "")
        if [[ -n "$version" ]]; then
            echo "$version"
            return 0
        fi
    fi

    # Fallback: GitHub releases API
    local gh_response
    gh_response=$(curl -sf --max-time 3 "https://api.github.com/repos/anthropics/claude-code-cat/releases/latest" 2>/dev/null || echo "")

    if [[ -n "$gh_response" ]]; then
        local version
        version=$(echo "$gh_response" | jq -r '.tag_name // ""' 2>/dev/null | sed 's/^v//')
        if [[ -n "$version" ]]; then
            echo "$version"
            return 0
        fi
    fi

    return 1
}

# Main logic
main() {
    local latest_version=""

    # Check cache first
    if is_cache_valid; then
        latest_version=$(get_cached_version)
    else
        # Fetch in background to avoid blocking startup
        latest_version=$(fetch_latest_version) || latest_version=""

        if [[ -n "$latest_version" ]]; then
            save_cache "$latest_version"
        fi
    fi

    # Compare and notify if update available
    if [[ -n "$latest_version" ]] && version_lt "$CURRENT_VERSION" "$latest_version"; then
        output_message "CAT update available: $CURRENT_VERSION -> $latest_version (npm install -g claude-code-cat)"
    else
        # Output empty success (hook requires JSON output)
        jq -n '{
            "hookSpecificOutput": {
                "hookEventName": "SessionStart"
            }
        }'
    fi
}

main
