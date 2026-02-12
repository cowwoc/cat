# Plan: autofix-high-review-concerns

## Goal
Automate fixing of HIGH+ stakeholder review concerns by looping the implementation subagent back to fix them, then
re-running the review. Present MEDIUM concerns at the approval gate with a "Fix remaining concerns" option.

## Satisfies
None - workflow enhancement

## Risk Assessment
- **Risk Level:** MEDIUM
- **Regression Risk:** Changes to the review/approval flow could affect merge safety gates
- **Mitigation:** Preserve all existing safety guarantees (M390, M479, M480); only add iteration on top

## Files to Modify
- `plugin/skills/work-with-issue/content.md` - Steps 4 and 6: add auto-fix loop after review, add "Fix concerns" option
  to approval gate
- `plugin/skills/stakeholder-review/content.md` - decide step: align with new auto-fix behavior

## Execution Steps

### Step 1: Add auto-fix loop to work-with-issue Step 4 (Review Phase)

In `plugin/skills/work-with-issue/content.md`, replace the "Handle Review Result" section (after stakeholder review
invocation) with an auto-fix loop:

**Current behavior (lines ~297-306):**
```
### Handle Review Result
Parse review result:
- REVIEW_PASSED: Continue to Step 5
- CONCERNS: Note concerns, continue to Step 5
- REJECTED: If trust=medium, return for user decision; else continue to approval gate
```

**New behavior:**
```
### Handle Review Result

Parse review result and filter false positives (concerns from reviewers that read base branch instead of worktree).

**If any concerns have severity >= HIGH:**

1. Spawn implementation subagent to fix the HIGH+ concerns:
   - Pass the concern list (severity, description, location, recommendation) as the fix instructions
   - Subagent works in the same worktree on the same branch
   - Subagent commits fixes
2. Re-run stakeholder review (invoke /cat:stakeholder-review again)
3. Repeat up to 3 iterations total
4. If HIGH+ concerns persist after 3 iterations, escalate to user at approval gate

**If all concerns are MEDIUM or lower (or no concerns):**
- Store concerns for display at approval gate
- Continue to Step 5

**Loop counter:** Track iteration count. On iteration > 3, stop looping and proceed to Step 5 with remaining concerns.
```

- Files: `plugin/skills/work-with-issue/content.md`

### Step 2: Add "Fix remaining concerns" option to approval gate (Step 6)

In `plugin/skills/work-with-issue/content.md`, update the approval gate AskUserQuestion to include a fourth option when
MEDIUM concerns exist:

**Current options (lines ~354-361):**
```
AskUserQuestion:
  header: "Approval"
  question: "Ready to merge ${ISSUE_ID}?"
  options:
    - "Approve and merge"
    - "Request changes" (provide feedback)
    - "Abort"
```

**New options (when MEDIUM concerns exist):**
```
AskUserQuestion:
  header: "Approval"
  question: "Ready to merge ${ISSUE_ID}?"
  options:
    - "Approve and merge"
    - "Fix remaining concerns" - Auto-fix MEDIUM concerns, re-review, then prompt again
    - "Request changes" (provide feedback)
    - "Abort"
```

**"Fix remaining concerns" handler:**
1. Spawn implementation subagent with the MEDIUM concern list
2. Re-run stakeholder review
3. Return to approval gate with updated results

Only show this option when there are actual MEDIUM+ concerns. If review was clean, show original 3 options.

- Files: `plugin/skills/work-with-issue/content.md`

### Step 3: Align stakeholder-review decide step

In `plugin/skills/stakeholder-review/content.md`, update the decide step to remove the auto-loop logic (since that
responsibility now lives in work-with-issue):

**Current (lines ~646-682):**
The decide step has trust-level-dependent rejection handling with auto-loop for trust=medium.

**New:**
Simplify the decide step to only return the aggregated result. The calling skill (work-with-issue) handles iteration.
The stakeholder-review skill should:
- Return REJECTED/CONCERNS/APPROVED status with full concern details
- NOT attempt to auto-loop or spawn fix subagents (that is work-with-issue responsibility)
- NOT ask the user how to proceed (that is the approval gate responsibility)

- Files: `plugin/skills/stakeholder-review/content.md`

## Success Criteria
- [ ] HIGH+ concerns trigger automatic fix loop (up to 3 iterations) before reaching approval gate
- [ ] MEDIUM concerns appear at approval gate with "Fix remaining concerns" option
- [ ] "Fix remaining concerns" spawns subagent, re-reviews, returns to approval gate
- [ ] Existing safety guarantees (M390, M479, M480) preserved
- [ ] stakeholder-review decide step simplified to pure result return
- [ ] No regressions in review/approval flow for clean reviews (no concerns)