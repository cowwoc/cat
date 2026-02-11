#!/usr/bin/env bash
# get-render-diff.sh - Generate rendered diff output
#
# USAGE: get-render-diff.sh [--base-branch <branch>] [--project-dir <dir>]
#
# OUTPUTS: 4-column diff table (script output)
#
# This script is designed to be called via silent preprocessing (!`command`).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Parse arguments
PROJECT_DIR=""
BASE_BRANCH=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --project-dir)
            PROJECT_DIR="$2"
            shift 2
            ;;
        --base-branch)
            BASE_BRANCH="$2"
            shift 2
            ;;
        *)
            shift
            ;;
    esac
done

# Default to current directory
if [[ -z "$PROJECT_DIR" ]]; then
    PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"
fi

cd "$PROJECT_DIR"

# Auto-detect base branch if not provided
if [[ -z "$BASE_BRANCH" ]]; then
    # Try to extract from worktree name
    WORKTREE_NAME=$(basename "$(pwd)")
    if [[ "$(dirname "$(pwd)")" == *"worktrees"* ]]; then
        # Extract version from worktree name (e.g., "2.0-issue-name" -> "v2.0")
        if [[ "$WORKTREE_NAME" =~ ^([0-9]+\.[0-9]+)- ]]; then
            BASE_BRANCH="v${BASH_REMATCH[1]}"
        fi
    fi

    # Try to extract from current branch
    if [[ -z "$BASE_BRANCH" ]]; then
        CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")
        if [[ "$CURRENT_BRANCH" =~ ^([0-9]+\.[0-9]+)- ]]; then
            BASE_BRANCH="v${BASH_REMATCH[1]}"
        elif [[ "$CURRENT_BRANCH" =~ ^v[0-9]+\.[0-9]+$ ]]; then
            BASE_BRANCH="main"
        fi
    fi

    # Fail-fast: base branch must be determinable
    if [[ -z "$BASE_BRANCH" ]]; then
        echo "ERROR: Could not determine base branch from worktree or current branch" >&2
        echo "Solution: Provide BASE_BRANCH as first argument or run from a properly configured worktree" >&2
        exit 1
    fi
fi

# Check if base branch exists
if ! git rev-parse --verify "$BASE_BRANCH" >/dev/null 2>&1; then
    # Try with origin/ prefix
    if git rev-parse --verify "origin/$BASE_BRANCH" >/dev/null 2>&1; then
        BASE_BRANCH="origin/$BASE_BRANCH"
    else
        echo "## Rendered Diff"
        echo ""
        echo "**Error:** Base branch '$BASE_BRANCH' not found."
        echo ""
        echo "Unable to generate diff. Check that the base branch exists."
        exit 0
    fi
fi

# Get changed files
CHANGED_FILES=$(git diff --name-only "$BASE_BRANCH..HEAD" 2>/dev/null || echo "")
if [[ -z "$CHANGED_FILES" ]]; then
    echo "## Rendered Diff"
    echo ""
    echo "**No changes** detected between $BASE_BRANCH and HEAD."
    exit 0
fi

# Get stats
STATS=$(git diff --stat "$BASE_BRANCH..HEAD" 2>/dev/null | tail -1 || echo "")
FILES_CHANGED=$(echo "$STATS" | grep -oE '[0-9]+ files? changed' | grep -oE '[0-9]+' || echo "0")
INSERTIONS=$(echo "$STATS" | grep -oE '[0-9]+ insertions?' | grep -oE '[0-9]+' || echo "0")
DELETIONS=$(echo "$STATS" | grep -oE '[0-9]+ deletions?' | grep -oE '[0-9]+' || echo "0")

echo "## Rendered Diff"
echo ""
echo "### Summary"
echo "- **Base branch:** $BASE_BRANCH"
echo "- **Files changed:** $FILES_CHANGED"
echo "- **Insertions:** +$INSERTIONS"
echo "- **Deletions:** -$DELETIONS"
echo ""

echo "### Changed Files"
echo "$CHANGED_FILES" | head -20 | while read -r f; do
    echo "  - $f"
done
FILE_COUNT=$(echo "$CHANGED_FILES" | wc -l)
if [[ $FILE_COUNT -gt 20 ]]; then
    echo "  ... and $((FILE_COUNT - 20)) more files"
fi
echo ""

echo "### Diff (4-column format)"
echo ""

# Generate rendered diff using Java
# hook.sh is the canonical bridge between bash scripts and Java handlers.
# It locates the jlink runtime and invokes the class via the Java module system.
# Pattern: hook.sh <module.ClassName> invokes the class's main() method.
HOOK_SCRIPT="$SCRIPT_DIR/../hooks/hook.sh"
if [[ -f "$HOOK_SCRIPT" ]]; then
    "$HOOK_SCRIPT" skills.GetRenderDiffOutput 2>/dev/null || {
        echo "**Error:** Failed to render diff. Showing raw stats instead."
        echo ""
        git diff --stat "$BASE_BRANCH..HEAD" 2>/dev/null || echo "(unable to get diff stats)"
    }
else
    echo "**Error:** hook.sh not found at $HOOK_SCRIPT"
    echo ""
    git diff --stat "$BASE_BRANCH..HEAD" 2>/dev/null || echo "(unable to get diff stats)"
fi

echo ""
echo "---"
echo ""
echo "**INSTRUCTION**: Output this diff directly. Do NOT wrap in code blocks."
