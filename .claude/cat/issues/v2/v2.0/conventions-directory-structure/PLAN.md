# Plan: conventions-directory-structure

## Goal
Split conventions between always-loaded (.claude/rules/) and on-demand (.claude/cat/conventions/) locations.

## Satisfies
None - infrastructure improvement

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None
- **Mitigation:** N/A

## Files to Modify
- `plugin/commands/init.md` - Update directory creation and documentation

## Acceptance Criteria
- [ ] init.md creates both .claude/rules/ and .claude/cat/conventions/
- [ ] Documentation explains when to use each location
- [ ] conventions.md in .claude/rules/ points to on-demand conventions

## Execution Steps
1. Update mkdir commands to create both directories
2. Update documentation section to explain the distinction
3. Update success criteria table
