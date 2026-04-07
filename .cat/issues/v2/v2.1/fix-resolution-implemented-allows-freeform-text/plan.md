# Plan: fix-resolution-implemented-allows-freeform-text

## Type
bugfix

## Problem

`StateSchemaValidator.validateClosedResolution()` allows free-form notes after `"implemented"` in
the `resolution` field of `index.json`. For example, the following is incorrectly accepted:

```json
{
  "status": "closed",
  "resolution": "implemented with security concern addressed: path traversal validation added to getCatSessionPath()"
}
```

This happens because the validator checks `resolution.startsWith(prefix + " ")` for all prefixes
in `VALID_RESOLUTION_PREFIXES`, including `"implemented"`. The intent was to allow trailing context
only for resolutions that require it (`duplicate <issue-id>`, `obsolete <explanation>`, etc.),
not for `"implemented"` which should be self-contained.

## Root Cause

In `StateSchemaValidator`:

```java
private static final Set<String> VALID_RESOLUTION_PREFIXES =
  Set.of("implemented", "duplicate", "obsolete", "won't-fix", "not-applicable");
```

All prefixes are treated identically — the validator accepts both exact match and `prefix + " " + text`.
`"implemented"` should only accept an exact match.

## Parent Requirements

None

## Expected vs Actual

- **Expected:** `"implemented with notes"` → blocked with schema violation error
- **Actual:** `"implemented with notes"` → allowed (startsWith `"implemented "`)

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Minimal — only tightens validation for `"implemented"`. All other prefixes
  (`duplicate`, `obsolete`, `won't-fix`, `not-applicable`) continue to allow trailing text.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/write/StateSchemaValidator.java` — split
  `VALID_RESOLUTION_PREFIXES` into exact-match set (`"implemented"`) and prefix-match set (the rest)
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/StateSchemaValidatorTest.java` — add test
  case that verifies `"implemented with notes"` is rejected

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1

- In `StateSchemaValidator`, split the single `VALID_RESOLUTION_PREFIXES` set into:
  - `EXACT_RESOLUTIONS = Set.of("implemented")` — must match exactly, no trailing text
  - `PREFIX_RESOLUTIONS = Set.of("duplicate", "obsolete", "won't-fix", "not-applicable")` — allow
    `prefix + " " + explanation` as before
  - Update `validateClosedResolution()` to check exact resolutions first (equality), then prefix
    resolutions (`startsWith(prefix + " ")`). Reject anything that matches neither.
  - Update the error message to reflect the new constraint (show `implemented` without angle brackets)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/write/StateSchemaValidator.java`
- Add a test case `implementedResolutionWithNotesIsRejected()` that writes a closed `index.json`
  with `"resolution": "implemented with security concern addressed: ..."` and asserts the result is
  blocked with an error mentioning `"implemented"`.
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/StateSchemaValidatorTest.java`
- Run all tests: `python3 /workspace/run_tests.py`

## Post-conditions

- [ ] `StateSchemaValidator` rejects `"implemented with <any trailing text>"` with a schema violation
- [ ] `StateSchemaValidator` still accepts `"implemented"` exactly
- [ ] `StateSchemaValidator` still accepts `"duplicate 2.1-other-issue"`, `"obsolete <explanation>"`,
  `"won't-fix <explanation>"`, `"not-applicable <explanation>"`
- [ ] New test `implementedResolutionWithNotesIsRejected` passes
- [ ] All existing tests pass with no regressions
