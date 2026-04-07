# Plan: lowercase-planning-file-names

## Goal
Rename all uppercase planning and state file names (CHANGELOG.md, PLAN.md, STATE.md, ROADMAP.md, PROJECT.md) to
lowercase/json (changelog.md, plan.md, index.json, roadmap.md, project.md) in the filesystem and update all
references to these names across the codebase.

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
- All `.cat/issues/v*/v*/**/STATE.md` files — rename to `index.json` (via migration, already complete)
- `CHANGELOG.md` (root) — rename to `changelog.md` (already complete)
- `.cat/ROADMAP.md` — rename to `.cat/roadmap.md`
- `.cat/PROJECT.md` — rename to `.cat/project.md`
- `CLAUDE.md` — update all uppercase planning file name string references
- `README.md` — update all uppercase planning file name string references
- `plugin/concepts/hierarchy.md` — update references
- `plugin/concepts/version-paths.md` — update references
- `plugin/concepts/merge-and-cleanup.md` — update references
- `plugin/concepts/agent-architecture.md` — update references
- `plugin/concepts/commit-types.md` — update references
- `plugin/concepts/version-completion.md` — update references
- `plugin/templates/changelog.md` — update any self-references
- `plugin/migrations/2.1.sh` — add phases to rename ROADMAP.md → roadmap.md, PROJECT.md → project.md
- `client/src/main/java/io/github/cowwoc/cat/hooks/write/WarnBaseBranchEdit.java` — update protected file list
- All other files containing "ROADMAP.md" or "PROJECT.md" string references

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

### Job 5: Update all string references to STATE.md
- Note: Filesystem rename (STATE.md → index.json under `.cat/issues/`) was already completed in
  v2.1-redesign-issue-file-structure. This job updates textual references only.
- Replace `STATE.md` with `index.json` in documentation and source files:
  - `CLAUDE.md`, `README.md`, `CHANGELOG.md`
  - `.cat/PROJECT.md`, `.cat/retrospectives/mistakes-*.json`, `.cat/retrospectives/index.json`
  - `.claude/rules/common.md`, `.claude/rules/hooks.md`
  - `.claude/skills/cat-release-plugin/SKILL.md`
  - `plugin/concepts/hierarchy.md`, `plugin/concepts/version-paths.md`,
    `plugin/concepts/merge-and-cleanup.md`, `plugin/concepts/version-completion.md`
  - `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java`
  - `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetStatusOutputTest.java`
  - `tests/eval/test_cases.json`, `tests/eval/results/results.json`
- Rename concept/rules files that describe the STATE.md schema:
  - `plugin/concepts/index-schema.md` → `plugin/concepts/index-schema.md` (update all references to it)
  - `.claude/rules/index-schema.md` → `.claude/rules/index-schema.md` (update all references to it)
- Leave `plugin/migrations/2.0.sh` and `plugin/migrations/2.1.sh` unchanged — these scripts target
  `STATE.md` by design (they run on the old format before renaming)
- Leave `tests/hooks/migration-2.1.bats` unchanged — migration tests create `STATE.md` fixtures
  to simulate pre-migration state
- Verify with `git grep -l "STATE\.md"` that only migration scripts and migration tests remain
  after the update; all other tracked files must reference `index.json`

### Job 6: Rename .cat/ROADMAP.md and .cat/PROJECT.md
- Add Phase 23 to `plugin/migrations/2.1.sh` to rename `.cat/ROADMAP.md` → `.cat/roadmap.md` (idempotent)
- Add Phase 24 to `plugin/migrations/2.1.sh` to rename `.cat/PROJECT.md` → `.cat/project.md` (idempotent)
- Run the migration in the worktree to rename existing files
  - Files: `.cat/ROADMAP.md`, `.cat/PROJECT.md`

### Job 7: Update all string references to ROADMAP.md and PROJECT.md
- Replace all occurrences of `ROADMAP.md` with `roadmap.md` in all tracked files
- Replace all occurrences of `PROJECT.md` with `project.md` in all tracked files
  - Key files: `CLAUDE.md`, `README.md`, plugin concepts, plugin skills, `.cat/` files, Java source
- Leave `plugin/migrations/2.1.sh` references unchanged where they target the old filename for migration
- Verify with `git grep -l "ROADMAP\.md"` and `git grep -l "PROJECT\.md"` that only migration scripts
  remain after the update; all other tracked files must reference lowercase names

## Post-conditions
- [ ] `git grep -l "CHANGELOG\.md"` returns only `plugin/migrations/2.1.sh` (Phase 22 targets it for renaming)
  and `changelog.md` (historical changelog entries); all other tracked files use lowercase
- [ ] `git grep -l "PLAN\.md"` returns only `plugin/migrations/2.0.sh`, `plugin/migrations/2.1.sh`,
  `plugin/migrations/registry.json`, `tests/hooks/migration-2.1.bats`,
  and `tests/hooks/block-merge-commits.bats` (migration scripts and tests process pre-rename state);
  all other tracked files use lowercase `plan.md`
- [ ] `git grep -l "STATE\.md"` returns only `plugin/migrations/2.0.sh`, `plugin/migrations/2.1.sh`,
  and `tests/hooks/migration-2.1.bats` (all other files updated to index.json)
- [ ] `git grep -l "ROADMAP\.md"` returns no tracked files (all references lowercase)
- [ ] `git grep -l "PROJECT\.md"` returns no tracked files (all references lowercase)
- [ ] No `CHANGELOG.md` files exist under `.cat/issues/` (all renamed to `changelog.md`)
- [ ] Root `CHANGELOG.md` renamed to `changelog.md` (already complete)
- [ ] `.cat/ROADMAP.md` renamed to `.cat/roadmap.md`
- [ ] `.cat/PROJECT.md` renamed to `.cat/project.md`
- [ ] `.cat/rules/INDEX.md` renamed to `.cat/rules/index.md`
- [ ] Migration script `plugin/migrations/2.1.sh` Phases 23-25 handle ROADMAP.md, PROJECT.md, and INDEX.md
  renames and are idempotent
- [ ] `mvn -f client/pom.xml verify -e` passes with no test failures
