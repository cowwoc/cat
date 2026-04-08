# Plan: fix-giving-up-detector-broad-compound-patterns

## Problem
`GivingUpDetector.detectCodeDisabling()` fires a false positive on normal technical discussion because
the compound check `hasBrokenCodeIndicator(text) && hasRemovalAction(text)` matches isolated words
anywhere in the same message rather than requiring first-person intent to remove/disable code.

## Reproduction Code
```
GivingUpDetector detector = new GivingUpDetector();
detector.check("allow fixing adjacent broken code — don't skip error handling");
// Returns CODE_REMOVAL_REMINDER (wrong — should return empty)
```

## Expected vs Actual
- **Expected:** empty string (no violation — this is technical discussion, not agent giving up)
- **Actual:** `CODE_REMOVAL_REMINDER` fires because `"broken"` matches `hasBrokenCodeIndicator` and
  `"skip"` matches `hasRemovalAction`, even though neither reflects agent intent to disable code

## Root Cause
`hasBrokenCodeIndicator` matches the word `"broken"` anywhere in the message.
`hasRemovalAction` matches the word `"skip"` anywhere in the message.
The compound check fires whenever both appear in the same message — regardless of whether they describe
the agent's own intent. Normal discussion of broken code as a topic (e.g., summarising a gist patch
about "fixing adjacent broken code") combined with any negated removal word ("don't skip") triggers
a false positive that persists for the entire session since the assistant message remains in the
JSONL conversation log.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Tightening the compound check could miss real giving-up patterns that use
  third-person phrasing; mitigated by keeping all standalone exact-phrase checks unchanged
- **Mitigation:** Add false-positive test before changing logic; verify all existing tests still pass

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GivingUpDetector.java` — replace compound
  `hasBrokenCodeIndicator + hasRemovalAction` check with a new method requiring first-person
  co-location (e.g. `"i'll remove"`, `"let me skip"`, `"i'll disable"`, `"let me remove"`,
  `"i will disable"`, `"i'm going to remove"`)
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GivingUpDetectorTest.java` — add failing
  test for the false-positive case first (TDD), then confirm it passes after fix

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1
- Add failing test to `GivingUpDetectorTest`:
  - Input: `"allow fixing adjacent broken code — don't skip error handling"`
  - Expected: `detector.check(...)` returns empty string
  - Verify test FAILS before fix
- Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/GivingUpDetectorTest.java`

### Job 2
- Replace the compound `hasBrokenCodeIndicator(textLower) && hasRemovalAction(textLower)` check in
  `detectCodeDisabling()` with a new private method `hasFirstPersonCodeRemoval(text)` that matches
  first-person phrases pairing a broken-code indicator with a removal action:
  - `"i'll remove"`, `"i'll disable"`, `"i'll skip"`, `"i will remove"`, `"i will disable"`,
    `"i will skip"`, `"let me remove"`, `"let me disable"`, `"let me skip"`,
    `"i'm going to remove"`, `"i'm going to disable"`, `"i'm going to skip"`
  - Keep all standalone exact-phrase checks in `detectCodeDisabling()` unchanged
  - Keep `hasBrokenCodeIndicator` and `hasRemovalAction` methods if still used elsewhere;
    delete if they become dead code
- Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/GivingUpDetector.java`

## Post-conditions
- [ ] `detector.check("allow fixing adjacent broken code — don't skip error handling")` returns empty
- [ ] All existing `GivingUpDetectorTest` and `DetectGivingUpTest` tests pass
- [ ] `mvn -f client/pom.xml verify -e` exits 0
