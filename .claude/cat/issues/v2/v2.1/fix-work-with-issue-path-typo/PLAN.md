# Plan: fix-work-with-issue-path-typo

## Problem

When work-with-issue-agent constructs the arguments to pass to work-implement-agent, a path
typo (`.claire` instead of `.claude`) is silently passed downstream. The downstream agent
fails with confusing lock and worktree errors rather than a clear path error.

## Parent Requirements

None

## Reproduction Code

```
# Invoke work-implement-agent with a path containing a typo
# (as happened in mistake M523 when orchestrating 2.1-refactor-skill-loader-to-get-skill)
Skill tool:
  skill: "cat:work-implement-agent"
  args: "<agent_id> <issue_id> /path/with/.claire/cat/issues/... ..."
# Result: worktree/lock setup fails with confusing session ID mismatch errors
```

## Expected vs Actual

- **Expected:** Immediate clear error: "issue_path contains '.claire' — did you mean '.claude'? Got: /path/with/.claire/..."
- **Actual:** Silent propagation to work-implement-agent → lock acquired with wrong session → worktree failures

## Root Cause

work-with-issue-agent passes issue_path directly to work-implement-agent without validating
that the path refers to a real `.claude` directory. A single-character typo in path
construction goes undetected until deep in the implementation phase where it manifests as
cryptic session/lock errors (M523).

## Approaches

### A: Validate issue_path in work-with-issue-agent before delegation (chosen)
- **Risk:** LOW
- **Scope:** 1 file (targeted)
- **Description:** Add a validation step to SKILL.md that checks the issue_path argument
  contains `/.claude/` before constructing the Skill tool call for work-implement-agent.
  Fail fast with a human-readable error message.

### B: Validate in work-implement-agent on receipt
- **Risk:** LOW
- **Scope:** 1 file (targeted)
- **Description:** Validate the path on the receiving side inside work-implement-agent.
- **Rejected:** Validation should happen as early as possible (M523 showed the error propagates
  through multiple lock/worktree operations before failing). Catching it at the source (work-with-issue)
  avoids any lock acquisition at all.

### C: Rely on downstream PLAN.md read failure
- **Risk:** LOW
- **Scope:** No files
- **Description:** The current behavior — work-implement-agent fails when it tries to read
  PLAN.md from the wrong path.
- **Rejected:** Error message is cryptic (lock session mismatch, not "path not found"). Users
  have no idea the root cause is a path typo.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** None — the validation is additive and only runs before the Skill call
- **Mitigation:** The validation checks the standard `.claude` path component which is invariant

## Files to Modify

- `plugin/skills/work-with-issue-agent/SKILL.md` — Add path validation step before the
  work-implement-agent Skill tool invocation

## Impact Notes

Prevents silent path typos that corrupt worktree and lock state, discovered in mistake M523.

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Add a path validation step to `plugin/skills/work-with-issue-agent/SKILL.md` **immediately
  before** the work-implement-agent Skill tool invocation in Phase 1:
  - The validation must check that `${ISSUE_PATH}` contains the substring `/.claude/`
  - If valid: proceed silently
  - If invalid: display this exact error message format and STOP immediately:
    ```
    ERROR: issue_path does not contain '/.claude/' — possible path typo.
    Expected: a path containing /.claude/cat/issues/
    Actual:   ${ISSUE_PATH}
    Did you mean: ${ISSUE_PATH with any non-'.claude' segment replaced by '.claude'}?
    STOP. Fix the issue_path before re-invoking /cat:work.
    ```
  - The check should be expressed as a natural-language validation rule in the SKILL.md
    (not bash code — this is a Markdown skill file instructing the LLM)
  - Place the validation immediately after parsing the ARGUMENTS and before constructing
    the Skill tool call for work-implement-agent
  - File: `plugin/skills/work-with-issue-agent/SKILL.md`
- Update STATE.md: status closed, progress 100%
- Commit with type `bugfix:`

## Post-conditions

- [ ] work-with-issue-agent validates that issue_path contains `/.claude/` before invoking work-implement-agent
- [ ] Validation fails fast with a clear error message showing expected vs actual path format
- [ ] Error message includes the expected path format and actual value received
- [ ] No regression in normal work-with-issue execution when path is correct
- [ ] E2E: Manually trace through the SKILL.md instructions with a path containing `.claire` and confirm the validation step would catch it and display the error
