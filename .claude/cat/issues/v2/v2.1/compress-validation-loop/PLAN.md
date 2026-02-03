# Plan: compress-validation-loop

## Problem
The /cat:work compression workflow and /cat:compare-docs validation can produce different execution equivalence scores when run in separate sessions, indicating potential issues with how compressions are being validated or how /cat:learn identifies root causes.

## Satisfies
None - infrastructure/tooling issue

## Root Cause
To be determined through automated iteration of compress -> validate -> learn cycle.

## Risk Assessment
- **Risk Level:** MEDIUM
- **Regression Risk:** Changes to /cat:learn could affect other RCA workflows
- **Mitigation:** Run full test suite after /cat:learn modifications

## Files to Modify
- `scripts/compress-validate-loop.py` - New orchestration script (create)
- `plugin/skills/learn/learn.md` - Potential improvements based on findings

## Execution Steps
1. **Create Python orchestration script** that:
   - Manages two external Claude processes via FIFO
   - Handles plugin reinstall (`claude plugin uninstall/install cat@cat`)
   - Removes old worktrees before each iteration
   - Runs /cat:work for compression in process 1
   - Runs /cat:compare-docs validation in process 2
   - Iterates /cat:learn on score mismatches
   - Evaluates /cat:learn effectiveness and provides corrective prompts
   - Merges only /cat:learn fixes (discards compressed files)
   - Loops until all scores are 1.0

2. **Run the loop** to identify compression/validation issues

3. **Update /cat:learn** based on any deficiencies found in step 9 evaluation

## Test Cases
- [ ] Script successfully manages FIFO-based Claude process interaction
- [ ] Plugin reinstall works correctly
- [ ] Compression scores validated across processes
- [ ] /cat:learn fixes are properly isolated and merged
- [ ] Loop terminates when all scores reach 1.0

## Acceptance Criteria
- [ ] Bug fixed - compression validation is consistent across sessions
- [ ] Regression test added - validation loop can be re-run
- [ ] No new issues introduced
