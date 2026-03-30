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

## Jobs

### Job 1

- Read `plugin/concepts/instruction-testing.md` from the worktree
- Write the resolved file to `plugin/concepts/instruction-testing.md` keeping only the INCOMING side
  (`5ac7a0be6`) for every conflict block:
  - Conflict 1 (line ~6): Keep `# Skill Instruction-Testing` heading and `instruction-test/iterate` description
  - Conflict 2 (line ~19): Keep `Skill instruction-testing produces...` overview with `with-skill`/`without-skill`
    config names and `SkillTestRunner`/`instruction-test.json` references
  - Conflict 3 (line ~118): Keep `instruction-test JSON` wording and add `semantic_unit_id` row to the table
  - Conflict 4 (line ~256): Keep `## Instruction-Test JSON Schema` heading and `skill-analyzer-agent` reference
  - Conflict 5 (line ~327): Keep `## Instruction-Test/Iterate Workflow` heading
  - Conflict 6 (line ~352): Keep `skill-grader-agent`, `skill-analyzer-agent`, `INSTRUCTION-TEST SUMMARY` with
    `with-skill`/`without-skill` config names
- Verify: `grep -c "<<<<<<\|>>>>>>>\|=======" plugin/concepts/instruction-testing.md` outputs `0`
- Commit: `config: resolve merge conflict markers in instruction-testing.md`
- Update `index.json` in the same commit: `{"status": "closed", "target_branch": "v2.1"}`

## Post-conditions

- [ ] `grep -c "<<<<<<\|>>>>>>>\|=======" plugin/concepts/instruction-testing.md` outputs `0`
- [ ] File heading is `# Skill Instruction-Testing`
- [ ] Overview section uses `with-skill`/`without-skill` config names
- [ ] Section heading is `## Instruction-Test JSON Schema`
- [ ] Section heading is `## Instruction-Test/Iterate Workflow`
- [ ] Step 3 references `skill-grader-agent` and `skill-analyzer-agent`
