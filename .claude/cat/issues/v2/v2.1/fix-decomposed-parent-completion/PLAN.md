# Plan: fix-decomposed-parent-completion

## Problem
`get-available-issues.sh` unconditionally skips any issue whose STATE.md contains `## Decomposed Into`, even when all
sub-issues are closed. This means decomposed parent issues can never be selected, validated, and marked closed — they
remain as open issues forever, blocking dependents.

## Satisfies
None

## Reproduction Code
```bash
# A decomposed parent with all sub-issues closed (e.g., migrate-python-to-java)
# STATE.md has status=open and "## Decomposed Into" section
# All 5 sub-issues have status=closed
# Running get-available-issues.sh will never return this issue
python3 scripts/work-prepare.py --session-id test --project-dir /workspace --trust-level medium
# Returns NO_TASKS even though migrate-python-to-java should be selectable
```

## Expected vs Actual
- **Expected:** When all sub-issues of a decomposed parent are closed, the parent should be returned as selectable so the
  agent can validate and close it
- **Actual:** Decomposed parents are unconditionally skipped with `continue` (line 459) in scan loop and rejected with
  `decomposed` status (line 590) in targeted selection

## Root Cause
Two locations in `get-available-issues.sh` skip decomposed parents without checking sub-issue completion:
1. **Scan loop (line 457-461):** `grep -q "^## Decomposed Into" -> continue` — skips unconditionally
2. **Targeted selection (line 588-593):** `grep -q "^## Decomposed Into" -> return decomposed status` — rejects
   unconditionally

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Could cause a decomposed parent to be selected for worktree creation when sub-issues are still open
  if the sub-issue-check logic has bugs
- **Mitigation:** Reuse existing sub-issue-checking logic from `validate_issue_status()` (line 215-252) which already knows
  how to check if all sub-issues are closed

## Files to Modify
- `plugin/scripts/get-available-issues.sh` - Modify both skip locations to check sub-issue completion before skipping
- `tests/scripts/get-available-issues.bats` - Add test cases for decomposed parent completion

## Test Cases
- [ ] Decomposed parent with all sub-issues closed — returned as selectable
- [ ] Decomposed parent with some sub-issues still open — still skipped
- [ ] Decomposed parent with no sub-issues listed — still skipped (defensive)
- [ ] Targeted selection of completed decomposed parent — returns found, not decomposed
- [ ] Targeted selection of incomplete decomposed parent — returns decomposed status

## Execution Steps
1. **Step 1:** Extract sub-issue-completion-check into a reusable function
   - Files: `plugin/scripts/get-available-issues.sh`
   - Create function `all_subissues_closed()` that takes a STATE.md path, reads the `## Decomposed Into` section,
     checks each sub-issue STATUS, returns 0 if all closed, 1 otherwise
   - Reuse the parsing logic already in `validate_issue_status()` (lines 218-251)

2. **Step 2:** Update scan loop (line 457-461) to check sub-issue completion
   - Files: `plugin/scripts/get-available-issues.sh`
   - Change from unconditional `continue` to: if `all_subissues_closed`, allow selection; else `continue`

3. **Step 3:** Update targeted selection (line 588-593) to check sub-issue completion
   - Files: `plugin/scripts/get-available-issues.sh`
   - Change from unconditional rejection to: if `all_subissues_closed`, proceed normally; else return decomposed status

4. **Step 4:** Add bats test cases for all 5 scenarios listed in Test Cases
   - Files: `tests/scripts/get-available-issues.bats`
   - Create test fixtures with decomposed parent STATE.md files (all-closed and partial-closed variants)

5. **Step 5:** Run full test suite
   - Run: `python3 /workspace/run_tests.py`

## Success Criteria
- [ ] All 5 test cases pass
- [ ] Existing tests still pass (no regressions)
- [ ] `migrate-python-to-java` would be returned by the script when all its sub-issues are closed
