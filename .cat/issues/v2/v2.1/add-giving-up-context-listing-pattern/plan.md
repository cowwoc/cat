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

Pattern to detect:
```
Given:
- Full instruction-builder flow = multi-hour process
- Current token usage: NNN/200K
```

Or more generally: "Given:" followed by bulleted list containing references to:
- Process duration ("multi-hour", "hours", "large task")
- Token usage ("NNN/200K", "token usage")

Implementation:
1. Read DetectGivingUp.java to understand current pattern structure
2. Add new pattern matching "Given:" prefix with context bullets
3. Ensure pattern does NOT trigger on CURIOSITY level mentions alone
4. Files: `client/src/main/java/io/github/cowwoc/cat/hooks/prompt/DetectGivingUp.java`

### Job 2: Add test coverage

Create test cases covering:
- Positive: "Given: - Full flow = multi-hour process - Token usage: 100K/200K"
- Negative: Legitimate context provision without giving-up intent
- Edge: "Given:" without duration/token context (should not trigger)

Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/DetectGivingUpTest.java`

## Post-conditions

- [ ] DetectGivingUp hook detects "Given:" context listing pattern
- [ ] Tests verify pattern triggers on positive cases
- [ ] Tests verify no false positives on legitimate context
- [ ] Build passes (mvn verify)
