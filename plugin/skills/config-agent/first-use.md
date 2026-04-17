<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# CAT Configuration Agent

Interactive wizard for configuring CAT settings, including work directory location.

## Primary Function: Work Directory Configuration

This skill guides users through configuring where CAT stores its working directory (worktrees, locks, temporary files).

**Current default:** `.cat/work/` inside the project directory  
**Recommended:** `~/.cat/` (outside project) to avoid duplicate config loading (~26K tokens saved per conversation)

## Workflow

### Step 1: Ask User for Work Directory Preference

Use AskUserQuestion to present options:

```
Where would you like CAT to store its working directory?

Option 1: Home directory (~/.cat) - **Recommended**
  ✓ Avoids duplicate configuration loading
  ✓ Reduces ~26K tokens per conversation
  ✓ Worktrees stored outside project
  Note: This will update .claude/settings.json to allow Claude Code to read/write ~/.cat/

Option 2: Project directory (.cat/work)
  • Legacy default
  • Worktrees stored inside project
  • No settings.json changes needed

Please enter 1 or 2:
```

**CRITICAL:** You MUST use the AskUserQuestion tool. Do NOT use conversational prompts. Forward the AskUserQuestion tool call verbatim to the user.

### Step 2: Process User Selection

Parse the user's response (should be "1" or "2").

### Option 1 Selected: Home Directory (~/.cat)

Execute the following steps:

#### 2.1. Update `.claude/settings.json`

Read the current settings file:

```bash
SETTINGS_FILE="${CLAUDE_PROJECT_DIR}/.claude/settings.json"
```

If the file doesn't exist, create it with:

```json
{
  "additionalDirectories": ["~/.cat"],
  "sandbox": {
    "filesystem": {
      "allowWrite": ["~/.cat"]
    }
  }
}
```

If the file exists, use Edit tool to:
- Add `"~/.cat"` to `additionalDirectories` array (if not already present)
- Add `"~/.cat"` to `sandbox.filesystem.allowWrite` array (if not already present)

**Important:** Preserve all existing configuration. Only add new entries, never remove existing ones.

#### 2.2. Update `.cat/config.json`

Read the current config:

```bash
CAT_CONFIG="${CLAUDE_PROJECT_DIR}/.cat/config.json"
```

Update the `workPath` field using Edit tool:

```json
{
  "workPath": "~/.cat",
  ...existing fields...
}
```

Preserve all other fields.

#### 2.3. Create Migration Marker

Create `${CLAUDE_PROJECT_DIR}/.cat/migration-pending.json`:

```json
{
  "oldPath": "${CLAUDE_PROJECT_DIR}/.cat/work",
  "newPath": "~/.cat",
  "timestamp": "2026-04-17T10:30:00Z"
}
```

Use current ISO timestamp.

#### 2.4. Update `.gitignore` (if needed)

If `.cat/work` was previously tracked (check `.cat/.gitignore` or project `.gitignore`), no action needed — migration will handle cleanup.

#### 2.5. Inform User

Tell the user:

```
Configuration updated successfully!

Changes made:
✓ .claude/settings.json: Added ~/.cat/ to additionalDirectories and sandbox.filesystem.allowWrite
✓ .cat/config.json: Set workPath to ~/.cat
✓ Migration marker created

IMPORTANT: Please restart Claude Code for changes to take effect.

After restart, the migration will automatically:
• Move existing worktrees from .cat/work/ to ~/.cat/
• Clean up old .cat/work/ directory
• Update .gitignore entries
```

### Option 2 Selected: Project Directory (.cat/work)

Execute the following steps:

#### 2.1. Update `.cat/config.json`

Ensure the `workPath` field is set:

```json
{
  "workPath": "${CLAUDE_PROJECT_DIR}/.cat/work",
  ...existing fields...
}
```

This should already be the default if the user hasn't changed it.

#### 2.2. Update `.cat/.gitignore`

Check if `.cat/.gitignore` exists. If not, create it.

Add entry `work` if not already present:

```bash
if ! grep -q "^work$" "${CLAUDE_PROJECT_DIR}/.cat/.gitignore" 2>/dev/null; then
  echo "work" >> "${CLAUDE_PROJECT_DIR}/.cat/.gitignore"
fi
```

#### 2.3. Inform User

Tell the user:

```
Configuration confirmed!

Changes made:
✓ .cat/config.json: workPath set to ${CLAUDE_PROJECT_DIR}/.cat/work
✓ .cat/.gitignore: Added 'work' entry

No restart required. CAT will use the project directory for working files.
```

## Error Handling

- If `.cat/config.json` doesn't exist: Report error and suggest running from project root
- If unable to write files: Report permission error
- If AskUserQuestion receives invalid input: Ask again with clarification

## Notes

- This skill does NOT perform the actual migration — that happens automatically on session start via SessionStart hook
- The migration-pending.json marker triggers the migration after restart
- Work directory must be writable and have sufficient space for worktrees
