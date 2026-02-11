#!/bin/bash

# Claude Code statusline script
# Displays git worktree, model name, session duration, and context usage

# Ensure UTF-8 encoding for proper emoji rendering
export LC_ALL=C.UTF-8
export LANG=C.UTF-8

# Extract git worktree name (directory name of the worktree root)
GIT_WORKTREE=$(basename "$(git rev-parse --show-toplevel 2>/dev/null)" 2>/dev/null || echo "N/A")

# Read JSON input from stdin
input=$(cat)

# Extract duration using simple string manipulation (faster than jq)
DURATION_MS=0
if [[ "$input" =~ \"total_duration_ms\"[[:space:]]*:[[:space:]]*([0-9]+) ]]; then
    DURATION_MS="${BASH_REMATCH[1]}"
fi
DURATION_SECONDS=$((DURATION_MS / 1000))
HOURS=$((DURATION_SECONDS / 3600))
MINUTES=$(((DURATION_SECONDS % 3600) / 60))

# Extract model name
MODEL_NAME="Claude"
if [[ "$input" =~ \"display_name\"[[:space:]]*:[[:space:]]*\"([^\"]+)\" ]]; then
    MODEL_NAME="${BASH_REMATCH[1]}"
fi

# Extract session ID
SESSION_ID="N/A"
if [[ "$input" =~ \"session_id\"[[:space:]]*:[[:space:]]*\"([^\"]+)\" ]]; then
    SESSION_ID="${BASH_REMATCH[1]}"
fi

# Extract context usage percentage (pre-calculated by Claude Code)
CONTEXT_PCT=0
if [[ "$input" =~ \"used_percentage\"[[:space:]]*:[[:space:]]*([0-9]+) ]]; then
    CONTEXT_PCT="${BASH_REMATCH[1]}"
    (( CONTEXT_PCT > 100 )) && CONTEXT_PCT=100
fi

# Build progress bar (20 chars wide)
BAR_WIDTH=20
FILLED=$(( (CONTEXT_PCT * BAR_WIDTH) / 100 ))
EMPTY=$(( BAR_WIDTH - FILLED ))

# Calculate color: green (0%) -> yellow (50%) -> red (100%)
# Green component: 255 at 0%, 0 at 100%
# Red component: 0 at 0%, 255 at 100%
if (( CONTEXT_PCT <= 50 )); then
    # Green to Yellow: red increases 0->255, green stays 255
    RED=$(( (CONTEXT_PCT * 255) / 50 ))
    GREEN=255
else
    # Yellow to Red: red stays 255, green decreases 255->0
    RED=255
    GREEN=$(( ((100 - CONTEXT_PCT) * 255) / 50 ))
fi
(( RED > 255 )) && RED=255
(( GREEN > 255 )) && GREEN=255
(( RED < 0 )) && RED=0
(( GREEN < 0 )) && GREEN=0

BAR_COLOR="\033[38;2;${RED};${GREEN};0m"

# Build the bar string
BAR_STR=""
for (( i=0; i<FILLED; i++ )); do BAR_STR+="█"; done
for (( i=0; i<EMPTY; i++ )); do BAR_STR+="░"; done

# Colors for statusline components
WORKTREE_COLOR="\033[38;2;255;255;255m"  # Bright White
MODEL_COLOR="\033[38;2;220;150;9m"       # Warm Gold
TIME_COLOR="\033[38;2;255;127;80m"       # Coral
SESSION_COLOR="\033[38;2;147;112;219m"   # Medium Purple
DIM="\033[2m"
RESET="\033[0m"

# Set Windows Terminal tab title to git worktree name
if [[ "$GIT_WORKTREE" != "N/A" && -n "$GIT_WORKTREE" ]]; then
    printf "\033]0;%s\007" "$GIT_WORKTREE"
else
    printf "\033]0;\007"
fi

# Generate and output the statusline
printf '🌿 %b%s%b 🤖 %b%s%b ⏰ %b%02d:%02d%b 🆔 %b%s%b 📊 %b%s%b %b%3d%%%b\n' \
    "$WORKTREE_COLOR" "$GIT_WORKTREE" "$RESET" \
    "$MODEL_COLOR" "$MODEL_NAME" "$RESET" \
    "$TIME_COLOR" "$HOURS" "$MINUTES" "$RESET" \
    "$SESSION_COLOR" "$SESSION_ID" "$RESET" \
    "$BAR_COLOR" "$BAR_STR" "$RESET" \
    "$BAR_COLOR" "$CONTEXT_PCT" "$RESET"
