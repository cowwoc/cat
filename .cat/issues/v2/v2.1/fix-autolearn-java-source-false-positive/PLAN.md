# Plan: fix-autolearn-java-source-false-positive

## Problem

AutoLearnMistakes.filterJsonContent() only strips JSON-structured content (lines starting with `{` or `[{`)
but not Java source code output. When the agent reads hook source files via Bash (e.g., `sed`, `grep`, `cat`
on `.java` files), the stdout contains Java string literals like `'PROTOCOL VIOLATION'`, `'BUILD FAILURE'`,
and test-related text from the hook's own pattern definitions and Javadoc. These literals pass through
filterJsonContent() unfiltered and match AutoLearnMistakes Patterns 1, 2, and 3, producing spurious
mistake detections that interrupt workflow and pollute the mistake record.

This is the **third recurrence** of M461/M466 (insufficient output filtering in AutoLearnMistakes).
Previous recurrences: M461, M466. Current: M555.

## Parent Requirements

None

## Reproduction Code

```bash
# Read a hook source file containing pattern keywords in string literals
sed -n '65,90p' client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/DetectAssistantGivingUp.java
# → stdout contains "PROTOCOL VIOLATION" from hook's Javadoc/string literals
# → AutoLearnMistakes Pattern 3 fires: false positive protocol_violation
```

## Expected vs Actual

- **Expected:** Reading Java source files via Bash does not trigger AutoLearnMistakes patterns
- **Actual:** AutoLearnMistakes Pattern 3 (`PROTOCOL_VIOLATION|VIOLATION`) fires when stdout contains
  `'VIOLATION'` from Java source code string literals (not from actual agent protocol violations)

## Root Cause

`AutoLearnMistakes.filterJsonContent()` (line ~85 in AutoLearnMistakes.java) only strips JSON-formatted
lines. Java source code lines contain keywords that match mistake detection patterns but are not agent
actions. The method needs to detect and strip Java source code output before pattern matching.

Recurrence root cause: each prior fix addressed only the specific pattern that caused the observed
false positive, not the underlying filter gap. A structural fix is needed that filters all recognized
non-agent-action content types (JSON, Java source, Maven output metadata), not just one pattern at a time.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Could over-strip content and miss real mistakes if filter is too broad
- **Mitigation:** TDD approach — write failing tests for true positives (real Maven BUILD FAILURE,
  real protocol violations) AND for false positives (Java source code reads), verify all pass after fix

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/AutoLearnMistakes.java` — extend
  `filterJsonContent()` to detect and strip Java source code lines using Java-specific syntax patterns:
  - Lines matching `^\s*(return Result\.|Pattern\.compile\(|if \(|public final class|private static final|\* @param|/\*\*|\*/\s*$|\s*\*\s)`
  - Lines matching standard Java code structure that cannot appear in Maven output or agent text
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/` (AutoLearnMistakesTest or new test file) —
  add test cases per the TDD-first acceptance criteria

## Test Cases

Per M555 learn task acceptance criteria (TDD order: write failing tests first):
- [ ] `Pattern 3 (protocol_violation) does NOT trigger when stdout contains DetectAssistantGivingUp.java source`
- [ ] `Pattern 1 (build_failure) does NOT trigger when stdout contains AutoLearnMistakes.java 'BUILD FAILURE' string literal`
- [ ] `Pattern 2 (test_failure) does NOT trigger when stdout contains hook source with 'test failures' in Javadoc`
- [ ] `Real Maven BUILD FAILURE output still triggers Pattern 1` (true positive preserved)
- [ ] `Real protocol violations from non-Java-source output still trigger Pattern 3` (true positive preserved)
- [ ] `No regression in other AutoLearnMistakes patterns`

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1 (TDD: Write Failing Tests)

- Invoke `/cat:tdd-implementation` for GAP-1773608389
- Write failing tests for all 6 test cases above in AutoLearnMistakes test class
- Run `mvn -f client/pom.xml test -pl . -Dtest=AutoLearnMistakesTest` — verify tests FAIL (confirms bug)
- Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/AutoLearnMistakesTest.java` (or equivalent)

### Wave 2 (Fix)

- Extend `filterJsonContent()` in AutoLearnMistakes.java to detect Java source code patterns
- Design the filter to strip lines matching Java-specific syntax (not broad enough to strip valid agent output)
- Run full test suite: `mvn -f client/pom.xml test`
- Verify all 6 test cases PASS after fix
- Files: `client/src/main/java/io/github/cowwoc/cat/hooks/tool/post/AutoLearnMistakes.java`

### Wave 3 (Verify + Commit)

- Confirm false-positive scenario is resolved: simulate reading DetectAssistantGivingUp.java and verify no spurious trigger
- Confirm true-positive scenarios still work: real Maven BUILD FAILURE output triggers Pattern 1
- Update STATE.md with completion
- Commit with type `bugfix:`

## Post-conditions

- [ ] `filterJsonContent()` strips lines matching Java source code syntax patterns
- [ ] Pattern 3 does NOT trigger when Bash reads DetectAssistantGivingUp.java or similar hook source files
- [ ] Pattern 1 does NOT trigger when Bash reads AutoLearnMistakes.java containing 'BUILD FAILURE' in string literals
- [ ] Real Maven `BUILD FAILURE` output still triggers Pattern 1 (true positive preserved)
- [ ] Real protocol violations from non-Java-source output still trigger Pattern 3 (true positive preserved)
- [ ] Tests in AutoLearnMistakesTest (or equivalent) cover all false-positive and true-positive cases
- [ ] No regression in other AutoLearnMistakes patterns
- [ ] E2E: Run the exact reproduction step (read DetectAssistantGivingUp.java via sed) — confirm no protocol_violation hook fires