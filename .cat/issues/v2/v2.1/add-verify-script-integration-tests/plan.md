## Type

feature

## Goal

Add integration tests for `VerifyDeferPlanGeneration` Java class (`client/src/main/java/io/github/cowwoc/cat/hooks/util/VerifyDeferPlanGeneration.java`). The verify tool currently has only static checks and no tests, so regressions in the verification logic go undetected.

## Pre-conditions

- `VerifyDeferPlanGeneration.java` exists with 4 verification checks
- TestNG test infrastructure is available in the project
- `TestClaudeTool` is available for test scope injection

## Research Findings

The `VerifyDeferPlanGeneration` class performs 4 checks against skill files:
1. `plugin/skills/add-agent/first-use.md` must NOT contain `cat:plan-builder-agent`
2. `plugin/skills/add-agent/first-use.md` must contain `planTempFile=$(mktemp`
3. `plugin/skills/work-implement-agent/first-use.md` must contain `hasSteps`
4. `plugin/skills/work-implement-agent/first-use.md` must invoke `cat:plan-builder-agent`

The `run()` method accepts `String[] args, JvmScope scope, PrintStream out` — it is already testable by passing a temp directory as `args[0]` and capturing output via a `ByteArrayOutputStream`/`PrintStream`.

Existing test patterns (e.g., `StateSchemaValidatorTest`) use:
- `TestClaudeTool` for scope injection
- `Files.createTempDirectory` for isolation
- `TestUtils.deleteDirectoryRecursively` for cleanup
- `requireThat` for assertions

The class calls `System.exit(1)` on line 197 inside `run()` when checks fail. This must be refactored to return
the failure count instead, with `System.exit(1)` moved to `main()`.

## Approach

Create a TestNG test class `VerifyDeferPlanGenerationTest.java` in the test module. Refactor `run()` to return
the failure count as an `int` (moving `System.exit` to `main()`), then test each check scenario.

## Sub-Agent Waves

### Wave 1

- Refactor `client/src/main/java/io/github/cowwoc/cat/hooks/util/VerifyDeferPlanGeneration.java`:
  - Change `run()` return type from `void` to `int` (the failure count). The new signature is: `public static int run(String[] args, JvmScope scope, PrintStream out) throws IOException`.
  - Replace the `System.exit(1)` call at line 197 (inside `run()`) with `return failed;`.
  - Add `return failed;` as the final statement of `run()` (after the results line, replacing the implicit void return).
  - Move `@SuppressWarnings("PMD.DoNotTerminateVM")` from `run()` to `main()`.
  - In `main()`, capture the return value: `int failures = run(args, scope, System.out);` and add `if (failures > 0) System.exit(1);` after the call.
- Create `client/src/test/java/io/github/cowwoc/cat/hooks/test/VerifyDeferPlanGenerationTest.java` with the following tests:
  - `allChecksPass()` — Creates temp dir with `plugin/skills/add-agent/first-use.md` (contains `planTempFile=$(mktemp` but NOT `cat:plan-builder-agent`) and `plugin/skills/work-implement-agent/first-use.md` (contains both `hasSteps` and `cat:plan-builder-agent`). Verifies output contains 4 PASS lines, "0 failed" in the results summary, and return value is 0.
  - `check1FailsWhenPlanBuilderPresent()` — Creates both skill files with all valid content, EXCEPT `add-agent/first-use.md` also contains `cat:plan-builder-agent`. Verifies output contains FAIL for check 1, PASS for checks 2-4, and return value > 0.
  - `check2FailsWhenLightweightPlanMissing()` — Creates both skill files with all valid content, EXCEPT `add-agent/first-use.md` omits `planTempFile=$(mktemp`. Verifies output contains FAIL for check 2, PASS for checks 1, 3, 4, and return value > 0.
  - `check3FailsWhenHasStepsMissing()` — Creates both skill files with all valid content, EXCEPT `work-implement-agent/first-use.md` omits `hasSteps`. Verifies output contains FAIL for check 3, PASS for checks 1, 2, 4, and return value > 0.
  - `check4FailsWhenPlanBuilderMissing()` — Creates both skill files with all valid content, EXCEPT `work-implement-agent/first-use.md` omits `cat:plan-builder-agent`. Verifies output contains FAIL for check 4, PASS for checks 1, 2, 3, and return value > 0.
  - `missingAddSkillFile()` — Creates only `work-implement-agent/first-use.md` (valid). Does not create `add-agent/first-use.md`. Verifies output contains FAIL for checks 1 and 2 with "File not found" messages, PASS for checks 3 and 4, and return value > 0.
  - `missingWorkImplementSkillFile()` — Creates only `add-agent/first-use.md` (valid). Does not create `work-implement-agent/first-use.md`. Verifies output contains FAIL for checks 3 and 4 with "File not found" messages, PASS for checks 1 and 2, and return value > 0.
- No module-info changes needed: `io.github.cowwoc.cat.hooks.util` is already exported by the main module (`client/src/main/java/io/github/cowwoc/cat/hooks/module-info.java` line 34), and the test module already requires `io.github.cowwoc.cat.hooks` (`client/src/test/java/io/github/cowwoc/cat/hooks/test/module-info.java` line 9).
- Run `mvn -f client/pom.xml verify -e` to confirm all tests pass and no lint violations exist.

## Post-conditions

- Integration tests verify each static check in the verify script passes on valid input
- Integration tests verify each static check in the verify script fails appropriately on invalid input
- Tests run in CI (via `mvn verify`)
- All new tests pass
- No regressions in existing verify script behavior
- E2E verification: the `allChecksPass()` test runs the verify tool against a representative file structure and confirms all checks pass
