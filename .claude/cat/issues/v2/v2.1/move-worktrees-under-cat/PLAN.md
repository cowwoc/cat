# Plan: move-worktrees-under-cat

## Current State
Worktrees are created at `${CLAUDE_PROJECT_DIR}/.worktrees/` which places them at the project root
alongside user code. This is visible clutter and not clearly associated with CAT.

## Target State
Worktrees are created at `${CLAUDE_PROJECT_DIR}/.claude/cat/.worktrees/` grouping all CAT-managed
directories under `.claude/cat/`.

## Satisfies
None - infrastructure cleanup

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Any active worktrees from prior sessions will be at the old path. The cleanup
  skill and discovery scripts must handle both old and new paths during transition.
- **Mitigation:** Search-and-replace is mechanical. Run full test suite to catch regressions.

## Files to Modify

**Path construction (primary - where the path is built):**
- `plugin/skills/work-prepare/SKILL.md` - line 225: `WORKTREE_PATH` assignment
- `plugin/skills/work/phase-prepare.md` - line 384: `WORKTREE_PATH` assignment
- `plugin/skills/delegate/SKILL.md` - line 487: `WORKTREE_PATH` assignment
- `plugin/scripts/get-available-issues.sh` - lines 490, 607: `worktree_path` construction

**Documentation references (secondary - mention the path pattern):**
- All other files in the 35-file list that reference `.worktrees/` in examples, constraints,
  or documentation text

## Acceptance Criteria
- [ ] Behavior unchanged - worktrees still created and managed correctly
- [ ] Tests passing - no regressions
- [ ] Code quality improved - all CAT artifacts consolidated under `.claude/cat/`

## Execution Steps
1. **Step 1:** Find all files referencing `.worktrees/` or `.worktrees` path pattern
   - Files: `plugin/` directory tree (scripts, skills, hooks, concepts)
2. **Step 2:** Replace `.worktrees/` with `.claude/cat/.worktrees/` in path construction sites
   - Files: work-prepare/SKILL.md, phase-prepare.md, delegate/SKILL.md, get-available-issues.sh
3. **Step 3:** Update documentation references in remaining files
   - Files: All 35 files from grep results
4. **Step 4:** Update `.gitignore` if `.worktrees` is listed there
5. **Step 5:** Run test suite to verify no regressions
   - Files: `python3 /workspace/run_tests.py`

## Success Criteria
- [ ] All references to `.worktrees/` updated to `.claude/cat/.worktrees/`
- [ ] All tests pass
- [ ] No hardcoded old paths remain in plugin directory
