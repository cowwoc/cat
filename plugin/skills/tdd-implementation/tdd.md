<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
<overview>
TDD is about design quality, not coverage metrics. The red-green-refactor cycle forces you to think about behavior
before implementation, producing cleaner interfaces and more testable code.

**Principle:** If you can describe the behavior as `expect(fn(input)).toBe(output)` before writing `fn`, TDD improves
the result.

**Key insight:** TDD work is fundamentally heavier than standard tasks. It requires multiple execution cycles
(RED → GREEN → REFACTOR), each with file reads, test runs, and potential debugging. TDD features get dedicated changes
to ensure full context is available throughout the cycle.
</overview>

<when_to_use_tdd>
## When TDD Improves Quality

**TDD candidates (create a TDD change):**
- Business logic with defined inputs/outputs
- API endpoints with request/response contracts
- Data transformations, parsing, formatting
- Validation rules and constraints
- Algorithms with testable behavior
- State machines and workflows
- Utility functions with clear specifications

**Skip TDD (use standard change with `type="auto"` tasks):**
- UI layout, styling, visual components
- Configuration changes
- Glue code connecting existing components
- One-off scripts and migrations
- Simple CRUD with no business logic
- Exploratory prototyping

**Heuristic:** Can you write `expect(fn(input)).toBe(output)` before writing `fn`?
→ Yes: Create a TDD change
→ No: Use standard change, add tests after if needed
</when_to_use_tdd>

<tdd_plan_structure>
## TDD Change Structure

Each TDD change implements **one feature** through the full RED-GREEN-REFACTOR cycle.

```markdown
---
release: XX-name
change: NN
type: tdd
---

<objective>
[What feature and why]
Purpose: [Design benefit of TDD for this feature]
Output: [Working, tested feature]
</objective>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@relevant/source/files.ts
</context>

<feature>
  <name>[Feature name]</name>
  <files>[source file, test file]</files>
  <behavior>
    [Expected behavior in testable terms]
    Cases: input → expected output
  </behavior>
  <implementation>[How to implement once tests pass]</implementation>
</feature>

<verification>
[Test command that proves feature works]
</verification>

<success_criteria>
- Failing test written and committed
- Implementation passes test
- Refactor complete (if needed)
- All 2-3 commits present
</success_criteria>

<output>
After completion, create SUMMARY.md with:
- RED: What test was written, why it failed
- GREEN: What implementation made it pass
- REFACTOR: What cleanup was done (if any)
- Commits: List of commits produced
</output>
```

**One feature per TDD change.** If features are trivial enough to batch, they're trivial enough to skip TDD—use a
standard change and add tests after.
</tdd_plan_structure>

<execution_flow>
## Red-Green-Refactor Cycles

TDD features are built iteratively, one behavior at a time. Each behavior completes a full RED-GREEN-REFACTOR cycle,
then the ITERATE OR VERIFY step decides whether to loop back for more behaviors or proceed to final verification.

### Single Cycle (One Behavior)

**RED - Write failing test:**
1. Create test file following project conventions
2. Write test describing ONE behavior (from `<behavior>` element)
3. Run test - it MUST fail
4. If test passes: feature exists or test is wrong. Investigate.
5. Commit: `test: add failing test for [behavior]`

**GREEN - Implement to pass:**
1. Write minimal code to make test pass
2. No cleverness, no optimization - just make it work
3. Run test - it MUST pass
4. Commit: `feature: implement [behavior]`

**REFACTOR (if needed):**
1. Clean up implementation if obvious improvements exist
2. Run tests - MUST still pass
3. Only commit if changes made: `refactor: clean up [behavior]`

### Multiple Cycles (Multiple Behaviors)

Features often need multiple behaviors implemented incrementally:

```
Behavior 1: Basic case
├─ RED: test basic behavior
├─ GREEN: implement basic behavior
└─ REFACTOR: clean up if needed
  └─ Commit count: 2-3 commits for Behavior 1

Behavior 2: Edge case
├─ RED: test edge case
├─ GREEN: implement edge case handling
└─ REFACTOR: clean up if needed
  └─ Commit count: 2-3 commits for Behavior 2

Behavior N: Additional case
├─ RED: test additional behavior
├─ GREEN: implement additional case
└─ REFACTOR: clean up if needed
  └─ Commit count: 2-3 commits for Behavior N
```

At each ITERATE OR VERIFY step, decide:
- **More behaviors?** Loop back to RED
- **All behaviors done?** Proceed to VERIFY

**Result:** Each feature produces multiple cycles (e.g., 6-9 commits if 3 behaviors), then squash before review.
</execution_flow>

<test_quality>
## Good Tests vs Bad Tests

**Test behavior, not implementation:**
- Good: "returns formatted date string"
- Bad: "calls formatDate helper with correct params"
- Tests should survive refactors

**One concept per test:**
- Good: Separate tests for valid input, empty input, malformed input
- Bad: Single test checking all edge cases with multiple assertions

**Descriptive names:**
- Good: "should reject empty email", "returns null for invalid ID"
- Bad: "test1", "handles error", "works correctly"

**No implementation details:**
- Good: Test public API, observable behavior
- Bad: Mock internals, test private methods, assert on internal state
</test_quality>

<framework_setup>
## Test Framework Setup (If None Exists)

When executing a TDD change but no test framework is configured, set it up as part of the RED release:

**1. Detect project type:**
```bash
# JavaScript/TypeScript
if [ -f package.json ]; then echo "node"; fi

# Python
if [ -f requirements.txt ] || [ -f pyproject.toml ]; then echo "python"; fi

# Go
if [ -f go.mod ]; then echo "go"; fi

# Rust
if [ -f Cargo.toml ]; then echo "rust"; fi
```

**2. Install minimal framework:**
| Project | Framework | Install |
|---------|-----------|---------|
| Node.js | Jest | `npm install -D jest @types/jest ts-jest` |
| Node.js (Vite) | Vitest | `npm install -D vitest` |
| Python | pytest | `pip install pytest` |
| Go | testing | Built-in |
| Rust | cargo test | Built-in |

**3. Create config if needed:**
- Jest: `jest.config.js` with ts-jest preset
- Vitest: `vitest.config.ts` with test globals
- pytest: `pytest.ini` or `pyproject.toml` section

**4. Verify setup:**
```bash
# Run empty test suite - should pass with 0 tests
npm test  # Node
pytest    # Python
go test ./...  # Go
cargo test    # Rust
```

**5. Create first test file:**
Follow project conventions for test location:
- `*.test.ts` / `*.spec.ts` next to source
- `__tests__/` directory
- `tests/` directory at root

Framework setup is a one-time cost included in the first TDD change's RED release.
</framework_setup>

<error_handling>
## Error Handling

**Test doesn't fail in RED release:**
- Feature may already exist - investigate
- Test may be wrong (not testing what you think)
- Fix before proceeding

**Test doesn't pass in GREEN release:**
- Debug implementation
- Don't skip to refactor
- Keep iterating until green

**Tests fail in REFACTOR release:**
- Undo refactor
- Commit was premature
- Refactor in smaller steps

**Unrelated tests break:**
- Stop and investigate
- May indicate coupling issue
- Fix before proceeding
</error_handling>

<commit_pattern>
## Commit Pattern for TDD Changes

TDD changes produce multiple cycles with granular commits, then squash before review.

### Development Phase: Granular Per-Cycle Commits

Each cycle produces 2-3 atomic commits, one per release:

```
Cycle 1 - Valid email detection:
test: add failing test for valid email formats
- Tests RFC 5322 compliant emails accepted
- Tests simple and complex formats

feature: implement basic email validation
- Regex pattern validates format
- Returns boolean for validity

refactor: extract regex pattern (optional)
- Moved to EMAIL_REGEX constant
- No behavior changes

Cycle 2 - Invalid format rejection:
test: add failing test for invalid email formats
- Tests malformed emails rejected
- Tests empty input handling

feature: add invalid format handling
- Rejects emails not matching pattern
- Handles null and empty string cases

Cycle 3 - Edge cases (if needed):
...
```

### Pre-Review Phase: Squash for Release

Before creating a pull request, use `cat:git-squash` to combine cycles by topic:

```bash
# During development: granular commits (6-9+ commits for 3 behaviors)
git log --oneline
# a3f5x2z refactor: extract regex pattern
# b2e4c1y feature: add invalid format handling
# c1d3b0w test: add failing test for invalid email formats
# d0c2a9v feature: implement basic email validation
# e9b1a8u refactor: extract validation helper
# f8a0a7t test: add failing test for valid email formats

# After squash: focused commits per topic
cat:git-squash --topic "email-validation"

git log --oneline
# x1z3c5a feature: implement email validation
# (contains all 6 commits squashed with combined message)
```

**Why granular → squash workflow:**
- **Granular during development:** Each test and implementation is independently revertable; easier to debug
- **Squashed for review:** Clean history tells the story of what changed and why
- **Flexibility:** Supports both debug workflows (need revert) and release workflows (need clean history)

**Comparison with standard changes:**
- Standard changes: 1 commit per task, 2-4 commits per change
- TDD changes (granular): 2-3 commits per behavior cycle
- TDD changes (final): 1 focused commit per feature topic

**Benefits:**
- During development: Atomic reverts, clear git bisect points
- For review: Clean narrative of implementation decisions
- Flexible workflow: Choose granularity based on need
- Consistent with overall commit strategy
</commit_pattern>

<context_budget>
## Context Budget

TDD changes target **~40% context usage per cycle** (lower than standard changes' ~50% per task).

### Why Lower Per Cycle

Each RED-GREEN-REFACTOR cycle is inherently heavier than linear task execution:
- RED release: write test, run test, potentially debug why it didn't fail
- GREEN release: implement, run test, potentially iterate on failures
- REFACTOR release: modify code, run tests, verify no regressions

Each release involves reading files, running commands, analyzing output with potential debugging loops.

### Iterative Impact on Budget

Features with multiple behaviors will produce multiple cycles:

- **Single behavior:** 1 cycle = ~40% context (2-3 commits)
- **Three behaviors:** 3 cycles = ~120% context across cycles (6-9 commits), then squash before review
- **Within-session total:** Multiple cycles can fit in a larger session, or continue across sessions

If the feature requires more cycles than fit in remaining context, use ITERATE OR VERIFY step to pause, commit the
current cycles, and continue in the next session. The squash-before-review pattern ensures commits remain atomic and
revertable during development, supporting both single-session and multi-session workflows.

### Multi-Session Workflow

When pausing mid-feature:
1. Complete the current RED-GREEN-REFACTOR cycle (commit the last behavior)
2. Document remaining behaviors in issue comments or STATE.md
3. Next session picks up at STEP 1 (RED) for the next behavior
4. Final session uses `cat:git-squash` to consolidate all cycles before review

Single feature focus within each cycle ensures full quality throughout the development process.
</context_budget>
