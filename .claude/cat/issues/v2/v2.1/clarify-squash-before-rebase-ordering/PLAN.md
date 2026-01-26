# Plan: Clarify Squash-Before-Rebase Ordering in Work-With-Issue Skill

## Current State

Step 7 in `work-with-issue-agent` is named "Rebase and Squash Commits Before Review". This name uses the word
"Rebase" ambiguously: Step 7 squashes commits using `cat:git-squash` (which uses `git rebase -i` internally), while
Step 8 performs an explicit rebase of the issue branch onto the target branch tip. The overlapping terminology makes
it unclear which operation happens first.

## Target State

Step 7 is renamed to "Squash Commits by Topic Before Review" and all in-skill references updated accordingly. The
name change makes the ordering explicit: squash (Step 7) happens before rebase onto target branch (Step 8). The
MANDATORY STEPS section header and the step heading and body references all use the new name.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None (naming change only; no behavioral change)
- **Mitigation:** Verify all in-file references to "Rebase and Squash" are updated consistently

## Files to Modify
- `plugin/skills/work-with-issue-agent/first-use.md` - rename Step 7 references from "Rebase and Squash" to
  "Squash Commits by Topic" (or "Squash by Topic" in inline references)

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- In `plugin/skills/work-with-issue-agent/first-use.md`, apply the following renames:
  - Line ~29 (MANDATORY STEPS): `**Step 7: Rebase and Squash Commits Before Review**` →
    `**Step 7: Squash Commits by Topic Before Review**`
  - Line ~864 (auto-fix loop note): `Rebase and Squash` → `Squash Commits by Topic`
  - Line ~1183 (step heading): `## Step 7: Rebase and Squash Commits Before Review (MANDATORY)` →
    `## Step 7: Squash Commits by Topic Before Review (MANDATORY)`
  - Line ~1269 (Pre-Gate Squash Verification): `Step 7 (Rebase and Squash)` → `Step 7 (Squash by Topic)`
  - Line ~1273: `rebase and squash` → `squash by topic`
  - Files: `plugin/skills/work-with-issue-agent/first-use.md`

## Post-conditions
- [ ] The string "Rebase and Squash" does not appear in `plugin/skills/work-with-issue-agent/first-use.md`
- [ ] Step 7 heading reads "Squash Commits by Topic Before Review" in the MANDATORY STEPS section and in the step
  body heading
- [ ] Step 8 heading still reads "Rebase onto Target Branch Before Approval Gate" (unchanged)
- [ ] All cross-references to Step 7 within the file use the new name or abbreviated form
