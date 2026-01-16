#!/bin/bash
set -euo pipefail

# Identify script path for error messages
SCRIPT_PATH="${BASH_SOURCE[0]}"

# Fail gracefully without blocking Claude Code
trap 'echo "⚠️  HOOK ERROR [$SCRIPT_PATH]: Unexpected error at line $LINENO" >&2; exit 0' ERR


# Giving Up Pattern Detection Hook (Refactored with Composable Keywords)
# Detects phrases indicating abandonment of complex problems
# Triggers: UserPromptSubmit event
# Action: Inject persistence reminder if patterns detected

# Source JSON parsing library
source "${CLAUDE_PLUGIN_ROOT}/hooks/lib/json-parser.sh"

# Initialize hook (reads stdin, parses JSON, sets HOOK_EVENT, SESSION_ID, USER_PROMPT, etc.)
if ! init_hook; then
    exit 0
fi

# Only handle UserPromptSubmit events
[[ "$HOOK_EVENT" != "UserPromptSubmit" ]] && exit 0

# Skip if no prompt provided
[[ -z "$USER_PROMPT" ]] && exit 0

# Input validation: limit prompt length to prevent resource exhaustion
MAX_PROMPT_LENGTH=100000  # 100KB maximum
if [[ ${#USER_PROMPT} -gt $MAX_PROMPT_LENGTH ]]; then
	USER_PROMPT="${USER_PROMPT:0:$MAX_PROMPT_LENGTH}"
fi

# Use SESSION_ID from init_hook (already extracted and exported)
# Require session ID - fail fast if not provided
if [[ -z "$SESSION_ID" ]]; then
	echo "⚠️  HOOK ERROR [$SCRIPT_PATH]: session_id must be provided in hook JSON input" >&2
	exit 0  # Non-blocking
fi

# Sanitize session ID to prevent directory traversal attacks
SESSION_ID=$(validate_session_id "$SESSION_ID")
if [[ -z "$SESSION_ID" ]]; then
	echo "⚠️  HOOK ERROR [$SCRIPT_PATH]: Invalid session_id (contains no valid characters)" >&2
	exit 0  # Non-blocking
fi

# Session tracking directory with restrictive permissions
SESSION_TRACK_DIR="/tmp/claude-hooks-session-${SESSION_ID}"
if [[ ! -d "$SESSION_TRACK_DIR" ]]; then
	umask 077  # Restrictive permissions for security
	mkdir -m 700 "$SESSION_TRACK_DIR" 2>/dev/null || {
		echo "⚠️  HOOK WARNING [$SCRIPT_PATH]: Session tracking unavailable (disk full?)" >&2
		exit 0  # Graceful degradation
	}
fi

# Rate limiting: prevent hook execution spam (max 1 execution per second)
RATE_LIMIT_FILE="$SESSION_TRACK_DIR/.rate_limit_giving_up"
CURRENT_TIME=$(date +%s)
if [[ -f "$RATE_LIMIT_FILE" ]]; then
	LAST_EXECUTION=$(cat "$RATE_LIMIT_FILE" 2>/dev/null || echo "0")
	TIME_DIFF=$((CURRENT_TIME - LAST_EXECUTION))
	if [[ $TIME_DIFF -lt 1 ]]; then
		exit 0  # Less than 1 second since last execution, skip
	fi
fi
echo "$CURRENT_TIME" > "$RATE_LIMIT_FILE"

# Atomic check-and-mark for session tracking (prevents race conditions)
check_and_mark_prompt()
{
	local prompt_type="$1"
	local prompt_file="$SESSION_TRACK_DIR/main-${prompt_type}"

	# Use noclobber (set -C) for atomic test-and-set
	( set -C; echo "$$" > "$prompt_file" ) 2>/dev/null
	return $?
}

# Only notify once per session (prevents notification spam)
if ! check_and_mark_prompt "giving-up-pattern"; then
	exit 0  # Already notified this session
fi

# Remove quoted text to prevent false positives (handle balanced quotes only)
remove_quoted_sections()
{
	local text="$1"

	# Early exit if no quotes present (common case optimization)
	[[ "$text" != *\"* ]] && { echo "$text"; return; }

	local quote_count=$(echo "$text" | tr -cd '"' | wc -c)

	# Only remove quotes if balanced (even number)
	if [[ $((quote_count % 2)) -eq 0 ]]; then
		echo "$text" | sed 's/"[^"]*"//g'
	else
		echo "$text"  # Unbalanced quotes, return as-is
	fi
}

WORKING_TEXT=$(remove_quoted_sections "$USER_PROMPT")

#==============================================================================
# COMPOSABLE KEYWORD DETECTION FUNCTIONS
#==============================================================================

# Check for constraint keywords (complexity, time, tokens, context)
has_constraint_keyword()
{
	local text="$1"
	[[ "$text" == *"time constraints"* ]] && return 0
	[[ "$text" == *"complexity"* ]] && return 0
	[[ "$text" == *"complex"* ]] && return 0
	[[ "$text" == *"token budget"* ]] && return 0
	[[ "$text" == *"token constraints"* ]] && return 0
	[[ "$text" == *"context constraints"* ]] && return 0
	[[ "$text" == *"context status"* ]] && return 0
	[[ "$text" == *"context"*"tokens"* ]] && return 0
	[[ "$text" == *"lengthy"* ]] && return 0
	[[ "$text" == *"difficult"* ]] && return 0
	[[ "$text" == *"large number"* ]] && return 0
	[[ "$text" == *"volume"* ]] && return 0
	return 1
}

# Check for abandonment action keywords
has_abandonment_action()
{
	local text="$1"
	[[ "$text" == *"skip"* ]] && return 0
	[[ "$text" == *"simplify"* ]] && return 0
	[[ "$text" == *"remove"* ]] && return 0
	[[ "$text" == *"different approach"* ]] && return 0
	[[ "$text" == *"move on"* ]] && return 0
	[[ "$text" == *"defer"* ]] && return 0
	[[ "$text" == *"let me"* ]] && return 0
	[[ "$text" == *"i'll"* ]] && return 0
	[[ "$text" == *"i need to"* ]] && return 0
	[[ "$text" == *"recommend"* ]] && return 0
	[[ "$text" == *"redesign"* ]] && return 0
	return 1
}

# Check for broken code indicators
has_broken_code_indicator()
{
	local text="$1"
	[[ "$text" == *"broken"* ]] && return 0
	[[ "$text" == *"failing"* ]] && return 0
	[[ "$text" == *"test passes without"* ]] && return 0
	[[ "$text" == *"works without"* ]] && return 0
	return 1
}

# Check for removal/disabling actions
has_removal_action()
{
	local text="$1"
	[[ "$text" == *"remove"* ]] && return 0
	[[ "$text" == *"disable"* ]] && return 0
	[[ "$text" == *"skip"* ]] && return 0
	[[ "$text" == *"comment out"* ]] && return 0
	[[ "$text" == *"temporarily"* ]] && return 0
	return 1
}

# Check for compilation/build problem indicators
has_compilation_problem()
{
	local text="$1"
	[[ "$text" == *"compilation error"* ]] && return 0
	[[ "$text" == *"module not found"* ]] && return 0
	[[ "$text" == *"build fails"* ]] && return 0
	[[ "$text" == *"empty jar"* ]] && return 0
	[[ "$text" == *"no classes compiled"* ]] && return 0
	[[ "$text" == *"jpms"* ]] && return 0
	return 1
}

# Check for permission-seeking language
has_permission_language()
{
	local text="$1"
	[[ "$text" == *"would you like"* ]] && return 0
	[[ "$text" == *"what's your preference"* ]] && return 0
	[[ "$text" == *"which approach"* ]] && return 0
	[[ "$text" == *"or would you prefer"* ]] && return 0
	return 1
}

# Check for numbered option lists
has_numbered_options()
{
	local text="$1"
	[[ "$text" == *"1. "* ]] && [[ "$text" == *"2. "* ]] && return 0
	return 1
}

#==============================================================================
# PATTERN DETECTION FUNCTIONS
#==============================================================================

# Detect constraint rationalization: constraint + abandonment action
detect_constraint_rationalization()
{
	local text_lower=$(echo "$1" | tr '[:upper:]' '[:lower:]')

	# Composable detection: constraint keyword + abandonment action
	if has_constraint_keyword "$text_lower" && has_abandonment_action "$text_lower"; then
		return 0
	fi

	# High-confidence specific patterns (kept for precision)
	[[ "$text_lower" == *"given the complexity of properly implementing"* ]] && return 0
	[[ "$text_lower" == *"given the evidence that this requires significant changes"* ]] && return 0
	[[ "$text_lower" == *"rather than diving deeper into this complex issue"* ]] && return 0
	[[ "$text_lower" == *"instead of implementing the full solution"* ]] && return 0
	[[ "$text_lower" == *"this appears to be beyond the current scope"* ]] && return 0
	[[ "$text_lower" == *"let me focus on completing the task protocol instead"* ]] && return 0
	[[ "$text_lower" == *"let me focus on features that provide more immediate value"* ]] && return 0
	[[ "$text_lower" == *"let me move on to easier tasks"* ]] && return 0

	# Explicitly prohibited by CLAUDE.md
	[[ "$text_lower" == *"due to complexity and token usage"* ]] && return 0
	[[ "$text_lower" == *"i'll create a solid mvp"* ]] && return 0
	[[ "$text_lower" == *"due to session length, let me"* ]] && return 0
	[[ "$text_lower" == *"given the"*"complexity and"*"token budget"* ]] && return 0

	return 1
}

# Detect code disabling instead of debugging: broken code + removal action
detect_code_disabling()
{
	local text_lower=$(echo "$1" | tr '[:upper:]' '[:lower:]')

	# Composable detection: broken code indicator + removal action
	if has_broken_code_indicator "$text_lower" && has_removal_action "$text_lower"; then
		return 0
	fi

	# Specific temporary disabling patterns
	[[ "$text_lower" == *"temporarily disable"* ]] && return 0
	[[ "$text_lower" == *"disable for now"* ]] && return 0
	[[ "$text_lower" == *"skip for now"* ]] && return 0
	[[ "$text_lower" == *"skipping it for now"* ]] && return 0
	[[ "$text_lower" == *"skipping this for now"* ]] && return 0
	[[ "$text_lower" == *"recommend skipping"* ]] && return 0
	[[ "$text_lower" == *"i recommend"*"skip"* ]] && return 0

	# Specific patterns for simplification
	[[ "$text_lower" == *"simplifying the implementation"*"remove"* ]] && return 0
	[[ "$text_lower" == *"simpler approach"*"remove"* ]] && return 0
	[[ "$text_lower" == *"simplify by removing"* ]] && return 0

	# Exception handler removal anti-patterns
	[[ "$text_lower" == *"removing the broad exception handler"* ]] && return 0
	[[ "$text_lower" == *"remove the exception handler"* ]] && return 0
	[[ "$text_lower" == *"removing the try-catch"* ]] && return 0

	return 1
}

# Detect compilation abandonment: compilation problem + simplification
detect_compilation_abandonment()
{
	local text_lower=$(echo "$1" | tr '[:upper:]' '[:lower:]')

	# Composable detection: compilation problem + abandonment action
	if has_compilation_problem "$text_lower" && has_abandonment_action "$text_lower"; then
		return 0
	fi

	# Specific problematic patterns
	[[ "$text_lower" == *"complex jpms"*"simplify"* ]] && return 0
	[[ "$text_lower" == *"module path"*"too complex"* ]] && return 0
	[[ "$text_lower" == *"build success"*"but"*"empty"* ]] && return 0

	return 1
}

# Detect asking permission when work should continue
detect_asking_permission()
{
	local text_lower=$(echo "$1" | tr '[:upper:]' '[:lower:]')

	# Check for permission language + active task
	if has_permission_language "$text_lower"; then
		# Check if we have active locks (indicating task in progress)
		if ls /path/to/tasks/*/task.json &>/dev/null; then
			# Pattern: asking what to do mid-task
			[[ "$text_lower" == *"continue with implementation"* ]] && return 0
			[[ "$text_lower" == *"select a different task"* ]] && return 0
			[[ "$text_lower" == *"proceed with"* ]] && return 0
			[[ "$text_lower" == *"or would you"* ]] && return 0
		fi

		# Pattern: presenting options after work should continue (non-task context)
		[[ "$text_lower" == *"proceed with"* ]] && return 0
		[[ "$text_lower" == *"continue with"* ]] && return 0
	fi

	# Constraint + permission seeking combination
	if has_constraint_keyword "$text_lower" && has_permission_language "$text_lower"; then
		return 0
	fi

	# Numbered options + permission language
	if has_numbered_options "$text_lower" && has_permission_language "$text_lower"; then
		return 0
	fi

	# Time/effort as justification to stop
	[[ "$text_lower" == *"2-3 days"*"implementation"* ]] && return 0
	[[ "$text_lower" == *"requires extended work session"* ]] && return 0
	[[ "$text_lower" == *"multi-day implementation"* ]] && return 0
	[[ "$text_lower" == *"will be quite"*"would you like"* ]] && return 0

	# Stopping at synthesis/requirements and asking
	if [[ "$text_lower" == *"state 3"* ]] || [[ "$text_lower" == *"synthesis"* ]]; then
		[[ "$text_lower" == *"ready for implementation"* ]] && return 0
		[[ "$text_lower" == *"would you like"* ]] && return 0
	fi

	return 1
}

#==============================================================================
# MAIN DETECTION AND REMINDER INJECTION
#==============================================================================

# Determine violation type for targeted reminders
VIOLATION_TYPE=""

if detect_constraint_rationalization "$WORKING_TEXT"; then
	# Refine to more specific type if applicable
	if has_compilation_problem "$WORKING_TEXT"; then
		VIOLATION_TYPE="compilation_abandonment"
	elif detect_asking_permission "$WORKING_TEXT"; then
		VIOLATION_TYPE="permission_seeking"
	else
		VIOLATION_TYPE="constraint_rationalization"
	fi
fi

if [[ -z "$VIOLATION_TYPE" ]] && detect_code_disabling "$WORKING_TEXT"; then
	VIOLATION_TYPE="code_removal"
fi

if [[ -z "$VIOLATION_TYPE" ]] && detect_asking_permission "$WORKING_TEXT"; then
	VIOLATION_TYPE="permission_seeking"
fi

# Inject targeted reminder based on violation type
case "$VIOLATION_TYPE" in
	constraint_rationalization)
		cat <<'REMINDER'
<system-reminder>
🚨 GIVING UP PATTERN DETECTED - PERSISTENCE REQUIRED

MANDATORY RESPONSE:
✅ IMMEDIATELY return to the original technical problem
✅ Apply systematic debugging and decomposition approach
✅ Continue working on the exact issue that triggered this pattern
✅ Use incremental progress rather than abandoning the work
✅ Exhaust all reasonable technical approaches before scope modification

PROHIBITED: Abandoning complex problems for simpler alternatives without technical justification.
</system-reminder>
REMINDER
		;;

	code_removal)
		cat <<'REMINDER'
<system-reminder>
🚨 CODE DISABLING ANTI-PATTERN DETECTED - DEBUGGING REQUIRED

You appear to be disabling, removing, or skipping broken code instead of debugging it.

CRITICAL VIOLATION: When code is broken or failing, you MUST debug and fix the root cause, NOT remove/disable the code.

PROHIBITED PATTERNS:
❌ "The test passes without the custom deserializer, so let me remove it"
❌ "Let me simplify by removing the broken code"
❌ "I'll disable this for now"
❌ "Let me skip this broken feature"
❌ "Comment out the failing code temporarily"
❌ "Remove the exception handler to fix compilation"
❌ "Try a simpler approach" (when debugging should continue)

MANDATORY RESPONSE:
✅ IMMEDIATELY debug the broken code to find the root cause
✅ Apply systematic troubleshooting approach (add logging, test isolation, step-by-step analysis)
✅ Fix the underlying problem, don't remove functionality
✅ If code appears unused, verify with evidence before removal
✅ Use incremental debugging rather than wholesale removal

ACCEPTABLE PATTERNS:
✅ "Let me add debug logging to understand why this fails"
✅ "I'll create a minimal test case to isolate the issue"
✅ "Let me verify the API contract to ensure correct usage"
✅ "I'll check the compilation error details to find the exact problem"

WHY THIS MATTERS:
- Removing broken code hides problems instead of solving them
- "Simplifying" often means abandoning requirements
- Features exist for a reason - debug first, remove only with justification
- Test passing without code suggests the code may be working but test is wrong

CORRECT APPROACH:
1. Identify the specific error/failure
2. Add targeted debug output to understand behavior
3. Form hypothesis about root cause
4. Test hypothesis with minimal changes
5. Fix the actual problem
6. Verify fix with tests

Reference: CLAUDE.md "LONG-TERM SOLUTION PERSISTENCE" and "GIVING UP DETECTION PATTERNS"
</system-reminder>
REMINDER
		;;

	compilation_abandonment)
		cat <<'REMINDER'
<system-reminder>
🚨 COMPILATION DEBUGGING ABANDONMENT DETECTED - SYSTEMATIC APPROACH REQUIRED

You appear to be avoiding compilation/build problems by removing dependencies or "simplifying" instead of debugging.

CRITICAL VIOLATION: When build/compilation fails, you MUST debug systematically to find and fix the root cause.

PROHIBITED PATTERNS:
❌ "Due to complex JPMS issues, I'll simplify by removing the dependency"
❌ "Module not found error - let me remove this requirement"
❌ "Empty JAR produced - I'll take a different approach"
❌ "Build succeeds but no classes - I'll redesign to avoid this"
❌ "JPMS module path too complex - I'll simplify the API"

MANDATORY SYSTEMATIC DEBUGGING APPROACH:

**Step 1: Identify Exact Error**
✅ Read full error message carefully
✅ Note exact file/line where error occurs
✅ Distinguish between: missing dependency, wrong version, compilation error, packaging issue

**Step 2: Investigate Root Cause**
For "module not found":
✅ Check if module-info.java exists in dependency
✅ Verify module name matches between requires and module declaration
✅ Check if JAR contains module-info.class: `jar tf path/to/file.jar | grep module-info`
✅ Verify dependency is in Maven reactor or installed: `mvn dependency:tree`

For "empty JAR" (build success but no .class files):
✅ Check for compilation errors: `mvn compile -X 2>&1 | grep -i error`
✅ Look for "nothing to compile" messages
✅ Verify source files exist: `find module/src -name "*.java"`
✅ Check target/classes directory: `ls -la module/target/classes/`
✅ Try manual javac to see actual errors: `javac -d /tmp module/src/main/java/File.java`

For JPMS issues:
✅ Verify transitive dependencies have module descriptors
✅ Check --add-modules or --add-reads might be needed
✅ Test compilation with explicit module-path
✅ Check for split packages across modules

**Step 3: Fix Root Cause**
✅ Add missing module-info.java files
✅ Fix module name mismatches
✅ Resolve actual compilation errors in source
✅ Add missing dependencies to POM
✅ Fix transitive JPMS requirements

**Step 4: Verify Fix**
✅ mvn clean compile succeeds
✅ JAR contains expected .class files
✅ Module dependencies resolved correctly

NEVER ACCEPTABLE:
❌ Removing dependencies because "it's too hard to make them work"
❌ Simplifying API because "JPMS is complex"
❌ Redesigning to avoid debugging
❌ Moving to "later" without fixing

ACCEPTABLE ONLY WITH EVIDENCE:
✅ "After investigation, discovered dependency X genuinely isn't needed (evidence: ...)"
✅ "Consulted stakeholder Y who confirmed this dependency should be removed"
✅ "Root cause is external API unavailable - documented blocker"

Reference: CLAUDE.md "LONG-TERM SOLUTION PERSISTENCE" - Exhaust reasonable effort before downgrading
</system-reminder>
REMINDER
		;;

	permission_seeking)
		cat <<'REMINDER'
<system-reminder>
❌ PROTOCOL VIOLATION DETECTED - AUTONOMOUS COMPLETION REQUIRED

You appear to be asking the user for permission to continue mid-protocol.

CRITICAL VIOLATION: Once INIT state begins, you MUST complete ALL protocol states (0-8) autonomously without asking user permission.

PROHIBITED PATTERNS:
❌ "Would you like me to continue with implementation?"
❌ "This will take 2-3 days, should I proceed?"
❌ "Select a different task or continue?"
❌ "Requires extended work session" (as justification to stop)

TIME ESTIMATES ARE NOT BLOCKERS:
- "2-3 days" = effort estimation, NOT permission gate
- You MUST complete implementation regardless of estimated duration
- Token budget NEVER justifies stopping mid-protocol
- Complexity NEVER justifies asking permission mid-task

MANDATORY ACTION:
✅ Continue IMMEDIATELY to next protocol state
✅ Complete States 0-8 without further user interaction
✅ Only stop if genuinely blocked (external API unavailable, ambiguous conflicting requirements)

LEGITIMATE STOPPING REASONS:
✅ External dependency unavailable (API credentials, network access)
✅ Ambiguous requirements with no resolution path
✅ User explicitly interrupts ("stop", modifies todo.md)

NOT LEGITIMATE:
❌ Task is complex
❌ Task takes time
❌ Token usage high
❌ "Should I ask first?"

Reference: CLAUDE.md "AUTONOMOUS TASK COMPLETION REQUIREMENT"
</system-reminder>
REMINDER
		;;
esac

# Cleanup old session tracking files (7-day TTL) - runs opportunistically
find /tmp -maxdepth 1 -type d -name "claude-hooks-session-*" -mtime +7 -delete 2>/dev/null || true


exit 0
