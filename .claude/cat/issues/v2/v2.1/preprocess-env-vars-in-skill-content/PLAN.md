# Plan: preprocess-env-vars-in-skill-content

## Problem
Skill files (68 files) reference `${CLAUDE_PLUGIN_ROOT}`, `${CLAUDE_PROJECT_DIR}`, and `${CLAUDE_SESSION_ID}` in bash
code blocks. These variables are only available during `!` backtick preprocessing (SKILL.md exclamation lines), NOT as
shell environment variables. When the agent copies bash commands from skill instructions into Bash tool calls, the
variables are empty, causing "No such file or directory" errors.

The previous fix (`self-discover-env-vars`, closed) only addressed one script (`get-available-issues.sh`) with
self-discovery logic. The systemic issue across all skill files remains.

## Satisfies
None - infrastructure/bugfix (M471 recurrence of M359)

## Root Cause
`load-skill.sh` outputs skill content verbatim via `cat`. It does not substitute `${CLAUDE_PLUGIN_ROOT}` etc. with
actual values. The `!` backtick lines in SKILL.md run in a shell where these are passed as arguments, but the content
files loaded by `load-skill.sh` are just echoed as-is.

## Solution: Extend load-skill.sh to Substitute Variables
Modify `load-skill.sh` to replace environment variable references with literal values before outputting content to the
agent. The script already receives plugin root (`$1`) and session ID (`$3`) as arguments. Project dir can be derived.

### Implementation
1. In `load-skill.sh`, after reading content files, pipe through `sed` to replace:
   - `${CLAUDE_PLUGIN_ROOT}` → value of `$1` (ROOT argument)
   - `${CLAUDE_PROJECT_DIR}` → derived from project directory
   - `${CLAUDE_SESSION_ID}` → value of `$3` (session ID argument)
2. Apply substitution to both `context.list` file contents and `content.md`
3. No changes needed to the 68 skill files themselves - they keep using `${CLAUDE_PLUGIN_ROOT}` syntax

### Advantages
- Single-point fix (one file: `load-skill.sh`)
- No changes to any of the 68 skill files
- Variables become literal paths before agent sees them
- Backward compatible - `!` backtick preprocessing continues working

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** sed substitution must handle paths with special characters; must not break `!` backtick lines
- **Mitigation:** `!` backtick lines are processed separately (not by load-skill.sh), so no conflict

## Files to Modify
- `plugin/scripts/load-skill.sh` - Add sed-based variable substitution after content loading

## Acceptance Criteria
- [ ] Bash commands in skill content have literal paths when agent receives them
- [ ] `${CLAUDE_PLUGIN_ROOT}`, `${CLAUDE_PROJECT_DIR}`, `${CLAUDE_SESSION_ID}` are all substituted
- [ ] `!` backtick preprocessing still works (not affected by this change)
- [ ] Existing skills continue to function correctly

## Execution Steps
1. **Modify load-skill.sh** to add variable substitution
   - After loading content via `cat`, pipe through sed to replace all three variables
   - Derive PROJECT_DIR from script context or pass as argument
   - Files: `plugin/scripts/load-skill.sh`
2. **Test** by verifying skill content output contains literal paths
   - Run load-skill.sh manually and check output
   - Verify: `python3 /workspace/run_tests.py` passes
