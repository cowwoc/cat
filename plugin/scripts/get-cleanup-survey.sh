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
# OUTPUTS: Cleanup survey with worktrees, locks, branches (skill output)
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

echo "## Skill Output: Cleanup Survey"
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

# Detect stale in-progress issues (in-progress status but no worktree/lock/branch)
echo "### Stale In-Progress Issues"
echo ""
ISSUES_DIR="$PROJECT_DIR/.claude/cat/issues"
STALE_ISSUES=""
if [[ -d "$ISSUES_DIR" ]]; then
    while IFS= read -r state_file; do
        # Skip version-level STATE.md files (parent dir starts with "v")
        issue_dir=$(dirname "$state_file")
        issue_name=$(basename "$issue_dir")
        [[ "$issue_name" =~ ^v[0-9] ]] && continue

        status=$(grep -oP '\*\*Status:\*\* \K.*' "$state_file" 2>/dev/null | tr -d ' ')
        [[ "$status" != "in-progress" ]] && continue

        # Extract version from path: .../vMAJOR/vMAJOR.MINOR/issue-name/STATE.md
        version_dir=$(dirname "$issue_dir")
        version=$(basename "$version_dir")
        major_dir=$(dirname "$version_dir")
        major=$(basename "$major_dir")

        # Derive the issue ID (e.g., 2.1-issue-name)
        major_num="${major#v}"
        minor_num="${version#v${major_num}.}"
        issue_id="${major_num}.${minor_num}-${issue_name}"

        # Check for corresponding worktree, lock, or branch
        has_worktree=false
        has_lock=false
        has_branch=false

        [[ -d "$PROJECT_DIR/.claude/cat/worktrees/$issue_id" ]] && has_worktree=true
        [[ -f "$PROJECT_DIR/.claude/cat/locks/${issue_id}.lock" ]] && has_lock=true
        git show-ref --verify --quiet "refs/heads/$issue_id" 2>/dev/null && has_branch=true

        if [[ "$has_worktree" == "false" && "$has_lock" == "false" && "$has_branch" == "false" ]]; then
            STALE_ISSUES+="  $issue_id (no worktree, no lock, no branch)"$'\n'
        fi
    done < <(find "$ISSUES_DIR" -name "STATE.md" 2>/dev/null)
fi

if [[ -n "$STALE_ISSUES" ]]; then
    echo '```'
    echo -n "$STALE_ISSUES"
    echo '```'
    echo ""
    echo "These issues have **Status: in-progress** but no active worktree, lock, or branch."
    echo "They were likely set to in-progress by a session that crashed or was cancelled."
else
    echo "No stale in-progress issues found."
fi
echo ""

echo "---"
echo ""
echo "**Survey complete.** Review the items above and proceed with cleanup plan."
