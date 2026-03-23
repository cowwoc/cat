# Plan

## Goal

Fix the cleanup skill (`plugin/skills/cleanup-agent/first-use.md`) to only delete the corrupt index.json file from corrupt issue directories, never the entire directory. Corrupt directories may contain plan.md and other valuable files. Edge case: always preserve the directory even if index.json is the only file present.

## Pre-conditions

(none)

## Post-conditions

- [ ] Bug fixed: cleanup skill deletes only the empty/corrupt index.json file from corrupt issue directories, not the entire directory
- [ ] Regression test added verifying the directory is preserved after corrupt index.json cleanup
- [ ] No new issues introduced
- [ ] E2E verification: run the cleanup skill and verify the issue directory still exists (with plan.md intact) after cleaning up a corrupt index.json
- [ ] Commit message in cleanup step updated from "remove corrupt issue directory" to "remove corrupt index.json from [issue-name]"
- [ ] Step 4 confirmation flow updated to reference "the index.json file" not "the directory"
- [ ] AskUserQuestion option label updated from "Delete all corrupt issue directories" to accurately reflect that only index.json files are deleted (e.g., "Delete corrupt index.json files")
