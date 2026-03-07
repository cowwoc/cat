# Plan: fix-learn-skill-prevent-issue-creation

## Problem

When the learn skill's subagent returns `prevention_implemented: false`, the main agent is supposed to
create a follow-up CAT issue from `issue_creation_info` (Step 5 of `first-use.md`). This step is being
skipped, leaving the prevention untracked. Observed in M503 and M504.

## Parent Requirements

None

## Reproduction Code

```
# 1. Run /cat:learn with a mistake whose prevention requires plugin/ changes
# 2. Subagent returns: prevention_implemented=false, issue_creation_info={...}
# 3. Main agent displays phase summaries (Step 5) and final summary (Step 6)
# 4. No follow-up issue is created — the issue_creation_info block is silently ignored
```

## Expected vs Actual

- **Expected:** After Phase 4 (record-learning), main agent checks `prevention_implemented`. If false,
  creates a follow-up CAT issue via `cat:add-agent` before proceeding to the final summary.
- **Actual:** Main agent displays summaries and stops. No follow-up issue is created.

## Root Cause

The current Step 5 in `first-use.md` conflates two responsibilities: (1) display phase summaries, and
(2) create a follow-up issue if `prevention_implemented=false`. The issue creation is documented as a
conditional sub-section of the display step, making it easy for the agent to treat the display as the
entire step and miss the follow-up. The lack of a MANDATORY/BLOCKING marker means the agent can skip
it without a hard failure signal.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** None — this is additive documentation. Existing `prevention_implemented=true`
  paths are unaffected.
- **Mitigation:** Step numbering change renumbers Step 6, which is referenced only within `first-use.md`
  itself. No external cross-references to step numbers.

## Files to Modify

- `plugin/skills/learn/first-use.md` — split current Step 5 into two separate numbered steps:
  - Step 5: Display Phase Summaries (display only)
  - Step 6: Create Follow-up Issue (**MANDATORY** when `prevention_implemented=false`, else skip)
  - Step 7: Display Final Summary (renamed from current Step 6)

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- In `plugin/skills/learn/first-use.md`:
  1. Keep current Step 5 header + display-summaries block as **Step 5: Display Phase Summaries**
     — remove the `**If prevent.prevention_implemented is false:**` sub-section from Step 5
  2. Add new **Step 6: Create Follow-up Issue** immediately after Step 5:
     ```markdown
     ## Step 6: Create Follow-up Issue

     **MANDATORY when `prevent.prevention_implemented` is false. Skip this step entirely if
     `prevent.prevention_implemented` is true.**

     When `prevention_implemented` is false, the subagent could not commit prevention because the
     current branch is protected. A follow-up issue must be created so the prevention is not lost.

     1. **Validate `issue_creation_info` before proceeding.** Verify that:
        - `issue_creation_info` is present and non-empty in the prevent phase output
        - `issue_creation_info.suggested_title` is a non-empty string
        - `issue_creation_info.suggested_description` is a non-empty string
        - `issue_creation_info.suggested_acceptance_criteria` is a non-empty string

        If any field is missing or empty, display:
        ```
        Error: Cannot create follow-up issue — issue_creation_info is incomplete.
        Missing fields: [list the missing field names]
        Please create the issue manually using /cat:add.
        Suggested title: {suggested_title or "(not provided)"}
        Suggested description: {suggested_description or "(not provided)"}
        Suggested acceptance criteria: {suggested_acceptance_criteria or "(not provided)"}
        ```
        Then continue to Step 7.

     2. Display to user: "Prevention requires code changes that cannot be committed on protected
        branch. Creating follow-up issue."

     3. Invoke `/cat:add-agent suggested_title` where `suggested_title` is the one-line summary
        from `issue_creation_info.suggested_title`. When cat:add-agent prompts for more detail,
        provide `suggested_description` as the description and `suggested_acceptance_criteria`
        as the acceptance criteria.

        If `cat:add-agent` fails or returns an error, display:
        ```
        Error: Failed to create follow-up issue via cat:add-agent.
        You can create the issue manually using /cat:add with the following values:
        Title: {suggested_title}
        Description: {suggested_description}
        Acceptance criteria: {suggested_acceptance_criteria}
        ```
     ```
  3. Rename current **Step 6** header to **Step 7: Display Final Summary**
     — update the `If retrospective_triggered` block within the renamed Step 7 if needed
  - Files: `plugin/skills/learn/first-use.md`

- Commit: `bugfix: split Step 5 of learn skill to make follow-up issue creation a distinct mandatory step`
  - Files: _(git only)_

## Post-conditions

- [ ] `first-use.md` has a distinct Step 6 titled "Create Follow-up Issue" with a MANDATORY marker
  when `prevention_implemented=false`
- [ ] `first-use.md` Step 7 is "Display Final Summary" (formerly Step 6)
- [ ] The `**If prevent.prevention_implemented is false:**` sub-section is removed from Step 5
- [ ] E2E: Run `/cat:learn` with a mistake that cannot be prevented on a protected branch; a
  follow-up issue is created
