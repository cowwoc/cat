#!/usr/bin/env bash
# get-progress-banner.sh - Generate properly aligned progress banners
#
# USAGE: get-progress-banner.sh [task-id] [--phase <phase>] [--all-phases]
#
# Arguments:
#   task-id      Issue ID to display (positional, or auto-discover if omitted)
#   --phase      Phase to render (preparing|executing|reviewing|merging)
#   --all-phases Generate all phase banners (default if no --phase)
#   --project-dir Directory containing .claude/cat/ (for auto-discovery)
#   --session-id  Session ID for task locking (for auto-discovery)
#
# OUTPUTS: Pre-rendered banner(s) with correct alignment
#
# This script is designed to be called via silent preprocessing (!`command`).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Get emoji widths library
LIB_DIR="$SCRIPT_DIR/lib"
if [[ ! -f "$LIB_DIR/emoji_widths.py" ]]; then
    echo "Error: emoji_widths.py not found at $LIB_DIR" >&2
    exit 1
fi

# Show usage
usage() {
    cat << 'EOF'
Usage: get-progress-banner.sh [task-id] [--phase <phase>] [--all-phases]

Arguments:
  task-id        Issue ID to display (positional, auto-discover if omitted)
  --phase        Phase to render (preparing|executing|reviewing|merging)
  --all-phases   Generate all phase banners (default)
  --project-dir  Project directory for auto-discovery
  --session-id   Session ID for task locking

Examples:
  get-progress-banner.sh 2.1-migrate-progress-banners
  get-progress-banner.sh 2.1-migrate-progress-banners --phase preparing
  get-progress-banner.sh --project-dir /workspace --session-id abc123
EOF
}

# Parse arguments
TASK_ID=""
PHASE=""
ALL_PHASES=false
PROJECT_DIR=""
SESSION_ID=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --phase)
            PHASE="$2"
            shift 2
            ;;
        --all-phases)
            ALL_PHASES=true
            shift
            ;;
        --project-dir)
            PROJECT_DIR="$2"
            shift 2
            ;;
        --session-id)
            SESSION_ID="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        --*)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 1
            ;;
        *)
            # Positional argument = task ID
            if [[ -z "$TASK_ID" ]]; then
                TASK_ID="$1"
            fi
            shift
            ;;
    esac
done

# If no task ID provided, try to auto-discover
if [[ -z "$TASK_ID" ]]; then
    if [[ -n "$PROJECT_DIR" && -n "$SESSION_ID" ]]; then
        # Try to discover next task
        DISCOVERY_SCRIPT="$SCRIPT_DIR/get-available-issues.sh"
        if [[ -x "$DISCOVERY_SCRIPT" ]]; then
            RESULT=$("$DISCOVERY_SCRIPT" --session-id "$SESSION_ID" 2>/dev/null) || true
            if echo "$RESULT" | jq -e '.status == "found"' > /dev/null 2>&1; then
                MAJOR=$(echo "$RESULT" | jq -r '.major')
                MINOR=$(echo "$RESULT" | jq -r '.minor')
                ISSUE_NAME=$(echo "$RESULT" | jq -r '.issue_name')
                TASK_ID="${MAJOR}.${MINOR}-${ISSUE_NAME}"
            fi
        fi
    fi
fi

# If still no task ID, output placeholder message
if [[ -z "$TASK_ID" ]]; then
    echo "## Progress Banners"
    echo ""
    echo "Task will be identified during execution. Banners will be displayed inline."
    exit 0
fi

# Default to all phases if no specific phase requested
if [[ -z "$PHASE" ]]; then
    ALL_PHASES=true
fi

# Phase symbols
# ○ Pending | ● Complete | ◉ Active | ✗ Failed
PENDING="○"
COMPLETE="●"
ACTIVE="◉"

# Build banner using Python for accurate width calculation
build_banner() {
    local task_id="$1"
    local p1="$2"  # Preparing symbol
    local p2="$3"  # Executing symbol
    local p3="$4"  # Reviewing symbol
    local p4="$5"  # Merging symbol

    python3 << PYTHON_EOF
import sys
sys.path.insert(0, '$LIB_DIR')
from emoji_widths import EmojiWidths

ew = EmojiWidths()

task_id = "$task_id"
p1, p2, p3, p4 = "$p1", "$p2", "$p3", "$p4"

# Phase content without border characters
phase_content = f"  {p1} Preparing ────── {p2} Executing ────── {p3} Reviewing ────── {p4} Merging "
phase_width = ew.display_width(phase_content)

# Header content: "─ 🐱 " + task_id + " "
header_prefix = "─ 🐱 "
header_content = header_prefix + task_id + " "
header_width = ew.display_width(header_content)

# Box width is determined by the wider of header or phase content
inner_width = max(header_width, phase_width)

# Build top border: ┌ + header_content + dashes + ┐
top_dashes = "─" * (inner_width - header_width)
top_line = "┌" + header_content + top_dashes + "┐"

# Build middle line: │ + phase_content + padding + │
phase_padding = " " * (inner_width - phase_width)
middle_line = "│" + phase_content + phase_padding + "│"

# Build bottom border: └ + dashes + ┘
bottom_line = "└" + "─" * inner_width + "┘"

print(top_line)
print(middle_line)
print(bottom_line)
PYTHON_EOF
}

# Generate banners based on mode
if [[ "$ALL_PHASES" == "true" ]]; then
    echo "**Preparing phase** (◉ on Preparing):"
    echo '```'
    build_banner "$TASK_ID" "$ACTIVE" "$PENDING" "$PENDING" "$PENDING"
    echo '```'
    echo ""
    echo "**Executing phase** (● ◉ pattern):"
    echo '```'
    build_banner "$TASK_ID" "$COMPLETE" "$ACTIVE" "$PENDING" "$PENDING"
    echo '```'
    echo ""
    echo "**Reviewing phase** (● ● ◉ pattern):"
    echo '```'
    build_banner "$TASK_ID" "$COMPLETE" "$COMPLETE" "$ACTIVE" "$PENDING"
    echo '```'
    echo ""
    echo "**Merging phase** (● ● ● ◉ pattern):"
    echo '```'
    build_banner "$TASK_ID" "$COMPLETE" "$COMPLETE" "$COMPLETE" "$ACTIVE"
    echo '```'
else
    case "$PHASE" in
        preparing)
            build_banner "$TASK_ID" "$ACTIVE" "$PENDING" "$PENDING" "$PENDING"
            ;;
        executing)
            build_banner "$TASK_ID" "$COMPLETE" "$ACTIVE" "$PENDING" "$PENDING"
            ;;
        reviewing)
            build_banner "$TASK_ID" "$COMPLETE" "$COMPLETE" "$ACTIVE" "$PENDING"
            ;;
        merging)
            build_banner "$TASK_ID" "$COMPLETE" "$COMPLETE" "$COMPLETE" "$ACTIVE"
            ;;
        *)
            echo "Error: Unknown phase '$PHASE'. Valid: preparing, executing, reviewing, merging" >&2
            exit 1
            ;;
    esac
fi
