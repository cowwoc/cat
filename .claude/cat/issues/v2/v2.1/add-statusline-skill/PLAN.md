# Plan: Add /cat:statusline Skill

## Goal
Create a `/cat:statusline` skill that configures the user's Claude Code statusline to use CAT's custom statusline
script, displaying git worktree, model name, session duration, session ID, and a color-coded context usage bar.

## Satisfies
None (UX enhancement)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Overwrites user's existing statusline config without backup
- **Mitigation:** Skill checks for existing config and asks before overwriting

## Files to Create
- `plugin/skills/statusline/SKILL.md` - Skill definition with allowed-tools
- `plugin/skills/statusline/first-use.md` - Skill instructions loaded by skill loader
- `plugin/scripts/statusline-command.sh` - The statusline script bundled with the plugin

## Files to Modify
- None (new skill only)

## Acceptance Criteria
- [ ] `/cat:statusline` skill exists and is invocable
- [ ] Running the skill copies `statusline-command.sh` to `${CLAUDE_PROJECT_DIR}/.claude/` with mode 755
- [ ] Creates `.claude/` directory if it doesn't exist
- [ ] Running the skill sets `statusLine.type` = `"command"` and `statusLine.command` =
  `"${CLAUDE_PROJECT_DIR}/.claude/statusline-command.sh"` in `.claude/settings.json`, preserving existing settings
- [ ] If `.claude/settings.json` already contains a `statusLine` entry, skill asks user before overwriting. If user
  declines, exit with informational message (not an error — this is a user choice, not a failure)
- [ ] Bundled script produces formatted output with color codes and emoji when fed JSON with `total_duration_ms`,
  `display_name`, `session_id`, and `used_percentage` fields on stdin
- [ ] Script handles missing git repo gracefully (displays "N/A" for worktree, exits 0)

## Execution Steps
1. **Create statusline script:** Copy the statusline-command.sh into `plugin/scripts/statusline-command.sh`
   - Files: `plugin/scripts/statusline-command.sh`
2. **Create skill definition:** Create SKILL.md with appropriate tools (Read, Write, Edit, Bash, AskUserQuestion)
   - Files: `plugin/skills/statusline/SKILL.md`
3. **Create skill instructions:** Write first-use.md with steps to install and configure the statusline
   - Files: `plugin/skills/statusline/first-use.md`
4. **Test:** Verify the skill loads correctly via the skill loader

## Success Criteria
- [ ] Skill appears in `/cat:help` output
- [ ] After invocation, `.claude/settings.json` contains correct `statusLine` JSON structure
- [ ] Installed script is executable and produces colored output when fed sample JSON on stdin
