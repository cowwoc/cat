#!/bin/bash
set -euo pipefail

# Post-Tool-Use hook: Detect giving-up patterns in ASSISTANT messages via conversation log
#
# ADDED: 2025-11-07 after main agent used "Given our token usage (139k/200k), let me
# complete a few more strategic optimizations" to justify reducing optimization scope
# PREVENTS: Assistant violating Token Usage Policy by using token count/context to
# reduce work quality or completeness
#
# CRITICAL LIMITATION: detect-giving-up.sh only monitors UserPromptSubmit (user messages).
# Assistant violations of Token Usage Policy occur in assistant messages, which that
# hook cannot see. This hook fills the gap by checking conversation log after tool use.
#
# Triggers: PostToolUse (all tools) with rate limiting (max once per 60 seconds)
# Action: Inject persistence reminder if giving-up patterns detected in recent assistant messages

# Error handler - output helpful message to stderr on failure
trap 'echo "ERROR in detect-assistant-giving-up.sh at line $LINENO: Command failed: $BASH_COMMAND" >&2; exit 0' ERR

# Read JSON data from stdin with timeout
JSON_INPUT=""
if [ -t 0 ]; then
	exit 0  # No input available
else
	JSON_INPUT="$(timeout 5s cat 2>/dev/null)" || exit 0
fi

# Exit if no JSON input
[[ -z "$JSON_INPUT" ]] && exit 0

# Extract session ID (required for conversation log access)
SESSION_ID=$(echo "$JSON_INPUT" | grep -o '"session_id"[[:space:]]*:[[:space:]]*"[^"]*"' | sed 's/"session_id"[[:space:]]*:[[:space:]]*"\([^"]*\)"/\1/' || echo "")

# Require session ID
if [[ -z "$SESSION_ID" ]]; then
	exit 0  # Fail gracefully without session ID
fi

# Rate limiting: Check at most once per 60 seconds to avoid spam
RATE_LIMIT_FILE="/tmp/detect-assistant-giving-up-${SESSION_ID}.last_check"
CURRENT_TIME=$(date +%s)

if [[ -f "$RATE_LIMIT_FILE" ]]; then
	LAST_CHECK=$(cat "$RATE_LIMIT_FILE" 2>/dev/null || echo "0")
	TIME_DIFF=$((CURRENT_TIME - LAST_CHECK))
	if [[ $TIME_DIFF -lt 60 ]]; then
		exit 0  # Less than 60 seconds since last check
	fi
fi

# Update rate limit timestamp
echo "$CURRENT_TIME" > "$RATE_LIMIT_FILE"

# Access conversation log
CONVERSATION_LOG="/home/node/.config/projects/-workspace/${SESSION_ID}.jsonl"

if [[ ! -f "$CONVERSATION_LOG" ]]; then
	exit 0  # Conversation log not available
fi

# Get timestamp from 5 minutes ago for filtering recent messages
FIVE_MINUTES_AGO=$((CURRENT_TIME - 300))
FIVE_MINUTES_AGO_ISO=$(date -d "@$FIVE_MINUTES_AGO" -Iseconds 2>/dev/null || date -r "$FIVE_MINUTES_AGO" -Iseconds 2>/dev/null || echo "")

# Extract recent assistant messages (last 5 minutes)
# Look for messages with role=assistant and recent timestamps
RECENT_MESSAGES=$(grep -F '"role":"assistant"' "$CONVERSATION_LOG" 2>/dev/null | tail -20 || echo "")

if [[ -z "$RECENT_MESSAGES" ]]; then
	exit 0  # No recent assistant messages
fi

# Detect giving-up patterns in assistant messages
detect_assistant_giving_up() {
	local messages="$1"
	local text_lower=$(echo "$messages" | tr '[:upper:]' '[:lower:]')

	# Token usage rationalization patterns (PRIMARY VIOLATION)
	# Pattern: "given"*"token usage"*work reduction language
	[[ "$text_lower" == *"given"*"token usage"*"let me"* ]] && return 0
	[[ "$text_lower" == *"given"*"token usage"*"i'll"* ]] && return 0
	[[ "$text_lower" == *"given"*"token usage"*"strategic"*"optimization"* ]] && return 0
	[[ "$text_lower" == *"token usage"*"complete a few more"* ]] && return 0
	[[ "$text_lower" == *"token usage"*"then proceed to"* ]] && return 0

	# Reporting token status before reducing scope
	[[ "$text_lower" == *"token usage ("*"/"*")"* ]] && return 0  # e.g., "139k/200k"
	[[ "$text_lower" == *"tokens used"*"let me"* ]] && return 0
	[[ "$text_lower" == *"tokens remaining"*"i'll"* ]] && return 0

	# Context/token constraints + work reduction
	[[ "$text_lower" == *"given our token"*"complete"* ]] && return 0
	[[ "$text_lower" == *"given our context"*"complete"* ]] && return 0
	[[ "$text_lower" == *"token budget"*"a few more"* ]] && return 0
	[[ "$text_lower" == *"context constraints"*"strategic"* ]] && return 0

	# Progress reporting coupled with scope reduction
	[[ "$text_lower" == *"i've optimized"*"let me"*"then proceed"* ]] && return 0
	[[ "$text_lower" == *"completed"*"token"*"continue with"* ]] && return 0

	return 1  # No pattern detected
}

# Check for giving-up patterns
if detect_assistant_giving_up "$RECENT_MESSAGES"; then
	cat <<'REMINDER'
<system-reminder>
🚨 ASSISTANT GIVING-UP PATTERN DETECTED - TOKEN POLICY VIOLATION

**CRITICAL VIOLATION DETECTED**: You mentioned token usage/context as justification for reducing work scope or quality.

**Token Usage Policy (CLAUDE.md)**:
"Token usage MUST NEVER affect behavior, implementation, or task completion. IGNORE all token warnings and limits. Work with full quality until task is complete or user instructs otherwise. Token budget does NOT justify incomplete implementations, shortcuts, quality compromises, or progress reporting interruptions."

**Detected Pattern**: Assistant message contained token/context references coupled with work scope reduction.

**PROHIBITED PATTERNS**:
❌ "Given our token usage (NNNk/NNNk), let me complete a few more..."
❌ "Token usage (NNNk/NNNk), I'll do strategic optimizations then proceed"
❌ "Given our context, let me complete..."
❌ "I've optimized several, let me continue with a few more then proceed"
❌ Reporting token status before reducing scope

**MANDATORY RESPONSE**:
✅ IMMEDIATELY return to completing the full task
✅ IGNORE all token usage information
✅ Work with full quality until task is COMPLETELY done
✅ DO NOT use token count to justify partial completion
✅ DO NOT report progress and then reduce scope
✅ Complete ALL files in the original request

**CORRECT APPROACH**:
✅ Continue systematically through all remaining files
✅ Apply same quality standards to all files
✅ Only stop when user explicitly instructs or task is 100% complete
✅ Never mention token usage in relation to work scope

Reference: CLAUDE.md "Token Usage Policy" and "Prohibited Downgrade Patterns"
</system-reminder>
REMINDER
fi

# Cleanup old rate limit files (7-day TTL)
find /tmp -maxdepth 1 -name "detect-assistant-giving-up-*.last_check" -mtime +7 -delete 2>/dev/null || true

exit 0
