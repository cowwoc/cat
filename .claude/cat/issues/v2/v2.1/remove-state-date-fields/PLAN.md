# Plan: remove-state-date-fields

## Current State

STATE.md files contain two date fields — `Last Updated` (open issues) and `Completed` (closed issues) — that duplicate
information already available via `git log`. These fields are error-prone to maintain manually, as demonstrated by M440
where STATE.md was closed without the `Resolution` field being set.

## Target State

STATE.md schema contains no date fields. `StateSchemaValidator` rejects files that still contain `Last Updated` or
`Completed`. The `plugin/migrations/2.1.sh` script removes these fields from all existing STATE.md files idempotently.

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** Schema change — existing STATE.md files will fail validator until migrated
- **Mitigation:** Migration script removes fields from all existing files before validator runs; idempotent

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/write/StateSchemaValidator.java` — remove Last Updated
  validation, add rejection of Last Updated/Completed fields
- `client/src/main/java/io/github/cowwoc/cat/hooks/write/ValidateStateMdFormat.java` — remove Last Updated from
  error guidance
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/StateSchemaValidatorTest.java` — update tests
- `plugin/migrations/2.1.sh` — add Phase 7 to strip Last Updated and Completed from all issue-level STATE.md files
- `plugin/concepts/templates/state.md` — remove Last Updated and Completed fields and their documentation
- `.claude/cat/issues/v2/v2.1/*/STATE.md` — removed by migration script (all existing open and closed issues)

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Steps

1. **Step 1:** Read `StateSchemaValidator.java`, `ValidateStateMdFormat.java`, and `StateSchemaValidatorTest.java` to
   understand current validation logic for `Last Updated`.
2. **Step 2:** Update `StateSchemaValidator.java`:
   - Remove `Last Updated` from the mandatory fields set
   - Remove `Last Updated` date-format validation method and its call
   - Add validation that rejects any field named `Last Updated` or `Completed` with a clear error message directing
     user to run `plugin/migrations/2.1.sh`
3. **Step 3:** Update `ValidateStateMdFormat.java` to remove `Last Updated` from the example/guidance text.
4. **Step 4:** Update `StateSchemaValidatorTest.java`:
   - Remove tests that require `Last Updated` to be present
   - Add tests that verify `Last Updated` and `Completed` are rejected with appropriate error messages
   - Remove `Last Updated` from all test STATE.md fixtures
5. **Step 5:** Run `mvn -f client/pom.xml test` — all tests must pass.
6. **Step 6:** Add Phase 7 to `plugin/migrations/2.1.sh` that removes `Last Updated` and `Completed` lines from all
   issue-level STATE.md files under `.claude/cat/issues/`. Must be idempotent (skip files that don't have the fields).
7. **Step 7:** Run the migration script against the workspace to update all existing STATE.md files.
8. **Step 8:** Update `plugin/concepts/templates/state.md` — remove `Last Updated` and `Completed` from the schema
   table, mandatory header fields table, and resolution pattern examples.
9. **Step 9:** Commit all changes.

## Post-conditions

- [ ] `StateSchemaValidator` rejects any STATE.md containing `Last Updated` or `Completed` with a clear error
- [ ] No STATE.md file under `.claude/cat/issues/` contains `Last Updated` or `Completed`
- [ ] `plugin/migrations/2.1.sh` Phase 7 removes these fields idempotently (second run is a no-op)
- [ ] All Java tests pass (`mvn -f client/pom.xml test` exits 0)
- [ ] `plugin/concepts/templates/state.md` contains no mention of `Last Updated` or `Completed` as schema fields
