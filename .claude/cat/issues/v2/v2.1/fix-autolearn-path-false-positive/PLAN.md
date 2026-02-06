# Plan: fix-autolearn-path-false-positive

## Problem
Pattern 12c in `auto_learn.py` triggers `wrong_working_directory` on Bash commands that intentionally handle missing
paths (e.g., `ls <path> || echo "removed"`). The pattern matches "No such file or directory" in output without checking
`exit_code`, causing false positives when commands use `||` fallback (exit code 0).

## Satisfies
None - infrastructure bugfix

## Reproduction Code
```bash
# Merge subagent runs after worktree removal:
ls -la /workspace/.claude/cat/worktrees/2.1-issue-name 2>&1 || echo "Worktree successfully removed"
# Output contains "No such file or directory" but exit code is 0
# Pattern 12c triggers wrong_working_directory false positive
```

## Expected vs Actual
- **Expected:** No mistake detected when exit_code is 0 (command handled the missing path intentionally)
- **Actual:** `wrong_working_directory` mistake detected because Pattern 12c only checks output text, not exit_code

## Root Cause
`plugin/hooks/posttool_handlers/auto_learn.py` line 188-193: Pattern 12c checks for "No such file or directory" in Bash
output but does NOT check `exit_code != 0`. Compare with Pattern 13 (line 195-200) which correctly guards with
`exit_code != 0`.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Could miss genuine wrong_working_directory errors if exit code happens to be 0 despite real path
  issues. However, real path errors from failed commands will have non-zero exit codes.
- **Mitigation:** Existing tests verify pattern matching; add test for exit_code=0 case

## Files to Modify

| File | Change |
|------|--------|
| `plugin/hooks/posttool_handlers/auto_learn.py` | Add `exit_code != 0` guard to Pattern 12c (line 188) |
| Test file for auto_learn.py | Add regression test for exit_code=0 with path error output |

## Acceptance Criteria
- [ ] Bug fixed - Pattern 12c no longer triggers when exit_code is 0
- [ ] Regression test added for the false positive scenario
- [ ] Existing tests still pass
- [ ] No new issues introduced

## Execution Steps
1. **Step 1:** Edit `plugin/hooks/posttool_handlers/auto_learn.py`
   - Add `exit_code != 0 and` guard to the Pattern 12c condition at line 188
   - Change from: `if tool_name == "Bash" and re.search(`
   - Change to: `if tool_name == "Bash" and exit_code != 0 and re.search(`

2. **Step 2:** Add regression test
   - Find or create test file for auto_learn.py
   - Add test case: Bash output with "No such file or directory" + `/workspace` path + exit_code=0 should NOT trigger
     wrong_working_directory
   - Add test case: same output + exit_code=1 should still trigger wrong_working_directory

3. **Step 3:** Run tests
   - `python3 /workspace/run_tests.py`

4. **Step 4:** Commit changes
   - Commit type: `config:` (plugin hook modification)

## Success Criteria
- [ ] All tests pass including new regression tests
- [ ] No false positives for intentional path handling with exit_code=0
- [ ] Real path errors (exit_code != 0) still detected
