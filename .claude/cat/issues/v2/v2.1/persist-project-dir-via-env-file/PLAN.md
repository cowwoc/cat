# Plan: persist-project-dir-via-env-file

## Problem
`CLAUDE_PROJECT_DIR` is only available in hook execution context, not in `!` backtick skill preprocessing or Bash tool
commands. The `load-skill.sh` script receives it as `$4` from the `!` backtick line `"${CLAUDE_PROJECT_DIR}"`, but since
the variable doesn't exist in that shell context, it passes an empty string. This causes `work-prepare.py` and other
scripts to fail with `"project-dir cannot be empty"`.

Empirical evidence:
- `!` backtick context: no `CLAUDE_PROJECT_DIR` in `env`
- Bash tool context: no `CLAUDE_PROJECT_DIR` in `env`
- Hook context: `CLAUDE_PROJECT_DIR` is set (documented at https://code.claude.com/docs/en/hooks)

Previous fixes (`self-discover-env-vars`, `preprocess-env-vars-in-skill-content`) addressed symptoms but not root cause.

## Satisfies
None - infrastructure/bugfix

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Must not break existing hook scripts that read CLAUDE_PROJECT_DIR from env
- **Mitigation:** Additive change - adds env persistence, doesn't remove existing behavior

## Solution
Use Claude Code's `CLAUDE_ENV_FILE` mechanism (documented for SessionStart hooks) to persist `CLAUDE_PROJECT_DIR` into
all subsequent Bash tool commands. Then remove `load-skill.sh` substitution of `CLAUDE_PROJECT_DIR` since skill content
can reference it as a live shell variable.

## Files to Modify
- `plugin/hooks/hooks.json` - Add new SessionStart hook entry (or extend existing)
- `plugin/hooks/persist-project-dir.sh` - New script: write CLAUDE_PROJECT_DIR to CLAUDE_ENV_FILE
- `plugin/scripts/load-skill.sh` - Remove CLAUDE_PROJECT_DIR substitution (lines 19, 24)
- `plugin/skills/work/content.md` - Verify `${CLAUDE_PROJECT_DIR}` works as live shell variable

## Acceptance Criteria
- [ ] SessionStart hook persists CLAUDE_PROJECT_DIR to CLAUDE_ENV_FILE
- [ ] `env | grep CLAUDE_PROJECT_DIR` returns the project dir in Bash tool context after session start
- [ ] `load-skill.sh` no longer substitutes CLAUDE_PROJECT_DIR (only CLAUDE_PLUGIN_ROOT and CLAUDE_SESSION_ID)
- [ ] `/cat:work` runs without the empty project-dir error
- [ ] Existing hook scripts that read CLAUDE_PROJECT_DIR from env still work

## Execution Steps
1. **Create persist-project-dir.sh** SessionStart hook script
   - Read `CLAUDE_PROJECT_DIR` from hook environment
   - Write `export CLAUDE_PROJECT_DIR=<value>` to `$CLAUDE_ENV_FILE`
   - Files: `plugin/hooks/persist-project-dir.sh`
2. **Register SessionStart hook** in hooks.json
   - Add entry to SessionStart array
   - Files: `plugin/hooks/hooks.json`
3. **Remove CLAUDE_PROJECT_DIR substitution** from load-skill.sh
   - Remove `project_dir_escaped` and its sed line
   - Keep CLAUDE_PLUGIN_ROOT and CLAUDE_SESSION_ID substitutions
   - Files: `plugin/scripts/load-skill.sh`
4. **Run tests** to verify no regressions
   - `python3 /workspace/run_tests.py`
5. **Verify manually** that `/cat:work` no longer errors on empty project-dir
