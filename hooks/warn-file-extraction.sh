#!/bin/bash
set -euo pipefail
trap 'echo "ERROR in warn-file-extraction.sh at line $LINENO: $BASH_COMMAND" >&2; exit 0' ERR

# warn-file-extraction.sh - Warn about file extraction from commits
#
# ADDED: 2026-01-12 per A007 action item
# BASED ON: M025 - cherry-picked files from commit based on stale main,
# causing 60 lines of switch-parsing code to be incorrectly removed.
#
# When extracting files from commits (git show/checkout), the files contain the
# ENTIRE content from that commit - not just the changes. If the commit was based
# on an older version of main, the extracted files will have stale base code.
#
# PREVENTS: Silent code loss when extracting files from commits based on stale main.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/json-parser.sh"

# Initialize as Bash hook (reads stdin, parses JSON, extracts command)
if ! init_bash_hook; then
    echo '{}'
    exit 0
fi

# Use HOOK_COMMAND from init_bash_hook
COMMAND="$HOOK_COMMAND"
if [[ -z "$COMMAND" ]]; then
    echo '{}'
    exit 0
fi

FILE_EXTRACTION=false
EXTRACTION_TYPE=""

# Check for git show with file path (commit:path pattern)
if [[ "$COMMAND" =~ git[[:space:]]+show[[:space:]].*:[^[:space:]]+ ]]; then
    FILE_EXTRACTION=true
    EXTRACTION_TYPE="git show"
fi

# Check for git checkout with commit and file path
if [[ "$COMMAND" =~ git[[:space:]]+checkout[[:space:]]+[^[:space:]]+[[:space:]]+--[[:space:]] ]]; then
    FILE_EXTRACTION=true
    EXTRACTION_TYPE="git checkout"
fi

# Check for git restore --source
if [[ "$COMMAND" =~ git[[:space:]]+restore[[:space:]]+--source ]]; then
    FILE_EXTRACTION=true
    EXTRACTION_TYPE="git restore --source"
fi

# If file extraction detected, warn about base version verification
if [[ "$FILE_EXTRACTION" == "true" ]]; then
    # Rate limit: Only warn once per 5 minutes to avoid spam
    RATE_LIMIT_FILE="/tmp/cat-file-extraction-warning-$(date +%Y%m%d%H)"
    RATE_LIMIT_MINUTE=$(($(date +%M) / 5))
    RATE_LIMIT_KEY="${RATE_LIMIT_FILE}-${RATE_LIMIT_MINUTE}"

    if [[ -f "$RATE_LIMIT_KEY" ]]; then
        # Already warned recently, skip
        exit 0
    fi
    touch "$RATE_LIMIT_KEY"

    output_hook_warning "
================================================================
  WARNING: File Extraction from Commit Detected
================================================================

You're using '$EXTRACTION_TYPE' to extract file content from a commit.

CRITICAL: Extracted files contain the ENTIRE file content from that
commit - not just the changes. If the source commit was based on an
older version of main, the extracted files will have STALE BASE CODE.

VERIFICATION REQUIRED:
  1. Identify what commit main was at when source commit was created:
     git log --oneline --graph main <source-commit> | head -20

  2. Compare extracted file against current main:
     git diff main:<file> <source-commit>:<file>

  3. If main has changes not in source commit, DO NOT use file extraction.
     Instead, use git diff/patch to apply only the delta changes.

SAFE ALTERNATIVE: Apply only the changes, not the whole file:
  git diff <base-commit>..<source-commit> -- <file> | git apply
================================================================
"
    exit 0
fi

# No file extraction detected
exit 0
