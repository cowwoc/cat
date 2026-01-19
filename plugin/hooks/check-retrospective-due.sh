#!/bin/bash
set -euo pipefail

# Check if retrospective is due (hybrid: time-based OR mistake count)
#
# Trigger: SessionStart hook
# Purpose: Remind about retrospectives when due based on time or mistake count

trap 'echo "ERROR in check-retrospective-due.sh at line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# Use CLAUDE_PROJECT_DIR for project files, CLAUDE_PLUGIN_ROOT for plugin files
# CLAUDE_PROJECT_DIR = absolute path to project root
# CLAUDE_PLUGIN_ROOT = absolute path to plugin installation

# Early exit if not in a CAT project (no .planning directory)
if [[ ! -d "${CLAUDE_PROJECT_DIR}/.planning" ]]; then
    exit 0
fi

RETRO_DIR="${CLAUDE_PROJECT_DIR}/.planning/retrospectives"
RETRO_FILE="$RETRO_DIR/retrospectives.json"
MISTAKES_FILE="$RETRO_DIR/mistakes.json"

# Early exit if retrospectives directory doesn't exist
if [[ ! -d "$RETRO_DIR" ]]; then
    exit 0
fi

# Default configuration
DEFAULT_TRIGGER_DAYS=14
DEFAULT_MISTAKE_THRESHOLD=10

# Read configuration and state using jq for reliable parsing
if [[ -f "$RETRO_FILE" ]]; then
    TRIGGER_DAYS=$(jq -r '.config.trigger_interval_days // empty' "$RETRO_FILE" 2>/dev/null)
    MISTAKE_THRESHOLD=$(jq -r '.config.mistake_count_threshold // empty' "$RETRO_FILE" 2>/dev/null)
    LAST_RETRO=$(jq -r '.last_retrospective // empty' "$RETRO_FILE" 2>/dev/null)
    MISTAKE_COUNT=$(jq -r '.mistake_count_since_last // 0' "$RETRO_FILE" 2>/dev/null)
else
    TRIGGER_DAYS=""
    MISTAKE_THRESHOLD=""
    LAST_RETRO=""
    MISTAKE_COUNT=0
fi

# Apply defaults
TRIGGER_DAYS="${TRIGGER_DAYS:-$DEFAULT_TRIGGER_DAYS}"
MISTAKE_THRESHOLD="${MISTAKE_THRESHOLD:-$DEFAULT_MISTAKE_THRESHOLD}"

# Check if retrospective is due
RETRO_DUE=false
TRIGGER_REASON=""

# Check 1: Time-based trigger
if [[ -z "$LAST_RETRO" || "$LAST_RETRO" == "null" ]]; then
    # No retrospective ever run - check if we have any mistakes logged
    if [[ -f "$MISTAKES_FILE" ]]; then
        TOTAL_MISTAKES=$(jq '.mistakes | length' "$MISTAKES_FILE" 2>/dev/null || echo "0")
        if [[ "$TOTAL_MISTAKES" -gt 0 ]]; then
            RETRO_DUE=true
            TRIGGER_REASON="First retrospective with $TOTAL_MISTAKES logged mistakes"
        fi
    fi
else
    # Calculate days since last retrospective
    LAST_RETRO_EPOCH=$(date -d "$LAST_RETRO" +%s 2>/dev/null || echo "0")
    NOW_EPOCH=$(date +%s)
    DAYS_SINCE=$(( (NOW_EPOCH - LAST_RETRO_EPOCH) / 86400 ))

    if [[ "$DAYS_SINCE" -ge "$TRIGGER_DAYS" ]]; then
        RETRO_DUE=true
        TRIGGER_REASON="$DAYS_SINCE days since last retrospective (threshold: $TRIGGER_DAYS)"
    fi
fi

# Check 2: Mistake count trigger
if [[ "$RETRO_DUE" == "false" && "$MISTAKE_COUNT" -ge "$MISTAKE_THRESHOLD" ]]; then
    RETRO_DUE=true
    TRIGGER_REASON="$MISTAKE_COUNT mistakes accumulated (threshold: $MISTAKE_THRESHOLD)"
fi

# If retrospective is due, output reminder
if [[ "$RETRO_DUE" == "true" ]]; then
    cat << EOF

================================================================================
📊 RETROSPECTIVE DUE
================================================================================

Trigger: $TRIGGER_REASON

A retrospective review is recommended to analyze accumulated mistakes and
identify recurring patterns that need systemic fixes.

SUGGESTED ACTION: Invoke the retrospective skill:

  Skill: retrospective

This will:
1. Aggregate all mistakes since last retrospective
2. Identify recurring patterns
3. Check effectiveness of previous action items
4. Generate new action items for systemic fixes

================================================================================

EOF
fi

exit 0
