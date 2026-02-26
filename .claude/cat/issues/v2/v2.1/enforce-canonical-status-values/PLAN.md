# Plan: enforce-canonical-status-values

## Goal

Remove all backwards-compatibility status aliases from the codebase and enforce that only canonical status
values (`open`, `in-progress`, `closed`, `blocked`) are accepted. Legacy aliases (`pending`, `completed`,
`complete`, `done`, `in_progress`, `active`) must be migrated via the v2.1 migration script rather than
silently normalized at read time.

## Satisfies

Data Migrations policy in common.md: "We do NOT maintain backward compatibility when a migration script
updates the data structure."

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Existing STATE.md files with legacy status values will fail to parse after this change
- **Mitigation:** Migration script (`plugin/migrations/2.1.sh`) converts all legacy values before the
  readers enforce canonical-only

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java` - Remove alias normalization
  from `getIssueStatus()`, reject non-canonical values with IOException
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatusOutput.java` - Remove `"done"` checks
  from version and issue status comparisons
- `client/src/main/java/io/github/cowwoc/cat/hooks/write/StateSchemaValidator.java` - Update error message
  to reference migration script
- `plugin/migrations/2.1.sh` - Add migrations for `done` -> `closed`, `in_progress` -> `in-progress`,
  `active` -> `in-progress`
- `client/src/main/java/io/github/cowwoc/cat/hooks/SharedSecrets.java` - Add IssueDiscoveryAccess support
  for cross-module testing
- `client/src/main/java/io/github/cowwoc/cat/hooks/IssueDiscoveryAccess.java` - New interface for
  SharedSecrets mechanism
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/IssueDiscoveryTest.java` - Replace alias
  normalization tests with canonical acceptance and non-canonical rejection tests

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. Remove backwards-compatibility aliases from `IssueDiscovery.getIssueStatus()` — reject non-canonical
   values with IOException instead of normalizing them
2. Remove `"done"` alias checks from `GetStatusOutput.java`
3. Update `StateSchemaValidator.java` error message to reference migration script
4. Add legacy status migrations to `plugin/migrations/2.1.sh` Phase 2
5. Create `IssueDiscoveryAccess` interface and register with `SharedSecrets` to enable cross-module testing
   of the private `getIssueStatus()` method
6. Write tests verifying canonical statuses are accepted and non-canonical aliases are rejected

## Post-conditions
- [ ] `IssueDiscovery.getIssueStatus()` throws IOException for any non-canonical status value
- [ ] `GetStatusOutput` does not reference any legacy alias values
- [ ] `StateSchemaValidator` error message includes migration guidance
- [ ] `plugin/migrations/2.1.sh` migrates `done`, `in_progress`, and `active` to canonical values
- [ ] Tests verify both acceptance of canonical values and rejection of non-canonical aliases
- [ ] All tests pass (`mvn -f client/pom.xml verify` exits 0)
