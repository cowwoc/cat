# Plan: fix-record-learning-error-diagnosis

## Problem
When `record-learning` fails to parse the existing `mistakes-YYYY-MM.json` file (e.g., due to literal
newlines in string values from a prior malformed write), the error message is a raw Jackson exception:
`Illegal unquoted character ((CTRL-CHAR, code 10))`. This gives no indication of whether the problem
is in (a) the input prevent-phase JSON passed by the orchestrator, or (b) the pre-existing mistakes file.
The learn skill's step 4c currently has no way to distinguish these two failure modes and stops in both
cases, even when the fix (repair the mistakes file) is straightforward.

## Satisfies
None — internal tooling bugfix

## Root Cause
`RecordLearning.java` `loadOrCreateMistakesFile()` propagates the raw Jackson `StreamReadException`
without wrapping it in a message that names the file being parsed or identifies the failure source.
The caller (the learn orchestrator) receives an opaque error and cannot distinguish input corruption
from existing-file corruption.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None — only changes error message content, not behavior
- **Regression Risk:** None — existing tests still pass; only adds a new test case

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/RecordLearning.java` — Wrap the parse
  exception in `loadOrCreateMistakesFile()` with a message naming the file path and indicating the
  mistakes file needs manual repair
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/RecordLearningMainTest.java` — Add regression
  test for the corrupted mistakes file scenario; verify the error message names the file path

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
1. **Step 1:** In `RecordLearning.java`, locate `loadOrCreateMistakesFile()`. Wrap the Jackson parse
   exception with an `IOException` whose message reads:
   `"Failed to parse mistakes file at '<path>': <original message>. The file contains malformed JSON. Inspect and fix the file, then retry."`.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/RecordLearning.java`
2. **Step 2:** Add a test to `RecordLearningMainTest.java` that writes a mistakes file containing a
   string value with a literal newline, invokes `record-learning`, and verifies the error output
   contains the file path.
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/RecordLearningMainTest.java`
3. **Step 3:** Run tests: `mvn -f client/pom.xml test`
   Update STATE.md: status: closed, progress: 100%

## Post-conditions
- [ ] When `record-learning` is invoked with a valid prevent-phase JSON but a corrupted mistakes file,
  the error message names the mistakes file path and instructs the user to inspect and repair it
- [ ] `RecordLearningMainTest` includes a regression test for the corrupted mistakes file scenario
- [ ] No regression: valid invocations (good input + good mistakes file) continue to succeed
