# Plan: Fix cat:work Skill — Prohibit Manual Worktree Investigation After work-prepare Errors

## Problem

When `work-prepare` returns an ERROR referencing an existing worktree or session lock, the `cat:work`
skill's "ERROR: Existing Worktree Handling" section instructs the agent to display the error and present
an `AskUserQuestion` (steps 1–2). However, no prohibition against manual worktree investigation (`git
worktree list`, `ls`, filesystem reads) exists in that pre-AskUserQuestion path. The prohibition only
appears in step 3 (after `cleanup-agent` returns). This gap allows an agent to perform manual investigation
between receiving the ERROR and presenting the AskUserQuestion, which is incorrect — the AskUserQuestion
must be presented immediately after displaying the error message.

## Parent Requirements

None

## Reproduction Code

```
# Trigger: work-prepare returns ERROR with message "worktree already exists for issue X"
# Bug: agent may then run:
#   git worktree list
#   ls /workspace/.worktrees/
# ...before presenting AskUserQuestion
# Expected: AskUserQuestion presented immediately after displaying error (no investigation)
```

## Expected vs Actual

- **Expected:** After `work-prepare` returns ERROR referencing an existing worktree, the agent displays
  the error and immediately presents `AskUserQuestion` with "Clean up and retry" / "Abort" options —
  no intermediate investigation commands.
- **Actual:** The skill text does not prohibit `git worktree list`, `ls`, or any other investigation
  commands between receiving the ERROR and presenting the `AskUserQuestion`. An agent following the
  letter of the instructions may investigate worktree state before presenting the question.

## Root Cause

The "ERROR: Existing Worktree Handling" section (lines 116–143 of `plugin/skills/work/first-use.md`)
has its no-investigation prohibition placed exclusively inside step 3 (post-cleanup path). Steps 1 and 2
— the pre-AskUserQuestion path — lack an equivalent explicit prohibition. The constraint "go directly to
AskUserQuestion without intermediate investigation" is implied but not stated, creating a gap that agents
can exploit.

## Rejected Alternatives

### A: Add prohibition only to the step 1 bullet

- **Why rejected:** Step 1 says "Display the error message to the user." Adding a prohibition there
  is awkward — it mixes display instructions with behavioral prohibitions. The prohibition belongs at
  the boundary (after step 2) where the AskUserQuestion is presented, not within the display step.

### B: Add a CRITICAL callout after step 4 (symmetric with the existing CRITICAL at line 142)

- **Why rejected:** A post-step-4 callout addresses the wrong boundary. The existing CRITICAL at
  lines 142–143 already covers the post-cleanup path. Adding another CRITICAL after "Abort" does
  not close the gap in steps 1–2.

### C: Restructure the entire section to merge pre- and post-AskUserQuestion prohibitions

- **Why rejected:** Restructuring is a larger change that risks introducing new ambiguity. The
  targeted fix — inserting a single prohibition note between steps 2 and 3 — is lower risk and
  sufficient to close the documented gap.

### D: Move the prohibition into a "No-investigation" rule file referenced by the section

- **Why rejected:** The no-investigation rule is highly specific to this particular ERROR path.
  Extracting it to a shared rule file would require agents to follow cross-file references for a
  small, localized constraint. Inline placement is clearer and self-contained.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** None — this is a documentation-only change to a single skill file. No code,
  tests, or other files are modified. The fix adds clarity without removing any existing instructions.
- **Mitigation:** Review the modified section end-to-end to confirm the prohibition appears in the
  correct location and does not conflict with existing text.

## Files to Modify

- `plugin/skills/work/first-use.md` — Add explicit prohibition of `git worktree list`, `ls`, and any
  other manual worktree investigation between step 2 (AskUserQuestion) and step 3 (cleanup-agent path),
  specifically covering the pre-AskUserQuestion period.

## Test Cases

- [ ] Modified section explicitly prohibits `git worktree list` and `ls` commands in the
  pre-AskUserQuestion path (steps 1–2)
- [ ] Existing prohibition in step 3 (post-cleanup path) is unchanged
- [ ] Existing CRITICAL callout at lines 142–143 is unchanged
- [ ] No other behavior of the section is altered

## Pre-conditions

- [ ] All dependent issues are closed

## Main Agent Waves

<!-- No main-agent-level skills needed for this documentation-only fix -->

## Sub-Agent Waves

### Wave 1

- Read `plugin/skills/work/first-use.md` to confirm current line content around the
  "ERROR: Existing Worktree Handling" section (lines 116–144)
  - Files: `plugin/skills/work/first-use.md`
- Insert the following prohibition note immediately after step 2's AskUserQuestion block (after line 131,
  before the "3. If user selects" line):

  ```
     **Do NOT investigate worktree state between steps 1–2 and presenting the AskUserQuestion.**
     Do NOT run `git worktree list`, `ls`, or any filesystem/git commands to inspect existing worktree
     state. The error message from `work-prepare` is sufficient context. Go directly to the
     AskUserQuestion.
  ```
  - Files: `plugin/skills/work/first-use.md`

## Post-conditions

- [ ] `plugin/skills/work/first-use.md` contains an explicit prohibition of manual worktree investigation
  (`git worktree list`, `ls`) in the pre-AskUserQuestion path of the "ERROR: Existing Worktree Handling"
  section
- [ ] The prohibition is positioned between step 2's AskUserQuestion block and step 3's cleanup-agent
  instructions
- [ ] All existing content in the section (steps 3, 4, and the CRITICAL callout) remains unchanged
- [ ] No regressions introduced in other sections of the file
- [ ] E2E: Reproduce the scenario where `work-prepare` returns ERROR referencing an existing worktree
  and confirm the skill text explicitly directs the agent to skip investigation and proceed directly
  to AskUserQuestion
