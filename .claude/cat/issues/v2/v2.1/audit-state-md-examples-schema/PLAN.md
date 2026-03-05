# Plan: Audit and update all STATE.md examples to use current schema

## Goal
Search all .claude/cat/issues/**/*.md files for deprecated STATE.md fields ('Last Updated', etc.) and update
examples to reflect current schema. Add schema validation documentation listing deprecated vs current fields.

## Satisfies
None - documentation and schema hygiene

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Some examples may be embedded in skill files or documentation that is used as reference
- **Mitigation:** Search broadly before modifying; verify no semantic loss after updates

## Files to Modify
- All .md files under `.claude/cat/issues/` that contain deprecated STATE.md field examples
- Schema documentation files that describe STATE.md fields

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- Search all .claude/cat/issues/**/*.md files for deprecated STATE.md fields (e.g., 'Last Updated', 'Completed',
  'Version', 'Tokens Used', 'Started', 'Assignee', 'Priority', 'Worktree', 'Merged', 'Commit')
  - Files: `.claude/cat/issues/`
- Update any examples found to use only current valid fields (Status, Progress, Dependencies, Blocks, Resolution)
  - Files: affected .md files
- Add or update schema documentation listing deprecated fields vs current fields with removal timeline
  - Files: `.claude/cat/conventions/state-schema.md` or similar

## Post-conditions
- [ ] All STATE.md examples in .claude/cat/ use only current valid fields
- [ ] Schema documentation lists deprecated fields with removal timeline
- [ ] No deprecated field names appear in example STATE.md content in documentation or plan files
- [ ] E2E: Grep for deprecated field names across .claude/cat/issues/ returns no hits in example/documentation
  contexts
