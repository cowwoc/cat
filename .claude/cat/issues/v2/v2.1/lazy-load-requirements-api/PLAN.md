# Plan: lazy-load-requirements-api

## Current State
`requirements-api.md` (3,367 bytes) lives in `.claude/rules/` which means it is loaded into the system prompt of every
session and every subagent, even when no Java files are being edited. This wastes ~3.4KB of context per agent instance.

## Target State
Move `requirements-api.md` to `plugin/concepts/` so it is only loaded on-demand when a skill or agent needs it.
CLAUDE.md already instructs "read before editing .java files", so the on-demand loading mechanism is already in place.

## Satisfies
None - infrastructure optimization

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - CLAUDE.md convention table already points agents to read conventions before editing
- **Mitigation:** Verify CLAUDE.md language conventions table references the new location correctly

## Files to Modify
- `.claude/rules/requirements-api.md` - Delete (move source)
- `plugin/concepts/requirements-api.md` - Create (move destination)
- `CLAUDE.md` - Update the convention table to reference `plugin/concepts/requirements-api.md` instead of
  `.claude/rules/requirements-api.md`

## Execution Steps
1. **Step 1:** Copy `.claude/rules/requirements-api.md` to `plugin/concepts/requirements-api.md`
2. **Step 2:** Delete `.claude/rules/requirements-api.md`
3. **Step 3:** Update the Language Conventions table in `CLAUDE.md` to change the Convention File column for `*.java`
   from `.claude/cat/conventions/java.md` to include a reference to `plugin/concepts/requirements-api.md` as well,
   or update the existing reference path. Verify the "Read Before Editing" instruction still points agents to read the
   requirements API conventions before editing Java files.
4. **Step 4:** Run `python3 /workspace/run_tests.py` to verify no regressions

## Success Criteria
- [ ] `requirements-api.md` no longer exists in `.claude/rules/`
- [ ] `requirements-api.md` exists in `plugin/concepts/` with identical content
- [ ] CLAUDE.md convention table correctly references the new location
- [ ] All tests pass
- [ ] Non-Java sessions no longer have requirements-api.md in system prompt
