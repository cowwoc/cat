#!/usr/bin/env bash
# precompute-config-display.sh - Generate properly aligned config display box
#
# USAGE: precompute-config-display.sh <project-dir>
#
# Arguments:
#   project-dir    Project root directory (contains .claude/cat/) - REQUIRED
#
# OUTPUTS: Pre-computed box display with correct alignment

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_SCRIPT="$SCRIPT_DIR/build-box-lines.py"

# Show usage
usage() {
    cat << 'EOF'
Usage: precompute-config-display.sh <project-dir>

Arguments:
  project-dir    Project root directory (contains .claude/cat/) - REQUIRED

Outputs:
  Pre-computed box display with correct alignment for config settings
EOF
}

# Validate arguments
if [[ $# -lt 1 ]] || [[ "$1" == "-h" ]] || [[ "$1" == "--help" ]]; then
    usage
    exit 0
fi

PROJECT_DIR="$1"

if [[ ! -d "$PROJECT_DIR/.claude/cat" ]]; then
    echo "Error: Not a CAT project: '$PROJECT_DIR' (no .claude/cat directory)" >&2
    exit 1
fi

CONFIG_PATH="$PROJECT_DIR/.claude/cat/cat-config.json"
if [[ ! -f "$CONFIG_PATH" ]]; then
    echo "Error: Cannot find cat-config.json at $CONFIG_PATH" >&2
    exit 1
fi

# Check Python script exists
if [[ ! -f "$PYTHON_SCRIPT" ]]; then
    echo "Error: build-box-lines.py not found at $PYTHON_SCRIPT" >&2
    exit 1
fi

# Read config values with safe defaults
read_config() {
    local key="$1"
    local default="$2"
    jq -r --arg k "$key" --arg d "$default" '.[$k] // $d' "$CONFIG_PATH" 2>/dev/null || echo "$default"
}

TRUST=$(read_config "trust" "medium")
VERIFY=$(read_config "verify" "changed")
CURIOSITY=$(read_config "curiosity" "low")
PATIENCE=$(read_config "patience" "high")
AUTO_REMOVE=$(jq -r '.autoRemoveWorktrees // true' "$CONFIG_PATH" 2>/dev/null || echo "true")
TERMINAL_WIDTH=$(read_config "terminalWidth" "120")
COMPLETION_WORKFLOW=$(read_config "completionWorkflow" "merge")

# Convert autoRemoveWorktrees to display string
if [[ "$AUTO_REMOVE" == "true" ]]; then
    CLEANUP_DISPLAY="Auto-remove"
else
    CLEANUP_DISPLAY="Keep"
fi

# Convert completionWorkflow to display string
if [[ "$COMPLETION_WORKFLOW" == "pr" ]]; then
    WORKFLOW_DISPLAY="Pull Request"
else
    WORKFLOW_DISPLAY="Merge"
fi

# Build content lines for the settings box
# Using format that matches config.md display WITH EMOJIS
CONTENT_LINES=(
    ""
    "  🤝 Trust: $TRUST"
    "  ✅ Verify: $VERIFY"
    "  🔍 Curiosity: $CURIOSITY"
    "  ⏳ Patience: $PATIENCE"
    "  🧹 Cleanup: $CLEANUP_DISPLAY"
    ""
)

# Compute box lines using Python script
# The script handles emoji width correctly
BOX_JSON=$(printf '%s\n' "${CONTENT_LINES[@]}" | python3 "$PYTHON_SCRIPT" --format json 2>/dev/null) || {
    echo "Error: Failed to compute box lines" >&2
    exit 1
}

# Extract values from JSON
MAX_WIDTH=$(echo "$BOX_JSON" | jq -r '.max_content_width')
TOP=$(echo "$BOX_JSON" | jq -r '.top')
BOTTOM=$(echo "$BOX_JSON" | jq -r '.bottom')

# Build the header line with title
# Format: ╭─── SETTINGS ───...───╮
# The header needs to match the same total width as content lines
# Content line = "│ " + content + " │" = 4 chars + max_width
# Header line = "╭─── " (5) + title + " " (1) + dashes + "╮" (1)

TITLE="⚙️ CURRENT SETTINGS"
# Calculate display width of title (emoji=2, others=1)
# ⚙️ = 2, space = 1, "CURRENT SETTINGS" = 16, total = 19
TITLE_DISPLAY_WIDTH=19

# Total inner width = max_width + 2 (for the spaces in "│ content │")
INNER_WIDTH=$((MAX_WIDTH + 2))

# Header format: ╭─── TITLE dashes╮
# After "╭───" we have " TITLE " then dashes then "╮"
# "╭───" = 4 display chars, " " + TITLE + " " = TITLE_DISPLAY_WIDTH + 2, dashes, "╮" = 1
# So: 4 + TITLE_DISPLAY_WIDTH + 2 + DASHES + 1 = INNER_WIDTH + 2
# DASHES = INNER_WIDTH + 2 - 4 - TITLE_DISPLAY_WIDTH - 2 - 1 = INNER_WIDTH - TITLE_DISPLAY_WIDTH - 5
DASH_COUNT=$((INNER_WIDTH - TITLE_DISPLAY_WIDTH - 5))
if [[ $DASH_COUNT -lt 0 ]]; then DASH_COUNT=0; fi

DASHES=$(printf '%0.s─' $(seq 1 $DASH_COUNT 2>/dev/null) || true)
HEADER="╭─── $TITLE $DASHES╮"

# Output the complete box
echo "$HEADER"
echo "$BOX_JSON" | jq -r '.lines[]'
echo "$BOTTOM"
