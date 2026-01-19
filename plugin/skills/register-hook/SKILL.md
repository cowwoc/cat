---
name: register-hook
description: Create and register hook scripts with proper error handling and settings
allowed-tools: Bash, Write, Read, Edit
---

# Register Hook Skill

**Purpose**: Create hook scripts with mandatory error handling patterns and register them in settings.json with proper matcher syntax.

**Performance**: Ensures hooks work correctly, prevents registration errors, enforces restart requirement

## When to Use This Skill

### ✅ Use register-hook When:

- Creating new hook script from scratch
- Need to register hook in settings.json
- Want to ensure proper error handling pattern
- Setting up hook with specific trigger event

### ❌ Do NOT Use When:

- Modifying existing hook (use Edit tool)
- Hook already registered (verify first)
- Testing hook behavior (use manual execution)

## What This Skill Does

### 1. Creates Hook Script

```bash
# Creates script with mandatory pattern:
#!/bin/bash
set -euo pipefail

# Error handler - output helpful message to stderr on failure
trap 'echo "ERROR in <script-name>.sh at line $LINENO: Command failed: $BASH_COMMAND" >&2; exit 1' ERR

# Hook logic...
```

### 2. Sets Permissions

```bash
chmod +x ~/.claude/hooks/{hook-name}.sh
```

### 3. Registers in settings.json

```json
{
  "hooks": {
    "{TriggerEvent}": [
      {
        "matcher": "{tool-pattern}",
        "hooks": [
          {
            "type": "command",
            "command": "~/.claude/hooks/{hook-name}.sh"
          }
        ]
      }
    ]
  }
}
```

### 4. Warns About Restart

```markdown
⚠️ Please restart Claude Code for hook changes to take effect
```

### 5. Provides Test Instructions

```markdown
After restart, test hook with:
[specific command to trigger hook]
```

## Trigger Events

### Available Events

**SessionStart**: Runs when session starts or resumes after compaction
```json
"SessionStart": [
  {
    "hooks": [{"type": "command", "command": "~/.claude/hooks/my-hook.sh"}]
  }
]
```

**UserPromptSubmit**: Runs when user submits a prompt
```json
"UserPromptSubmit": [
  {
    "hooks": [{"type": "command", "command": "~/.claude/hooks/my-hook.sh"}]
  }
]
```

**PreToolUse**: Runs before tool execution (supports matchers)
```json
"PreToolUse": [
  {
    "matcher": "Bash",
    "hooks": [{"type": "command", "command": "~/.claude/hooks/my-hook.sh"}]
  }
]
```

**PostToolUse**: Runs after tool execution (supports matchers)
```json
"PostToolUse": [
  {
    "matcher": "Write|Edit",
    "hooks": [{"type": "command", "command": "~/.claude/hooks/my-hook.sh"}]
  }
]
```

**PreCompact**: Runs before context compaction
```json
"PreCompact": [
  {
    "hooks": [{"type": "command", "command": "~/.claude/hooks/my-hook.sh"}]
  }
]
```

## Matcher Syntax

### Tool-Level Filtering

**Matcher patterns filter by tool name only**. Command/path filtering must be done inside hook scripts.

**✅ CORRECT Matcher Syntax**:
```json
"matcher": "Bash"                   // Single tool
"matcher": "Write|Edit"             // Multiple tools (regex)
"matcher": "Notebook.*"             // Pattern matching
"matcher": "*"                      // All tools
// No matcher field = all tools
```

**❌ WRONG Matcher Syntax**:
```json
"matcher": "tool:Bash && command:*git*"  // ❌ 'tool:' prefix not supported
"matcher": "command:*echo*"              // ❌ command filtering not in matcher
"matcher": "path:**/*.java"              // ❌ path filtering not in matcher
```

### Command/Path Filtering in Hook

```bash
# Inside hook script - parse JSON input
JSON_INPUT=$(cat)
COMMAND=$(echo "$JSON_INPUT" | jq -r '.tool_input.command // empty')

# Now filter based on command content
if [[ "$COMMAND" == *"git filter-branch"* ]]; then
  echo "Blocking dangerous command" >&2
  exit 2
fi
```

## Usage

### Create Simple Hook

```bash
# Create hook that logs all bash commands
HOOK_NAME="log-bash-commands"
TRIGGER="PreToolUse"
MATCHER="Bash"

~/.claude/scripts/register-hook.sh \
  --name "$HOOK_NAME" \
  --trigger "$TRIGGER" \
  --matcher "$MATCHER" \
  --script-content "$(cat <<'SCRIPT'
#!/bin/bash
set -euo pipefail
trap 'echo "ERROR in log-bash-commands.sh at line $LINENO: Command failed: $BASH_COMMAND" >&2; exit 1' ERR

# Log bash command to file
JSON_INPUT=$(cat)
COMMAND=$(echo "$JSON_INPUT" | jq -r '.tool_input.command // "unknown"')
echo "[$(date -Iseconds)] $COMMAND" >> /tmp/bash-log.txt
SCRIPT
)"
```

### Create Blocking Hook

```bash
# Create hook that blocks dangerous git commands
HOOK_NAME="block-dangerous-git"
TRIGGER="PreToolUse"
MATCHER="Bash"

~/.claude/scripts/register-hook.sh \
  --name "$HOOK_NAME" \
  --trigger "$TRIGGER" \
  --matcher "$MATCHER" \
  --can-block true \
  --script-content "$(cat <<'SCRIPT'
#!/bin/bash
set -euo pipefail
trap 'echo "ERROR in block-dangerous-git.sh at line $LINENO: Command failed: $BASH_COMMAND" >&2; exit 1' ERR

JSON_INPUT=$(cat)
COMMAND=$(echo "$JSON_INPUT" | jq -r '.tool_input.command // empty')

# Block dangerous commands
if [[ "$COMMAND" == *"git filter-branch"* ]] || [[ "$COMMAND" == *"--all"* ]]; then
  echo '{"permissionDecision": "deny"}'
  echo "❌ BLOCKED: Dangerous git command detected" >&2
  exit 2
fi
SCRIPT
)"
```

### Create Session Start Hook

```bash
# Create hook that runs on session start
HOOK_NAME="inject-session-context"
TRIGGER="SessionStart"

~/.claude/scripts/register-hook.sh \
  --name "$HOOK_NAME" \
  --trigger "$TRIGGER" \
  --script-content "$(cat <<'SCRIPT'
#!/bin/bash
set -euo pipefail
trap 'echo "ERROR in inject-session-context.sh at line $LINENO: Command failed: $BASH_COMMAND" >&2; exit 1' ERR

# Inject context at session start
echo "## Session Context"
echo "Working directory: $(pwd)"
echo "Git branch: $(git branch --show-current)"
echo "Planning state: $(cat .planning/STATE.md 2>/dev/null | head -5 || echo 'No planning context')"
SCRIPT
)"
```

## Safety Features

### Mandatory Error Handling

- ✅ Enforces `set -euo pipefail`
- ✅ Requires trap with ERR handler
- ✅ Stderr output for errors
- ✅ Helpful diagnostic information

### Registration Validation

- ✅ Checks hook doesn't already exist
- ✅ Validates trigger event is valid
- ✅ Confirms matcher syntax correct
- ✅ Verifies settings.json is valid JSON

### Permission Management

- ✅ Makes script executable
- ✅ Validates script is readable
- ✅ Confirms script at correct path

## Workflow Integration

### Complete Hook Creation Workflow

```markdown
1. ✅ Identify hook need (what to trigger on)
2. ✅ Invoke register-hook skill
3. ✅ Skill creates script with error handling
4. ✅ Skill registers in settings.json
5. ✅ Skill warns about restart requirement
6. ✅ User restarts Claude Code
7. ✅ Test hook triggers correctly
8. ✅ Commit hook and settings.json together
```

## Output Format

Script returns JSON:

```json
{
  "status": "success",
  "message": "Hook registered successfully",
  "hook_name": "log-bash-commands",
  "hook_path": "~/.claude/hooks/log-bash-commands.sh",
  "trigger_event": "PreToolUse",
  "matcher": "Bash",
  "executable": true,
  "registered": true,
  "restart_required": true,
  "test_command": "Bash tool with any command",
  "timestamp": "2025-11-11T12:34:56-05:00"
}
```

## Related

- **CLAUDE.md § Hook Script Standards**: Mandatory requirements
- **CLAUDE.md § Hook Registration**: Registration checklist
- **settings.json**: Hook configuration file

## Troubleshooting

### Hook Not Triggering After Registration

```bash
# Most common cause: Forgot to restart
# Fix: Restart Claude Code

# Verify hook registered:
cat ~/.claude/settings.json | jq '.hooks.PreToolUse'

# Verify hook executable:
test -x ~/.claude/hooks/my-hook.sh
echo $? # Should be 0
```

### Hook Failing on Execution

```bash
# Check hook error output (stderr)
# Look for trap error message with line number

# Test hook manually:
echo '{"tool_name": "Bash", "tool_input": {"command": "echo test"}}' | \
  ~/.claude/hooks/my-hook.sh
```

### Matcher Not Working

```bash
# Remember: Matcher is tool-level only
# ✅ CORRECT: "matcher": "Bash"
# ❌ WRONG: "matcher": "Bash && command:git"

# For command filtering, parse JSON inside hook:
JSON_INPUT=$(cat)
COMMAND=$(echo "$JSON_INPUT" | jq -r '.tool_input.command')
```

### Settings.json Invalid After Registration

```bash
# Validate JSON syntax:
jq empty ~/.claude/settings.json

# If invalid, fix manually:
# 1. Open settings.json
# 2. Find syntax error (trailing comma, missing bracket, etc.)
# 3. Fix error
# 4. Validate: jq empty settings.json
# 5. Restart Claude Code
```

## Common Patterns

### Pattern 1: Validation Hook

```bash
# Validate something before tool execution
PreToolUse + can-block = validation hook
```

### Pattern 2: Logging Hook

```bash
# Log tool usage for audit
PostToolUse + no blocking = logging hook
```

### Pattern 3: Context Injection Hook

```bash
# Add context at session start
SessionStart + echo statements = context injection
```

### Pattern 4: User Prompt Hook

```bash
# React to user input
UserPromptSubmit + parse prompt = prompt hook
```

## Implementation Notes

The register-hook script performs:

1. **Validation Release**
   - Check hook name not already used
   - Validate trigger event valid
   - Confirm matcher syntax correct
   - Verify settings.json accessible

2. **Script Creation Release**
   - Write script with mandatory error handling
   - Add provided hook logic
   - Make executable (chmod +x)
   - Verify script readable

3. **Registration Release**
   - Parse settings.json
   - Add hook to appropriate trigger event
   - Add matcher if specified
   - Write updated settings.json

4. **Verification Release**
   - Validate settings.json still valid JSON
   - Confirm hook entry exists
   - Check script executable
   - Warn about restart requirement

5. **Documentation Release**
   - Return success status
   - Provide test instructions
   - List next steps
