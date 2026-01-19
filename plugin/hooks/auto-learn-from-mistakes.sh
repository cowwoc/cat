#!/bin/bash
set -euo pipefail

# Error handler - output helpful message to stderr on failure
trap 'echo "ERROR in auto-learn-from-mistakes.sh at line $LINENO: Command failed: $BASH_COMMAND" >&2; exit 1' ERR

# Auto-invoke learn-from-mistakes skill when mistakes are detected
#
# This hook monitors tool results for error patterns and automatically
# triggers the learn-from-mistakes skill for root cause analysis.
#
# Triggered on: PostToolUse (all tools)
# Detects: Build failures, test failures, validation errors, protocol violations,
#          skill step failures, git operation failures, merge conflicts, edit failures,
#          missing cleanup steps, self-acknowledged mistakes, restore from backup,
#          critical self-acknowledgments ("CRITICAL DISASTER", "catastrophic", etc.),
#          wrong working directory (not a git repo, missing pom.xml, path errors)
#
# PATTERN EVOLUTION:
#   - 2025-11-05: Added Pattern 6 (skill step failures) and Pattern 7 (git operation failures)
#   - 2025-11-06: Added Pattern 8 (missing cleanup), Pattern 9 (self-acknowledged mistakes),
#                 and Pattern 10 (restore from backup)
#   - 2025-11-07: Added Pattern 11 (critical self-acknowledgments) for detecting phrases
#                 like "CRITICAL DISASTER", "CRITICAL MISTAKE", "CRITICAL ERROR"
#   - 2025-11-12: Enhanced Pattern 11 to check assistant messages via conversation log
#                 (inspired by detect-assistant-giving-up.sh approach)
#                 Improved to track last checked line number, only parsing new messages
#                 (eliminates missed messages and removes need for rate limiting)
#   - 2025-11-28: Added Pattern 12 (wrong working directory) for detecting commands
#                 run from incorrect directories (not a git repo, missing pom.xml, etc.)
#   - 2025-12-17: Added Pattern 13 (parse errors) for detecting jq/JSON parse errors
#                 and other data format parsing failures
#   - 2025-12-22: Added Pattern 14 (bash parse errors) for detecting shell parse errors
#                 from command substitution $(...) in multi-line commands
#   - 2026-01-08: Fixed Pattern 7 and Pattern 11 false positives - added exclusions for
#                 git log output (commit hashes, headers, indented messages), diff lines,
#                 and JSON content
#   - 2026-01-16: Fixed Pattern 13/13b false positives (M100) - parse errors in stderr
#                 are now only flagged if the command actually failed (non-zero exit code).
#                 Benign jq stderr output no longer triggers mistake detection.

# Read input from stdin (hook context JSON for PostToolUse)
HOOK_CONTEXT=$(cat)

# Extract tool name and result from context
# Note: Claude Code uses tool_name (not tool.name) and tool_response (not result)
TOOL_NAME=$(echo "$HOOK_CONTEXT" | jq -r '.tool_name // "unknown"')
# Combine stdout and stderr for error detection
TOOL_STDOUT=$(echo "$HOOK_CONTEXT" | jq -r '.tool_response.stdout // ""')
TOOL_STDERR=$(echo "$HOOK_CONTEXT" | jq -r '.tool_response.stderr // ""')
TOOL_RESULT="${TOOL_STDOUT}${TOOL_STDERR}"

# Extract exit code for success/failure determination
# Some patterns should only trigger on actual failures, not benign stderr output
TOOL_EXIT_CODE=$(echo "$HOOK_CONTEXT" | jq -r '.tool_response.exit_code // 0')

# Extract session ID for conversation log access
SESSION_ID=$(echo "$HOOK_CONTEXT" | jq -r '.session_id // ""')

# Initialize assistant message content for Pattern 11 enhancement
LAST_ASSISTANT_MESSAGE=""

# Track last checked line number to only parse new messages
# This prevents missing messages and is more efficient than rate limiting
if [[ -n "$SESSION_ID" ]]; then
  CONVERSATION_LOG="/home/node/.config/projects/-workspace/${SESSION_ID}.jsonl"
  LAST_LINE_FILE="/tmp/auto-learn-mistakes-lastline-${SESSION_ID}.txt"

  if [[ -f "$CONVERSATION_LOG" ]]; then
    # Get current line count
    CURRENT_LINE_COUNT=$(wc -l < "$CONVERSATION_LOG")

    # Get last checked line number (default to 0 if file doesn't exist)
    LAST_LINE_CHECKED=0
    if [[ -f "$LAST_LINE_FILE" ]]; then
      LAST_LINE_CHECKED=$(cat "$LAST_LINE_FILE" 2>/dev/null || echo "0")
    fi

    # Only check if there are new lines
    if [[ $CURRENT_LINE_COUNT -gt $LAST_LINE_CHECKED ]]; then
      # Extract only new assistant messages since last check
      LINES_TO_CHECK=$((CURRENT_LINE_COUNT - LAST_LINE_CHECKED))
      LAST_ASSISTANT_MESSAGE=$(tail -n "$LINES_TO_CHECK" "$CONVERSATION_LOG" | grep -F '"role":"assistant"' || echo "")

      # Update last checked line number
      echo "$CURRENT_LINE_COUNT" > "$LAST_LINE_FILE"
    fi
  fi
fi

# Initialize mistake detection
MISTAKE_TYPE=""
MISTAKE_DETAILS=""

# Pattern 1: Build failures (CRITICAL)
if echo "$TOOL_RESULT" | grep -qi "BUILD FAILURE\|COMPILATION ERROR\|compilation failure"; then
  MISTAKE_TYPE="build_failure"
  MISTAKE_DETAILS=$(echo "$TOOL_RESULT" | grep -A5 -i "error\|failure" | head -20)
fi

# Pattern 2: Test failures (CRITICAL)
if echo "$TOOL_RESULT" | grep -qE "Tests run:.*Failures: [1-9]|test.*failed"; then
  MISTAKE_TYPE="test_failure"
  MISTAKE_DETAILS=$(echo "$TOOL_RESULT" | grep -A5 -iE "test.*failed|failure" | head -20)
fi

# Pattern 3: Protocol violations (CRITICAL)
if echo "$TOOL_RESULT" | grep -qE "PROTOCOL VIOLATION|🚨.*VIOLATION"; then
  MISTAKE_TYPE="protocol_violation"
  MISTAKE_DETAILS=$(echo "$TOOL_RESULT" | grep -B2 -A5 -i "violation" | head -20)
fi

# Pattern 4: Merge conflicts (HIGH)
# Match actual git conflict markers, not casual mentions of "conflict" in text
# Real conflicts show: "CONFLICT (content):", "CONFLICT (modify/delete):", or conflict markers in files
if echo "$TOOL_RESULT" | grep -qE "CONFLICT \(|^<<<<<<<|^=======\$|^>>>>>>>"; then
  MISTAKE_TYPE="merge_conflict"
  MISTAKE_DETAILS=$(echo "$TOOL_RESULT" | grep -E -A3 "CONFLICT \(|<<<<<<<|=======|>>>>>>>" | head -10)
fi

# Pattern 5: Edit tool failures (old_string not found)
if echo "$TOOL_RESULT" | grep -qi "String to replace not found\|old_string not found"; then
  MISTAKE_TYPE="edit_failure"
  MISTAKE_DETAILS=$(echo "$TOOL_RESULT" | grep -B5 -A2 -i "string to replace not found\|old_string not found" | head -15)
fi

# Pattern 6: Skill step failures (HIGH)
if [[ "$TOOL_NAME" == "Skill" ]] && echo "$TOOL_RESULT" | grep -qiE "\bERROR\b|\bFAILED\b|failed to|step.*(failed|failure)|operation.*(failed|failure)|could not|unable to"; then
  MISTAKE_TYPE="skill_step_failure"
  MISTAKE_DETAILS=$(echo "$TOOL_RESULT" | grep -B3 -A5 -iE "error|failed|could not|unable to" | head -20)
fi

# Pattern 7: Git operation failures (HIGH)
# Note: Excludes JSON content, diff output, and git log output to avoid false positives
# Real git errors have "fatal:" or "error:" at line start, not in code/docs
# Exclusions: JSON (starts with "), diff lines (+/-/@), commit hashes (7+ hex chars)
if echo "$TOOL_RESULT" | grep -vE '^\s*"|^[+-@]|^[a-f0-9]{7,}' | grep -qE "^fatal:|^error: " | head -1 | grep -q .; then
  MISTAKE_TYPE="git_operation_failure"
  MISTAKE_DETAILS=$(echo "$TOOL_RESULT" | grep -vE '^\s*"|^[+-@]' | grep -B2 -A3 -E "^fatal:|^error: " | head -15)
fi

# Pattern 8: Missing cleanup after successful operations (MEDIUM)
# Detects when user asks "Why didn't you [cleanup action]?"
if echo "$TOOL_RESULT" | grep -qiE "why didn't you (remove|delete|clean|cleanup)|didn't you (remove|delete|clean)"; then
  MISTAKE_TYPE="missing_cleanup"
  MISTAKE_DETAILS=$(echo "$TOOL_RESULT" | grep -B5 -A2 -iE "why didn't you|didn't you" | head -15)
fi

# Pattern 9: Self-acknowledged mistakes (HIGH)
# Detects when assistant acknowledges "You're right - I should have..."
if echo "$TOOL_RESULT" | grep -qiE "(you're|you are) (right|correct|absolutely right).*(should have|should've|ought to have)|I should have.*instead"; then
  MISTAKE_TYPE="self_acknowledged_mistake"
  MISTAKE_DETAILS=$(echo "$TOOL_RESULT" | grep -B2 -A5 -iE "(you're|you are) right|should have|ought to" | head -20)
fi

# Pattern 10: Restore from backup (HIGH)
# Detects when assistant says "let me restore from backup" indicating failed operation
if echo "$TOOL_RESULT" | grep -qiE "(let me|I'll|I will|going to) (restore|reset).*(from|using|to).{0,10}backup|restoring from.{0,10}backup"; then
  MISTAKE_TYPE="restore_from_backup"
  MISTAKE_DETAILS=$(echo "$TOOL_RESULT" | grep -B5 -A3 -iE "restore|reset|backup" | head -20)
fi

# Pattern 11: Critical self-acknowledgments (CRITICAL)
# Detects when assistant uses phrases like "CRITICAL DISASTER", "CRITICAL MISTAKE", "CRITICAL ERROR"
# indicating awareness of a major problem
#
# ENHANCED 2025-11-12: Now also checks assistant text messages via conversation log
# Searches both tool results AND recent assistant messages for error acknowledgments
# Note: Excludes git log output to avoid false positives from commit messages
# Exclusions: JSON, diff lines, commit hashes, git log headers, indented commit messages (4 spaces)
if echo "$TOOL_RESULT" | grep -vE '^\s*"|^[+-@]|^[a-f0-9]{7,}|^commit |^Author:|^Date:|^    ' | grep -qiE "CRITICAL (DISASTER|MISTAKE|ERROR|FAILURE|BUG|PROBLEM|ISSUE)|catastrophic|devastating"; then
  MISTAKE_TYPE="critical_self_acknowledgment"
  MISTAKE_DETAILS=$(echo "$TOOL_RESULT" | grep -vE '^\s*"|^[+-@]|^[a-f0-9]{7,}|^commit |^Author:|^Date:|^    ' | grep -B3 -A5 -iE "CRITICAL|catastrophic|devastating" | head -20)
fi

# Pattern 12: Wrong working directory (HIGH)
# Detects when commands are run from the wrong directory
# Common indicators: git "not a git repository", missing build files, path errors
if echo "$TOOL_RESULT" | grep -qiE "fatal: not a git repository|not a git repository \(or any|fatal: not a git directory"; then
  MISTAKE_TYPE="wrong_working_directory"
  MISTAKE_DETAILS=$(echo "$TOOL_RESULT" | grep -B2 -A3 -iE "not a git repository|not a git directory" | head -15)
fi

# Pattern 12b: Missing build configuration files (suggests wrong directory)
if echo "$TOOL_RESULT" | grep -qiE "Could not (find|locate) (the )?pom\.xml|No pom\.xml found|pom\.xml.*does not exist|The goal you specified requires a project"; then
  MISTAKE_TYPE="wrong_working_directory"
  MISTAKE_DETAILS=$(echo "$TOOL_RESULT" | grep -B2 -A3 -iE "pom\.xml|goal you specified" | head -15)
fi

# Pattern 12c: Directory-related path errors in Bash commands
if [[ "$TOOL_NAME" == "Bash" ]] && echo "$TOOL_RESULT" | grep -qiE "No such file or directory.*(/workspace|/tasks)|cannot access.*/workspace|cd:.*no such file"; then
  MISTAKE_TYPE="wrong_working_directory"
  MISTAKE_DETAILS=$(echo "$TOOL_RESULT" | grep -B2 -A3 -iE "No such file or directory|cannot access|cd:" | head -15)
fi

# Pattern 13: Parse errors (HIGH)
# Detects jq parse errors, JSON parse errors, XML parse errors, etc.
# These often indicate function signature mismatches or malformed data
# NOTE: Only trigger if command actually failed (non-zero exit code)
# Parse errors in stderr are benign if the overall command succeeded (M100)
if [[ "$TOOL_EXIT_CODE" != "0" ]] && echo "$TOOL_RESULT" | grep -qiE "parse error.*Invalid|parse error.*Unexpected|jq: error|JSON.parse.*SyntaxError|Invalid JSON|malformed JSON|Unexpected token|Unexpected end of"; then
  MISTAKE_TYPE="parse_error"
  MISTAKE_DETAILS=$(echo "$TOOL_RESULT" | grep -B3 -A5 -iE "parse error|jq: error|JSON|SyntaxError|Invalid|malformed|Unexpected" | head -20)
fi

# Pattern 13b: jq-specific parse errors with line/column info
# NOTE: Only trigger if command actually failed (non-zero exit code) (M100)
if [[ "$TOOL_EXIT_CODE" != "0" ]] && echo "$TOOL_RESULT" | grep -qiE "parse error.*at line [0-9]+|Invalid literal at line"; then
  MISTAKE_TYPE="parse_error"
  MISTAKE_DETAILS=$(echo "$TOOL_RESULT" | grep -B3 -A5 -iE "parse error|Invalid literal" | head -20)
fi

# Pattern 14: Bash/zsh shell parse errors (HIGH)
# Detects shell parsing failures from command substitution $(...) in multi-line commands
# Format: (eval):N: parse error near '...'
if [[ "$TOOL_NAME" == "Bash" ]] && echo "$TOOL_RESULT" | grep -qE "\(eval\):[0-9]+:.*parse error"; then
  MISTAKE_TYPE="bash_parse_error"
  MISTAKE_DETAILS=$(echo "$TOOL_RESULT" | grep -B2 -A3 -E "\(eval\):[0-9]+:|parse error" | head -15)
fi

# Also check last assistant message from conversation log (if rate limit allows)
if [[ -z "$MISTAKE_TYPE" ]] && [[ -n "$LAST_ASSISTANT_MESSAGE" ]]; then
  # Check for critical acknowledgments first (higher priority)
  if echo "$LAST_ASSISTANT_MESSAGE" | grep -qiE "I made a critical (error|mistake)|CRITICAL (DISASTER|MISTAKE|ERROR|FAILURE)|catastrophic failure|devastating (error|failure|mistake)"; then
    MISTAKE_TYPE="critical_self_acknowledgment"
    MISTAKE_DETAILS=$(echo "$LAST_ASSISTANT_MESSAGE" | grep -iE "I made a critical|CRITICAL|catastrophic|devastating" | head -5)
  # Check for general mistake acknowledgments (medium priority)
  # Includes: "My error", "I made a mistake", "this was an error", "I accidentally"
  elif echo "$LAST_ASSISTANT_MESSAGE" | grep -qiE "My error|I (made|created) (a|an) (mistake|error)|this (was|is) (a|an) (mistake|error)|I accidentally"; then
    MISTAKE_TYPE="self_acknowledged_mistake"
    MISTAKE_DETAILS=$(echo "$LAST_ASSISTANT_MESSAGE" | grep -iE "My error|I (made|created).*(mistake|error)|this (was|is).*(mistake|error)|I accidentally" | head -5)
  fi
fi

# If no mistake detected, exit silently
if [[ -z "$MISTAKE_TYPE" ]]; then
  exit 0
fi

# Construct a unique TodoWrite entry ID for deduplication
# Format: "LFM: {mistake_type}" - agent can check if this exists in TodoWrite
TODOWRITE_CONTENT="LFM: Investigate ${MISTAKE_TYPE} from ${TOOL_NAME}"
TODOWRITE_ACTIVE="Investigating ${MISTAKE_TYPE} mistake"

# Output structured JSON with additionalContext for Claude to see
# PostToolUse hooks need hookSpecificOutput.additionalContext to communicate back
# See Claude Code docs: hooks.md#posttooluse-decision-control
ADDITIONAL_CONTEXT=$(cat << EOF
📚 MISTAKE DETECTED: ${MISTAKE_TYPE}

**MANDATORY**: Check TodoWrite for existing entry, add if not present:
- content: "${TODOWRITE_CONTENT}"
- status: "pending"
- activeForm: "${TODOWRITE_ACTIVE}"

**Context**: Detected ${MISTAKE_TYPE} during ${TOOL_NAME} execution.
**Details**: ${MISTAKE_DETAILS}
EOF
)

# Escape the context for JSON (newlines, quotes, backslashes)
ESCAPED_CONTEXT=$(echo "$ADDITIONAL_CONTEXT" | jq -Rs .)

# Output structured JSON to stdout
cat << EOF
{
  "hookSpecificOutput": {
    "hookEventName": "PostToolUse",
    "additionalContext": ${ESCAPED_CONTEXT}
  }
}
EOF

exit 0
