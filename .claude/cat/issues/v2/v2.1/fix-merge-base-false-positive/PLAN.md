# Plan: fix-merge-base-false-positive

## Problem
The `block_merge_commits.py` handler regex `r'(^|;|&&|\|)\s*git\s+merge'` matches `git merge-base` as
`git merge`, causing false positive blocks. Any `git merge-*` subcommand (merge-base, merge-tree, merge-file)
triggers the block incorrectly.

## Satisfies
None

## Reproduction Code
```bash
git merge-base v2.1 2.1-migrate-python-to-java
# BLOCKED: git merge without --ff-only may create merge commits
```

## Expected vs Actual
- **Expected:** `git merge-base` runs without being blocked
- **Actual:** Hook blocks it as if it were `git merge`

## Root Cause
Line 17 of `plugin/hooks/bash_handlers/block_merge_commits.py`:
```python
if not re.search(r'(^|;|&&|\|)\s*git\s+merge', command):
```
This matches `git merge` as a prefix of `git merge-base`. The regex needs a word boundary or negative
lookahead after `merge` to exclude `merge-*` subcommands.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Could accidentally allow `git merge` if regex is too permissive
- **Mitigation:** Test both `git merge` (should block) and `git merge-base` (should allow)

## Files to Modify
- `plugin/hooks/bash_handlers/block_merge_commits.py` - Fix regex on lines 17, 21, 36 to exclude merge-* subcommands
- `plugin/hooks/src/io/github/cowwoc/cat/hooks/bash/BlockMergeCommits.java` - Apply same fix to Java equivalent

## Test Cases
- [ ] `git merge branch` - still blocked (no --ff-only)
- [ ] `git merge --ff-only branch` - allowed
- [ ] `git merge --squash branch` - allowed
- [ ] `git merge` with --no-ff flag - blocked
- [ ] `git merge-base branch1 branch2` - allowed (was falsely blocked)
- [ ] `git merge-tree branch1 branch2` - allowed
- [ ] `git merge-file file1 file2 file3` - allowed
- [ ] `cmd1 && git merge-base x y` - allowed (with preceding command)

## Execution Steps
1. **Step 1:** Fix regex in `plugin/hooks/bash_handlers/block_merge_commits.py`
   - Files: `plugin/hooks/bash_handlers/block_merge_commits.py`
   - Change all three `re.search` patterns to add `(?!-)` negative lookahead after `merge`
   - Line 17: `r'(^|;|&&|\|)\s*git\s+merge(?!-)'`
   - Line 21: add `(?!-)` after `merge` in the no-ff check pattern
   - Line 36: Add `(?!-)` after `merge` in the --ff-only/--squash check
2. **Step 2:** Apply same fix to Java equivalent
   - Files: `plugin/hooks/src/io/github/cowwoc/cat/hooks/bash/BlockMergeCommits.java`
   - Apply identical negative lookahead pattern to Java regex
3. **Step 3:** Run test suite
   - Command: `python3 /workspace/run_tests.py`

## Success Criteria
- [ ] `git merge-base` no longer blocked by hook
- [ ] `git merge` without --ff-only still blocked
- [ ] All existing tests pass
- [ ] Regression test added for merge-base false positive
