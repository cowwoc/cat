<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# TDD Implementation Workflow

> **See also:** [tdd.md](tdd.md) for TDD philosophy and when to use TDD vs standard implementation.

Use Test-Driven Development for features that benefit from upfront behavior specification.

**Invoke for:**
- Adding features with testable inputs/outputs
- Fixing bugs (test captures the bug first)
- Implementing validation, parsing, or transformation logic
- Building state machines or workflows

**Skip TDD for:**
- UI layout and styling
- Configuration changes
- Glue code with no logic
- Exploratory prototyping

---

## TDD State Machine

```
╭─ TDD STATE MACHINE ───────────────────────────────────────────────────────────────────────────────╮

     [START] ──► [RED] ──► [GREEN] ──► [REFACTOR] ──► [STEP 3.5] ──PASS──► [ITERATE OR VERIFY]
                  │  ▲       │           │                │                   │                │
                  ▼  │       ▼           ▼                ▼  FAIL             ▼                ▼
              Write  │   Write impl   Clean up       Adversarial  │       Check behaviors   VERIFY
              test   │   Run test     Run tests      red/blue     │       ───────────────   ORIGINAL
              MUST   │   MUST PASS    MUST PASS      loop         │          │        │     use-case!
              FAIL   │               (skip if        (skip if not │         YES       NO        │
                     │                not green)     green)       │          │        │          │
                     │                                            │          └────────┘          ▼
                     │                                            │          ▼                 WORKS!
                     │                   ┌────────────────────────┘        MORE?                │
                     │                   │  impl gap revealed               │                   │
                     │                   ▼   → re-enter STEP 1             NO                   │
                     │             MORE BEHAVIORS?                           ▼                   │
                     │                   │                       STILL FAILS?                    │
                     │                  YES                           │                          │
                     │                   │                            ▼                          │
                     └───────────────[LOOP to RED]                   NO                  [COMPLETE]
                      Test didn't capture the REAL bug                │
                                                               [COMPLETE]

╰───────────────────────────────────────────────────────────────────────────────────────────────────╯
```

**Release transitions are VERIFIED by actually running tests.**

---

## STEP 1: RED RELEASE - Write Failing Test

### Actions:
1. Create test file following project conventions
2. **If changing behavior**: Find and UPDATE existing test, don't create duplicate
3. Write test that defines expected behavior
4. Run the test - it **MUST fail**

### Run Test Command (adapt to your stack):
```bash
# Java/Maven (TestNG)
mvn -f client/pom.xml test -Dtest=TestClassName#testMethodName

# JavaScript/TypeScript
npm test -- --grep "your test name"

# Python
pytest tests/test_file.py::test_function -v

# Go
go test -run TestFunctionName ./...

# Rust
cargo test test_name
```

### Verify Test FAILS:
- Look for failure output confirming test ran and failed
- The test must **compile and run** but fail at an assertion — a compilation error or syntax error does NOT
  count as a valid RED failure (fix the error and re-run until you get a runtime assertion failure)
- If test **passes**: feature may already exist or test is wrong - investigate

### Commit:
```bash
# Stage only the specific test file(s) you created or modified (adapt paths to your project layout):
git add path/to/YourNewTest.java
git commit -m "test: add failing test for [feature]

- Describes expected behavior
- Will pass when feature is implemented"
```

---

## ⚠️ CHANGING EXISTING BEHAVIOR vs NEW FEATURES

**CRITICAL: Update existing tests when changing behavior (avoid duplicates).**

### Adding NEW Feature:
- Create a NEW test method
- Test should FAIL because feature doesn't exist
- Implement feature → test passes

### Changing EXISTING Behavior (Bug Fix / Behavior Change):
- **UPDATE** the existing test to reflect the NEW expected behavior, OR
- **DELETE** the old test and create a new one

**WRONG approach (creates conflicts):**
```
// Old test expects behavior A
testDoesA() { ... expects A ... }

// New test expects behavior B
testDoesB() { ... expects B ... }
// ❌ Now you have TWO conflicting tests!
```

**CORRECT approach:**
```
// Update the existing test to reflect new behavior
testBehavior() {
    ... expects B (the NEW correct behavior) ...
}
// ✓ One test, one source of truth
```

---

## STEP 2: GREEN RELEASE - Implement Code

### Actions:
1. NOW you can edit production code
2. Write **minimal** code to make test pass
3. No cleverness, no optimization - just make it work
4. Run the test - it **MUST pass**

### Verify Test PASSES:
- All tests pass including your new one
- No regressions in existing tests

### Commit:
```bash
# Stage only the specific source file(s) you created or modified (adapt paths to your project layout):
git add path/to/YourImplementation.java
git commit -m "feature: implement [feature]

- Makes failing test pass
- [Brief description of implementation approach]"
```

---

## STEP 3: REFACTOR RELEASE - Clean Up

### Actions:
1. Clean up implementation if obvious improvements exist
2. Remove any debug code
3. Run **full test suite** to check for regressions
4. Tests **MUST still pass**

### Only commit if changes made:
```bash
# Stage only the specific production file(s) you refactored (adapt paths to your project layout).
# Do NOT stage test files — refactoring must not change test behavior.
git add path/to/RefactoredFile.java
git commit -m "refactor: clean up [feature]

- [What was improved]
- No behavior changes"
```

---

## STEP 3.5: ADVERSARIAL TEST HARDENING

**Curiosity gate:** Read `curiosity` from the effective config (`get-config-output effective`). If `curiosity = low`, skip
STEP 3.5 entirely and proceed to STEP 4.

### Capture Baseline Test Results

Before evaluating the gate below, run the full test suite and record (or re-record, replacing any prior
value) the result as `BASELINE_TEST_RESULT` (pass or fail, with the list of any failing test names). This
baseline is used by the gate to distinguish pre-existing failures from failures introduced during the
current TDD cycle. You MUST capture a fresh baseline every time STEP 3.5 is reached — including on
re-entry from adversarial hardening revealing uncovered behavior. Without a current baseline, the gate
comparison is impossible.

### Gate: Only Run When All Tests Pass

If the test suite is not fully green at this point (any failing tests remain), evaluate whether the failures
are newly introduced by this TDD cycle before skipping. Only skip this step and proceed directly to STEP 4 if
the current cycle introduced new test failures (see criteria below).

**IMPORTANT: This gate applies ONLY to tests failing DUE TO THE CURRENT TDD CYCLE.** If your worktree has:
- Pre-existing test failures (from before you started this TDD cycle), OR
- Unrelated test failures (in modules unconnected to your feature),

Then those pre-existing failures do NOT justify skipping adversarial hardening. The gate only allows skipping
if tests that were passing BEFORE the RED step started are now failing BECAUSE of the current RED-GREEN-REFACTOR
cycle. Compare the current failures against `BASELINE_TEST_RESULT` (captured above) to objectively determine
which failures are pre-existing versus newly introduced. A failure is newly introduced ONLY when BOTH of the
following are true:
1. The failing test names do NOT appear in `BASELINE_TEST_RESULT` (i.e., they were passing before this cycle)
2. The failure is connected to the current cycle's changes. To verify without modifying git history, check
   whether the failing test exercises code paths touched by commits in STEP 2 or STEP 3 (run
   `git diff HEAD~N..HEAD --name-only` and compare against the test's imports, call targets, and
   transitive dependencies — i.e., files called by the files the test directly imports). A direct import
   match confirms the connection, but the absence of a direct match does NOT prove the failure is
   pre-existing (the test may reach changed code through indirect dependencies). When no direct reference
   is found, default to treating the failure as newly introduced (do not skip hardening) unless you can
   positively confirm the failure existed before the current cycle by checking test results from a commit
   prior to STEP 1.

If tests are failing for unrelated reasons, fix them separately BEFORE starting the TDD cycle, or document them
and proceed with hardening anyway.

### Collect Test File Content

Read the current content of all test files modified during this TDD cycle. Concatenate them as
`CURRENT_TEST_CODE`. Store the absolute path(s) to each test file as `TEST_FILE_PATH` (one path per file).
Only include test files modified in the CURRENT RED-GREEN-REFACTOR cycle (not files only modified in prior
behavior cycles that were already hardened).

Determine the worktree root by running `git rev-parse --show-toplevel` and store as `WORKTREE_ROOT`. Pass
`WORKTREE_ROOT` as a resolved literal string to all subagents — do NOT pass variable references.

### Adversarial Hardening Loop (Convergence-Based)

Initialize `ROUND=1`. Record (or re-record, replacing any prior value) the current commit hash as
`PRE_HARDENING_COMMIT` (used later for the hardening summary diff). You MUST capture a fresh
`PRE_HARDENING_COMMIT` every time STEP 3.5 is reached, including on re-entry. Use the unified adversarial protocol in
[plugin/concepts/adversarial-protocol.md](${CLAUDE_PLUGIN_ROOT}/concepts/adversarial-protocol.md) as the
authoritative source for all round logic, including:

- **Spawn/resume agents:** Use the prompts in § "Full Protocol Flow"
- **Termination:** Loop stops when red-team returns `has_critical_high: false` (§ "Convergence Criterion").
- **Dispute mechanism:** Blue-team verifies premise and moves false-premise findings to `disputed` array with
  evidence (§ "Step 2: Blue-Team Patching with Dispute Mechanism")
- **Arbitration:** If blue-team disputed any findings, spawn fresh arbitration agent to verify disputes
  (§ "Step 3: Arbitration Phase")
- **Diff validation:** Ensure all non-disputed CRITICAL/HIGH findings have patch hunks (§ "Step 4: Diff Validation")
- **Round advancement:** Update `CURRENT_TEST_CODE`, increment round, check termination (§ "Step 5: Round Advancement")

**TDD-specific adaptations:**

1. **Target type:** Set `target_type: test_code` in all red-team, blue-team, and diff-validation prompts (not
   `instructions` or `source_code`).

2. **Pre-round snapshot:** Follow the shared protocol's `PRE_ROUND_COMMIT` procedure (capture `git rev-parse HEAD`
   before spawning blue-team). In TDD, this also serves as the rollback target for test validation failures (see
   item 3 below).

3. **Test file validation:** After blue-team completes patches from a round, run the full test suite. If any tests
   fail (blue-team introduced broken assertions):
   - Revert ALL blue-team commits from this round by running `git revert --no-edit {BLUE_TEAM_COMMITS}`
     in reverse chronological order (newest first). When there are multiple commits, pass them all to a
     single `git revert` invocation in reverse order. Note: this creates one revert commit per original
     commit, not a single atomic revert. If any revert in the sequence conflicts, abort the revert with
     `git revert --abort`, then create a backup ref (`git branch backup-round-{ROUND} HEAD`), and fall
     back to `git reset --hard {PRE_ROUND_COMMIT}` to restore the pre-round state.
   - Log: "Adversarial hardening exposed test errors in round {ROUND}. Reverted blue-team commits. Aborting loop."
   - Abort the loop immediately — do NOT run diff validation.

4. **Multiple test files:** If there are multiple `TEST_FILE_PATHS`, spawn and resume blue-team agents sequentially
   (one per file per round), accumulating a list of `BLUE_TEAM_COMMITS`. After each blue-team agent completes,
   extract all commit hashes it created by running `git log --format=%H {PRE_ROUND_COMMIT}..HEAD` (or
   `{PREVIOUS_BLUE_TEAM_COMMIT}..HEAD` for subsequent files) and append them to `BLUE_TEAM_COMMITS`. Use the
   LAST blue-team commit hash for all subsequent steps (arbitration, diff validation, etc.).

5. **Prior round context:** For round 2+, set `PRIOR_ROUND_DIFF` by running
   `git diff {PREVIOUS_ROUND_RED_TEAM_COMMIT}..{LAST_BLUE_TEAM_COMMIT} -- {TEST_FILE_PATHS}` and pass it to
   red-team in the "What Changed Since Last Round" field (see protocol § "Step 1: Red-Team Analysis").

### Re-Run Tests Against Hardened Suite

After the loop exits (whether by convergence, early termination, loop abort, or validation failure), run
the full test suite against the hardened test code:

```bash
# Run the same test command used in STEP 2
```

**If all tests pass:** Proceed to STEP 4 normally.

**If any tests fail:** The hardened tests have exposed a gap in the implementation. Log:

```
Adversarial hardening revealed uncovered behavior. Returning to RED.
```

Re-enter **STEP 1** with the failing test(s) as the starting point. Do not suppress or skip the failing
tests — treat them as new RED assertions that must be satisfied before proceeding.

### Display Hardening Summary

After the loop exits (even if tests then re-enter RED), print a summary line:

```
Adversarial test hardening: {N} rounds, {M} assertions added, {K} edge cases covered
```

Where:
- `{N}` = number of rounds completed
- `{M}` = total new assertions added across all blue-team patches (count added lines containing assertion
  **call invocations** such as `assertEquals(`, `assertThrows(`, `assertTrue(`, `assertFalse(`,
  `assertNotNull(`, `expect(`, `verify(`, etc.) — count only lines where an assertion method is called with
  an opening parenthesis, not import statements, variable declarations, comment lines, or method definitions
  that merely contain "assert" as a substring
- `{K}` = number of new test methods or test cases added across all blue-team patches

Derive `{M}` and `{K}` from the actual `git diff` between `PRE_HARDENING_COMMIT` and the current HEAD
(not `LAST_BLUE_TEAM_COMMIT`, which may point to a pre-revert state if blue-team commits were reverted).
Run `git diff {PRE_HARDENING_COMMIT}..HEAD -- {TEST_FILE_PATHS}` and count added lines matching assertion
keywords and new test method signatures. If all blue-team commits were reverted, `{M}` and `{K}` will
correctly be 0.

---

## STEP 4: ITERATE OR VERIFY - Check Behaviors and Decide Next Action

### Actions:
1. **Review your feature plan** - Check off each behavior you've implemented
2. **Identify remaining behaviors** - Are there more behaviors or edge cases to implement?
3. **Make the decision**:
   - **If MORE behaviors remain**: Commit current work, loop back to STEP 1 (RED)
   - **If ALL behaviors complete**: Proceed to STEP 5 (VERIFY)

### Why Iterate:
- TDD discovers behaviors incrementally - one test at a time
- Each RED-GREEN-REFACTOR cycle implements ONE behavior
- Build features incrementally: small test → minimal code → clean up → repeat
- Commit patterns stay atomic and focused per behavior

### Before Looping Back to RED:
```bash
# Your current work should be clean and committed
git status --porcelain  # Should show nothing or only untracked files
```

### Commit Message When Looping:
When you're ready to tackle the next behavior, no new commit is needed - STEP 1 will create a new test
commit. Just ensure previous cycle is complete.

---

## STEP 5: VERIFY AGAINST ORIGINAL USE-CASE

### For Bug Fixes:

**⚠️ CRITICAL: Test passing ≠ bug is fixed. Verify your RED test captured the actual bug.**

#### Verification Steps:
1. **Return to the original scenario** - The exact inputs/conditions that exposed the bug
2. **Run the original use-case** - Not your new test, but the ORIGINAL failing scenario
3. **Confirm it now works** - The specific behavior that was broken is now correct
4. **Check for side effects** - The fix didn't break related functionality

#### Why This Matters:
- Your RED test is a *hypothesis* about what the bug is
- The test might pass while the original bug remains (tested wrong thing)
- Simplified test cases may miss edge cases in the real scenario
- Only the original use-case proves the bug is truly fixed

#### If Original Use-Case Still Fails:

**Your RED test wasn't capturing the ACTUAL bug. Return to RED release:**

1. **Analyze why the fix didn't work**:
   - The test passed but the bug persists → test was testing the wrong thing
   - The test was an approximation, not a faithful reproduction
   - You fixed a symptom, not the root cause

2. **Write a NEW test** that:
   - Fails for the SAME reason as the original use-case
   - Uses the exact inputs/conditions that exposed the bug
   - Is a faithful reproduction, not an approximation

3. **Repeat the full cycle**: RED → GREEN → REFACTOR → ITERATE OR VERIFY

**This loop continues until the ORIGINAL use-case works. Safety limit: if the loop has not converged
after 3 RED-GREEN-REFACTOR-VERIFY iterations, stop and escalate to the user with a summary of what
was attempted and why the original use-case still fails.**

### For New Features:

**⚠️ CRITICAL: Features must be verified against the original requirements, not just unit tests.**

#### Verification Steps:
1. **Identify the original feature requirement** - What problem does this feature solve?
2. **Run the feature end-to-end** - Use the feature as the original requirement describes
3. **Confirm it solves the original problem** - Does the feature deliver on its stated goal?
4. **Check integration** - Does the feature work correctly with the rest of the system?

#### Why This Matters:
- Unit tests verify individual behaviors, not complete feature integration
- A feature may pass all tests but fail in real-world usage scenarios
- Integration with other system components may reveal missing behaviors
- Only end-to-end verification in the original use-case context proves feature completeness

#### If End-to-End Verification Fails:

**Your tests weren't capturing the original requirement. Return to RED release:**

1. **Identify which requirement was not met** - Which part of the original goal is missing?
2. **Write a NEW test** that:
   - Captures the missing behavior from the end-to-end scenario
   - Includes the surrounding context that the unit test missed
   - Is a faithful integration test, not an isolated unit test

3. **Repeat the full cycle**: RED → GREEN → REFACTOR → ITERATE OR VERIFY

**This loop continues until the original requirement is fully satisfied. Safety limit: if the loop has
not converged after 3 RED-GREEN-REFACTOR-VERIFY iterations, stop and escalate to the user with a summary
of what was attempted and which requirements remain unmet.**

---

## TDD Commit Pattern

TDD produces multiple commit cycles, each 2-3 commits per behavior, then squash before review.

### Development Phase: Per-Behavior Commits

Each behavior gets its own RED-GREEN-REFACTOR cycle with atomic commits:

```
Behavior 1: Valid email detection
├─ test: add failing test for valid email formats
│  - Tests RFC 5322 compliant emails accepted
│  - Tests simple and complex formats
│
├─ feature: implement basic email validation
│  - Regex pattern validates common formats
│  - Returns boolean for validity
│
└─ (optional) refactor: extract regex pattern
   - Moved to EMAIL_REGEX constant
   - No behavior changes

Behavior 2: Invalid email detection
├─ test: add failing test for invalid email formats
│  - Tests malformed emails rejected
│  - Tests empty input handling
│
└─ feature: add invalid format handling
   - Validates format with improved regex
   - Rejects empty and null inputs
```

### Pre-Review Phase: Squash by Topic

Before requesting review, use `cat:git-squash` to consolidate behavior cycles by topic:

```bash
# Squash all email validation commits into logical groups:
cat:git-squash --topic "email-validation"

# Result: single focused commit per topic
# Example output:
# feature: implement email validation
#
# - Valid email format detection (RFC 5322 compliant)
# - Invalid format rejection
# - Edge case handling (empty, null)
```

**Why squash before review:**
- Granular commits during development (easier debugging, easier to revert individual tests)
- Focused commits for review (cleaner history, tells the story of "what changed and why")
- Supports both development and release workflows

---

## Quick Reference: Decision Heuristic

**Can you write `expect(fn(input)).toBe(output)` before writing `fn`?**

→ **Yes**: Use this TDD workflow
→ **No**: Standard implementation, add tests after if needed

---

## Good Tests vs Bad Tests

### Test behavior, not implementation:
- ✅ Good: "returns formatted date string"
- ❌ Bad: "calls formatDate helper with correct params"

### One concept per test:
- ✅ Good: Separate tests for valid input, empty input, malformed input
- ❌ Bad: Single test checking all edge cases

### Descriptive names:
- ✅ Good: "should reject empty email", "returns null for invalid ID"
- ❌ Bad: "test1", "handles error", "works correctly"

### No implementation details:
- ✅ Good: Test public API, observable behavior
- ❌ Bad: Mock internals, test private methods

---

## Related Skills

- `build-test-report` - Run tests and report results
- `git-commit` - Commit conventions
