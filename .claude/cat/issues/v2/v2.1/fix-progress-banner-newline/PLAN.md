# Plan: fix-progress-banner-newline

## Problem
The `get-progress-banner.sh` invocation in `plugin/skills/work/SKILL.md` has `--session-id` and its value
`"${CLAUDE_SESSION_ID}"` split across two lines (lines 40-41). When silent preprocessing (`!`) evaluates this via
eval, the newline causes the session ID to be treated as a separate command rather than the argument to `--session-id`.

This produces two errors:
1. `line 69: $2: unbound variable` — `--session-id` has no following argument due to the line break
2. `command not found: <session-id-value>` — the session ID is executed as a standalone command

## Reproduction Code
```
# Current SKILL.md lines 40-41:
!`${CLAUDE_PLUGIN_ROOT}/scripts/get-progress-banner.sh $ARGUMENTS --project-dir "${CLAUDE_PROJECT_DIR}" --session-id
"${CLAUDE_SESSION_ID}"`
```

## Expected vs Actual
- **Expected:** `--session-id` receives the session ID as its argument; script runs successfully
- **Actual:** Newline splits the command; session ID becomes a separate command; script fails with unbound variable

## Root Cause
The 120-character line wrapping convention caused the long command to be split at the `--session-id` boundary.
Silent preprocessing treats newlines as command separators.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** None — this is a single-line fix in a skill template
- **Mitigation:** Verify the command works after fixing by checking the full line is under eval correctly

## Files to Modify
- `plugin/skills/work/SKILL.md` — Join lines 40-41 into a single line

## Test Cases
- [ ] Original bug scenario: `--session-id` and value on same line — script receives all arguments correctly
- [ ] Edge cases: Long session IDs, empty ARGUMENTS — still work

## Execution Steps
1. **Edit SKILL.md:** Join lines 40-41 so the entire `!` command is on a single line

## Success Criteria
- [ ] The `!` silent preprocessing command is on a single line in SKILL.md
- [ ] No `$2: unbound variable` error when the script is invoked
- [ ] All existing tests pass (`python3 /workspace/run_tests.py`)
