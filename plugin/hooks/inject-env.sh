#!/bin/bash
# Hook: inject-env.sh
# Trigger: SessionStart
# Purpose: Persist Claude environment variables into CLAUDE_ENV_FILE for all Bash tool invocations

set -euo pipefail
trap 'echo "ERROR in $(basename "$0") line $LINENO: $BASH_COMMAND" >&2; exit 1' ERR

# Read stdin
if [ -t 0 ]; then
    exit 0
fi

stdin_content=$(cat)

if [ -z "$stdin_content" ]; then
    exit 0
fi

# Verify CLAUDE_PROJECT_DIR is set
if [ -z "${CLAUDE_PROJECT_DIR:-}" ]; then
    echo "ERROR: CLAUDE_PROJECT_DIR not set in hook environment" >&2
    exit 1
fi

# Verify CLAUDE_PLUGIN_ROOT is set
if [ -z "${CLAUDE_PLUGIN_ROOT:-}" ]; then
    echo "ERROR: CLAUDE_PLUGIN_ROOT not set in hook environment" >&2
    exit 1
fi

# Verify CLAUDE_SESSION_ID is set
if [ -z "${CLAUDE_SESSION_ID:-}" ]; then
    echo "ERROR: CLAUDE_SESSION_ID not set in hook environment" >&2
    exit 1
fi

# Verify CLAUDE_ENV_FILE is set
if [ -z "${CLAUDE_ENV_FILE:-}" ]; then
    echo "ERROR: CLAUDE_ENV_FILE not set in hook environment" >&2
    exit 1
fi

# Persist all three env vars to env file
echo "export CLAUDE_PROJECT_DIR=\"${CLAUDE_PROJECT_DIR}\"" >> "$CLAUDE_ENV_FILE"
echo "export CLAUDE_PLUGIN_ROOT=\"${CLAUDE_PLUGIN_ROOT}\"" >> "$CLAUDE_ENV_FILE"
echo "export CLAUDE_SESSION_ID=\"${CLAUDE_SESSION_ID}\"" >> "$CLAUDE_ENV_FILE"
