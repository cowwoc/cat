# Plan: replace-prepare-subagent-with-script

## Goal
Replace the work-prepare LLM subagent with a deterministic Python script to reduce prepare phase latency from ~50s to
~4s. The current approach spawns a sonnet subagent that makes 8 LLM round-trips (~6s each) to perform entirely
deterministic work.

## Satisfies
None - infrastructure/performance optimization

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Script must handle all edge cases currently handled by SKILL.md (filtering, diagnostics, worktree
  creation, branch verification, existing work check, STATE.md update, token estimation)
- **Mitigation:** Existing test coverage for sub-scripts (get-available-issues.sh, check-existing-work.sh,
  issue-lock.sh). New script calls these internally.

## Current State
- Phase 1 (Prepare) spawns a `general-purpose` sonnet subagent via Task tool
- Subagent reads work-prepare/SKILL.md, then executes 8 steps with individual bash calls
- Each LLM round-trip costs ~6s, total ~50s
- All 8 steps are deterministic (no LLM judgment needed)
- Discovery script itself runs in ~3.4s

## Target State
- Phase 1 calls `python3 work-prepare.py` directly from main agent via single Bash call
- Script returns JSON matching existing output contract
- Total time: ~4s (script execution) vs ~50s (subagent)
- Main agent handles only the non-deterministic part: converting natural language filter to glob pattern

## Files to Create
- `plugin/scripts/work-prepare.py` - Main prepare script (~200 lines)

## Files to Modify
- `plugin/skills/work/SKILL.md` - Phase 1 section: replace Task tool delegation with direct Bash call to work-prepare.py
- `plugin/skills/work-prepare/SKILL.md` - Add header note that logic is now implemented as work-prepare.py script;
  SKILL.md retained as algorithm documentation

## Execution Steps
1. **Create work-prepare.py script** that:
   - Accepts args: `--session-id`, `--project-dir`, `--exclude-pattern` (optional), `--trust-level`
   - Step 1: Verifies `.claude/cat/` structure exists
   - Step 2: Calls `get-available-issues.sh` with appropriate flags, parses JSON result
   - Step 3: On `not_found`: gathers diagnostic info (blocked tasks with cross-version dep search, locked tasks,
     closed/total counts) and returns NO_TASKS JSON
   - Step 4: On `found`: reads PLAN.md, estimates tokens heuristically (files_to_create*5000 + files_to_modify*3000 +
     test_files*4000 + steps*2000)
   - Step 5: Creates worktree via `git worktree add`
   - Step 6: Verifies worktree branch matches expected (M351)
   - Step 7: Calls `check-existing-work.sh` for existing commits (M362/M394)
   - Step 8: Checks base branch for suspicious commits mentioning issue name (M394)
   - Step 9: Updates STATE.md in worktree to in-progress
   - Step 10: Returns READY JSON with all fields matching existing output contract
   - Error handling: releases lock on any failure
   - Uses existing scripts internally: get-available-issues.sh, check-existing-work.sh, issue-lock.sh

2. **Update work/SKILL.md Phase 1** to:
   - Have main agent convert natural language filter to glob pattern inline
   - Call `python3 work-prepare.py --session-id X --project-dir Y [--exclude-pattern Z]` directly
   - Parse JSON result (same handling as current subagent result)
   - Remove Task tool delegation for Phase 1

3. **Update work-prepare/SKILL.md** to:
   - Add note at top: "Algorithm documentation. Runtime logic implemented in `plugin/scripts/work-prepare.py`"
   - Keep existing content as reference documentation

4. **Write tests** for work-prepare.py:
   - Test argument parsing
   - Test diagnostic gathering with cross-version dependencies
   - Test token estimation heuristic
   - Test error handling (missing structure, script failures)
   - Test JSON output contract matches expected format

## Success Criteria
- [ ] Prepare phase completes in under 10 seconds (target: ~4s)
- [ ] JSON output contract unchanged (all existing fields present)
- [ ] All edge cases handled: NO_TASKS, LOCKED, OVERSIZED, ERROR, READY, potentially_complete
- [ ] Cross-version dependency diagnostic search works correctly (M454 fix integrated)
- [ ] Existing sub-scripts reused (get-available-issues.sh, check-existing-work.sh, issue-lock.sh)
- [ ] All tests pass including new tests for work-prepare.py
- [ ] No regressions in /cat:work end-to-end workflow
