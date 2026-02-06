# Plan: fix-add-remove-parent-update-steps

## Goal
Make all parent update steps in add.md and remove.md explicit with bash commands, eliminating vague instructions that
lead to skipped steps.

## Satisfies
- Learning from M163: Vague workflow steps cause agents to skip mandatory updates

## Current State
6 steps across add.md and remove.md have vague instructions like "Update parent STATE.md" or "Recalculate progress"
without specifying exactly what to do.

## Target State
All parent update steps have explicit bash commands showing:
- Which file to update
- What content to add/remove
- How to verify the update succeeded

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Bash commands must handle edge cases (missing sections, etc.)
- **Mitigation:** Include fallback logic for missing sections

## Files to Modify
- plugin/commands/add.md
  - `minor_update_parent` - Add minor to major/STATE.md "Minor Versions" list
  - `minor_update_roadmap` - Specify ROADMAP.md format and insertion point
  - `major_create` - Clarify which files to create (STATE.md only, or also PLAN.md?)
  - `major_update_roadmap` - Specify ROADMAP.md format and insertion point
- plugin/commands/remove.md
  - `task_update_parent` - Remove task from minor/STATE.md Tasks Pending/Completed
  - `minor_update_parent` - Remove minor from major/STATE.md Minor Versions list

## Acceptance Criteria
- [ ] All 6 vague steps replaced with explicit bash commands
- [ ] Each step includes verification (grep to confirm update)
- [ ] Edge cases handled (missing sections create them)
- [ ] Consistent with task_update_parent fix already applied

## Execution Steps
1. **Fix add.md minor_update_parent**
   - Add bash to append minor version to "## Minor Versions" section
   - Verify: grep confirms minor added

2. **Fix add.md minor_update_roadmap**
   - Add bash to append entry under correct major section
   - Verify: grep confirms entry added

3. **Fix add.md major_create**
   - Clarify: Major versions only get STATE.md (no PLAN.md/CHANGELOG.md)
   - Or: Add missing files to existing major versions
   - Verify: Check v1/ and v2/ for consistency

4. **Fix add.md major_update_roadmap**
   - Add bash to create new major section in ROADMAP.md
   - Verify: grep confirms section added

5. **Fix remove.md task_update_parent**
   - Add bash to remove task from Tasks Pending or Tasks Completed
   - Verify: grep confirms task removed

6. **Fix remove.md minor_update_parent**
   - Add bash to remove minor from Minor Versions list
   - Verify: grep confirms minor removed

7. **Test all workflows**
   - Run /cat:add for task, minor, major
   - Run /cat:remove for task, minor
   - Verify parent files updated correctly
