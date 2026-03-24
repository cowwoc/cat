# Plan

## Goal

Add a new `cat:scan-and-edit` skill that implements a scan-first, batch-edit, compile-once pattern. The
`plugin/agents/work-execute.md` implementation subagent will call this skill before editing any files. The skill greps
all usages of every symbol being changed (renamed, removed, or moved), builds a complete file→changes map, applies all
edits without intermediate compilation, then compiles once at the end. This eliminates the compile-fix loop that caused
an 11-hour looping session when a Wave 2 implementation subagent encountered cascading Java refactoring errors.

## Pre-conditions

(none)

## Post-conditions

- [ ] `plugin/skills/scan-and-edit/SKILL.md` created with valid frontmatter (`description`, `user-invocable: false`,
  `allowed-tools`, `argument-hint`) and preprocessor directive pointing to `first-use.md`
- [ ] `plugin/skills/scan-and-edit/first-use.md` created with license header and four-phase agent instructions:
  (1) scan — grep all usages of symbols being changed before any edits, (2) map — build complete file→changes list,
  (3) edit — apply all changes without recompiling between files, (4) compile — run build once at the end
- [ ] `plugin/agents/work-execute.md` updated to invoke `cat:scan-and-edit` (via `skill: "cat:scan-and-edit"`)
  before any file editing steps
- [ ] All existing tests pass after changes
- [ ] E2E: Manually run a refactoring scenario with multi-file symbol removal and confirm build passes with zero
  intermediate compilations
