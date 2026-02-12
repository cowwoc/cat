# Plan: add-record-validation

## Current State

7 private records across the codebase have missing or empty compact constructors without parameter
validation. Per java.md convention, all records MUST have compact constructors with validation.

## Target State

All private records have compact constructors that validate parameters using `requireThat()` for
public constructors or `assert that()` for private records (per java.md convention on private method
validation). Since these are private records, use `assert that()` with `.elseThrow()`.

## Satisfies

None

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** None. Private records, internal API only.
- **Mitigation:** Run full test suite after changes.

## Files to Modify

### Records with empty compact constructors (add validation):
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/prompt/DestructiveOps.java` - `KeywordPattern(String display, Pattern pattern)`: validate `display` not blank, `pattern` not null
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/tool/post/AutoLearnMistakes.java` - `MistakeDetection(String type, String details)`: validate `type` not blank, `details` not blank
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/read/pre/PredictBatchOpportunity.java` - `Operation(String tool, String path, int timestamp)`: validate `tool` not blank, `path` not blank
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/read/pre/PredictBatchOpportunity.java` - `TrackerState(List<Operation> operations, int warningsShown, int lastWarning)`: validate `operations` not null
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/read/post/DetectSequentialTools.java` - `TrackerState(int lastToolTime, int sequentialCount, List<String> lastToolNames)`: validate `lastToolNames` not null

### Records missing compact constructors entirely (add compact constructor with validation):
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/SubagentMonitor.java` - `TokenCounts(int tokens, int compactions)`: add compact constructor (primitives only, validate non-negative)
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/EnforceStatusOutput.java` - `CheckResult(boolean statusInvoked, boolean hasBoxOutput)`: add compact constructor (booleans only, no validation needed but add Javadoc)

## Execution Steps

1. **For each record with empty compact constructor**, replace the comment body with `assert that()` validation:
   - String parameters: `assert that(param, "param").isNotBlank().elseThrow()`
   - Object/List parameters: `assert that(param, "param").isNotNull().elseThrow()`
   - Add `import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.assertThat;` if not present
2. **For records missing compact constructors**, add a compact constructor with Javadoc and validation
3. **For CheckResult** (boolean-only record): add compact constructor with Javadoc only (no validation needed for primitives with no constraints)
4. **Run `mvn -f hooks/pom.xml verify`** to confirm all tests pass

## Acceptance Criteria

- [ ] All 7 private records have compact constructors
- [ ] All String parameters validated as not blank
- [ ] All object/collection parameters validated as not null
- [ ] All compact constructors have Javadoc
- [ ] All tests pass

## Success Criteria

- [ ] `mvn -f hooks/pom.xml verify` passes with exit code 0
- [ ] Zero records with `// Record validation` comments remain
- [ ] No private records exist without compact constructors (except boolean/primitive-only records where Javadoc-only is acceptable)