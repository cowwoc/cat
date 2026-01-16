#!/bin/bash
# Register Hook Script
# Creates hook scripts with proper error handling and registers them in settings.json
#
# Usage: register-hook.sh --name <name> --trigger <event> [--matcher <pattern>] [--can-block] --script-content <content>
#
# Arguments:
#   --name          Hook name (will be used as filename: <name>.sh)
#   --trigger       Trigger event (SessionStart, UserPromptSubmit, PreToolUse, PostToolUse, PreCompact)
#   --matcher       Tool pattern to match (optional, for PreToolUse/PostToolUse)
#   --can-block     If set, hook can block tool execution (optional)
#   --script-content  The bash script content for the hook
#
# Output: JSON with status, hook path, and registration details

set -euo pipefail
trap 'echo "{\"status\": \"error\", \"message\": \"ERROR at line $LINENO: $BASH_COMMAND\"}" >&2; exit 1' ERR

# Parse arguments
HOOK_NAME=""
TRIGGER=""
MATCHER=""
CAN_BLOCK="false"
SCRIPT_CONTENT=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --name)
            HOOK_NAME="$2"
            shift 2
            ;;
        --trigger)
            TRIGGER="$2"
            shift 2
            ;;
        --matcher)
            MATCHER="$2"
            shift 2
            ;;
        --can-block)
            CAN_BLOCK="true"
            shift
            ;;
        --script-content)
            SCRIPT_CONTENT="$2"
            shift 2
            ;;
        *)
            echo "{\"status\": \"error\", \"message\": \"Unknown argument: $1\"}"
            exit 1
            ;;
    esac
done

# Validate required arguments
if [[ -z "$HOOK_NAME" ]]; then
    echo "{\"status\": \"error\", \"message\": \"Missing required argument: --name\"}"
    exit 1
fi

if [[ -z "$TRIGGER" ]]; then
    echo "{\"status\": \"error\", \"message\": \"Missing required argument: --trigger\"}"
    exit 1
fi

if [[ -z "$SCRIPT_CONTENT" ]]; then
    echo "{\"status\": \"error\", \"message\": \"Missing required argument: --script-content\"}"
    exit 1
fi

# Validate trigger event
VALID_TRIGGERS="SessionStart UserPromptSubmit PreToolUse PostToolUse PreCompact"
if [[ ! " $VALID_TRIGGERS " =~ " $TRIGGER " ]]; then
    echo "{\"status\": \"error\", \"message\": \"Invalid trigger event: $TRIGGER. Valid: $VALID_TRIGGERS\"}"
    exit 1
fi

# Determine paths - fail-fast if CLAUDE_CONFIG_DIR not set
CLAUDE_DIR="${CLAUDE_CONFIG_DIR:?CLAUDE_CONFIG_DIR environment variable must be set}"
HOOKS_DIR="$CLAUDE_DIR/hooks"
SETTINGS_FILE="$CLAUDE_DIR/settings.json"
HOOK_PATH="$HOOKS_DIR/${HOOK_NAME}.sh"

# Create hooks directory if needed
mkdir -p "$HOOKS_DIR"

# Check if hook already exists
if [[ -f "$HOOK_PATH" ]]; then
    echo "{\"status\": \"error\", \"message\": \"Hook already exists: $HOOK_PATH\"}"
    exit 1
fi

# Validate script content
# 1. Must start with shebang
if [[ ! "$SCRIPT_CONTENT" =~ ^#!/ ]]; then
    echo "{\"status\": \"error\", \"message\": \"Script must start with shebang (e.g., #!/bin/bash)\"}"
    exit 1
fi

# 2. Syntax check via bash -n
TEMP_SCRIPT=$(mktemp)
echo "$SCRIPT_CONTENT" > "$TEMP_SCRIPT"
if ! bash -n "$TEMP_SCRIPT" 2>/dev/null; then
    rm -f "$TEMP_SCRIPT"
    echo "{\"status\": \"error\", \"message\": \"Script has syntax errors. Run 'bash -n' to check.\"}"
    exit 1
fi
rm -f "$TEMP_SCRIPT"

# 3. Block dangerous patterns (fail-fast security)
# Check each pattern separately for clear error messages
if echo "$SCRIPT_CONTENT" | grep -qE 'curl[^|]*\|[^|]*(ba)?sh'; then
    echo "{\"status\": \"error\", \"message\": \"BLOCKED: Script contains 'curl | sh' pattern (remote code execution risk)\"}"
    exit 1
fi
if echo "$SCRIPT_CONTENT" | grep -qE 'wget[^|]*\|[^|]*(ba)?sh'; then
    echo "{\"status\": \"error\", \"message\": \"BLOCKED: Script contains 'wget | sh' pattern (remote code execution risk)\"}"
    exit 1
fi
if echo "$SCRIPT_CONTENT" | grep -qE 'rm[[:space:]]+-rf[[:space:]]+/[^.]'; then
    echo "{\"status\": \"error\", \"message\": \"BLOCKED: Script contains 'rm -rf /' pattern (destructive operation)\"}"
    exit 1
fi
if echo "$SCRIPT_CONTENT" | grep -qE 'eval[[:space:]]+\$'; then
    echo "{\"status\": \"error\", \"message\": \"BLOCKED: Script contains 'eval \$' pattern (arbitrary code execution)\"}"
    exit 1
fi
if echo "$SCRIPT_CONTENT" | grep -qE 'exec[[:space:]]+<'; then
    echo "{\"status\": \"error\", \"message\": \"BLOCKED: Script contains 'exec <' pattern (input redirection risk)\"}"
    exit 1
fi

# Write hook script
echo "$SCRIPT_CONTENT" > "$HOOK_PATH"
chmod +x "$HOOK_PATH"

# Verify script is executable
if [[ ! -x "$HOOK_PATH" ]]; then
    echo "{\"status\": \"error\", \"message\": \"Failed to make hook executable: $HOOK_PATH\"}"
    exit 1
fi

# Create or update settings.json
if [[ ! -f "$SETTINGS_FILE" ]]; then
    echo '{}' > "$SETTINGS_FILE"
fi

# Build hook entry JSON
HOOK_COMMAND="~/.claude/hooks/${HOOK_NAME}.sh"
if [[ -n "$MATCHER" ]]; then
    HOOK_ENTRY=$(jq -n --arg matcher "$MATCHER" --arg cmd "$HOOK_COMMAND" \
        '{matcher: $matcher, hooks: [{type: "command", command: $cmd}]}')
else
    HOOK_ENTRY=$(jq -n --arg cmd "$HOOK_COMMAND" \
        '{hooks: [{type: "command", command: $cmd}]}')
fi

# Add hook to settings.json
UPDATED_SETTINGS=$(jq --arg trigger "$TRIGGER" --argjson entry "$HOOK_ENTRY" '
    .hooks //= {} |
    .hooks[$trigger] //= [] |
    .hooks[$trigger] += [$entry]
' "$SETTINGS_FILE")

echo "$UPDATED_SETTINGS" > "$SETTINGS_FILE"

# Validate settings.json is still valid JSON
if ! jq empty "$SETTINGS_FILE" 2>/dev/null; then
    echo "{\"status\": \"error\", \"message\": \"Settings.json is invalid after update\"}"
    exit 1
fi

# Determine test command
case "$TRIGGER" in
    "SessionStart")
        TEST_CMD="Restart Claude Code"
        ;;
    "UserPromptSubmit")
        TEST_CMD="Submit any prompt"
        ;;
    "PreToolUse"|"PostToolUse")
        if [[ -n "$MATCHER" ]]; then
            TEST_CMD="Use $MATCHER tool"
        else
            TEST_CMD="Use any tool"
        fi
        ;;
    "PreCompact")
        TEST_CMD="Wait for context compaction"
        ;;
    *)
        TEST_CMD="Unknown trigger"
        ;;
esac

# Output success JSON
jq -n \
    --arg status "success" \
    --arg message "Hook registered successfully" \
    --arg hook_name "$HOOK_NAME" \
    --arg hook_path "$HOOK_PATH" \
    --arg trigger_event "$TRIGGER" \
    --arg matcher "$MATCHER" \
    --argjson executable true \
    --argjson registered true \
    --argjson restart_required true \
    --arg test_command "$TEST_CMD" \
    --arg timestamp "$(date -Iseconds)" \
    '{
        status: $status,
        message: $message,
        hook_name: $hook_name,
        hook_path: $hook_path,
        trigger_event: $trigger_event,
        matcher: (if $matcher == "" then null else $matcher end),
        executable: $executable,
        registered: $registered,
        restart_required: $restart_required,
        test_command: $test_command,
        timestamp: $timestamp
    }'

echo "" >&2
echo "⚠️  Please restart Claude Code for hook changes to take effect" >&2
