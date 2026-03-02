# Plan: Fail-Fast on Rebase Conflicts in Work-Merge

## Goal

Replace work-merge Step 4's partial auto-resolution logic with an immediate fail-fast that returns
CONFLICT status regardless of the number of conflicting files.

## Satisfies

None

## Problem

Step 4 of the work-merge skill attempts to auto-resolve rebase conflicts when ≤3 files are involved
("prefer issue branch changes"), then calls `git rebase --continue`. This is unsafe because:

1. **Silent data loss:** Blindly preferring one side can discard important base branch changes without
   any warning.
2. **Semantic errors:** Auto-resolution may produce code that compiles but is logically wrong. Only a
   human can judge the semantics of a conflict.
3. **Artificial threshold:** The 3-file threshold adds complexity without adding safety — 1 conflicting
   file is just as risky as 10.

The correct behavior is to stop immediately, report the conflicting files, and require the user to resolve
them manually.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** More user interruptions when conflicts occur
- **Mitigation:** Conflicts are already rare; fail-fast is strictly safer

## Files to Modify

- `plugin/skills/work-merge/first-use.md` — Replace Step 4 with fail-fast behavior

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Steps

1. **Modify Step 4 in work-merge/first-use.md:**

   Remove the "If <= 3 files: Attempt resolution" branch. Replace with:

   > If rebase fails with any conflicts:
   > 1. Collect the list of conflicting files
   > 2. Return CONFLICT status with conflicting_files list and message "Rebase conflict — manual
   >    resolution required"
   > 3. STOP — do not attempt auto-resolution

   The `> 3 files` branch becomes unnecessary since all conflict counts now return CONFLICT.

2. **Run tests:** `mvn -f client/pom.xml test` — all tests must pass.

3. **Create E2E test for rebase conflict scenarios:**

   Add TestNG test method(s) to verify work-merge skill behavior when rebase conflicts occur:

   - Create a test that sets up a scenario where rebase will conflict (e.g., both feature branch
     and base branch modify the same file in incompatible ways)
   - Invoke the work-merge merge step (or equivalent command that triggers Step 4 rebase logic)
   - Verify that it returns CONFLICT status (not attempting any auto-resolution)
   - Verify that conflicting_files list is present in the response
   - Verify that the message indicates manual resolution is required
   - Ensure the test isolates its changes to a temporary worktree/repo (no side effects on main repo)

   The test should cover at minimum:
   - Single conflicting file scenario
   - Multiple conflicting files scenario (to ensure no "3-file threshold" logic remains)

4. **Run all tests including E2E:** `mvn -f client/pom.xml test` — all tests including the new E2E
   test must pass.

## Post-conditions

- [ ] Step 4 no longer contains any auto-resolution logic
- [ ] All conflict counts (1 or more files) return CONFLICT immediately
- [ ] The 3-file threshold is removed
- [ ] All tests pass: `mvn -f client/pom.xml test` exits with code 0
- [ ] E2E: Trigger a simulated rebase conflict in a test worktree and confirm work-merge returns
  CONFLICT without attempting resolution
