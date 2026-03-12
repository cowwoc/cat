# Plan: clarify-retrospective-occurrence-counts

## Goal

Make pattern occurrence counts in `/cat:retrospective` output more intuitive by replacing the opaque
`X/Y` notation with labeled values so users can understand the meaning without consulting the source code.

## Parent Requirements

None — internal UX improvement.

## Type

feature

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Existing tests reference the current format string and will need updating.
- **Mitigation:** Update test assertions alongside the format change.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetRetrospectiveOutput.java` — Change the
  format string at lines 387-390 from `(occurrences: %d/%d)` to a labeled format.
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetRetrospectiveOutputTest.java` — Update test
  assertions to match the new format string.

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Read `GetRetrospectiveOutput.java` lines 380-395 to understand the current format code.
- Change the format string from `"(occurrences: %d/%d)"` to `"(%d total, %d after fix)"` (or equivalent
  clear labeling) in both the pattern-name and no-pattern-name branches.
- Read `GetRetrospectiveOutputTest.java` and update any test assertions that reference the old
  `occurrences: X/Y` format to match the new labeled format.
- Run `mvn -f client/pom.xml test` and confirm all tests pass.
- Update STATE.md: status closed, progress 100%.
  - Files: `client/src/main/java/.../GetRetrospectiveOutput.java`,
    `client/src/test/java/.../GetRetrospectiveOutputTest.java`,
    `.cat/issues/v2/v2.1/clarify-retrospective-occurrence-counts/STATE.md`

## Post-conditions

- [ ] `GetRetrospectiveOutput.java` uses a labeled format (e.g., `total X, Y after fix`) instead of `X/Y`
- [ ] All existing tests pass with the new format
- [ ] Running `/cat:retrospective` produces output where pattern occurrence counts are self-explanatory
