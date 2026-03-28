# Plan

## Goal

Fix the cleanup skill (`plugin/skills/cleanup/first-use.md`) to only delete the corrupt index.json
file from corrupt issue directories, never the entire directory. Corrupt directories may contain
plan.md and other valuable files. Edge case: always preserve the directory even if index.json is
the only file present.

## Pre-conditions

(none)

## Post-conditions

- [ ] Bug fixed: cleanup skill deletes only the corrupt index.json file from corrupt issue directories, not the entire directory
- [ ] Regression test added verifying the directory is preserved after corrupt index.json cleanup
- [ ] No new issues introduced
- [ ] E2E verification: run the cleanup skill and verify the issue directory still exists (with plan.md intact) after cleaning up a corrupt index.json
- [ ] Commit message in cleanup step updated from "remove corrupt issue directory" to "remove corrupt index.json from [issue-name]"
- [ ] Step 4 confirmation flow updated to reference "the index.json file" not "the directory"
- [ ] AskUserQuestion option label updated from "Delete all corrupt issue directories" to accurately reflect that only index.json files are deleted (e.g., "Delete corrupt index.json files")

## Research Findings

The bug is in `plugin/skills/cleanup/first-use.md`, Step 4, option 3 handling block.

Current behavior in Step 4:
- AskUserQuestion option 3 label: `**Delete all corrupt issue directories**`
- Option 3 description: "delete all directories listed under '⚠ Corrupt Issue Directories'"
- For each corrupt issue directory:
  1. Displays index.json contents
  2. Asks for confirmation of "deletion of that specific directory"
  3. Deletes the **directory** using `/cat:safe-rm-agent`
  4. Runs `git add -- "$DELETED_DIR"` and commits with `"planning: remove corrupt issue directory $ISSUE_NAME"`

Required behavior:
- Option label updated to reflect only `index.json` is deleted
- Delete only `index.json` inside the directory using `rm`
- Directory is always preserved (even if `index.json` was the only file)
- Variable should reference the file path, not the directory path
- Commit message reflects only the file was removed

Files to change:
- `plugin/skills/cleanup/first-use.md` — fix Step 4 option 3 label, description, confirmation text, deletion code, and commit message
- `tests/cleanup-corrupt-index-deletion.bats` — new regression test

## Jobs

### Job 1

- In `plugin/skills/cleanup/first-use.md`, locate Step 4 and make the following changes:
  1. Change the AskUserQuestion option 3 label from `**Delete all corrupt issue directories**` to `**Delete corrupt index.json files**`
  2. Change the option description from `delete all directories listed under "⚠ Corrupt Issue Directories"` to `delete the corrupt index.json files from issue directories listed under "⚠ Corrupt Issue Directories" (directories are preserved)`
  3. In the option 3 execution block, change the confirmation text from "Ask the user to confirm deletion of that specific directory after reviewing its contents" to "Ask the user to confirm deletion of the index.json file after reviewing its contents"
  4. Replace the `safe-rm-agent` invocation (which deletes the whole directory) with a direct `rm` of just `index.json`:
     - Replace: `3. Delete the directory using \`/cat:safe-rm-agent\``
     - With: `3. Delete only the corrupt index.json file: \`rm "${CORRUPT_DIR}/index.json"\` (never delete the directory itself — it may contain plan.md and other valuable files; always preserve it even if index.json was the only file present)`
  5. Rename the variable `DELETED_DIR` to `CORRUPT_DIR` throughout the option 3 block to reflect it is the issue directory (not deleted)
  6. Change `git add -- "$DELETED_DIR"` to `git add -- "${CORRUPT_DIR}/index.json"` (stage only the file deletion)
  7. Change the commit message from `"planning: remove corrupt issue directory $ISSUE_NAME"` to `"planning: remove corrupt index.json from $ISSUE_NAME"`
- Create `tests/cleanup-corrupt-index-deletion.bats` with regression tests. The tests directly exercise the
  Bash commands that the skill instructs the agent to run, using an isolated temporary git repository. Each test
  must:
  - Set up a temp git repo with `mktemp -d`, `git init`, `git config user.email`, `git config user.name`
  - Create the corrupt issue directory and `index.json` file, `git add` and `git commit` them as an initial commit
  - Run the exact commands from the skill's fixed option 3 block, substituting variables for test values
  - Assert the expected outcome using bats assertions

  Test cases:
  1. **Directory preserved after index.json deletion:** Create `TMPDIR/issues/my-issue/index.json`, run
     `rm "${CORRUPT_DIR}/index.json"` where `CORRUPT_DIR=TMPDIR/issues/my-issue`, assert directory still exists
     (`[ -d "$CORRUPT_DIR" ]`)
  2. **Other files preserved after deletion:** Create `TMPDIR/issues/my-issue/index.json` and
     `TMPDIR/issues/my-issue/plan.md`, run `rm "${CORRUPT_DIR}/index.json"`, assert `plan.md` still exists
     (`[ -f "${CORRUPT_DIR}/plan.md" ]`)
  3. **Directory preserved when index.json is the only file (edge case):** Create only
     `TMPDIR/issues/my-issue/index.json` (no other files), run `rm "${CORRUPT_DIR}/index.json"`, assert directory
     still exists (`[ -d "$CORRUPT_DIR" ]`)
  4. **git add targets the file, not the directory:** In the temp git repo, create and commit the issue directory
     with `index.json`, then run `git add -- "${CORRUPT_DIR}/index.json"` after deletion, assert that
     `git diff --cached --name-only` outputs `issues/my-issue/index.json` (the file path), not just `issues/my-issue/`
  5. **Commit message format:** After staging the deletion, run
     `git commit -m "planning: remove corrupt index.json from my-issue"`, assert exit code 0 and that
     `git log --format=%s -1` outputs `planning: remove corrupt index.json from my-issue`
- Update index.json: set status to `closed` and progress to `100%`
- Commit all changes with message: `bugfix: fix cleanup skill to delete only corrupt index.json, not entire directory`
