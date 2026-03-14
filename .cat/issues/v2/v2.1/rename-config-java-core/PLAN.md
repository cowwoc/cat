# Plan: rename-config-java-core

## Objective
Rename `cat-config.json` → `config.json` and `cat-config.local.json` → `config.local.json` in all Java source files and the migration script. This is Wave 1 of the parent issue decomposition.

## Parent Issue
2.1-rename-cat-config-files (Wave 1 of 3)

## Files to Modify

### Migration Script
- `plugin/migrations/2.1.sh` - Add migration step to rename physical files (`cat-config.json` → `config.json`, `cat-config.local.json` → `config.local.json`)

### Java Source
- `client/src/main/java/io/github/cowwoc/cat/hooks/Config.java` - Update config filename constants
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` - Update config path references
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/CheckDataMigration.java` - Update config path references
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetConfigOutput.java` - Update config path references
- `client/src/main/java/io/github/cowwoc/cat/hooks/task/EnforceApprovalBeforeMerge.java` - Update config path references
- `client/src/main/java/io/github/cowwoc/cat/hooks/bash/BlockUnauthorizedMergeCleanup.java` - Update config path references

## Pre-conditions
- [ ] Parent issue 2.1-rename-cat-config-files is open

## Post-conditions
- [ ] Migration script renames physical files atomically and idempotently
- [ ] All Java source references to `cat-config.json` replaced with `config.json`
- [ ] All Java source references to `cat-config.local.json` replaced with `config.local.json`
- [ ] `mvn -f client/pom.xml test` passes (ignoring test-file references — those are Wave 3)
