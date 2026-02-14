#!/usr/bin/env bash
# Copyright (c) 2026 Gili Tzabari. All rights reserved.
#
# Licensed under the CAT Commercial License.
# See LICENSE.md in the project root for license terms.
#
# get-cleanup-survey.sh - Generate cleanup survey display
#
# USAGE: get-cleanup-survey.sh --project-dir <dir>
#
# OUTPUTS: Cleanup survey with worktrees, locks, branches (script output)
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
        *)
            echo "ERROR: $(basename "$0"): unknown argument: $1" >&2
            exit 1
            ;;
    esac
done

# Default to CLAUDE_PROJECT_DIR or current directory
if [[ -z "$PROJECT_DIR" ]]; then
    PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"
fi

echo "## Script Output: Cleanup Survey"
echo ""

# Gather worktrees
echo "### Worktrees"
echo ""
WORKTREES=$(git worktree list 2>/dev/null || echo "Unable to list worktrees")
if [[ -n "$WORKTREES" ]]; then
    echo '```'
    echo "$WORKTREES"
    echo '```'
else
    echo "No worktrees found."
fi
echo ""

# Gather locks
echo "### Task Locks"
echo ""
LOCK_DIR="$PROJECT_DIR/.claude/cat/locks"
if [[ -d "$LOCK_DIR" ]]; then
    LOCKS=$(ls -la "$LOCK_DIR" 2>/dev/null | grep -v "^total" | grep -v "^d" || true)
    if [[ -n "$LOCKS" ]]; then
        echo '```'
        echo "$LOCKS"
        echo '```'
    else
        echo "No locks found."
    fi
else
    echo "No lock directory found."
fi
echo ""

# Gather CAT branches
echo "### CAT Branches"
echo ""
CAT_BRANCHES=$(git branch -a 2>/dev/null | grep -E '[0-9]+\.[0-9]+-' || echo "")
if [[ -n "$CAT_BRANCHES" ]]; then
    echo '```'
    echo "$CAT_BRANCHES"
    echo '```'
else
    echo "No CAT task branches found."
fi
echo ""

# Stale remotes (branches older than 1 day)
echo "### Stale Remote Branches (>1 day old)"
echo ""
git fetch --prune 2>/dev/null || true
STALE=$(git for-each-ref --sort=-committerdate refs/remotes --format='%(refname:short) %(committerdate:relative) %(authorname)' 2>/dev/null | grep -E '[0-9]+\.[0-9]+-' | head -10 || echo "")
if [[ -n "$STALE" ]]; then
    echo '```'
    echo "$STALE"
    echo '```'
else
    echo "No stale remote branches found."
fi
echo ""

echo "---"
echo ""
echo "**Survey complete.** Review the items above and proceed with cleanup plan."
