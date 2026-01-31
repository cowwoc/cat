#!/bin/bash
# check-hooks-loaded.sh - Detect if hooks are loaded after plugin install
#
# Usage: check-hooks-loaded.sh "what's missing" "command to retry"
#
# Exit codes:
#   0 = Hooks appear loaded (caller should investigate other causes)
#   1 = Hooks not loaded (restart required message shown)
#
# Example:
#   "${CLAUDE_PLUGIN_ROOT}/scripts/check-hooks-loaded.sh" "status display" "/cat:status"

WHAT="${1:-preprocessor output}"
COMMAND="${2:-the command}"

if [[ -n "${CLAUDE_PLUGIN_ROOT}" ]] && [[ -f "${CLAUDE_PLUGIN_ROOT}/hooks/hooks.json" ]]; then
  echo "ERROR: Preprocessor $WHAT not found."
  echo ""
  echo "Hooks are configured but not running. This usually means:"
  echo "→ Plugin was recently installed/reinstalled without restarting Claude Code."
  echo ""
  echo "Solution: Restart Claude Code, then run $COMMAND again."
  exit 1
else
  # Hooks infrastructure doesn't exist - different problem
  exit 0
fi
