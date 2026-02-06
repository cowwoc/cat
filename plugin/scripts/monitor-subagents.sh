#!/bin/bash
set -euo pipefail
trap 'echo "{\"error\":\"Script failed at line $LINENO: $BASH_COMMAND\"}" >&2; exit 1' ERR

# monitor-subagents.sh - Lightweight subagent status check
#
# Returns compact JSON with status of all active subagents.
# Designed to minimize context impact when called repeatedly.
#
# Usage:
#   monitor-subagents.sh [--worktree-dir DIR]
#
# Output:
#   {"subagents":[...],"summary":{"total":N,"running":N,"complete":N}}

# ============================================================================
# CONFIGURATION
# ============================================================================

WORKTREE_DIR=".claude/cat/worktrees"
SESSION_BASE="/home/node/.config/claude/projects/-workspace"
THRESHOLD_TOKENS=80000  # 40% of 200K context

while [[ $# -gt 0 ]]; do
  case "$1" in
    --worktree-dir)
      WORKTREE_DIR="$2"
      shift 2
      ;;
    *)
      echo "{\"error\":\"Unknown option: $1\"}" >&2
      exit 1
      ;;
  esac
done

# ============================================================================
# COLLECT SUBAGENT STATUS (optimized: collect data first, single jq call at end)
# ============================================================================

TOTAL=0
RUNNING=0
COMPLETE=0
WARNING=0

# Collect subagent data as tab-separated values (avoids O(n) jq calls in loop)
SUBAGENT_DATA=""

# Find all subagent worktrees
while IFS= read -r worktree_line; do
  [[ -z "$worktree_line" ]] && continue

  # Extract worktree path from porcelain output
  WORKTREE_PATH="${worktree_line#worktree }"

  # Skip if not a subagent worktree
  [[ ! "$WORKTREE_PATH" =~ -sub- ]] && continue

  TOTAL=$((TOTAL + 1))

  # Extract subagent ID from path (last 8 chars after -sub-)
  SUBAGENT_ID=$(echo "$WORKTREE_PATH" | grep -oE 'sub-[a-f0-9]+' | tail -1 | sed 's/sub-//')
  [[ -z "$SUBAGENT_ID" ]] && SUBAGENT_ID="unknown"

  # Extract task name (path component before -sub-)
  TASK_NAME=$(basename "$WORKTREE_PATH" | sed 's/-sub-.*//')

  # Check for completion marker first (fast path)
  COMPLETION_FILE="${WORKTREE_PATH}/.completion.json"
  if [[ -f "$COMPLETION_FILE" ]]; then
    STATUS="complete"
    COMPLETE=$((COMPLETE + 1))

    # Read completion data (single jq call for both fields)
    COMPLETION_DATA=$(jq -r '[.tokensUsed // 0, .compactionEvents // 0] | @tsv' "$COMPLETION_FILE" 2>/dev/null || echo "0\t0")
    TOKENS=$(echo "$COMPLETION_DATA" | cut -f1)
    COMPACTIONS=$(echo "$COMPLETION_DATA" | cut -f2)
  else
    # No completion marker - check session file for running status
    STATUS="running"
    RUNNING=$((RUNNING + 1))
    TOKENS=0
    COMPACTIONS=0

    # Try to find session file (look for .session_id marker in worktree)
    SESSION_ID_FILE="${WORKTREE_PATH}/.session_id"
    if [[ -f "$SESSION_ID_FILE" ]]; then
      SESSION_ID=$(cat "$SESSION_ID_FILE" 2>/dev/null || echo "")
      SESSION_FILE="${SESSION_BASE}/${SESSION_ID}.jsonl"

      if [[ -f "$SESSION_FILE" ]]; then
        # Calculate tokens and compactions in single jq call
        TOKEN_DATA=$(jq -s '
          [
            ([.[] | select(.type == "assistant") | .message.usage | (.input_tokens + .output_tokens)] | add // 0),
            ([.[] | select(.type == "summary")] | length)
          ] | @tsv' "$SESSION_FILE" 2>/dev/null || echo "0\t0")
        TOKENS=$(echo "$TOKEN_DATA" | cut -f1)
        COMPACTIONS=$(echo "$TOKEN_DATA" | cut -f2)
      fi
    fi

    # Check if approaching threshold
    if [[ "$TOKENS" -ge "$THRESHOLD_TOKENS" ]]; then
      STATUS="warning"
      WARNING=$((WARNING + 1))
      RUNNING=$((RUNNING - 1))
    fi
  fi

  # Accumulate as tab-separated values (no jq in loop)
  SUBAGENT_DATA+="${SUBAGENT_ID}\t${TASK_NAME}\t${STATUS}\t${TOKENS}\t${COMPACTIONS}\t${WORKTREE_PATH}\n"

done < <(git worktree list --porcelain 2>/dev/null | grep "^worktree" || true)

# Build subagents JSON array with single jq call
if [[ -z "$SUBAGENT_DATA" ]]; then
  SUBAGENTS_JSON="[]"
else
  SUBAGENTS_JSON=$(printf "%b" "$SUBAGENT_DATA" | jq -R -s '
    split("\n") | map(select(length > 0)) | map(
      split("\t") | {
        id: .[0],
        task: .[1],
        status: .[2],
        tokens: (.[3] | tonumber),
        compactions: (.[4] | tonumber),
        worktree: .[5]
      }
    )
  ')
fi

# ============================================================================
# OUTPUT RESULT
# ============================================================================

jq -n \
  --argjson subagents "$SUBAGENTS_JSON" \
  --argjson total "$TOTAL" \
  --argjson running "$RUNNING" \
  --argjson complete "$COMPLETE" \
  --argjson warning "$WARNING" \
  '{
    subagents: $subagents,
    summary: {
      total: $total,
      running: $running,
      complete: $complete,
      warning: $warning
    }
  }'
