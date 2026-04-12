# Plan: add-giving-up-context-listing-pattern

## Goal

Add pattern to DetectGivingUp hook to detect when agent lists "Given:" context items (process duration, token usage) before presenting alternative approaches or asking user to choose options.

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Pattern must not trigger false positives when agent legitimately provides context
- **Mitigation:** Test pattern against historical sessions to verify precision

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/prompt/DetectGivingUp.java` — add detection pattern
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/DetectGivingUpTest.java` — add test cases

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1: Add detection pattern to DetectGivingUp hook

Pattern to detect: "Given:" followed by a list where one of the items contains "Token usage:"

Example trigger text:
```
Given:
- Full instruction-builder flow = multi-hour process
- Token usage: 100K/200K
```

Specific pattern: Match "Given:" followed by bulleted list items, where at least one item contains the substring "Token usage:" (case-sensitive).

Implementation:
1. Read DetectGivingUp.java to understand current pattern structure
2. Add new pattern matching "Given:" prefix with list items containing "Token usage:"
3. Ensure pattern does NOT trigger on CURIOSITY level mentions alone
4. Files: `client/src/main/java/io/github/cowwoc/cat/hooks/prompt/DetectGivingUp.java`

### Job 2: Add test coverage

Create test cases covering:
- Positive: "Given:" followed by list with "Token usage:" item
- Negative: "Given:" without "Token usage:" in list items (should not trigger)
- Negative: "Token usage:" without "Given:" prefix (should not trigger)

Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/DetectGivingUpTest.java`

## Post-conditions

- [ ] DetectGivingUp hook detects "Given:" context listing pattern
- [ ] Tests verify pattern triggers on positive cases
- [ ] Tests verify no false positives on legitimate context
- [ ] Build passes (mvn verify)
