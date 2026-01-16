#!/bin/bash
set -euo pipefail

# Progress tracking
source "$(dirname "$0")/lib/progress.sh"
progress_init 3

# batch-read.sh - Batch file search and read with smart filtering
#
# This script finds files matching a pattern and reads them in one operation,
# reducing LLM round-trips from 1+N to 2-3 (50-70% faster for N≥3 files).
#
# Usage:
#   batch-read.sh <pattern> [--max-files N] [--context-lines N] [--type TYPE]
#
# Parameters:
#   pattern          - Grep pattern to search for (regex supported)
#   --max-files N    - Maximum number of files to read (default: 5)
#   --context-lines N - Lines to include per file (default: 100, 0 = all)
#   --type TYPE      - File type filter (e.g., "java", "sh", "md")

# ============================================================================
# CONFIGURATION
# ============================================================================

PATTERN="${1:?ERROR: PATTERN required (grep pattern to search for)}"
shift

MAX_FILES=5
CONTEXT_LINES=100
FILE_TYPE=""

# Parse arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
    --max-files)
      MAX_FILES="$2"
      shift 2
      ;;
    --context-lines)
      CONTEXT_LINES="$2"
      shift 2
      ;;
    --type)
      FILE_TYPE="$2"
      shift 2
      ;;
    *)
      echo "ERROR: Unknown option: $1" >&2
      echo "Usage: batch-read.sh <pattern> [--max-files N] [--context-lines N] [--type TYPE]" >&2
      exit 1
      ;;
  esac
done

# ============================================================================
# EXECUTION RESULT TRACKING
# ============================================================================

RESULT_JSON="/tmp/batch-read-result-$$.json"
OUTPUT_FILE="/tmp/batch-read-output-$$.txt"
START_TIME=$(date +%s)

# Function to output structured JSON result
output_result() {
  local status="$1"
  local message="$2"
  local end_time=$(date +%s)
  local duration=$((end_time - START_TIME))

  cat > "$RESULT_JSON" <<EOF
{
  "status": "$status",
  "message": "$message",
  "duration_seconds": $duration,
  "pattern": "$PATTERN",
  "files_found": ${FILES_FOUND:-0},
  "files_read": ${FILES_READ:-0},
  "output_file": "$OUTPUT_FILE",
  "working_directory": "$(pwd)",
  "timestamp": "$(date -Iseconds)"
}
EOF

  cat "$RESULT_JSON"

  # Also output the file contents for LLM to read
  if [[ -f "$OUTPUT_FILE" && -s "$OUTPUT_FILE" ]]; then
    echo ""
    echo "════════════════════════════════════════"
    echo "FILE CONTENTS"
    echo "════════════════════════════════════════"
    cat "$OUTPUT_FILE"
  fi

  [[ "$status" == "success" ]] && exit 0 || exit 1
}

# ============================================================================
# STEP 1: FIND MATCHING FILES
# ============================================================================

progress_step "Finding files matching pattern: $PATTERN"

# Build grep arguments as array (safe from injection)
GREP_ARGS=(-r -l)

if [[ -n "$FILE_TYPE" ]]; then
  GREP_CMD="$GREP_CMD --include=*.${FILE_TYPE}"
  echo "  File type filter: *.$FILE_TYPE"
fi

# Find files containing pattern (no eval - safe from command injection)
if ! MATCHING_FILES=$(grep "${GREP_ARGS[@]}" -- "$PATTERN" . 2>/dev/null | head -n "$MAX_FILES"); then
  FILES_FOUND=0
  output_result "error" "No files found matching pattern: $PATTERN"
fi

# Count matches
FILES_FOUND=$(echo "$MATCHING_FILES" | grep -c . || echo "0")

if [[ "$FILES_FOUND" -eq 0 ]]; then
  output_result "error" "No files found matching pattern: $PATTERN"
fi

# Limit to MAX_FILES
if [[ "$FILES_FOUND" -gt "$MAX_FILES" ]]; then
  echo "  ⚠️  Limiting to first $MAX_FILES files (found $FILES_FOUND total)"
  MATCHING_FILES=$(echo "$MATCHING_FILES" | head -n "$MAX_FILES")
  FILES_FOUND=$MAX_FILES
fi

progress_done "Found $FILES_FOUND file(s)"
echo ""
echo "  Files to read:"
echo "$MATCHING_FILES"

# ============================================================================
# STEP 2: READ FILES
# ============================================================================

progress_step "Reading file contents"

# Initialize output file
> "$OUTPUT_FILE"

FILES_READ=0

while IFS= read -r file; do
  if [[ ! -f "$file" ]]; then
    echo "⚠️  Skipping non-existent file: $file"
    continue
  fi

  FILES_READ=$((FILES_READ + 1))

  # Add file header
  {
    echo ""
    echo "═══════════════════════════════════════════════════════════"
    echo "FILE: $file"
    echo "═══════════════════════════════════════════════════════════"
    echo ""
  } >> "$OUTPUT_FILE"

  # Read file content (with optional line limit)
  if [[ "$CONTEXT_LINES" -eq 0 ]]; then
    # Read entire file
    cat "$file" >> "$OUTPUT_FILE"
  else
    # Read limited lines, showing line numbers
    head -n "$CONTEXT_LINES" "$file" | cat -n >> "$OUTPUT_FILE"

    # Check if file was truncated
    TOTAL_LINES=$(wc -l < "$file")
    if [[ "$TOTAL_LINES" -gt "$CONTEXT_LINES" ]]; then
      echo "" >> "$OUTPUT_FILE"
      echo "[... truncated: showing $CONTEXT_LINES of $TOTAL_LINES lines ...]" >> "$OUTPUT_FILE"
    fi
  fi

  # Add file footer
  {
    echo ""
    echo "───────────────────────────────────────────────────────────"
    echo ""
  } >> "$OUTPUT_FILE"

  echo "  ✓ Read: $file"
done <<< "$MATCHING_FILES"

progress_done "Read $FILES_READ file(s)"

# ============================================================================
# STEP 3: VERIFY OUTPUT
# ============================================================================

progress_step "Verifying output"

if [[ ! -s "$OUTPUT_FILE" ]]; then
  output_result "error" "No content read from files"
fi

OUTPUT_SIZE=$(wc -c < "$OUTPUT_FILE")
echo "Output size: $OUTPUT_SIZE bytes"

# Check if output is too large (warn but don't fail)
if [[ "$OUTPUT_SIZE" -gt 100000 ]]; then
  echo "  ⚠️  Warning: Output is large ($OUTPUT_SIZE bytes)"
  echo "     Consider using --context-lines to limit output"
fi

progress_done "Output verified ($OUTPUT_SIZE bytes)"

progress_summary
echo "Pattern: $PATTERN | Files: $FILES_READ/$FILES_FOUND"

# ============================================================================
# SUCCESS
# ============================================================================

output_result "success" "Successfully read $FILES_READ file(s) matching pattern"
