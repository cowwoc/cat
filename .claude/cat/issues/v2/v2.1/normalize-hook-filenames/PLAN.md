# Plan: normalize-hook-filenames

## Current State
1. The hook file `plugin/hooks/session_start.sh` uses underscores, while all other hook scripts use hyphens
   (e.g., `detect-giving-up.sh`, `inject-claudemd-section.sh`, `warn-base-branch-edit.sh`).
2. `plugin/scripts/load-skill.sh` does not pass `CLAUDE_PROJECT_DIR` to handler scripts, causing
   `handler.sh` scripts (like status/handler.sh) to fail with "unbound variable".
3. `plugin/concepts/agent-architecture.md` incorrectly states that skill preprocessing `!` lines have
   access to `CLAUDE_PROJECT_DIR` as an env var. Empirical testing confirmed they do NOT — only
   `CLAUDE_PLUGIN_ROOT` and `CLAUDE_SESSION_ID` are substituted.

## Target State
1. Rename `session_start.sh` to `session-start.sh` and update all references.
2. Pass `CLAUDE_PROJECT_DIR` via `$(pwd)` as a 4th argument from SKILL.md `!` lines through
   `load-skill.sh` to handler scripts.
3. Correct the architecture documentation to reflect empirical findings.

## Satisfies
None - naming consistency and bugfix

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None if all references updated
- **Mitigation:** Grep for all references before and after

## Files to Modify
- `plugin/hooks/session_start.sh` - Rename to `session-start.sh`
- `plugin/hooks/hooks.json` - Update SessionStart command reference
- `plugin/scripts/load-skill.sh` - Accept 4th arg as CLAUDE_PROJECT_DIR and export it
- `plugin/skills/status/SKILL.md` - Pass `"$(pwd)"` as 4th arg to load-skill.sh
- `plugin/concepts/agent-architecture.md` - Correct env var availability table

## Execution Steps
1. **Step 1:** Rename the file: `git mv plugin/hooks/session_start.sh plugin/hooks/session-start.sh`
2. **Step 2:** Update `plugin/hooks/hooks.json` line 8: change `session_start.sh` to `session-start.sh`
3. **Step 3:** Grep the entire codebase for remaining `session_start.sh` references (excluding PLAN.md
   files in closed issues) and update any found
4. **Step 4:** Update `plugin/scripts/load-skill.sh`:
   - After line `CLAUDE_SESSION_ID="$3"`, add:
     ```
     CLAUDE_PROJECT_DIR="${4:-}"
     export CLAUDE_PROJECT_DIR
     ```
5. **Step 5:** Update `plugin/skills/status/SKILL.md` `!` line to pass `"$(pwd)"` as 4th arg:
   ```
   !`"${CLAUDE_PLUGIN_ROOT}/scripts/load-skill.sh" "${CLAUDE_PLUGIN_ROOT}" status "${CLAUDE_SESSION_ID}" "$(pwd)"`
   ```
6. **Step 6:** Update `plugin/concepts/agent-architecture.md` section "Environment Variable
   Availability (M359, M471)" (around line 407-415):
   - Change the skill preprocessing row from "YES | Set by plugin system before substitution" to
     "Partial | Only CLAUDE_PLUGIN_ROOT and CLAUDE_SESSION_ID are string-substituted in the command
     line. CLAUDE_PROJECT_DIR is NOT available unless passed explicitly as $(pwd)."
   - Add a note explaining the workaround: pass `"$(pwd)"` as argument from SKILL.md `!` lines
7. **Step 7:** Verify `session-start.sh` is executable (`chmod +x` if needed after rename)

## Success Criteria
- [ ] All tests pass after refactoring
- [ ] No functional references to `session_start.sh` remain
- [ ] `session-start.sh` is executable and functions identically
- [ ] `load-skill.sh` accepts and exports CLAUDE_PROJECT_DIR from 4th argument
- [ ] Architecture doc accurately describes env var availability in skill preprocessing
