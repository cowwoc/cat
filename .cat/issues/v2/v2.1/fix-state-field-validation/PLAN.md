# Plan: fix-state-field-validation

## Problem

Agents occasionally write STATE.md files with unsupported field names (e.g., `**Final commit:**`).
The StateSchemaValidator validates field values but does not reject field names outside the allowed
set, allowing schema violations to be committed silently.

## Parent Requirements

None — correctness fix

## Reproduction Code

```
# Agent writes STATE.md with unsupported field:
# State
# - **Status:** closed
# - **Progress:** 100%
# - **Dependencies:** []
# - **Blocks:** []
# - **Final commit:** abc123
```

## Expected vs Actual

- **Expected:** Pre-write hook rejects STATE.md containing `**Final commit:**` with a clear error
- **Actual:** Field is accepted silently; invalid STATE.md gets committed

## Root Cause

StateSchemaValidator checks field values against the schema but does not validate field names
against the allowed set (Status, Progress, Dependencies, Blocks, Resolution, Parent).

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Existing valid STATE.md files are unchanged; stricter validation may surface
  previously-hidden issues in test fixtures
- **Mitigation:** Verify all test STATE.md fixtures use only allowed fields before tightening validation

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/write/StateSchemaValidator.java` — add
  field name validation to reject keys outside the allowed set

## Test Cases

- [ ] STATE.md with unsupported field `**Final commit:**` is rejected with clear error message
- [ ] STATE.md with all allowed fields (Status, Progress, Dependencies, Blocks, Resolution, Parent)
  is accepted
- [ ] Partial STATE.md (only mandatory fields) is accepted

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Read `StateSchemaValidator.java` to understand current validation logic
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/write/StateSchemaValidator.java`
- Add allowed-field-names set and reject any key not in the set, with error message naming the
  unsupported field
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/write/StateSchemaValidator.java`
- Write test cases verifying unsupported fields are rejected and all valid fields are accepted
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/write/StateSchemaValidatorTest.java`

## Post-conditions

- [ ] STATE.md with unsupported field names is rejected by pre-write hook with clear error message
- [ ] All existing valid STATE.md files continue to pass validation
- [ ] All test cases pass (`mvn -f client/pom.xml test`)
- [ ] E2E: Attempt to write a STATE.md with `**Final commit:**` field — hook rejects with message
  naming the unsupported field
