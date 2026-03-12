# Plan: fix-work-with-issue-path-validation

## Problem

The `work-with-issue-agent` skill's Path Validation section checks that `ISSUE_PATH` contains the substring `/.claude/`,
but after the `relocate-claude-cat-to-cat` migration renamed `.claude/cat` → `.cat`, all issue paths now contain
`/.cat/issues/` instead of `/.claude/`. This stale check blocks ALL issues from being worked on. The error message in
the skill already acknowledges the correct expected format (`/.cat/issues/`), making the inconsistency obvious.

## Parent Requirements

None

## Reproduction Code

```
/cat:work v2.1-<any-issue>
```

The skill receives an ISSUE_PATH like `/workspace/.cat/issues/v2.1/fix-something/`, which does not contain `/.claude/`,
causing immediate STOP before any phase skill is invoked.

## Expected vs Actual

- **Expected:** Path validation passes for a valid ISSUE_PATH containing `/.cat/issues/`
- **Actual:** `ERROR: issue_path does not contain '/.claude/' — possible path typo.` — all work is blocked

## Root Cause

In `plugin/skills/work-with-issue-agent/first-use.md`, line 63, the substring check still uses `/.claude/` from before
the `.claude/cat` → `.cat` rename. The error message body was updated to show `/.cat/issues/` but the check itself was
not. The "Did you mean" suggestion also still references `.claude` instead of `.cat`.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Loosening or removing the typo-guard could allow malformed paths to proceed further before
  failing. The fix maintains the same guard purpose — just with the correct substring.
- **Mitigation:** Update all three places in the error block (check, error line, "Did you mean" line) to be consistent
  with `/.cat/issues/`.

## Files to Modify

- `plugin/skills/work-with-issue-agent/first-use.md` — update Path Validation section:
  - Line 63: change check from `/.claude/` to `/.cat/issues/`
  - Line 66: change `'/.claude/'` in the error message to `'/.cat/issues/'`
  - Line 69: change the "Did you mean" suggestion from referencing `.claude` to `.cat`

## Test Cases

- [ ] ISSUE_PATH containing `/.cat/issues/` passes validation (no STOP)
- [ ] ISSUE_PATH missing `/.cat/issues/` (e.g., a typo path) correctly triggers the STOP error
- [ ] Error message, expected format, and check are all consistent with `/.cat/issues/`

## Pre-conditions

- [ ] All dependent issues are closed (no dependencies)

## Sub-Agent Waves

### Wave 1

- Update the Path Validation section in `plugin/skills/work-with-issue-agent/first-use.md`:
  - Change the substring check from `/.claude/` to `/.cat/issues/` (line 63)
  - Change the error message text `'/.claude/'` to `'/.cat/issues/'` (line 66)
  - Update the "Did you mean" hint to reference `.cat` instead of `.claude` (line 69):
    `<ISSUE_PATH with any segment that looks like '.cat' misspelled replaced by '.cat'>`
  - Files: `plugin/skills/work-with-issue-agent/first-use.md`
- Update STATE.md to reflect completion
  - Files: `.cat/issues/v2.1/fix-work-with-issue-path-validation/STATE.md`

## Post-conditions

- [ ] The substring check in the Path Validation section uses `/.cat/issues/` instead of `/.claude/`
- [ ] The error message, expected format string, and the check are all consistent with `/.cat/issues/`
- [ ] Issues can be successfully worked on — path validation no longer blocks valid issue paths
- [ ] E2E: Running `/cat:work` with a valid issue path containing `/.cat/issues/` proceeds past path validation without
  error
- [ ] No regressions — ISSUE_PATH values that do NOT contain `/.cat/issues/` still trigger the STOP error
- [ ] All tests passing (`mvn -f client/pom.xml test`)
