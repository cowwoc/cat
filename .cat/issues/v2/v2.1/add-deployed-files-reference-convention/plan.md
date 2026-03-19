# Plan: add-deployed-files-reference-convention

## Goal
Establish and document the convention that deployed plugin files (under `plugin/`) must only reference other deployed
plugin files, and must not reference source-only paths like `.claude/rules/` that are not shipped to end-user machines.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Convention may be unclear about boundary between deployed and source-only paths
- **Mitigation:** Include concrete examples for both compliant and non-compliant cases

## Files to Modify
- `.claude/rules/plugin-file-references.md` — Create new file documenting the convention
- `.claude/rules/common.md` — Add cross-reference to the new convention file

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Create `.claude/rules/plugin-file-references.md` with the full convention content including:
  - Definition of "deployed file" (files under `plugin/`) vs "source-only path" (`.claude/rules/`, `.cat/rules/`, etc.)
  - At least 2 compliant examples (plugin/ files referencing other plugin/ files)
  - At least 2 non-compliant examples (plugin/ files referencing .claude/rules/ paths)
  - Guidance: end-user-facing rules go in `plugin/rules/`, developer-only rules go in `.claude/rules/`
  - Files: `.claude/rules/plugin-file-references.md`

### Wave 2
- Update `.claude/rules/common.md` to add a cross-reference to the new convention file in an appropriate section
  - Files: `.claude/rules/common.md`

## Post-conditions
- [ ] New file `.claude/rules/plugin-file-references.md` exists and documents the deployed-files-reference-convention
- [ ] Convention defines "deployed file" (`plugin/*` paths) vs "source-only path" (`.claude/rules/*`, `.cat/rules/*`)
- [ ] Convention includes at least 2 compliant examples and 2 non-compliant examples
- [ ] Convention provides guidance: end-user-facing rules → `plugin/rules/`, developer-only rules → `.claude/rules/`
- [ ] E2E: A developer reading the convention can determine whether any given path reference in a plugin file violates
  the convention without consulting source code
