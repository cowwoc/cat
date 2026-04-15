# Configurable CAT Work Directory Location

## Summary

Allow users to configure where CAT stores its working directory (currently `.cat/work/`) with support for moving it outside the project directory to `~/.cat/`. This avoids duplicate configuration loading that costs ~26K tokens per conversation when worktrees are inside the workspace.

## Problem

- Current worktrees at `.cat/work/worktrees/` are inside workspace
- Claude Code loads `.claude/rules/` from both main workspace and each worktree  
- Results in ~26K duplicate tokens per conversation with 13+ rule files
- No way for users to configure alternative location

## Solution

### 1. Add `workPath` Configuration

**Update `.cat/config.json` schema:**
```json
{
  "displayWidth": 50,
  "fileWidth": 120,
  "trust": "medium",
  "caution": "high",
  "curiosity": "high",
  "perfection": "high",
  "minSeverity": "low",
  "workPath": "${CLAUDE_PROJECT_DIR}/.cat/work"
}
```

**Configuration hierarchy (following Claude Code's pattern):**
- `.cat/config.local.json` (highest priority, gitignored, personal overrides)
- `.cat/config.json` (project-level, committed, team settings)
- `~/.cat/config.json` (user-level, personal defaults)

**Priority:** local > project > user

**Field specification:**
- `workPath`: CAT's working directory location
- Default: `${CLAUDE_PROJECT_DIR}/.cat/work`
- Variable: `${CLAUDE_PROJECT_DIR}` injected by `InjectEnv.java`
- Recommended: `~/.cat` (to avoid duplicate config loading)

### 2. Create `/cat:config` Skill

**Purpose:** Configure CAT work directory location and manage all related settings automatically

**Workflow:**

1. **Prompt user** (via AskUserQuestion):
   - Option 1: "Home directory (~/.cat) - **Recommended**"
     - Avoids duplicate config loading
     - Reduces ~26K tokens per conversation
     - Note: "This will update `.claude/settings.json` to allow Claude Code to read/write `~/.cat/`"
   - Option 2: "Project directory (.cat/work)"
     - Legacy default
     - No settings.json changes needed

2. **If "Home directory" selected:**
   
   a. **Update `.claude/settings.json`:**
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
   - Use Edit tool with careful JSON merging
   - Append to arrays, preserve existing config
   
   b. **Update `.cat/config.json`:**
   ```json
   {
     "workPath": "~/.cat",
     ...existing fields...
   }
   ```
   
   c. **Create migration marker** at `${CLAUDE_PROJECT_DIR}/.cat/migration-pending.json`:
   ```json
   {
     "oldPath": "${CLAUDE_PROJECT_DIR}/.cat/work",
     "newPath": "~/.cat",
     "timestamp": "2026-04-15T10:30:00Z"
   }
   ```
   
   d. **Instruct user:** "Please restart Claude Code for changes to take effect"

3. **If "Project directory" selected:**
   
   a. **Update `.cat/config.json`:**
   ```json
   {
     "workPath": "${CLAUDE_PROJECT_DIR}/.cat/work",
     ...existing fields...
   }
   ```
   
   b. **Update `.cat/.gitignore`:**
   - Add entry `work`
   - Create `.cat/.gitignore` if doesn't exist

4. **After restart (SessionStart hook):**
   
   When `migration-pending.json` exists:
   - Delete old directory at `${oldPath}`
   - Calculate and remove old `.gitignore` entry (if applicable)
   - Delete `migration-pending.json` marker
   - **No user prompt** - automatic cleanup

### 3. `.gitignore` Management

**Case 1: `workPath` under `${CLAUDE_PROJECT_DIR}/.cat/`**
- Target: `${CLAUDE_PROJECT_DIR}/.cat/.gitignore`
- Entry: relative path from `.cat/` to `workPath`
- Example: `.cat/work` → entry `work`
- Don't walk up - always use `.cat/.gitignore`
- Create `.cat/.gitignore` if doesn't exist

**Case 2: `workPath` under `${CLAUDE_PROJECT_DIR}` but not `.cat/`**
- Walk up from `workPath` to find first `.gitignore`
- Stop at `${CLAUDE_PROJECT_DIR}` (don't go above project root)
- If not found: create at `${CLAUDE_PROJECT_DIR}/.gitignore`
- Calculate relative path from `.gitignore` to `workPath`
- Add to `.gitignore`

**Case 3: `workPath` outside `${CLAUDE_PROJECT_DIR}`**
- No `.gitignore` update needed

**Algorithm:**
1. Expand `${CLAUDE_PROJECT_DIR}` in `workPath`
2. Check if under project → No: skip (Case 3)
3. Check if under `.cat/` → Yes: Case 1, No: Case 2
4. When removing: calculate what entry would have been, remove if exists

### 4. Update All CAT Agents

**Pattern for reading `workPath`:**
```markdown
1. Read config in priority order:
   - .cat/config.local.json (local)
   - .cat/config.json (project)
   - ~/.cat/config.json (user)
2. Extract workPath (default: ${CLAUDE_PROJECT_DIR}/.cat/work)
3. Expand ${CLAUDE_PROJECT_DIR}
4. Use resolved path
```

**Affected agents:**
- `cat:work-prepare-agent` - creates worktrees at `${workPath}/worktrees/`
- All agents using `.cat/work/` for temporary files

### 5. Documentation Updates

**README.md section:**
```markdown
## Configuration

### Work Directory Location

Default: `.cat/work/` inside your project

**Recommended:** Move to `~/.cat/`
- Avoids duplicate configuration loading
- Reduces ~26K tokens per conversation
- Works seamlessly with sandbox mode

**Setup:**
Run `/cat:config` and select "Home directory (~/.cat)"

**What happens:**
- Updates `.cat/config.json` with `"workPath": "~/.cat"`
- Updates `.claude/settings.json`:
  - Adds `~/.cat` to `additionalDirectories` (Claude's Read/Edit tools)
  - Adds `~/.cat` to `sandbox.filesystem.allowWrite` (Bash commands)
- Restart Claude Code to apply
- Automatically migrates and cleans up old `.cat/work/`
```

## Implementation Steps

1. ✅ Add `workPath` field to `.cat/config.json` schema
2. ✅ Create `/cat:config` skill with AskUserQuestion workflow
3. ✅ Implement settings.json auto-update logic
4. ✅ Implement `.gitignore` management (3 cases)
5. ✅ Create SessionStart hook for migration cleanup
6. ✅ Update `cat:work-prepare-agent` to read `workPath`
7. ✅ Update other agents using `.cat/work/`
8. ✅ Add README.md documentation

## Acceptance Criteria

- [ ] Config hierarchy works: `.cat/config.local.json` > `.cat/config.json` > `~/.cat/config.json`
- [ ] `/cat:config` updates config with `workPath` (preserves existing fields)
- [ ] External path: auto-updates `.claude/settings.json` (`additionalDirectories`, `sandbox.filesystem.allowWrite`)
- [ ] `.gitignore` management:
  - Case 1 (under `.cat/`): add to `.cat/.gitignore`
  - Case 2 (under project, not `.cat/`): walk up, create at project root if needed
  - Case 3 (external): skip
  - Correct relative paths
  - Remove old entries on path change
- [ ] Two-phase migration: create new → restart → auto-delete old + cleanup
- [ ] All agents read `workPath` with proper priority
- [ ] README recommends `~/.cat/`, explains settings.json updates
- [ ] Settings.json merging preserves existing config
- [ ] Token savings: ~26K per conversation

## Technical Notes

- `${CLAUDE_PROJECT_DIR}` injected by `InjectEnv.java`
- Default sandbox read access (no explicit `allowRead` needed)
- Dynamic `.gitignore` calculation (no tracking in config)
- Handle edge cases: missing files, malformed JSON, conflicts
- Preserve all existing `.cat/config.json` fields when adding `workPath`
- Settings priority: `.claude/settings.local.json` > `.claude/settings.json` > `~/.claude/settings.json`

## Files Modified

- `.cat/config.json` (add `workPath` field)
- New skill: `plugin/skills/cat-config/` (SKILL.md, first-use.md)
- `plugin/hooks/SessionStart.sh` (check for migration marker)
- `plugin/skills/cat-work-prepare-agent/first-use.md` (read `workPath`)
- Other agents using `.cat/work/`
- `README.md` (add configuration section)
