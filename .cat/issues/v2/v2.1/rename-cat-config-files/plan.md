# Plan: rename-cat-config-files

## Current State
The config files `.cat/cat-config.json` and `.cat/cat-config.local.json` have a redundant `cat-` prefix since they
already reside inside the `.cat/` directory.

## Target State
Rename to `.cat/config.json` and `.cat/config.local.json` respectively, and update all references across the codebase.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** All code referencing the old filenames must be updated atomically
- **Mitigation:** Migration script handles the physical rename; comprehensive grep to find all references

## Files to Modify

### Java Source (constants and path resolution)
- `client/src/main/java/io/github/cowwoc/cat/hooks/Config.java` - Update config filename constants
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` - Update config path references
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/CheckDataMigration.java` - Update config path references
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetConfigOutput.java` - Update config path references
- `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceApprovalBeforeMerge.java` - Update config path references
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnauthorizedMergeCleanup.java` - Update config path references

### Java Tests
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/ConfigTest.java` - Update test references
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetConfigOutputTest.java` - Update test references
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetDiffOutputTest.java` - Update test references
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/CheckDataMigrationTest.java` - Update test references
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java` - Update test references
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceApprovalBeforeMergeTest.java` - Update test references
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockUnauthorizedMergeCleanupTest.java` - Update test references
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionStartHookTest.java` - Update test references

### Plugin Skills & Concepts
- `plugin/skills/init/first-use.md` - Update config filename references
- `plugin/skills/help/first-use.md` - Update config filename references
- `plugin/skills/add/first-use.md` - Update config filename references
- `plugin/skills/config/first-use.md` - Update config filename references
- `plugin/skills/work/first-use.md` - Update config filename references
- `plugin/skills/work-prepare-agent/first-use.md` - Update config filename references
- `plugin/skills/work-implement-agent/first-use.md` - Update config filename references
- `plugin/skills/work-merge-agent/first-use.md` - Update config filename references
- `plugin/skills/work-review-agent/first-use.md` - Update config filename references
- `plugin/skills/skill-builder-agent/first-use.md` - Update config filename references
- `plugin/skills/tdd-implementation-agent/first-use.md` - Update config filename references
- `plugin/skills/stakeholder-review-agent/first-use.md` - Update config filename references
- `plugin/skills/learn/related-files-check.md` - Update config filename references
- `plugin/rules/work-request-handling.md` - Update config filename references
- `plugin/concepts/build-verification.md` - Update config filename references
- `plugin/concepts/worktree-isolation.md` - Update config filename references
- `plugin/concepts/hierarchy.md` - Update config filename references

### Configuration & Gitignore
- `.gitignore` - Update ignored filename pattern
- `.cat/.gitignore` - Update ignored filename pattern
- `plugin/templates/gitignore` - Update template gitignore entry
- `plugin/templates/project.md` - Update project template reference

### Migration
- `plugin/migrations/2.1.sh` - Add migration step to rename the physical files

### Documentation
- `.claude/rules/common.md` - Update config filename references
- `README.md` - Update config filename references
- `docs/severity.md` - Update config filename references
- `docs/patience.md` - Update config filename references

### Bats Tests
- `tests/hooks/migration-2.1.bats` - Update test references
- `tests/hooks/migration-2.2.bats` - Update test references

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1: Core rename and migration
- Add migration step to `plugin/migrations/2.1.sh` to rename physical files (`cat-config.json` → `config.json`,
  `cat-config.local.json` → `config.local.json`)
  - Files: `plugin/migrations/2.1.sh`
- Update Java config constants and path resolution
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/Config.java`,
    `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`,
    `client/src/main/java/io/github/cowwoc/cat/hooks/session/CheckDataMigration.java`,
    `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetConfigOutput.java`,
    `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceApprovalBeforeMerge.java`,
    `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnauthorizedMergeCleanup.java`

### Wave 2: Plugin and documentation updates
- Update all plugin skill files, concepts, rules, and templates
  - Files: All plugin skill `first-use.md` files, `plugin/rules/work-request-handling.md`,
    `plugin/concepts/build-verification.md`, `plugin/concepts/worktree-isolation.md`,
    `plugin/concepts/hierarchy.md`, `plugin/templates/project.md`, `plugin/templates/gitignore`
- Update gitignore files and documentation
  - Files: `.gitignore`, `.cat/.gitignore`, `.claude/rules/common.md`, `README.md`,
    `docs/severity.md`, `docs/patience.md`

### Wave 3: Test updates and verification
- Update all Java test files to use new config filenames
  - Files: All test files listed above
- Update Bats test files
  - Files: `tests/hooks/migration-2.1.bats`, `tests/hooks/migration-2.2.bats`

## Post-conditions
- [ ] User-visible behavior unchanged — all config reads/writes work as before
- [ ] All tests pass (`mvn -f client/pom.xml test`)
- [ ] No regressions in config loading, writing, or migration
- [ ] E2E: Run `get-config-output effective` and confirm it reads from `.cat/config.json` successfully
- [ ] No remaining references to `cat-config.json` or `cat-config.local.json` in active source code
  (historical references in closed issue PLAN.md files and changelogs are acceptable)
