#!/usr/bin/env bash
# CAT statusline script for Claude Code
# Reads JSON from stdin and produces a formatted statusline with:
# - Git worktree/branch info
# - Model name
# - Session duration
# - Session ID
# - Color-coded context usage bar

set -euo pipefail

# ANSI color codes
RESET='\033[0m'
BOLD='\033[1m'
DIM='\033[2m'
GREEN='\033[32m'
YELLOW='\033[33m'
RED='\033[31m'
CYAN='\033[36m'
GRAY='\033[90m'

# Check if jq is available (fail-fast for missing dependency)
if ! command -v jq >/dev/null 2>&1; then
  echo "ERROR: jq is required but not installed" >&2
  exit 0  # Graceful degradation for statusline
fi

# Read JSON from stdin
if ! JSON_INPUT=$(cat); then
  echo "WARNING: Failed to read stdin for statusline" >&2
  exit 0  # Graceful degradation for statusline
fi

# Extract all fields in a single jq call to avoid duplication
read -r DISPLAY_NAME SESSION_ID TOTAL_DURATION_MS USED_PERCENTAGE < <(
  echo "$JSON_INPUT" | jq -r '[
    .display_name // "unknown",
    .session_id // "unknown",
    .total_duration_ms // 0,
    .used_percentage // 0
  ] | @tsv'
)

# Validate and sanitize TOTAL_DURATION_MS (must be non-negative integer)
if [[ ! "$TOTAL_DURATION_MS" =~ ^[0-9]+$ ]]; then
  TOTAL_DURATION_MS=0
fi

# Validate and sanitize USED_PERCENTAGE (clamp to 0-100, ensure integer)
if [[ ! "$USED_PERCENTAGE" =~ ^[0-9]+$ ]]; then
  USED_PERCENTAGE=0
fi
if ((USED_PERCENTAGE > 100)); then
  USED_PERCENTAGE=100
fi

# Get git worktree info (gracefully handle non-git directories)
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  BRANCH=$(git branch --show-current 2>/dev/null || echo "detached")

  # Check if we're in a worktree
  WORKTREE_DIR=$(git rev-parse --git-dir 2>/dev/null || echo "")
  if [[ "$WORKTREE_DIR" == *"worktrees"* ]]; then
    # Extract worktree name from path
    WORKTREE_NAME=$(basename "$(git rev-parse --show-toplevel 2>/dev/null)" 2>/dev/null || echo "$BRANCH")
    GIT_INFO="$WORKTREE_NAME"
  else
    GIT_INFO="$BRANCH"
  fi
else
  GIT_INFO="N/A"
fi

# Convert duration from ms to human-readable format
format_duration() {
  local ms=$1
  local seconds=$((ms / 1000))
  local minutes=$((seconds / 60))
  local hours=$((minutes / 60))

  if ((hours > 0)); then
    echo "${hours}h$((minutes % 60))m"
  elif ((minutes > 0)); then
    echo "${minutes}m$((seconds % 60))s"
  else
    echo "${seconds}s"
  fi
}

DURATION=$(format_duration "$TOTAL_DURATION_MS")

# Session ID (first 8 chars)
SHORT_SESSION_ID="${SESSION_ID:0:8}"

# Color-coded context usage bar
get_usage_color() {
  local usage=$1
  if ((usage > 80)); then
    echo "$RED"
  elif ((usage > 50)); then
    echo "$YELLOW"
  else
    echo "$GREEN"
  fi
}

USAGE_COLOR=$(get_usage_color "$USED_PERCENTAGE")

# Create usage bar (10 segments)
create_usage_bar() {
  local percentage=$1
  local filled=$((percentage / 10))
  local bar=""

  for ((i=0; i<10; i++)); do
    if ((i < filled)); then
      bar+="█"
    else
      bar+="░"
    fi
  done

  echo "$bar"
}

USAGE_BAR=$(create_usage_bar "$USED_PERCENTAGE")

# Assemble and output the statusline
echo -e "${CYAN}${BOLD}${GIT_INFO}${RESET} ${GRAY}|${RESET} ${DIM}${DISPLAY_NAME}${RESET} ${GRAY}|${RESET} ${DIM}⏱ ${DURATION}${RESET} ${GRAY}|${RESET} ${DIM}📋 ${SHORT_SESSION_ID}${RESET} ${GRAY}|${RESET} ${USAGE_COLOR}${USAGE_BAR} ${USED_PERCENTAGE}%${RESET}"

exit 0
