# Plan: fix-potentially-complete-false-positive

## Problem

The `check_base_branch_commits()` function in `plugin/scripts/work-prepare.py` (line 426) searches the base branch for
commits mentioning the issue name. When found, it flags the issue as `potentially_complete` with `suspicious_commits`.

The function already filters out planning commits via `planning_prefixes` (line 455), but the filter does not cover
decomposition commits like `"config: decompose port-utility-scripts into 4 sub-issues"`. These commits mention
sub-issue names in their message body, causing every sub-issue of a decomposed parent to be flagged as potentially
complete — a false positive.

## Root Cause

Line 455 in `plugin/scripts/work-prepare.py`:
```python
planning_prefixes = ("planning:", "config: add issue", "config: add task", "config: mark")
```

Missing prefix: `"config: decompose"` — matches commits like `config: decompose port-utility-scripts into 4 sub-issues`.

## Satisfies

None (infrastructure bugfix)

## Approach

Add `"config: decompose"` to the `planning_prefixes` tuple. Add a unit test for `check_base_branch_commits()` that
verifies decomposition commits are filtered out.

## Execution Steps

1. **Step 1:** Add `"config: decompose"` to `planning_prefixes` in `check_base_branch_commits()`
   - File: `plugin/scripts/work-prepare.py` line 455
   - Change: `planning_prefixes = ("planning:", "config: add issue", "config: add task", "config: mark", "config: decompose")`

2. **Step 2:** Create unit test for `check_base_branch_commits()` false positive filtering
   - File: `plugin/scripts/tests/test_work_prepare.py` (new file)
   - Test that commits starting with each planning prefix are filtered out
   - Test that legitimate implementation commits are NOT filtered out
   - Use `unittest.mock.patch` to mock `subprocess.run` (no real git repo needed)
   - Test cases:
     - `"config: decompose port-utility-scripts into 4 sub-issues"` → filtered (returns None)
     - `"planning: add issue port-analysis-to-java to 2.1"` → filtered (returns None)
     - `"feature: port analysis scripts to java"` → NOT filtered (returns commit)
     - Mixed: one planning + one real commit → returns only the real commit

3. **Step 3:** Update STATE.md to closed, progress 100%

## Success Criteria

- [ ] Decomposition commits no longer trigger `potentially_complete` flag on sub-issues
- [ ] Regression test covers all planning prefix patterns including `config: decompose`
- [ ] Existing planning prefix filters still work (no regressions)
- [ ] All tests pass