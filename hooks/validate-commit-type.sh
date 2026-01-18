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
  exit 0
fi

# Extract the type (everything before the colon)
TYPE=$(echo "$MSG" | grep -oE "^[a-z]+" || echo "")

# Valid commit types (allowlist)
VALID_TYPES="feature|bugfix|test|refactor|performance|docs|style|config|planning"

# Check if type is in allowlist (M098: use allowlist instead of denylist)
if [[ -n "$TYPE" ]] && ! echo "$TYPE" | grep -qE "^($VALID_TYPES)$"; then
  echo "BLOCKED: Invalid commit type '$TYPE'"
  echo ""
  echo "Valid types: feature, bugfix, test, refactor, performance, docs, style, config, planning"
  echo ""
  # Suggest correction for common mistakes
  case "$TYPE" in
    feat) echo "Did you mean 'feature:'?" ;;
    fix) echo "Did you mean 'bugfix:'?" ;;
    perf) echo "Did you mean 'performance:'?" ;;
    security) echo "Security fixes should use 'bugfix:'" ;;
    skill) echo "Skill changes should use 'config:' (Claude-facing docs)" ;;
    chore|build|ci) echo "Use 'config:' for tooling/configuration changes" ;;
  esac
  exit 2
fi

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
