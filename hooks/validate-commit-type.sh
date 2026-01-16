#!/bin/bash
# Hook: Validate commit message uses correct commit types
# Triggered by: PreToolUse on Bash commands containing "git commit"
# Blocks commits with invalid types like 'feat:', 'fix:', etc.

set -euo pipefail
trap 'echo "ERROR in validate-commit-type.sh at line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# Get the command from tool input
COMMAND="${TOOL_INPUT:-}"

# Only check git commit commands
if [[ "$COMMAND" != *"git commit"* ]]; then
  exit 0
fi

# Extract commit message from -m flag
# Handles: git commit -m "msg", git commit -m 'msg', git commit -m "$(cat <<...)"
MSG=""
if [[ "$COMMAND" =~ -m[[:space:]]+[\"\']([^\"\']+)[\"\'] ]]; then
  MSG="${BASH_REMATCH[1]}"
elif [[ "$COMMAND" =~ -m[[:space:]]+\"?\$\(cat ]]; then
  # HEREDOC pattern - extract the first line after EOF (or 'EOF)
  # The content appears after the EOF marker on a new line
  MSG=$(printf '%s' "$COMMAND" | sed -n "/<<'*EOF/,/^EOF/{/<<'*EOF/d;/^EOF/d;p;}" | head -1 || echo "")
fi

# If no message found, allow (might be interactive or --amend without -m)
if [[ -z "$MSG" ]]; then
  exit 0
fi

# Extract the type (everything before the colon)
TYPE=$(echo "$MSG" | grep -oE "^[a-z]+" || echo "")

# Check for common INVALID types (conventional commit shortcuts)
case "$TYPE" in
  feat|fix|chore|build|ci|perf)
    echo "BLOCKED: Invalid commit type '$TYPE'"
    echo ""
    echo "Valid types: feature, bugfix, test, refactor, performance, docs, style, config, planning"
    echo "NOT valid: feat, fix, chore, build, ci, perf (use full names)"
    echo ""
    echo "Change '$TYPE:' to the correct full type name."
    exit 2
    ;;
esac

# Check for docs: used on Claude-facing files (M089, M112)
# Claude-facing files should use config:, not docs:
if [[ "$TYPE" == "docs" ]]; then
  # Get staged files
  STAGED=$(git diff --cached --name-only 2>/dev/null || echo "")

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
      echo "BLOCKED: 'docs:' used for Claude-facing file"
      echo ""
      echo "Files matching '$pattern' are Claude-facing (read by Claude for behavior)."
      echo "Use 'config:' instead of 'docs:' for these files."
      echo ""
      echo "Rule (M089): docs: = user-facing (README, API docs)"
      echo "             config: = Claude-facing (CLAUDE.md, hooks, skills, workflows)"
      exit 2
    fi
  done
fi

exit 0
