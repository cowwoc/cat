# Plan: Remove CWD Backup/Restore on Compaction

## Current State

The codebase implements a mechanism to preserve and restore the agent's current working directory (cwd) across context
compaction and session resume events. This consists of three classes plus hook infrastructure:
`PreCompactHook` saves the cwd to `session.cwd` before compaction; `RestoreCwdAfterCompaction` reads it after
compaction and injects a `cd` instruction (plus a batch ToolSearch instruction); `RestoreWorktreeOnResume` scans lock
files on session resume and injects a `cd` instruction for the active worktree.

## Target State

All three cwd/worktree restore mechanisms are removed. `SessionStartHook` no longer registers
`RestoreCwdAfterCompaction` or `RestoreWorktreeOnResume`. `PreCompactHook` is deleted entirely (it has no other
purpose). The `session.cwd` file is never written. `AotTraining` no longer references `PreCompactHook`. The
`PreCompact` hook entry is removed from `hooks.json`. All corresponding test classes are deleted. All tests pass.

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** Agents will no longer automatically re-`cd` after compaction or on resume. This is intentional
  — the feature is being removed.
- **Mitigation:** All existing tests pass after deletion. `SessionStartHookTest` must be updated to remove registrations
  of the deleted handlers.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/session/RestoreCwdAfterCompaction.java` — delete entirely
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/RestoreWorktreeOnResume.java` — delete entirely
- `client/src/main/java/io/github/cowwoc/cat/hooks/PreCompactHook.java` — delete entirely
- `client/src/main/java/io/github/cowwoc/cat/hooks/SessionStartHook.java` — remove imports for
  `RestoreCwdAfterCompaction` and `RestoreWorktreeOnResume`; remove both handler instantiations from the `List.of(...)`
  call in the primary constructor
- `client/src/main/java/io/github/cowwoc/cat/hooks/AotTraining.java` — remove `PreCompactHook` import and the line
  `new PreCompactHook(scope).run(input, output);`; if this leaves the surrounding block empty, clean it up accordingly
- `plugin/hooks/hooks.json` — remove the entire `"PreCompact"` array entry (lines containing the key and its nested
  object)
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/RestoreCwdAfterCompactionTest.java` — delete entirely
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/RestoreWorktreeOnResumeTest.java` — delete entirely
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/PreCompactHookTest.java` — delete entirely
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionStartHookTest.java` — remove any test cases or
  assertions that reference `RestoreCwdAfterCompaction`, `RestoreWorktreeOnResume`, or their behavior; remove imports
  for those classes

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Delete `RestoreCwdAfterCompaction.java`:
  - File: `client/src/main/java/io/github/cowwoc/cat/hooks/session/RestoreCwdAfterCompaction.java`
  - Use `cat:safe-rm-agent` or `git rm` to remove the file.

- Delete `RestoreWorktreeOnResume.java`:
  - File: `client/src/main/java/io/github/cowwoc/cat/hooks/session/RestoreWorktreeOnResume.java`
  - Use `cat:safe-rm-agent` or `git rm` to remove the file.

- Delete `PreCompactHook.java`:
  - File: `client/src/main/java/io/github/cowwoc/cat/hooks/PreCompactHook.java`
  - Use `cat:safe-rm-agent` or `git rm` to remove the file.

- Delete `RestoreCwdAfterCompactionTest.java`:
  - File: `client/src/test/java/io/github/cowwoc/cat/hooks/test/RestoreCwdAfterCompactionTest.java`
  - Use `cat:safe-rm-agent` or `git rm` to remove the file.

- Delete `RestoreWorktreeOnResumeTest.java`:
  - File: `client/src/test/java/io/github/cowwoc/cat/hooks/test/RestoreWorktreeOnResumeTest.java`
  - Use `cat:safe-rm-agent` or `git rm` to remove the file.

- Delete `PreCompactHookTest.java`:
  - File: `client/src/test/java/io/github/cowwoc/cat/hooks/test/PreCompactHookTest.java`
  - Use `cat:safe-rm-agent` or `git rm` to remove the file.

### Wave 2

- Update `SessionStartHook.java`:
  - File: `client/src/main/java/io/github/cowwoc/cat/hooks/SessionStartHook.java`
  - Remove the two import statements:
    - `import io.github.cowwoc.cat.hooks.session.RestoreCwdAfterCompaction;`
    - `import io.github.cowwoc.cat.hooks.session.RestoreWorktreeOnResume;`
  - In the primary constructor's `List.of(...)` call, remove these two lines:
    - `new RestoreWorktreeOnResume(scope),`
    - `new RestoreCwdAfterCompaction(scope)`
  - Ensure the remaining `List.of(...)` call is syntactically valid (no trailing comma on the last element).

- Update `AotTraining.java`:
  - File: `client/src/main/java/io/github/cowwoc/cat/hooks/AotTraining.java`
  - Remove the `import` statement for `PreCompactHook` (search for `import.*PreCompactHook`).
  - Remove the line `new PreCompactHook(scope).run(input, output);` and any surrounding block structure that becomes
    empty or invalid as a result.

- Update `plugin/hooks/hooks.json`:
  - File: `plugin/hooks/hooks.json`
  - Remove the entire `"PreCompact"` entry and its nested array value. The resulting JSON must remain valid.

- Update `SessionStartHookTest.java`:
  - File: `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionStartHookTest.java`
  - Remove imports for `RestoreCwdAfterCompaction` and `RestoreWorktreeOnResume` if present.
  - Remove any test methods or assertions that verify cwd-restore or worktree-resume behavior contributed by the two
    deleted handlers.

### Wave 3

- Run the full test suite and verify all tests pass:
  - Command: `mvn -f client/pom.xml test`
  - Fix any compilation errors caused by dangling references to removed classes.
  - Update `STATE.md` to reflect completion:
    - Set `Status` to `closed`
    - Set `Progress` to `100%`

## Post-conditions

- [ ] `RestoreCwdAfterCompaction.java`, `RestoreWorktreeOnResume.java`, and `PreCompactHook.java` no longer exist in
  the repository
- [ ] `RestoreCwdAfterCompactionTest.java`, `RestoreWorktreeOnResumeTest.java`, and `PreCompactHookTest.java` no longer
  exist in the repository
- [ ] `SessionStartHook.java` has no imports or instantiations of the deleted classes
- [ ] `AotTraining.java` has no import or usage of `PreCompactHook`
- [ ] `plugin/hooks/hooks.json` contains no `"PreCompact"` entry
- [ ] `mvn -f client/pom.xml test` exits with code 0
