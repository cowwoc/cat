---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

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
╭─ TDD STATE MACHINE ───────────────────────────────────────────────────────────────╮

     [START] ──► [RED] ──► [GREEN] ──► [REFACTOR] ──► [ITERATE OR VERIFY]
                  │  ▲       │           │              │                │
                  ▼  │       ▼           ▼              ▼                ▼
              Write  │   Write impl   Clean up     Check behaviors   VERIFY
              test   │   Run test     Run tests    ───────────────   ORIGINAL
              MUST   │   MUST PASS    MUST PASS      │        │      use-case!
              FAIL   │                              YES       NO        │
                     │                    ┌──────────┘          │       │
                     │                    ▼                     │       ▼
                     │              MORE BEHAVIORS?             │      WORKS!
                     │                    │                     │       │
                     │                   YES                    ▼       │
                     │                    │              STILL FAILS?  │
                     │                    ▼                    │        │
                     └────────────────[LOOP to RED]           │        ▼
                      Test didn't capture the REAL bug       NO      [COMPLETE]
                                                              │
                                                        [COMPLETE]

╰───────────────────────────────────────────────────────────────────────────────────╯
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
- If test **passes**: feature may already exist or test is wrong - investigate

### Commit:
```bash
git add tests/
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
git add src/
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
git add .
git commit -m "refactor: clean up [feature]

- [What was improved]
- No behavior changes"
```

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

**⚠️ CRITICAL: Test passing ≠ bug is fixed. Verify your RED test captured the actual bug.**

### Verification Steps:
1. **Return to the original scenario** - The exact inputs/conditions that exposed the bug
2. **Run the original use-case** - Not your new test, but the ORIGINAL failing scenario
3. **Confirm it now works** - The specific behavior that was broken is now correct
4. **Check for side effects** - The fix didn't break related functionality

### Why This Matters:
- Your RED test is a *hypothesis* about what the bug is
- The test might pass while the original bug remains (tested wrong thing)
- Simplified test cases may miss edge cases in the real scenario
- Only the original use-case proves the bug is truly fixed

### If Original Use-Case Still Fails:

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

**This loop continues until the ORIGINAL use-case works.**

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
