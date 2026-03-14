# Plan: rename-config-tests

## Objective
Update all Java and Bats test files to reference the new config filenames, and verify the full test suite passes. This is Wave 3 of the parent issue decomposition.

## Parent Issue
2.1-rename-cat-config-files (Wave 3 of 3)

## Files to Modify

### Java Tests
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/ConfigTest.java` - Update test references
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetConfigOutputTest.java` - Update test references
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetDiffOutputTest.java` - Update test references
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/CheckDataMigrationTest.java` - Update test references
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java` - Update test references
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceApprovalBeforeMergeTest.java` - Update test references
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/BlockUnauthorizedMergeCleanupTest.java` - Update test references
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/SessionStartHookTest.java` - Update test references

### Bats Tests
- `tests/hooks/migration-2.1.bats` - Update test references
- `tests/hooks/migration-2.2.bats` - Update test references

## Pre-conditions
- [ ] 2.1-rename-config-java-core is closed (Java source updated)
- [ ] 2.1-rename-config-plugin-docs is closed (plugin/docs updated)

## Post-conditions
- [ ] All Java tests pass: `mvn -f client/pom.xml test`
- [ ] No remaining references to `cat-config.json` or `cat-config.local.json` in any test file
- [ ] E2E: Run `get-config-output effective` and confirm it reads from `.cat/config.json` successfully
