#!/bin/bash
# Hook: Validate commit message uses correct commit types
# Triggered by: PreToolUse on Bash commands containing "git commit"
# Blocks commits with invalid types like 'feat:', 'fix:', etc.

set -euo pipefail
trap 'echo "ERROR in validate-commit-type.sh at line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# Source JSON libraries
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/json-parser.sh"
source "${SCRIPT_DIR}/lib/json-output.sh"

# Initialize as Bash hook (reads stdin, parses JSON, extracts command)
if ! init_bash_hook; then
    echo '{}'
    exit 0
fi

COMMAND="$HOOK_COMMAND"

# Only check git commit commands
if [[ "$COMMAND" != *"git commit"* ]]; then
    echo '{}'
    exit 0
fi

# Extract commit message from -m flag
# Handles: git commit -m "msg", git commit -m 'msg', git commit -m "$(cat <<...)"
MSG=""
if [[ "$COMMAND" =~ -m[[:space:]]+\"?\$\(cat ]]; then
    # HEREDOC pattern - check this FIRST (before simple quotes pattern)
    # Extract the first line after EOF (or 'EOF)
    MSG=$(printf '%s' "$COMMAND" | sed -n "/<<'*EOF/,/^EOF/{/<<'*EOF/d;/^EOF/d;p;}" | head -1 || echo "")
elif [[ "$COMMAND" =~ -m[[:space:]]+[\"\']([^\"\']+)[\"\'] ]]; then
    # Simple quoted message: -m "msg" or -m 'msg'
    MSG="${BASH_REMATCH[1]}"
fi

# If no message found, allow (might be interactive or --amend without -m)
if [[ -z "$MSG" ]]; then
    echo '{}'
    exit 0
fi

# Extract the type (everything before the colon)
TYPE=$(echo "$MSG" | grep -oE "^[a-z]+" || echo "")

# Valid commit types (allowlist)
VALID_TYPES="feature|bugfix|test|refactor|performance|docs|style|config|planning"

# Check if type is in allowlist (M098: use allowlist instead of denylist)
if [[ -n "$TYPE" ]] && ! echo "$TYPE" | grep -qE "^($VALID_TYPES)$"; then
    # Build helpful error message
    SUGGESTION=""
    case "$TYPE" in
        feat) SUGGESTION="Did you mean 'feature:'?" ;;
        fix) SUGGESTION="Did you mean 'bugfix:'?" ;;
        perf) SUGGESTION="Did you mean 'performance:'?" ;;
        security) SUGGESTION="Security fixes should use 'bugfix:'" ;;
        skill) SUGGESTION="Skill changes should use 'config:' (Claude-facing docs)" ;;
        chore|build|ci) SUGGESTION="Use 'config:' for tooling/configuration changes" ;;
    esac

    output_hook_block "Invalid commit type '$TYPE'" \
        "Valid types: feature, bugfix, test, refactor, performance, docs, style, config, planning" \
        "$SUGGESTION"
    exit 0
fi

# Check for docs: used on Claude-facing files (M089, M112, M156)
# Claude-facing files should use config:, not docs:
if [[ "$TYPE" == "docs" ]]; then
    # Get staged files - but note: for "git add X && git commit", the add hasn't run yet!
    # So also extract files from git add commands in the same command string (M156)
    STAGED=$(git diff --cached --name-only 2>/dev/null || echo "")

    # Extract files from "git add" in the command (handles "git add X && git commit")
    ADD_FILES=$(echo "$COMMAND" | grep -oE 'git add [^&|;]+' | sed 's/git add //' | tr ' ' '\n' || echo "")
    STAGED=$(printf '%s\n%s' "$STAGED" "$ADD_FILES")

    # Claude-facing file patterns (should use config:, not docs:)
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
        if echo "$STAGED" | grep -q "$pattern"; then
            output_hook_block "Use 'config:' for Claude-facing files" \
                "Files matching '$pattern' are read by Claude for behavior." \
                "Rule: docs: = user-facing, config: = Claude-facing"
            exit 0
        fi
    done
fi

# Check for config: used on actual source code (M134, M156)
# Source code changes should use feature:, bugfix:, refactor:, etc.
if [[ "$TYPE" == "config" ]]; then
    STAGED=$(git diff --cached --name-only 2>/dev/null || echo "")
    ADD_FILES=$(echo "$COMMAND" | grep -oE 'git add [^&|;]+' | sed 's/git add //' | tr ' ' '\n' || echo "")
    STAGED=$(printf '%s\n%s' "$STAGED" "$ADD_FILES")

    # Source code patterns that should NOT use config:
    SOURCE_PATTERNS=(
        "\.java$"
        "\.py$"
        "\.js$"
        "\.ts$"
        "\.go$"
        "\.rs$"
        "\.c$"
        "\.cpp$"
        "\.h$"
        "src/"
        "lib/"
        "test/"
        "tests/"
    )

    for pattern in "${SOURCE_PATTERNS[@]}"; do
        if echo "$STAGED" | grep -qE "$pattern"; then
            output_hook_block "Use feature:, bugfix:, refactor:, test:, or performance: for source code" \
                "config: is for configuration/tooling changes, not code changes"
            exit 0
        fi
    done
fi

# Check for planning: vs config: in .claude/cat/ (M133, M156, M166)
# .claude/cat/v*/ planning files MUST use planning:
# Other .claude/cat/ files should use config:
if [[ "$TYPE" == "planning" ]]; then
    STAGED=$(git diff --cached --name-only 2>/dev/null || echo "")
    ADD_FILES=$(echo "$COMMAND" | grep -oE 'git add [^&|;]+' | sed 's/git add //' | tr ' ' '\n' || echo "")
    STAGED=$(printf '%s\n%s' "$STAGED" "$ADD_FILES")

    # planning: is only valid for version planning files
    if ! echo "$STAGED" | grep -qE "\.claude/cat/v[0-9]"; then
        output_hook_block "Use 'config:' for non-version .claude/cat/ files" \
            "planning: is only for .claude/cat/v*/ version files (STATE.md, PLAN.md, CHANGELOG.md)"
        exit 0
    fi
fi

# M166: Enforce planning: for .claude/cat/v*/ files (not just block docs:)
# When committing version planning files, MUST use planning: type
if [[ "$TYPE" != "planning" ]]; then
    STAGED=$(git diff --cached --name-only 2>/dev/null || echo "")
    ADD_FILES=$(echo "$COMMAND" | grep -oE 'git add [^&|;]+' | sed 's/git add //' | tr ' ' '\n' || echo "")
    STAGED=$(printf '%s\n%s' "$STAGED" "$ADD_FILES")

    # Check if ANY staged file is in .claude/cat/v*/ (version planning files)
    if echo "$STAGED" | grep -qE "\.claude/cat/v[0-9]"; then
        output_hook_block "Use 'planning:' for version planning files" \
            "Files in .claude/cat/v*/ (STATE.md, PLAN.md, CHANGELOG.md) require 'planning:' type"
        exit 0
    fi
fi

echo '{}'
exit 0
