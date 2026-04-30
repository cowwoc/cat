# Plan: split-runtime-rules-directories

## Goal

Split `.cat/rules/` into runtime-specific directories:
- `.cat/claude/rules/`
- `.cat/codex/rules/`
- `.cat/agents/rules/`

The `agents` runtime must contain rules shared across all runtimes. Update all plugin code that currently reads from `.cat/rules/` so it resolves rules from the new runtime-specific structure.

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** Rule-loading regressions if path resolution is incompletely migrated; duplicated or conflicting rules between runtime directories
- **Mitigation:** Add path-resolution tests and run full verification for rule-loading flows

## Files to Modify

- `.cat/rules/` tree — move/split existing files into `.cat/claude/rules/`, `.cat/codex/rules/`, and `.cat/agents/rules/`
- `plugin/**` files that reference `.cat/rules/` paths — update to runtime-specific paths
- `client/**` path-resolution code if it currently hardcodes `.cat/rules/`
- Tests covering rule loading and path references

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1

- Inventory every rule file under `.cat/rules/`
- Classify each file as:
  - Claude-specific (`.cat/claude/rules/`)
  - Codex-specific (`.cat/codex/rules/`)
  - Shared (`.cat/agents/rules/`)
- Define deterministic resolution behavior when runtime-specific and shared rules both exist

### Job 2

- Create new directory structure under `.cat/`
- Move/split rules into runtime folders according to classification
- Remove legacy `.cat/rules/` reads (no backward-compatibility fallbacks)

### Job 3

- Update plugin/client code paths that load rules from `.cat/rules/`
- Ensure call sites pass runtime context (`claude`, `codex`, or `agents` resolution rules)
- Update any docs or templates that reference old rule paths

### Job 4

- Add/adjust tests for runtime-specific rule loading and shared-rule inclusion
- Run full test suite and verify no remaining `.cat/rules/` references in active code paths

## Post-conditions

- [ ] Runtime rule directories exist: `.cat/claude/rules/`, `.cat/codex/rules/`, `.cat/agents/rules/`
- [ ] Rules are classified and relocated with no legacy `.cat/rules/` path usage in runtime code
- [ ] Plugin/client rule-loading code resolves the new structure correctly
- [ ] Automated tests cover runtime-specific and shared rule resolution
- [ ] Full verification passes
