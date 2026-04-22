# Plan: SPRT Early Failure Detection

## Goal

Enhance sprt-runner-agent with automatic early failure detection and intelligent test prioritization. Stop testing after the first 3 runs if failures are detected, automatically analyze the failure, and prioritize previously-failed tests on re-runs.

## Parent Requirements

None - Testing infrastructure enhancement

## Current State

**Manual detection:**
- User must watch monitor output for repeated failures (Signal 2 in current procedure)
- Detection happens after 2+ runs when user observes identical failures
- Requires manual intervention to kill SPRT and investigate

**No test prioritization:**
- All tests run in original discovery order on every attempt
- Failed tests don't get priority treatment on retry
- No memory of which tests passed vs failed in previous runs

**Early abort only on REJECT:**
- Testing continues until SPRT reaches statistical REJECT boundary
- May waste resources running 10+ trials when failure is obvious after 2-3 runs

## Proposed Behavior

**Automatic early detection:**
1. After each batch in the first 3 runs completes, check for any test cases with failures
2. If failures found, complete the current batch then stop
3. Automatically trigger investigation procedure instead of continuing to SPRT boundaries

**Intelligent test prioritization:**
1. Track which test cases failed in previous runs
2. On re-run, execute previously-failed tests first
3. Only run previously-passing tests after all previous failures have passed
4. This gives fast feedback on whether fixes resolved the issues

**Auto-investigation:**
- Read partial SPRT results automatically
- Present failure patterns and evidence to user
- Skip manual "watch and kill" workflow

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** 
  - Early stopping might miss edge cases that only appear after many runs
  - Test ordering changes might affect tests with unintended dependencies
- **Mitigation:**
  - Only stop early when clear failure pattern detected (not on single flaky failure)
  - Test isolation requirements already prevent inter-test dependencies
  - Still run full SPRT to completion if no early failures detected

## Research Findings

**Current SPRT workflow** (`InstructionTestRunner.java run-full-sprt`):
- Orchestrates 8 steps: prepare run → create isolation branch → initialize SPRT state → run batches → write results → cleanup
- Each batch: create runner worktrees → launch parallel trials → grade outputs → update SPRT state
- Early abort exists but only triggers when any TC reaches REJECT decision (not on early failure patterns)

**SPRT state file** (`sprt-state.json`):
- Tracks per-test-case decisions (ACCEPT/REJECT/INCONCLUSIVE) and run counts
- Written after each batch completes
- Does NOT currently track which specific runs failed, only final decision state

**Test case ordering**:
- Currently uses directory scan order (alphabetical by filename)
- No reordering based on previous results

**Implementation strategy**:
1. Extend `sprt-state.json` schema to include `failed_test_ids` array
2. After each batch (runs 1-3), check if any test case has at least one FAIL result
3. If early failure detected, set flag and exit batch loop early
4. On next SPRT invocation, read previous state and prioritize failed tests

**Edge cases to handle**:
- First run with no sprt-state.json: Initialize failedTestIds as empty list, proceed normally
- Empty failedTestIds on re-run: Use original test order (no failures to prioritize)
- Failure after run 3: Do NOT add to failedTestIds, continue to SPRT boundaries as normal
- Multiple failures in one batch: Add all failed test IDs to the list

## Files to Modify

### Java Client Files

- `client/src/main/java/io/github/cowwoc/cat/client/InstructionTestRunner.java`
  - Add early failure detection after batch grading (check if run number ≤ 3 and any TC has failures)
  - Set early_stop flag when detected
  - Exit batch loop when flag is set
  - Sort test case list before batch execution: failed tests first

- `client/src/main/java/io/github/cowwoc/cat/client/SprtState.java`
  - Add `failedTestIds` field (List<String>)
  - Track test IDs that had any FAIL result in runs 1-3
  - Persist to/from JSON

### Plugin Documentation Files

- `plugin/skills/sprt-runner-agent/first-use.md`
  - Update "Early Abort on Failure" section to document automatic detection in first 3 runs
  - Add new section "Test Prioritization on Re-Run" explaining failed-first ordering
  - Update Procedure section to mention early detection check happens automatically

## Pre-conditions

- [ ] All dependent issues are closed (none)

## Jobs

### Job 1: Java Implementation

- Read current `client/src/main/java/io/github/cowwoc/cat/client/SprtState.java` to understand schema
- Add `failedTestIds` field (type: `List<String>`) to SprtState class
- Initialize `failedTestIds` as empty ArrayList in constructor
- Add getter `getFailedTestIds()` returning `List<String>`
- Add `@JsonProperty("failed_test_ids")` annotation to field for JSON serialization
- Read current `client/src/main/java/io/github/cowwoc/cat/client/InstructionTestRunner.java` 
- Locate the batch execution loop in `run-full-sprt` method (or equivalent orchestration method)
- Add local boolean variable `earlyStopped = false` before batch loop
- After each batch completes and SPRT state is updated:
  - Check if current run number ≤ 3
  - Locate the grading results variable in InstructionTestRunner (the data structure populated by the grader after batch execution - search for method calls like `grader.grade()` or similar, and identify the return value or output parameter)
  - The grading results structure maps test case IDs to verdicts - if it's a Map, use entrySet() to iterate; if it's a List/Collection of result objects, iterate and extract testId + verdict from each
  - For each test case in the results:
    - Extract the test case ID (String)
    - Extract the verdict (enum or String indicating PASS/FAIL/ERROR)
    - If verdict equals FAIL or ERROR (use .equals() for enum comparison or string comparison): call SprtState.getFailedTestIds().add(testId)
  - After iterating all test cases, check if SprtState.getFailedTestIds().isEmpty()
  - If NOT empty: set earlyStopped = true and break out of batch loop
- Before running each batch:
  - Read SprtState.failedTestIds (call getFailedTestIds())
  - If failedTestIds is empty (first run or no previous failures): use original test case list order
  - If failedTestIds is non-empty: create new sorted list using Comparator that returns -1 if test ID is in failedTestIds, 1 otherwise (failed tests sort first, maintains stable order within each group)
  - Pass sorted list to batch execution
- Add JavaDoc comment to failedTestIds field: "Test case IDs that failed in the first 3 runs. Used to prioritize failed tests on re-runs."
- Add JavaDoc comment to early detection logic: "Stops SPRT early if failures detected in first 3 runs to provide fast feedback."
- Run Java tests: `mvn -f client/pom.xml verify -e` (verify includes test and integration-test phases)
- Update `index.json` status to `closed`, resolution to `implemented`, progress to 100
- Commit changes: `feature: SPRT early failure detection and test prioritization` (includes index.json per CLAUDE.md)

### Job 2: Documentation Update

- Read current `plugin/skills/sprt-runner-agent/first-use.md`
- Locate "## Early Abort on Failure" section (search for exact heading)
- Replace the paragraph under that heading with: "SPRT testing aborts after current batch when any test case has failures in first 3 runs. This provides faster feedback than waiting for statistical REJECT."
- After the "## Early Abort on Failure" section (before the next ## heading), insert exactly:
  ```markdown
  
  ## Test Prioritization on Re-Run
  
  When SPRT is re-run after fixing failures, previously-failed tests execute first.
  
  **Why:** Fast feedback on whether fixes resolved the issues. No need to wait for all tests
  to complete before seeing if known failures are fixed.
  
  **Implementation:** The SPRT state file (`sprt-state.json`) tracks which test cases failed
  in the first 3 runs. On subsequent runs, these test IDs are sorted to the front of the
  execution queue.
  ```
- Locate "## Procedure" section
- Find Step 2 (Monitor step - search for "Monitor")
- After the Monitor step's existing content, add new paragraph: "The runner automatically checks for failures after each of the first 3 batches and will stop early if failures are detected."
- Commit changes: `docs: document SPRT early failure detection and test prioritization`

### Job 3: Verify Implementation

- Run full test suite: `mvn -f client/pom.xml verify -e`
- Verify exit code is 0 (all tests pass)
- Verify acceptance criteria are met (checklist in plan.md)

## Acceptance Criteria

- [ ] SprtState schema includes `failedTestIds` field that persists across runs
- [ ] If any test fails in first 3 runs, SPRT stops after current batch completes
- [ ] Failed test IDs are written to `sprt-state.json`
- [ ] On re-run, previously-failed tests execute before previously-passing tests
- [ ] All Java tests pass (`mvn -f client/pom.xml verify`)
- [ ] Documentation accurately describes new behavior
- [ ] Early detection does NOT trigger if failures only appear after run 3

## Post-conditions

- SPRT workflow stops early when failures are obvious within first 3 runs, saving resources
- Test re-runs give fast feedback by prioritizing known failures  
- Detection is automatic - no manual monitoring or kill required
- Behavior is backward compatible - runs without failures complete normally
