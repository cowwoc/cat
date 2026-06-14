## Testing

### Exception Testing
Use `@Test(expectedExceptions, expectedExceptionsMessageRegExp)` to declare both the expected exception type and message
pattern. No try/catch needed:

```java
// Good - TestNG validates both exception type and message; no try/catch needed
@Test(expectedExceptions = NullPointerException.class,
  expectedExceptionsMessageRegExp = ".*input.*")
public void executeRejectsNullInput() throws IOException
{
  cmd.execute(null, "value");
  // IOException propagates naturally → TestNG fails the test
}

// Good - multiple substrings: use regex lookahead
@Test(expectedExceptions = IllegalArgumentException.class,
  expectedExceptionsMessageRegExp = ".*(?=.*branch)(?=.*blank).*")
public void rejectsBlankBranch() throws IOException
{
  cmd.execute("", "value");
}

// Avoid - try/catch to validate message (unnecessary with expectedExceptionsMessageRegExp)
@Test(expectedExceptions = NullPointerException.class)
public void executeRejectsNullInput() throws IOException
{
  try
  {
    cmd.execute(null, "value");
  }
  catch (NullPointerException e)
  {
    requireThat(e.getMessage(), "message").contains("input");
    throw e;
  }
}
```

**Pattern syntax:** `expectedExceptionsMessageRegExp` uses `Pattern.matches()` which matches the **entire** string.
Wrap substrings in `.*` to match: `".*substring.*"`. For multiline messages, prefix with `(?s)` to make `.` match
newlines: `"(?s).*substring.*"`.

**For "accepts" tests** (verifying no validation exception is thrown):

```java
// Good - call the method directly; TestNG fails on unexpected exceptions
@Test
public void executeAcceptsEmptyBranch() throws IOException
{
  cmd.execute("HEAD~1", "HEAD", messageFile.toString(), "");
  // If NPE or IAE is thrown, TestNG fails the test automatically
  // IOException from git operations is acceptable → declared in throws
}
```

### Test Documentation
**All test methods must have Javadoc** describing:
- What the test verifies
- The expected behavior

```java
/**
 * Verifies that empty input returns an empty JSON object.
 */
@Test
public void emptyInput_returnsEmptyJson() throws IOException
{
  // ...
}
```

### Parallel Execution and Test Isolation
Tests run in parallel. In addition to the cross-language test isolation rules in `testing-conventions.md`, Java tests must follow
these Java-specific constraints:

1. **No class fields** - use local variables only
2. **No @BeforeMethod/@AfterMethod/@BeforeClass/@AfterClass** - initialize in each test method
3. **Use try-with-resources** for all resources (files, streams, temp directories)
4. **No shared mutable state** - each test must be fully self-contained
5. **No TestBase classes** - each test method must inline its own setup. This boilerplate is intentional and preferred
   over shared helpers or inheritance.
6. **Use test-specific scopes, not `Main*` scopes** - tests must never interact with production `Main*` scope
   implementations because they read environment variables, stdin, or engine-specific filesystem locations that may
   not be set in test contexts. Use injectable `Test*` scopes such as `TestClaudeTool(tempDir, tempDir)`,
   `TestCodexTool`, `TestCodexHook`, `TestClaudeTool`, or `TestClaudeHook` instead.
7. **Never use scope-provided objects after closing the scope** - objects returned by `AgentScope` (e.g., `JsonMapper`,
   `DisplayUtils`) must not be used after the scope is closed. Keep the scope open for the entire duration of the test.
   Do not create helper methods like `getTestMapper()` that open a scope, extract an object, and close the scope.
8. **No `System.setErr()`/`System.setOut()`** - these mutate JVM-wide shared state and are not thread-safe. Instead:
   - **Design for testability**: Create methods that accept arbitrary streams as parameters, then have `main()` delegate
     to these methods passing `System.in`/`System.out`/`System.err`. Tests pass their own streams.
   - **Assert observable side effects**: When stream injection isn't practical, verify behavior through other means (e.g.,
     file still exists after failed deletion) rather than capturing stderr.
9. **Classes that invoke git must accept a `directory` parameter** so tests can pass an isolated temporary repo created
   via `TestUtils.createTempGitRepo()`. For validation-only tests where execution fails before any git operation (e.g.,
   missing file check), passing `"."` is acceptable since no git command actually runs.

```java
// Good - self-contained test with TestClaudeTool
@Test
public void testProcess() throws IOException
{
  Path tempDir = Files.createTempDirectory("test-");
  try (AgentPluginScope scope = new TestClaudeTool(tempDir, tempDir))
  {
    JsonMapper mapper = scope.getJsonMapper();
    var result = process(scope, input);
    requireThat(result, "result").isEqualTo("expected");
  }
  finally
  {
    TestUtils.deleteDirectoryRecursively(tempDir);
  }
}

// Avoid - class fields, setup methods, or shared base classes
private InputStream sharedInput;  // Don't do this

@BeforeMethod
public void setup()  // Don't do this
{
  sharedInput = ...;
}
```

### Assertions
**Prefer requirements.java over TestNG assertions**:

```java
// Good - clear error messages
requireThat(result, "result").isEqualTo(expected);
requireThat(list, "list").contains(item);

// Avoid - less informative
assertEquals(result, expected);
assertTrue(list.contains(item));
```

### Validate Return Value Contents, Not Just Non-Null
Tests must validate the **contents** of return values, not merely that they are non-null. A non-null check proves the method
returned something but says nothing about correctness.

```java
// ❌ WRONG: Only checks non-null - passes even if result contains wrong data
SessionStartHandler.Result result = new CheckUpdateAvailable(scope).handle(input);
requireThat(result, "result").isNotNull();

// ✅ CORRECT: Validates the actual content of the result
SessionStartHandler.Result result = new CheckUpdateAvailable(scope).handle(input);
requireThat(result.output(), "output").contains("expected text");
requireThat(result.continueProcessing(), "continueProcessing").isTrue();
```

**Why:** A test that only asserts `isNotNull()` will pass even when the method returns completely wrong data. The test
provides false confidence - it "passes" but validates nothing meaningful.

**When `isNotNull()` alone is acceptable:**
- The test explicitly documents that it only verifies the method doesn't throw (smoke test)
- The value is an opaque handle where contents are not meaningful to the caller

### Test Requirements, Not Implementation
**Tests should validate business requirements with controlled inputs, not assert system configuration or implementation details.**

Tests that assert environment-dependent values (terminal emoji widths, system fonts, screen resolution) provide no business value - they only verify that your development machine has specific configuration.

```java
// ❌ WRONG: Asserts developer's terminal configuration
@Test
public void singleEmojiHasWidthTwo() throws IOException
{
  DisplayUtils display = new DisplayUtils();  // Auto-detects terminal
  int width = display.displayWidth("🐱");
  requireThat(width, "width").isEqualTo(2);  // Fails on different terminals
}

// ✅ CORRECT: Tests width calculation logic with controlled input
@Test
public void displayWidthCalculatesFromConfiguration() throws IOException
{
  Path tempConfig = createTempConfig(Map.of("🐱", 2, "✅", 1));
  DisplayUtils display = new DisplayUtils(tempConfig, TerminalType.KITTY);

  requireThat(display.displayWidth("🐱"), "catWidth").isEqualTo(2);
  requireThat(display.displayWidth("✅"), "checkWidth").isEqualTo(1);
  requireThat(display.displayWidth("🐱 cat"), "combinedWidth").isEqualTo(6);
}
```

**What to test:**
- **Behavior:** Does the class correctly load configuration and apply it?
- **Logic:** Does calculation handle edge cases (empty string, multiple emojis, mixed content)?
- **Requirements:** Does it meet the stated business requirements?

**What NOT to test:**
- **System state:** What emoji width does my terminal happen to have?
- **Implementation details:** What specific value is in the default config file?
- **Environment:** What does auto-detection return on my machine?

**Guideline:** If your test would fail when run on a different machine with different configuration (but the code still works correctly), you're testing implementation details, not requirements.

### Testability Over Convenience
If code cannot be tested in a thread-safe way (e.g., it reads from `System.in` or writes to `System.out`), ask the
user's permission to update the API to make it testable. For example, add a method overload that accepts an
`InputStream` parameter instead of reading from `System.in` directly. The `main()` method can delegate to the testable
overload.

### No Thread.sleep() in Tests
Avoid using `Thread.sleep()` in tests. There should always be a way to trigger the desired event/condition without
sleeping:

```java
// Good - inject Clock to control time
@Test
public void rateLimitExpires() throws IOException
{
  Instant baseTime = Instant.parse("2025-01-01T00:00:00Z");
  Clock clock1 = Clock.fixed(baseTime, ZoneOffset.UTC);
  Clock clock2 = Clock.fixed(baseTime.plusSeconds(2), ZoneOffset.UTC);

  DetectGivingUp handler1 = new DetectGivingUp(clock1);
  handler1.check(prompt, sessionId);

  DetectGivingUp handler2 = new DetectGivingUp(clock2);
  handler2.check(prompt, sessionId);  // Time has "passed"
}

// Avoid - sleeping slows tests and introduces flakiness
@Test
public void rateLimitExpires() throws InterruptedException
{
  handler.check(prompt, sessionId);
  Thread.sleep(1100);  // Don't do this
  handler.check(prompt, sessionId);
}
```

**Why:**
- `Thread.sleep()` makes tests slow and flaky
- Time-dependent code should accept a `Clock` parameter for testability
- Fixed clocks make tests deterministic and fast
