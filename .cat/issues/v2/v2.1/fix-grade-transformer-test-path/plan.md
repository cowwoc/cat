# Fix GradeJsonTransformerTest to Call Java Directly

## Problem

`GradeJsonTransformerTest` spawns the `grade-json-transformer` binary from the plugin cache via `ProcessBuilder`.
This creates a bootstrap dependency: tests can't pass unless the jlink image is already installed, but the build
(which includes tests) must pass before we can install the jlink image.

## Solution

Rewrite the test to call `GradeJsonTransformer.transform()` directly using `TestClaudeTool` scope instead of
spawning the external binary.

## Research Findings

### Class Hierarchy
- `GradeJsonTransformer` (in `io.github.cowwoc.cat.claude.hook.skills`) has a public instance method:
  `void transform(Path inputPath, String runId, Path outputPath, JvmScope scope) throws IOException`
- `TestClaudeTool` extends `AbstractClaudeTool` → `AbstractClaudePluginScope` → `AbstractJvmScope` → implements `JvmScope`
- Therefore `TestClaudeTool` can be passed directly as the `JvmScope scope` parameter

### Error Message Patterns (skills version of GradeJsonTransformer)
- **Wrong field "status" instead of "verdict":**
  `"assertion_results[0] has wrong field name 'status'. Must use 'verdict' instead."`
  The original plan regex `".*verdict.*status.*"` will NOT match because "status" appears before "verdict".
  Correct regex: `".*status.*verdict.*"`
- **Missing "verdict" field:**
  `"assertion_results[0] missing required field: verdict"`
  The original plan regex `".*(?i)missing.*verdict.*"` WILL match.

### TestClaudeTool Constructor Patterns
- Other tests use `new TestClaudeTool(tempDir, tempDir)` for 2-arg constructor (projectPath, pluginRoot)
- Or `new TestClaudeTool()` (no-arg, creates its own temp dirs)
- Tests typically use try-with-resources: `try (JvmScope scope = new TestClaudeTool(tempDir, tempDir))`

### Output Schema Differences
The `skills` version produces different output than the `util` version:
- Has `test_case_id`, `assertion_results`, `stats` (with `total`, `pass`, `fail`)
- Does NOT have `config`, `pass_count`, `fail_count`, `total_count`, `pass_rate` fields
- The `assertion_results` are passed through as-is (not re-built from fields)

## Files Changed

- `client/src/test/java/io/github/cowwoc/cat/client/test/GradeJsonTransformerTest.java` — rewrite

## Jobs

### Job 1

- Remove the `binaryExists()` test method entirely — it tests deployment state, not business logic
- Remove the `ProcessBuilder` import and `java.nio.charset.StandardCharsets` import (no longer needed)
- Add import for `io.github.cowwoc.cat.claude.hook.skills.GradeJsonTransformer` (the skills package version, NOT the util version)
- Add import for `io.github.cowwoc.cat.claude.hook.JvmScope`
- Update class javadoc from "Tests for the grade-json-transformer CLI binary" to "Tests for {@link GradeJsonTransformer}."
- Rewrite `validGraderJsonIsAccepted()`:
  - Create `tempDir` via `Files.createTempDirectory("test-")`
  - Declare `Path inputFile = tempDir.resolve("input.json")` and `Path outputFile = tempDir.resolve("output.json")`
  - Create `try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))` as the outer try-with-resources
  - Write the same input JSON to `inputFile`
  - Call `new GradeJsonTransformer().transform(inputFile, "tc1_run1", outputFile, scope)`
  - Read the output: `String outputJson = Files.readString(outputFile)`
  - Parse output with `scope.getJsonMapper().readTree(outputJson)`
  - Assert `test_case_id` equals `"tc1_run1"`
  - Assert `assertion_results` is present and is an array
  - Assert `stats` is present (the skills version outputs `stats`, not `pass_count`/`fail_count`/etc.)
  - Cleanup in `finally` block: delete tempDir via `TestUtils.deleteDirectoryRecursively(tempDir)` (scope is closed by try-with-resources)
- Rewrite `wrongFieldNameStatusIsRejected()`:
  - Create `tempDir` via `Files.createTempDirectory("test-")`
  - Declare `Path inputFile = tempDir.resolve("input.json")` and `Path outputFile = tempDir.resolve("output.json")`
  - Create `try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))` as the outer try-with-resources
  - Write input JSON with `"status"` instead of `"verdict"` to `inputFile`
  - Call `new GradeJsonTransformer().transform(inputFile, "tc1_run1", outputFile, scope)` — this throws `IOException`
  - Change regex to `".*status.*verdict.*"` (matches actual error message order)
  - Keep `expectedExceptions = IOException.class` annotation
  - Cleanup in `finally` block: delete tempDir via `TestUtils.deleteDirectoryRecursively(tempDir)` (scope is closed by try-with-resources)
- Rewrite `missingVerdictFieldIsRejected()`:
  - Create `tempDir` via `Files.createTempDirectory("test-")`
  - Declare `Path inputFile = tempDir.resolve("input.json")` and `Path outputFile = tempDir.resolve("output.json")`
  - Create `try (TestClaudeTool scope = new TestClaudeTool(tempDir, tempDir))` as the outer try-with-resources
  - Write input JSON that omits the `"verdict"` field entirely to `inputFile`
  - Call `new GradeJsonTransformer().transform(inputFile, "tc1_run1", outputFile, scope)` — this throws `IOException`
  - Keep regex as `".*(?i)missing.*verdict.*"` (matches the actual error message)
  - Keep `expectedExceptions = IOException.class` annotation
  - Cleanup in `finally` block: delete tempDir via `TestUtils.deleteDirectoryRecursively(tempDir)` (scope is closed by try-with-resources)
- Remove all `ProcessBuilder` references, `Process` references, binary path construction, `InterruptedException` handling
- Update `.cat/issues/v2/v2.1/fix-grade-transformer-test-path/index.json`: set `"status"` to `"closed"`

## Post-conditions

- `mvn -f client/pom.xml verify` passes without any pre-installed plugin cache
- All three validation scenarios are tested
- Tests use `TestClaudeTool` per test isolation conventions
- No `ProcessBuilder` or binary path references remain in the test
