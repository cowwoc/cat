# Plan

## Goal

Move the work verify directory from `.cat/work/verify` to `.cat/verify` inside the worktree. This refactoring improves directory organization by consolidating CAT-managed state under the `.cat/` directory hierarchy and clarifies the semantic separation between implementation work (`.cat/work/`) and verification state (`.cat/verify/`).

## Pre-conditions

(none)

## Post-conditions

- [ ] User-visible behavior unchanged
- [ ] Tests passing
- [ ] Code quality improved
- [ ] E2E verification — verify the new directory structure works as expected
