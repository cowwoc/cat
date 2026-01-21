#!/bin/bash
# PostToolUse hook: Verify commit type after git commit completes
# Defense-in-depth for A008 - catches commits that slip through PreToolUse validation
#
# This is a WARNING hook (does not block) since the commit already happened.
# Warns user to amend if wrong commit type was used.

set -eo pipefail

# Source JSON libraries
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/json-parser.sh"
source "${SCRIPT_DIR}/lib/json-output.sh"

# Initialize as Bash hook
if ! init_bash_hook; then
    echo '{}'
    exit 0
fi

COMMAND="$HOOK_COMMAND"

# Only check git commit commands that completed
if [[ "$COMMAND" != *"git commit"* ]]; then
    echo '{}'
    exit 0
fi

# Skip if it was an amend (user is already fixing)
if [[ "$COMMAND" == *"--amend"* ]]; then
    echo '{}'
    exit 0
fi

# Get the most recent commit message and files
COMMIT_MSG=$(git log -1 --format=%B 2>/dev/null || echo "")
COMMIT_FILES=$(git diff-tree --no-commit-id --name-only -r HEAD 2>/dev/null || echo "")

if [[ -z "$COMMIT_MSG" ]]; then
    echo '{}'
    exit 0
fi

# Extract the commit type
TYPE=$(echo "$COMMIT_MSG" | head -1 | grep -oE "^[a-z]+" || echo "")

# Check for docs: used on Claude-facing files
if [[ "$TYPE" == "docs" ]]; then
    CLAUDE_FACING_PATTERNS=(
        "CLAUDE.md"
        ".claude/"
        "hooks/"
        "skills/"
        "workflows/"
        "commands/"
        "retrospectives/"
        "mistakes.json"
    )

    for pattern in "${CLAUDE_FACING_PATTERNS[@]}"; do
        if echo "$COMMIT_FILES" | grep -q "$pattern"; then
            COMMIT_HASH=$(git log -1 --format=%h)
            echo "" >&2
            echo "⚠️  POST-COMMIT WARNING: 'docs:' used for Claude-facing file" >&2
            echo "" >&2
            echo "Commit $COMMIT_HASH contains Claude-facing files (matched: $pattern)" >&2
            echo "Claude-facing files should use 'config:', not 'docs:'" >&2
            echo "" >&2
            echo "Rule (M089): docs: = user-facing, config: = Claude-facing" >&2
            echo "" >&2
            echo "TO FIX: git commit --amend" >&2
            echo "  Then change 'docs:' to 'config:' in the commit message" >&2
            echo "" >&2

            output_hook_warning "PostToolUse:verify-commit-type" \
                "Commit $COMMIT_HASH used 'docs:' for Claude-facing file ($pattern). Should use 'config:'. Fix with: git commit --amend"
            exit 0
        fi
    done
fi

# Check for config: used on source code
if [[ "$TYPE" == "config" ]]; then
    SOURCE_PATTERNS=(
        "\.java$"
        "\.py$"
        "\.js$"
        "\.ts$"
        "\.go$"
        "\.rs$"
        "src/"
        "lib/"
    )

    for pattern in "${SOURCE_PATTERNS[@]}"; do
        if echo "$COMMIT_FILES" | grep -qE "$pattern"; then
            COMMIT_HASH=$(git log -1 --format=%h)
            echo "" >&2
            echo "⚠️  POST-COMMIT WARNING: 'config:' used for source code" >&2
            echo "" >&2
            echo "Commit $COMMIT_HASH contains source code files (matched: $pattern)" >&2
            echo "Source code should use: feature:, bugfix:, refactor:, test:, or performance:" >&2
            echo "" >&2
            echo "TO FIX: git commit --amend" >&2
            echo "" >&2

            output_hook_warning "PostToolUse:verify-commit-type" \
                "Commit $COMMIT_HASH used 'config:' for source code ($pattern). Fix with: git commit --amend"
            exit 0
        fi
    done
fi

echo '{}'
exit 0
