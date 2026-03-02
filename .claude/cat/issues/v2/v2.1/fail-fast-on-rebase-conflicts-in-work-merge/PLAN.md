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

## Post-conditions

- [ ] Step 4 no longer contains any auto-resolution logic
- [ ] All conflict counts (1 or more files) return CONFLICT immediately
- [ ] The 3-file threshold is removed
- [ ] All tests pass: `mvn -f client/pom.xml test` exits with code 0
- [ ] E2E: Trigger a simulated rebase conflict in a test worktree and confirm work-merge returns
  CONFLICT without attempting resolution
