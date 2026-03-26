# Plan

## Goal

Implement the caution level test execution tiers so each level determines how much verification runs
before the approval gate. Currently caution maps to the old `verify` level (NONE/CHANGED/ALL) which
controlled file scope, not test depth. This issue redefines the behavior in terms of test depth:

## Type

feature

## Caution Level Definitions

### low — compile only (fastest feedback)

Verification runs:
1. Pre/post condition checks (plan.md post-conditions evaluated against implementation)
2. Compilation: `mvn -f client/pom.xml compile` — confirms the code builds without errors

Unit tests and E2E tests are skipped. Suitable for simple refactors and documentation changes where
the user prioritizes speed over coverage confidence.

### medium — compile + unit tests (current default behavior)

Verification runs:
1. Pre/post condition checks
2. Compilation: `mvn -f client/pom.xml compile`
3. Unit tests: `mvn -f client/pom.xml test` — runs the full unit test suite

E2E tests are skipped. This is the current behavior and matches what most issues need.

### high — compile + unit tests + E2E tests (maximum confidence)

Verification runs:
1. Pre/post condition checks
2. Compilation: `mvn -f client/pom.xml compile`
3. Unit tests: `mvn -f client/pom.xml test`
4. E2E tests: invoke the built jlink artifacts against a real worktree scenario to confirm the change
   works end-to-end in its actual runtime context (not just unit-level)

The E2E invocation runs the same verification described in each issue's E2E post-condition. The verify
subagent is responsible for determining the appropriate E2E command from the issue's plan.md.

## Pre-conditions

(none)

## Post-conditions

- [ ] caution=low: verify phase runs pre/post condition checks and `mvn compile`; `mvn test` skipped
- [ ] caution=medium: verify phase runs pre/post condition checks, `mvn compile`, and `mvn test` (current behavior)
- [ ] caution=high: verify phase runs pre/post condition checks, `mvn compile`, `mvn test`, and E2E tests
- [ ] Compilation step (`mvn compile`) added as an explicit verify step (currently absent)
- [ ] E2E test execution is gated on caution=high
- [ ] Verify subagent reads caution level from effective config before deciding which steps to run
- [ ] Unit tests for caution level routing logic in the verify subagent/handler
- [ ] No regressions in existing caution=medium workflows
- [ ] E2E: run /cat:work at caution=low and confirm only compile runs; caution=medium confirm unit tests run; caution=high confirm E2E step executes

## Sub-Agent Waves

### Wave 1

- Update `plugin/agents/work-verify.md` to read caution from effective config and add caution-gated
  compile, unit-test, and E2E steps (see execution details below)
- Update `plugin/skills/work-confirm-agent/first-use.md` to remove the skip-all-verification-for-low
  behavior and update the verify subagent prompt to pass CAUTION (see execution details below)
- Update `plugin/skills/config/first-use.md` to fix caution option descriptions and reference values
  to match the new definitions (see execution details below)
- Update `client/src/main/java/io/github/cowwoc/cat/hooks/util/CautionLevel.java` Javadoc for LOW,
  MEDIUM, HIGH enum values to match new definitions
- Create `client/src/test/java/io/github/cowwoc/cat/hooks/test/CautionLevelTest.java` with unit tests
  for `CautionLevel.fromString()`, `toString()`, and routing predicate helpers (see execution details
  below)

### Wave 2

- Run `mvn -f client/pom.xml verify -e` to confirm all tests pass
- Update `index.json` in the issue directory: set `status: closed`, `progress: 100%`
- Commit all changes

### Wave 3

- Add unit tests to `CautionLevelTest.java` that verify the string constants "low", "medium", "high"
  correspond to the expected `CautionLevel` enum values and verify the routing conditions:
  - `caution=low` → only compile runs (unit tests and E2E skipped): assert that `CAUTION != "low"` is
    `false` for `LOW` (i.e., the unit-test branch is skipped) and the E2E condition is also false
  - `caution=medium` → compile + unit tests run, E2E skipped: assert `CAUTION != "low"` is `true` for
    `MEDIUM` and the E2E condition (`CAUTION == "high"`) is `false`
  - `caution=high` → compile + unit tests + E2E run: assert `CAUTION != "low"` is `true` for `HIGH` and
    the E2E condition (`CAUTION == "high"`) is `true`
  - Implement as `CautionLevel` predicate methods `isUnitTestEnabled()` (returns `this != LOW`) and
    `isE2eEnabled()` (returns `this == HIGH`), then test each method for all three enum values
- In `CautionLevelTest.java`, update `fromString_caseInsensitive()` to assert all three values:
  `fromString("LOW")` returns `LOW`, `fromString("MEDIUM")` returns `MEDIUM`, and `fromString("HIGH")`
  returns `HIGH` (currently the test only asserts `fromString("HIGH")`)

## Execution Details

### plugin/agents/work-verify.md changes

After the existing "Violation Scanning" section (which reads CURIOSITY), add a new section reading
CAUTION and inserting compile, test, and E2E gating.

**Add CAUTION reading** (immediately before the compile step, after violation scanning):

```bash
CLIENT_BIN="${WORKTREE_PATH}/client/target/jlink/bin"
if [ ! -x "$CLIENT_BIN/get-config-output" ]; then
  CLIENT_BIN="/home/node/.config/claude/plugins/cache/cat/cat/2.1/client/bin"
fi
CONFIG=$("${CLIENT_BIN}/get-config-output" effective 2>/dev/null || echo '{"caution":"medium"}')
CAUTION=$(echo "$CONFIG" | grep -o '"caution"[[:space:]]*:[[:space:]]*"[^"]*"' \
  | sed 's/.*"caution"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/' | tr '[:upper:]' '[:lower:]')
CAUTION="${CAUTION:-medium}"
```

**Add compile step** (always runs, all caution levels):

```bash
echo "◆ Running compilation (caution: ${CAUTION})"
cd "${WORKTREE_PATH}" && mvn -f client/pom.xml compile -q 2>&1
if [ $? -ne 0 ]; then
  echo "FAIL: Compilation failed"
  # Add compile failure as a Missing criterion
fi
```

**Add unit test step** (conditional on CAUTION != "low"):

```bash
if [ "${CAUTION}" != "low" ]; then
  echo "◆ Running unit tests (caution: ${CAUTION})"
  cd "${WORKTREE_PATH}" && mvn -f client/pom.xml test -q 2>&1
  if [ $? -ne 0 ]; then
    echo "FAIL: Unit tests failed"
    # Add unit test failure as a Missing criterion
  fi
else
  echo "Unit tests skipped (caution: low)"
fi
```

**E2E gating**: Change the existing E2E condition from "run for feature/bugfix/refactor/performance"
to "run for feature/bugfix/refactor/performance AND CAUTION == 'high'". For other caution levels,
set e2e status to SKIPPED.

The E2E section in work-verify.md currently reads the issue type and decides to run or skip based on
that alone. Update it to also check CAUTION:

```
- For `docs` and `config` issue types only: set e2e status to SKIPPED (existing behavior, unchanged)
- For all other issue types: run E2E only if CAUTION == "high"; otherwise set e2e status to SKIPPED
  with explanation "E2E skipped (caution: ${CAUTION})"
```

The compile and unit test results must be recorded in the `criteria` array in the output JSON:
- If compile fails: add criterion `{"name": "Compilation passes", "status": "Missing", "explanation": "mvn compile failed"}`
- If unit tests fail: add criterion `{"name": "Unit tests pass", "status": "Missing", "explanation": "mvn test failed"}`
- If compile passes: add criterion `{"name": "Compilation passes", "status": "Done", "explanation": "mvn compile succeeded"}`
- If unit tests pass: add criterion `{"name": "Unit tests pass", "status": "Done", "explanation": "mvn test succeeded"}`
- If unit tests skipped (caution=low): do NOT add a unit test criterion (skipped means not evaluated)

These build criteria contribute to the overall assessment (COMPLETE/PARTIAL/INCOMPLETE). Any Missing
criterion triggers INCOMPLETE.

### plugin/skills/work-confirm-agent/first-use.md changes

**Remove** the skip-all behavior for caution=low. The current text says:
```
### Skip Verification if Configured
Skip **only the verification steps below** if: `CAUTION == "low"`
If skipping, output: "Verification skipped (caution: ${CAUTION})"
```

Remove this section entirely. Verification now always runs; the caution level controls which steps
run (compile only vs. compile+test vs. compile+test+E2E). The work-verify subagent reads the caution
level from effective config itself.

**Also update** the verify subagent prompt to include CAUTION in the Issue Configuration section:
```
CAUTION: ${CAUTION}
```

This is informational — the verify subagent reads the caution value from effective config directly,
but including it in the prompt improves traceability.

### plugin/skills/config/first-use.md changes

Update the caution wizard option descriptions (in the `<step name="caution">` section) to:
- Low: "Compile only (fastest feedback)"
- Medium (Default): "Compile and unit tests"
- High: "Compile, unit tests, and E2E tests (maximum confidence)"

Update the `### Caution Values` reference section to:
- `low` — Compile only (fastest feedback).
- `medium` — Compile and unit tests (default).
- `high` — Compile, unit tests, and E2E tests (maximum confidence).

### client/src/main/java/io/github/cowwoc/cat/hooks/util/CautionLevel.java changes

Update Javadoc for each enum constant to match terminology convention (Javadoc must match wizard
description word-for-word):

```java
/**
 * Compile only (fastest feedback).
 */
LOW,
/**
 * Compile and unit tests (default).
 */
MEDIUM,
/**
 * Compile, unit tests, and E2E tests (maximum confidence).
 */
HIGH;
```

Also update the class-level Javadoc to reflect the new meaning:
```java
/**
 * How cautiously the agent validates changes before the approval gate.
 */
```

### CautionLevelTest.java

Create file at:
`client/src/test/java/io/github/cowwoc/cat/hooks/test/CautionLevelTest.java`

Include the license header (Java format). Test class:
- `fromString_low_returnsLow()` — `fromString("low")` returns `LOW`
- `fromString_medium_returnsMedium()` — `fromString("medium")` returns `MEDIUM`
- `fromString_high_returnsHigh()` — `fromString("high")` returns `HIGH`
- `fromString_caseInsensitive()` — `fromString("LOW")` returns `LOW`, `fromString("MEDIUM")` returns
  `MEDIUM`, `fromString("HIGH")` returns `HIGH`
- `fromString_invalid_throwsException()` — `fromString("invalid")` throws `IllegalArgumentException`
- `fromString_blank_throwsException()` — `fromString("")` throws `IllegalArgumentException`
- `fromString_null_throwsException()` — `fromString(null)` throws `NullPointerException`
- `toString_returnsLowercase()` — `LOW.toString()` is "low", `MEDIUM.toString()` is "medium",
  `HIGH.toString()` is "high"

Use TestNG `@Test` annotations. All test methods must have Javadoc.
Use `requireThat` assertions from requirements.java.
The class has no fields (test isolation requirement).
Use `@Test(expectedExceptions = ..., expectedExceptionsMessageRegExp = ...)` for exception tests.
