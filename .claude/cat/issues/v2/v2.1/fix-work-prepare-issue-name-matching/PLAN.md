# Plan: fix-work-prepare-issue-name-matching

## Problem
The `/cat:work` skill's argument regex requires a version prefix (e.g., `2.1-unify-hook-dispatchers`). When a user
passes just the issue name (e.g., `unify-hook-dispatchers`), the regex doesn't match, `ISSUE_ID_ARG` stays empty, and
the prepare script silently falls through to "find next available task" mode — selecting an unrelated task with no
warning.

## Satisfies
None - usability fix

## Reproduction Code
```
/cat:work unify-hook-dispatchers
# Expected: selects unify-hook-dispatchers
# Actual: selects ci-build-jlink-bundle (next available task)
```

## Expected vs Actual
- **Expected:** Either select the named issue (searching across versions) or warn that the argument format is invalid
- **Actual:** Silently ignores the argument and picks an unrelated task

## Root Cause
The skill's argument parsing regex `^[0-9]+\.[0-9]+(-[a-zA-Z0-9_-]+)?$` only matches version-prefixed IDs. Bare issue
names fall through to the else branch which treats them as filter patterns (or ignores them). Additionally,
`get-available-issues.sh` line 121 has the same version-prefix-only regex for TARGET auto-detection.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Minimal - only affects argument parsing, not task execution logic

## Files to Modify
- `plugin/skills/work/SKILL.md` - Update argument parsing to also match bare issue names (letters, numbers, dashes)
- `plugin/scripts/get-available-issues.sh` - Accept bare issue names and search for matching issue across versions

## Acceptance Criteria
- [ ] `/cat:work issue-name` (without version prefix) correctly selects the issue
- [ ] `/cat:work 2.1-issue-name` (with version prefix) continues to work
- [ ] Unrecognized arguments produce a warning instead of silent fallthrough
- [ ] When a bare name matches multiple versions, the script selects from the current branch's version

## Execution Steps
1. Update the argument regex in `plugin/skills/work/SKILL.md` to match bare issue names
2. Update `get-available-issues.sh` to accept bare issue names and resolve them to full issue IDs
3. Add a fallback warning when no pattern matches
4. Test with both formats: bare name and version-prefixed
