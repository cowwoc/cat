# Plan

## Goal

Fix `/cat-update-client` skill to derive `CLAUDE_PLUGIN_ROOT` from marketplace.json and plugin.json when the environment variable is not set. This ensures the skill works correctly in development sessions where hook-provided environment variables may not be available.

## Problem

The skill currently uses `${CLAUDE_PLUGIN_ROOT}` directly in Step 2 commands:
```bash
rm -rf "${CLAUDE_PLUGIN_ROOT}/client"
cp -r /workspace/client/target/jlink "${CLAUDE_PLUGIN_ROOT}/client"
echo "2.1" > "${CLAUDE_PLUGIN_ROOT}/client/VERSION"
```

When `CLAUDE_PLUGIN_ROOT` is not set (e.g., in main session vs hook context), these commands fail or use incorrect paths.

## Solution

Add a derivation block at the start of Step 2 that:
1. Checks if `CLAUDE_PLUGIN_ROOT` is set
2. If not set, reads publisher from `/workspace/.claude-plugin/marketplace.json`
3. Reads plugin name and version from `/workspace/plugin/.claude-plugin/plugin.json`
4. Constructs the standard install path: `${CLAUDE_CONFIG_DIR}/plugins/cache/${publisher}/${name}/${version}`
5. Exports `CLAUDE_PLUGIN_ROOT` for use in subsequent commands

## Pre-conditions

None

## Post-conditions

- [ ] Step 2 includes a fallback block that derives `CLAUDE_PLUGIN_ROOT` when not set
- [ ] The derivation reads publisher from `.claude-plugin/marketplace.json` ("name" field)
- [ ] The derivation reads plugin name and version from `plugin/.claude-plugin/plugin.json`
- [ ] The derived path follows the pattern: `${CLAUDE_CONFIG_DIR}/plugins/cache/<publisher>/<name>/<version>`
- [ ] The skill outputs the derived value when fallback is used
- [ ] Running the skill when `CLAUDE_PLUGIN_ROOT` is not set succeeds and installs to the correct location
- [ ] Running the skill when `CLAUDE_PLUGIN_ROOT` is already set preserves the existing value (no override)

## Jobs

### Job 1: Update `.claude/skills/cat-update-client/SKILL.md`

Add the following fallback block at the start of Step 2, immediately before "Perform the plugin reinstall":

```markdown
First, ensure `CLAUDE_PLUGIN_ROOT` is set. If not set, derive it from plugin metadata files:

```bash
# Derive CLAUDE_PLUGIN_ROOT if not set
if [[ -z "${CLAUDE_PLUGIN_ROOT:-}" ]]; then
  # Read publisher from marketplace.json (top-level "name" field)
  PUBLISHER=$(grep -o '"name"[[:space:]]*:[[:space:]]*"[^"]*"' /workspace/.claude-plugin/marketplace.json | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
  
  # Read plugin name and version from plugin.json
  PLUGIN_NAME=$(grep -o '"name"[[:space:]]*:[[:space:]]*"[^"]*"' /workspace/plugin/.claude-plugin/plugin.json | sed 's/.*"\([^"]*\)"$/\1/')
  PLUGIN_VERSION=$(grep -o '"version"[[:space:]]*:[[:space:]]*"[^"]*"' /workspace/plugin/.claude-plugin/plugin.json | sed 's/.*"\([^"]*\)"$/\1/')
  
  # Construct plugin install path: ${CLAUDE_CONFIG_DIR}/plugins/cache/<publisher>/<name>/<version>
  export CLAUDE_PLUGIN_ROOT="${CLAUDE_CONFIG_DIR}/plugins/cache/${PUBLISHER}/${PLUGIN_NAME}/${PLUGIN_VERSION}"
  
  echo "Derived CLAUDE_PLUGIN_ROOT=${CLAUDE_PLUGIN_ROOT}"
fi
```

If any read fails, stop and report the error.

Then perform
```

**Insertion point:** After the Step 2 heading and before the existing "Perform the plugin reinstall" paragraph.

**Change type:** Insert new content (do not remove existing content)

After making the edit:
- Verify the fallback block is properly formatted
- Verify "Then perform" connects to the existing plugin reinstall instructions
- Test the skill manually by unsetting CLAUDE_PLUGIN_ROOT and running the commands

### Job 2: Update index.json and commit

After verifying the changes work:
1. Update `index.json` to set `"status": "closed"` and `"resolution": "implemented"`
2. Commit both files with message: `bugfix: derive CLAUDE_PLUGIN_ROOT in cat-update-client when not set`

## Implementation Notes

**File paths:**
- Publisher source: `/workspace/.claude-plugin/marketplace.json` (top-level "name" field, first occurrence)
- Plugin metadata: `/workspace/plugin/.claude-plugin/plugin.json` ("name" and "version" fields)
- Config directory: `${CLAUDE_CONFIG_DIR}` (already set by Claude Code)

**Install path pattern:**
```
${CLAUDE_CONFIG_DIR}/plugins/cache/<publisher>/<name>/<version>/
```

For CAT plugin:
- Publisher: `cat` (from marketplace.json)
- Name: `cat` (from plugin.json)
- Version: `2.1` (from plugin.json)
- Resulting path: `${CLAUDE_CONFIG_DIR}/plugins/cache/cat/cat/2.1/`

**Error handling:**
The grep commands should be wrapped in error checks. If any metadata file is missing or malformed, the skill should stop and report which file failed to read.
