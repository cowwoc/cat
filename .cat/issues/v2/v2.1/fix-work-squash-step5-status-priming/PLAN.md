# Plan: fix-work-squash-step5-status-priming

## Problem

`work-squash.md` Step 5 contains documentation that primed the work-squash subagent to use the invalid
status value `completed` instead of `closed` when closing issues. Two sources of priming combined to
cause the mistake:

1. The echo message `"STATE.md status is not 'closed' — fixing before returning"` framed the step as an
   active "fixing" task, leading the subagent to conclude it should modify the status value itself.
2. The migration script `plugin/migrations/2.1.sh` exposes `completed` as a historical status value
   (converting `completed → closed`), giving the subagent a plausible but invalid candidate value.

The correct approach: Step 5 is a verification step that should fail-fast with exit 1 when the status
is wrong, leaving the fix to the caller (work-with-issue). The subagent must NOT modify status values
itself.

## Root Cause Analysis

Learning entries M505-M506 document this mistake. Root cause: Step 5's pseudocode says "fixing before
returning" but only shows a check. The mismatch between the prose description ("fixing") and the code
(only a check) created ambiguity that was resolved using historical migration patterns as priming.

## Satisfies
None (infrastructure/bugfix)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Step 5 currently describes a recovery procedure (open STATE.md, amend commit). Replacing
  with fail-fast means work-squash no longer attempts self-repair — the caller must handle the error.
  This is the intended design since work-squash should not be making policy decisions.
- **Mitigation:** Verify that work-with-issue correctly handles FAILED status from work-squash and
  surfaces it to the user.

## Research Findings

`plugin/agents/work-squash.md` Step 5 (lines 194–228) is the only location that requires changes.
The valid status values are defined in `.claude/rules/state-schema.md` and enforced by
`StateSchemaValidator.java` at write time.

Current problematic text (line 215):
```bash
echo "STATE.md status is not 'closed' — fixing before returning"
```

Followed by "Blocking condition" (lines 219–228) that describes opening STATE.md and amending the commit.
This combination primed the subagent to believe its role was to set the status value.

The fix replaces the permissive echo + description with an explicit fail-fast that exits 1 and cites the
authoritative source for valid values.

## Files to Modify

- `plugin/agents/work-squash.md` — Revise Step 5 as follows:
  - Remove the misleading `echo "STATE.md status is not 'closed' — fixing before returning"` from the
    bash block
  - Replace the if-block body with an explicit `exit 1` and error message citing valid status values
  - Remove the "Blocking condition" prose section (lines 219–228) that instructed the subagent to
    self-modify STATE.md — this logic belongs in the caller
  - Add a comment above the status check citing `.claude/rules/state-schema.md` as the authoritative
    source of valid status values, listing them: `open`, `in-progress`, `closed`, `blocked`
  - Add a note that only `closed` is valid for an issue being closed, and that `StateSchemaValidator`
    enforces schema at write time

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Edit `plugin/agents/work-squash.md` Step 5 per the exact specification in "Files to Modify"
  - Replace the if-block echo with fail-fast exit 1
  - Remove the "Blocking condition" section
  - Add schema reference comment above the status check
  - Update STATE.md to closed/100%
  - Commit: `bugfix: remove misleading status handling from work-squash.md Step 5`
- Invoke `/cat:empirical-test` to verify that the work-squash agent uses `closed` (not `completed`
  or any other invalid value) when closing an issue after this fix; confirm the test passes before
  marking the issue complete

## Post-conditions
- [ ] `work-squash.md` Step 5 bash block no longer contains the phrase "fixing before returning"
- [ ] The if-block for status mismatch exits with code 1 and a clear error message
- [ ] The error message cites `.claude/rules/state-schema.md` and lists valid values: `open`,
      `in-progress`, `closed`, `blocked`
- [ ] The "Blocking condition" section (lines 219–228 in original) is removed
- [ ] A comment above the status check references `.claude/rules/state-schema.md`
- [ ] The comment notes that `StateSchemaValidator` enforces valid values at write time
- [ ] No new priming patterns introduced (no phrases implying the subagent should modify status itself)
- [ ] E2E: `/cat:empirical-test` confirms the work-squash agent uses `closed` (not `completed` or any
      other value) when closing an issue after this fix
