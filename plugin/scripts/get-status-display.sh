#!/usr/bin/env bash
# get-status-display.sh - Generate project status display
#
# USAGE: get-status-display.sh [--project-dir <dir>]
#
# Arguments:
#   --project-dir  Directory containing .claude/cat/ (defaults to cwd)
#
# OUTPUTS: Pre-rendered status display box with correct alignment
#
# This script is designed to be called via silent preprocessing (!`command`).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Parse arguments
PROJECT_DIR=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --project-dir)
            PROJECT_DIR="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: get-status-display.sh [--project-dir <dir>]"
            exit 0
            ;;
        *)
            shift
            ;;
    esac
done

# Default to current directory
if [[ -z "$PROJECT_DIR" ]]; then
    PROJECT_DIR="$(pwd)"
fi

# Verify CAT structure exists
CAT_DIR="$PROJECT_DIR/.claude/cat"
if [[ ! -d "$CAT_DIR" ]]; then
    echo "No CAT project found. Run /cat:init to initialize."
    exit 0
fi

# Call Python script to generate status display
# The heavy computation is in Python for accurate emoji width handling
python3 "${SCRIPT_DIR}/get-status-display.py" --project-dir "$PROJECT_DIR"
