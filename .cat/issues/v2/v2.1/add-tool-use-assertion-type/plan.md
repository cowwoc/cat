## Type
feature

## Goal
Add `tool_use` as a valid assertion type in `EmpiricalTestRunner.java` and add TC5 to
`plugin/skills/get-output-agent/benchmark/test-cases.json` to verify the agent invokes the `Skill` tool.

## Research Findings
- `EmpiricalTestRunner.java` already captures tool use names in `ParsedOutput.toolUses` (flat `List<String>`)
  and in `TurnOutput.toolUses` (per-turn `List<String>`)
- `evaluateOutput()` (line 839) uses the OLD criteria-map format (`must_contain`, `must_use_tools`, etc.)
- The new `test-cases.json` schema uses typed `assertions[]` (`type`, `assertion_id`, `expected`, plus
  type-specific fields: `method`+`pattern` for deterministic, `instruction` for semantic, `tool` for tool_use)
- No existing Java method evaluates the typed assertion schema — this is new functionality
- `EvaluationResult` record (line 1646) is the correct return type: `(boolean pass, Map<String, Boolean> checks)`

## Execution Steps

### Step 1: Add `evaluateAssertions()` to `EmpiricalTestRunner.java`

File: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/EmpiricalTestRunner.java`

Add the following public method after `evaluateOutput()` (after line 874):

```java
/**
 * Evaluates a list of typed assertions against agent output.
 * <p>
 * Supports the following assertion types:
 * <ul>
 *   <li>{@code deterministic} — evaluates string-match assertions against the text output</li>
 *   <li>{@code semantic} — skipped (evaluated by Claude as a judge, not programmatically)</li>
 *   <li>{@code tool_use} — checks whether the named tool was invoked</li>
 * </ul>
 *
 * @param assertions list of assertion maps, each containing at minimum {@code assertion_id},
 *                   {@code type}, and {@code expected}
 * @param texts      the text outputs from the agent
 * @param toolUses   the tool use names from the agent
 * @return the evaluation result
 * @throws NullPointerException     if {@code assertions}, {@code texts}, or {@code toolUses} are null
 * @throws IllegalArgumentException if an assertion has an unknown {@code type} or an unknown
 *                                  {@code method} within a {@code deterministic} assertion
 */
public EvaluationResult evaluateAssertions(List<Map<String, Object>> assertions,
  List<String> texts, List<String> toolUses)
{
  requireThat(assertions, "assertions").isNotNull();
  requireThat(texts, "texts").isNotNull();
  requireThat(toolUses, "toolUses").isNotNull();
  String fullText = String.join("\n", texts);
  String lowerText = fullText.toLowerCase(Locale.ROOT);

  Map<String, Boolean> checks = new HashMap<>();
  for (Map<String, Object> assertion : assertions)
  {
    String assertionId = (String) assertion.get("assertion_id");
    String type = (String) assertion.get("type");
    boolean expected = Boolean.TRUE.equals(assertion.get("expected"));

    switch (type)
    {
      case "deterministic" ->
      {
        String method = (String) assertion.get("method");
        if (method == null || !method.equals("string_match"))
        {
          throw new IllegalArgumentException(
            "assertion '" + assertionId + "': unknown deterministic method: '" + method +
            "'. Supported methods: [string_match]");
        }
        String pattern = (String) assertion.get("pattern");
        boolean found = lowerText.contains(pattern.toLowerCase(Locale.ROOT));
        checks.put(assertionId, found == expected);
      }
      case "semantic" ->
      {
        // Semantic assertions are evaluated by Claude as a judge, not programmatically.
        // Skip without adding to checks — they do not affect the deterministic pass/fail.
      }
      case "tool_use" ->
      {
        String tool = (String) assertion.get("tool");
        boolean found = toolUses.contains(tool);
        checks.put(assertionId, found == expected);
      }
      default ->
        throw new IllegalArgumentException(
          "assertion '" + assertionId + "': unknown type: '" + type +
          "'. Supported types: [deterministic, semantic, tool_use]");
    }
  }

  boolean allPass = checks.isEmpty() || checks.values().stream().allMatch(v -> v);
  return new EvaluationResult(allPass, checks);
}
```

No new imports are needed (uses only existing imports: `List`, `Map`, `HashMap`, `Locale`).

### Step 2: Add unit tests to `EmpiricalTestRunnerTest.java`

File: `client/src/test/java/io/github/cowwoc/cat/hooks/test/EmpiricalTestRunnerTest.java`

Add the following test methods immediately before the closing `}` of the class (the last line of the file, currently
line 2894). Each test must be self-contained (no class fields, no @Before methods):

**Test 1: tool_use passes when tool is in toolUses and expected is true**
```java
/**
 * Verifies that a tool_use assertion passes when the expected tool appears in toolUses.
 */
@Test
public void toolUseAssertion_passes_whenToolFound() throws IOException
{
  Path tempDir = Files.createTempDirectory("empirical-test-");
  try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
  {
    EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
    List<Map<String, Object>> assertions = List.of(
      Map.of("assertion_id", "TC5_tool_1", "type", "tool_use", "tool", "Skill", "expected", true));
    EvaluationResult result = runner.evaluateAssertions(assertions, List.of("some text"), List.of("Skill", "Bash"));
    requireThat(result.pass(), "pass").isTrue();
    requireThat(result.checks().get("TC5_tool_1"), "TC5_tool_1").isTrue();
  }
  finally
  {
    TestUtils.deleteDirectoryRecursively(tempDir);
  }
}
```

**Test 2: tool_use fails when tool is absent and expected is true**
```java
/**
 * Verifies that a tool_use assertion fails when the expected tool is absent from toolUses.
 */
@Test
public void toolUseAssertion_fails_whenToolNotFound() throws IOException
{
  Path tempDir = Files.createTempDirectory("empirical-test-");
  try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
  {
    EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
    List<Map<String, Object>> assertions = List.of(
      Map.of("assertion_id", "TC5_tool_1", "type", "tool_use", "tool", "Skill", "expected", true));
    EvaluationResult result = runner.evaluateAssertions(assertions, List.of("some text"), List.of("Bash"));
    requireThat(result.pass(), "pass").isFalse();
    requireThat(result.checks().get("TC5_tool_1"), "TC5_tool_1").isFalse();
  }
  finally
  {
    TestUtils.deleteDirectoryRecursively(tempDir);
  }
}
```

**Test 3: tool_use passes when tool is absent and expected is false**
```java
/**
 * Verifies that a tool_use assertion passes when the tool is absent and expected is false.
 */
@Test
public void toolUseAssertion_passes_whenToolAbsentAndExpectedFalse() throws IOException
{
  Path tempDir = Files.createTempDirectory("empirical-test-");
  try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
  {
    EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
    List<Map<String, Object>> assertions = List.of(
      Map.of("assertion_id", "TC5_tool_1", "type", "tool_use", "tool", "Skill", "expected", false));
    EvaluationResult result = runner.evaluateAssertions(assertions, List.of(), List.of("Bash"));
    requireThat(result.pass(), "pass").isTrue();
    requireThat(result.checks().get("TC5_tool_1"), "TC5_tool_1").isTrue();
  }
  finally
  {
    TestUtils.deleteDirectoryRecursively(tempDir);
  }
}
```

**Test 4: deterministic string_match passes when pattern found and expected true**
```java
/**
 * Verifies that a deterministic string_match assertion passes when pattern is found and expected is true.
 */
@Test
public void deterministicAssertion_passes_whenPatternFound() throws IOException
{
  Path tempDir = Files.createTempDirectory("empirical-test-");
  try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
  {
    EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
    List<Map<String, Object>> assertions = List.of(
      Map.of("assertion_id", "TC1_det_1", "type", "deterministic", "method", "string_match",
        "pattern", "hello world", "expected", true));
    EvaluationResult result = runner.evaluateAssertions(assertions, List.of("Hello World output"), List.of());
    requireThat(result.pass(), "pass").isTrue();
    requireThat(result.checks().get("TC1_det_1"), "TC1_det_1").isTrue();
  }
  finally
  {
    TestUtils.deleteDirectoryRecursively(tempDir);
  }
}
```

**Test 5: semantic assertions are skipped (do not affect pass/fail)**
```java
/**
 * Verifies that semantic assertions are skipped and do not affect the pass/fail result.
 */
@Test
public void semanticAssertion_isSkipped_doesNotAffectResult() throws IOException
{
  Path tempDir = Files.createTempDirectory("empirical-test-");
  try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
  {
    EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
    List<Map<String, Object>> assertions = List.of(
      Map.of("assertion_id", "TC1_sem_1", "type", "semantic",
        "instruction", "Check if the response is good", "expected", true));
    EvaluationResult result = runner.evaluateAssertions(assertions, List.of("some text"), List.of());
    // Semantic assertions are skipped — checks map is empty — so allPass is true (vacuously)
    requireThat(result.pass(), "pass").isTrue();
    requireThat(result.checks().containsKey("TC1_sem_1"), "containsKey").isFalse();
  }
  finally
  {
    TestUtils.deleteDirectoryRecursively(tempDir);
  }
}
```

**Test 6: unknown assertion type throws IllegalArgumentException**
```java
/**
 * Verifies that an unknown assertion type throws IllegalArgumentException.
 */
@Test(expectedExceptions = IllegalArgumentException.class,
  expectedExceptionsMessageRegExp = ".*unknown type.*'invalid_type'.*")
public void evaluateAssertions_rejectsUnknownType() throws IOException
{
  Path tempDir = Files.createTempDirectory("empirical-test-");
  try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
  {
    EmpiricalTestRunner runner = new EmpiricalTestRunner(scope);
    List<Map<String, Object>> assertions = List.of(
      Map.of("assertion_id", "TC_bad", "type", "invalid_type", "expected", true));
    runner.evaluateAssertions(assertions, List.of(), List.of());
  }
  finally
  {
    TestUtils.deleteDirectoryRecursively(tempDir);
  }
}
```

### Step 3: Add TC5 to `plugin/skills/get-output-agent/benchmark/test-cases.json`

File: `plugin/skills/get-output-agent/benchmark/test-cases.json`

Add TC5 as the last element in the `test_cases` array, after TC4 (before the closing `]`):

```json
    {
      "test_case_id": "TC5",
      "semantic_unit_id": "unit_1",
      "category": "REQUIREMENT",
      "prompt": "You are running the get-output-agent skill. Invoke the skill now.",
      "assertions": [
        {
          "assertion_id": "TC5_tool_1",
          "type": "tool_use",
          "description": "agent invoked the Skill tool at least once",
          "tool": "Skill",
          "expected": true
        }
      ]
    }
```

### Step 4: Build and run tests

```bash
cd /workspace/.cat/work/worktrees/2.1-add-write-session-marker-cli && mvn -f client/pom.xml verify
```

All tests must pass (exit code 0).

### Step 5: Commit

From the worktree directory, commit all changes with type `feature:`:

```bash
cd /workspace/.cat/work/worktrees/2.1-add-write-session-marker-cli && \
git add client/src/main/java/io/github/cowwoc/cat/hooks/skills/EmpiricalTestRunner.java \
        client/src/test/java/io/github/cowwoc/cat/hooks/test/EmpiricalTestRunnerTest.java \
        plugin/skills/get-output-agent/benchmark/test-cases.json && \
git commit -m "feature: add tool_use assertion type to benchmark schema and EmpiricalTestRunner"
```

## Post-Conditions
- `EmpiricalTestRunner.java` contains a public `evaluateAssertions()` method that accepts
  `List<Map<String, Object>> assertions`, `List<String> texts`, `List<String> toolUses`
- `evaluateAssertions()` handles `type: "tool_use"` by checking `toolUses.contains(tool)`
  compared against `expected`, returning pass/fail in the `EvaluationResult.checks` map
- `evaluateAssertions()` handles `type: "deterministic"` with `method: "string_match"` via
  case-insensitive text matching, compared against `expected`
- `evaluateAssertions()` skips `type: "semantic"` assertions (they are not added to checks)
- `evaluateAssertions()` throws `IllegalArgumentException` for unknown types
- Unit tests cover: tool_use passes (tool found + expected true), tool_use fails (tool absent
  + expected true), tool_use passes (tool absent + expected false), deterministic passes,
  semantic is skipped, unknown type throws
- `test-cases.json` for `plugin/skills/get-output-agent` includes TC5 with `type: "tool_use"`,
  `tool: "Skill"`, `expected: true`
- All existing tests continue to pass (`mvn -f client/pom.xml verify` exits 0)
