# Plan: fix-conflict-markers-instruction-testing

## Current State

`plugin/concepts/instruction-testing.md` was committed with git merge conflict markers still present.
The file contains 6 conflict blocks (20 marker lines total) from a merge of the incoming commit
`5ac7a0be6` (refactor: rename benchmark to instruction-test throughout plugin).

## Target State

All conflict markers are resolved by keeping the INCOMING side (`5ac7a0be6`) for every conflict block.
The file contains no `<<<<<<<`, `=======`, or `>>>>>>>` lines.

## Files to Modify

- `plugin/concepts/instruction-testing.md` — remove all conflict markers, keep INCOMING sides

## Pre-conditions

- [ ] None

## Post-conditions

- [ ] `grep -c "<<<<<<\|>>>>>>>\|=======" plugin/concepts/instruction-testing.md` outputs `0`
- [ ] File heading is `# Skill Instruction-Testing`
- [ ] Overview section uses `with-skill`/`without-skill` config names
- [ ] Section heading is `## Instruction-Test JSON Schema`
- [ ] Section heading is `## Instruction-Test/Iterate Workflow`
- [ ] Step 3 references `skill-grader-agent` and `skill-analyzer-agent`
