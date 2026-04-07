# Plan: lowercase-planning-file-names

## Goal
Rename all uppercase planning file names (CHANGELOG.md, PLAN.md) to lowercase (changelog.md, plan.md)
in the filesystem and update all references to these names across the codebase.

## Parent Requirements
None

## Type
refactor

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Large number of reference sites; missing a reference leaves stale uppercase names in docs
- **Mitigation:** Use comprehensive grep across all file types; migration script handles .cat/issues/ files

## Files to Modify
- All `.cat/issues/v*/v*/CHANGELOG.md` files — rename to `changelog.md` (17 files via migration)
- `CHANGELOG.md` (root) — rename to `changelog.md`
- `CLAUDE.md` — update "CHANGELOG.md" and "PLAN.md" string references
- `README.md` — update "CHANGELOG.md" and "PLAN.md" string references
- `plugin/concepts/hierarchy.md` — update references
- `plugin/concepts/version-paths.md` — update references
- `plugin/concepts/merge-and-cleanup.md` — update references
- `plugin/concepts/agent-architecture.md` — update references
- `plugin/concepts/commit-types.md` — update references
- `plugin/concepts/version-completion.md` — update references
- `plugin/templates/changelog.md` — update any self-references
- `plugin/migrations/2.2.sh` — add migration phase to rename CHANGELOG.md → changelog.md
- `client/src/main/java/io/github/cowwoc/cat/hooks/write/WarnBaseBranchEdit.java` — update protected file list
- All other files containing "CHANGELOG.md" or "PLAN.md" string references

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1: Rename CHANGELOG.md files in .cat/issues/
- Write or update `plugin/migrations/2.2.sh` to rename all `CHANGELOG.md` files to `changelog.md`
  under `.cat/issues/`
  - Files: `plugin/migrations/2.2.sh`
- Run the migration in the worktree to rename existing `.cat/issues/v*/v*/CHANGELOG.md` files
  - Files: `.cat/issues/v1/*/CHANGELOG.md`, `.cat/issues/v2/*/CHANGELOG.md`

### Job 2: Rename root CHANGELOG.md
- Rename `CHANGELOG.md` to `changelog.md` at the project root using `git mv`
  - Files: `CHANGELOG.md`

### Job 3: Update all string references to CHANGELOG.md
- Replace all occurrences of the string `CHANGELOG.md` with `changelog.md` in:
  - `CLAUDE.md`
  - `README.md`
  - All plugin concept files (`plugin/concepts/*.md`)
  - All plugin skill files that reference the name
  - All plugin template files that reference the name
  - `client/src/main/java/io/github/cowwoc/cat/hooks/write/WarnBaseBranchEdit.java`
  - Any other tracked file containing the string `CHANGELOG.md`
- Verify with `git grep -l "CHANGELOG\.md"` that no tracked files still contain uppercase references
  after the update

### Job 4: Update all string references to PLAN.md
- Replace all occurrences of the string `PLAN.md` with `plan.md` in all tracked files
  - Key files: `CLAUDE.md`, `README.md`, plugin concepts, plugin skills, Java source, migration scripts,
    `.cat/retrospectives/mistakes-*.json`
- Verify with `git grep -l "PLAN\.md"` that no tracked files still contain uppercase references
  after the update

## Post-conditions
- [ ] `git grep -l "CHANGELOG\.md"` returns no tracked files (all references lowercase)
- [ ] `git grep -l "PLAN\.md"` returns no tracked files (all references lowercase)
- [ ] No `CHANGELOG.md` files exist under `.cat/issues/` (all renamed to `changelog.md`)
- [ ] Root `CHANGELOG.md` renamed to `changelog.md`
- [ ] Migration script `plugin/migrations/2.2.sh` handles CHANGELOG.md → changelog.md rename
  and is idempotent
- [ ] `mvn -f client/pom.xml verify -e` passes with no test failures
