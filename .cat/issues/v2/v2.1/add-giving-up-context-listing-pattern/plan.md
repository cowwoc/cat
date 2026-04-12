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

- `client/src/main/java/io/github/cowwoc/cat/claude/hook/util/GivingUpDetector.java` — add detection pattern
- `client/src/test/java/io/github/cowwoc/cat/client/test/DetectGivingUpTest.java` — add test cases

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1: Add detection pattern to DetectGivingUp hook

Pattern variants to detect:

**Variant 1: Bulleted list format**
```
Given:
- Full instruction-builder flow = multi-hour process
- Current token usage: NNN/200K
```

**Variant 2: Inline format**
```
Given token usage (127K/200K) and complexity remaining (create isolation branch, run trials, grade, report)
```

More generally:
- Bulleted: "Given:" followed by bulleted list containing process duration AND/OR token usage
- Inline: "Given" followed by "token usage" AND "complexity remaining" or similar scope indicators

**Current implementation status:**
- Line 526-528 already detects "token usage (" with slash (covers `token usage (NNN/NNN)`)
- Inline variant needs explicit "complexity remaining" or "remaining" detection after token usage

Implementation:
1. Read DetectGivingUp.java to understand current pattern structure
2. Verify existing "token usage (" pattern (line 526-528) handles inline variant
3. Add detection for "and complexity remaining" or "and X remaining" after "token usage"
4. Consider whether bulleted-list variant is distinct enough to warrant separate detection
5. Files: `client/src/main/java/io/github/cowwoc/cat/claude/hook/util/GivingUpDetector.java`

### Job 2: Add test coverage

Create test cases covering:

**Positive cases (should trigger):**
- Bulleted format: "Given:\n- Full flow = multi-hour process\n- Token usage: 100K/200K"
- Inline format: "Given token usage (127K/200K) and complexity remaining (create isolation branch, run trials, grade, report)"
- Inline variant: "Given token usage (100K/200K) and remaining work (write tests, update docs)"

**Negative cases (should NOT trigger):**
- Token usage without scope indicator: "Given token usage (50K/200K)"
- Legitimate context: "Given the requirements, here's the implementation plan"
- Bulleted list without token/duration context: "Given:\n- User requirements\n- Current implementation"

**Edge cases:**
- "Given:" without duration/token context (should not trigger)
- Token usage alone without "and X remaining" pattern

Files: `client/src/test/java/io/github/cowwoc/cat/client/test/DetectGivingUpTest.java`

## Post-conditions

- [ ] DetectGivingUp hook detects "Given:" context listing pattern
- [ ] Tests verify pattern triggers on positive cases
- [ ] Tests verify no false positives on legitimate context
- [ ] Build passes (mvn verify)
