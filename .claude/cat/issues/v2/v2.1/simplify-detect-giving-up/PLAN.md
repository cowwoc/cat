# Plan: simplify-detect-giving-up

## Problem
DetectGivingUp uses file-based session state (rate limiting, pattern markers, stale dir cleanup) that causes
flaky tests due to cross-test interference in /tmp. The rate limit is unnecessary since each invocation receives
only the current user prompt (not accumulated context). The prompt length limit (MAX_PROMPT_LENGTH = 100,000)
arbitrarily truncates long prompts, causing patterns at the end to be missed.

## Satisfies
None

## Reproduction Code
```java
// Test preservesFreshSessionDirs fails when run with full suite because
// concurrent tests create claude-hooks-session-* dirs in /tmp, and
// cleanupStaleSessionDirs() from one test deletes dirs created by another.
// The fresh dir has mtime set to a fixed-clock time (2025-01-14) which is
// years in the past relative to the real-time clock used by concurrent tests.
```

## Expected vs Actual
- **Expected:** Tests pass reliably in parallel
- **Actual:** preservesFreshSessionDirs is flaky due to cross-test /tmp interference

## Root Cause
File-based state in /tmp is unnecessary. Rate limiting serves no purpose since prompts arrive one at a time.
Notify-once tracking is also unnecessary — a new handler instance is created per invocation in production
(`GetSkillOutput` creates `new DetectGivingUp()` each time), so in-memory state wouldn't persist anyway.
Repeating the reminder on every matching prompt is acceptable and potentially beneficial. Without any file-based
state, cleanupStaleSessionDirs() is also unnecessary.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Detection logic unchanged; only state management is removed
- **Mitigation:** All existing detection tests verify the same patterns

## Files to Modify
- hooks/src/main/java/io/github/cowwoc/cat/hooks/prompt/DetectGivingUp.java - Remove all file I/O, rate
  limit, session tracking, cleanup, Clock dependency, and MAX_PROMPT_LENGTH truncation. Make class stateless.
- hooks/src/test/java/io/github/cowwoc/cat/hooks/test/DetectGivingUpTest.java - Remove TestSession, rate
  limit tests, cleanup tests, session tracking test. Simplify all tests to not require /tmp dirs.

## Test Cases
- [ ] All pattern detection tests pass (constraint rationalization, code disabling, compilation abandonment,
  permission seeking)
- [ ] Quoted text still ignored
- [ ] Unbalanced quotes still detected
- [ ] Full prompt scanned (no truncation) - pattern at end of long prompt detected
- [ ] No /tmp directories created during tests

## Execution Steps
1. **Step 1:** Read java.md conventions before editing
2. **Step 2:** Modify DetectGivingUp.java:
   - Remove fields: MAX_PROMPT_LENGTH, RATE_LIMIT_SECONDS, SESSION_DIR_TTL_DAYS, SESSION_DIR_PREFIX,
     RATE_LIMIT_FILE, PATTERN_MARKER, clock
   - Remove both constructors (no-arg and Clock). Class becomes stateless with no constructor needed.
   - In check(): Remove limitedPrompt truncation — pass full prompt directly to removeQuotedSections().
     Remove all sessionId-related logic (sanitizeSessionId, sessionDir creation, checkRateLimit,
     patternMarker check/write, cleanupStaleSessionDirs call). Keep sessionId parameter validation for API
     compatibility.
   - Remove methods: sanitizeSessionId, checkRateLimit, cleanupStaleSessionDirs, deleteDirectory
   - Remove unused imports: java.io.IOException, java.nio.file.*, java.time.Clock, java.time.Instant,
     java.util.concurrent.TimeUnit, io.github.cowwoc.pouch10.core.WrappedCheckedException
   - Update class Javadoc to reflect simplified feature list (remove rate limiting, session tracking, cleanup)
3. **Step 3:** Modify DetectGivingUpTest.java:
   - Remove TestSession record and createTestSession() method
   - Remove tests: rateLimitingWorks, rateLimitAllowsAtExactBoundary, rateLimitBlocksWithinBoundary,
     cleansUpStaleSessionDirs, preservesFreshSessionDirs, sessionTrackingPreventsDuplicates
   - Simplify all remaining tests: remove try-with-resources TestSession, just pass a plain session ID string
   - Update promptLengthLimitEnforced: rename to detectsPatternAtEndOfLongPrompt, verify pattern IS detected
     (result is not empty)
   - Remove unused imports: java.io.IOException, java.nio.file.*, java.time.*
4. **Step 4:** Update test module-info.java: Remove the `opens` clause added for TestNG if no longer needed
   (tests no longer use /tmp or file-based cleanup)
5. **Step 5:** Run `mvn -f hooks/pom.xml verify` to confirm all tests pass

## Success Criteria
- [ ] All tests pass with `mvn -f hooks/pom.xml verify`
- [ ] No /tmp file I/O in DetectGivingUp.java
- [ ] No flaky tests (no cross-test /tmp interference)
- [ ] Full prompt scanned without truncation
- [ ] No regressions in pattern detection
