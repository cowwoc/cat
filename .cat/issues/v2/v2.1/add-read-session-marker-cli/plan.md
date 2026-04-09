# Plan: add-read-session-marker-cli

## Goal
Add a Java CLI tool `read-session-marker` that reads any named marker file from a session directory,
printing its content to stdout and exiting non-zero when the file is absent. The tool accepts a
`<marker-name>` argument (the full filename) rather than being tied to the `squash-complete-{issueId}`
naming convention used by `write-session-marker`.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Path traversal if session-id or marker-name contain `..`; handled by `GetSkill.resolveAndValidateContainment()` (same as `WriteSessionMarker`)
- **Mitigation:** Mirror the containment validation from `WriteSessionMarker`

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/claude/hook/util/ReadSessionMarker.java` — new file
- `client/src/test/java/io/github/cowwoc/cat/client/test/ReadSessionMarkerTest.java` — new file
- `client/src/test/java/io/github/cowwoc/cat/client/test/ReadSessionMarkerMainTest.java` — new file
- `client/build-jlink.sh` — add `"read-session-marker:hook.util.ReadSessionMarker"` after the `write-session-marker` entry

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1: Create ReadSessionMarker.java

Create `client/src/main/java/io/github/cowwoc/cat/claude/hook/util/ReadSessionMarker.java`.

- Package: `io.github.cowwoc.cat.claude.hook.util`
- Class: `public final class ReadSessionMarker implements SkillOutput`
- License header: standard CAT Commercial License block comment

**Constructor:**
```java
public ReadSessionMarker(ClaudeTool scope)
```
- Stores scope in a final field.
- Validates: `requireThat(scope, "scope").isNotNull()`

**`getOutput(String[] args)` method:**

Signature: `public String getOutput(String[] args) throws IOException`

Args: exactly 2 arguments: `session-id`, `marker-name`

Validates:
- `args` is not null
- `args.length == 2`; if not, throws `IllegalArgumentException`:
  `"Expected exactly 2 arguments (session-id, marker-name), got " + args.length + ". " +
   "Usage: read-session-marker <session-id> <marker-name>"`
- `args[0]` (sessionId) is not blank; if blank, throws `IllegalArgumentException`:
  `"session-id is required as the first argument but was blank. " +
   "Usage: read-session-marker <session-id> <marker-name>"`
- `args[1]` (markerName) is not blank; if blank, throws `IllegalArgumentException`:
  `"marker-name is required as the second argument but was blank. " +
   "Usage: read-session-marker <session-id> <marker-name>"`

Path construction using `GetSkill.resolveAndValidateContainment()`:
```java
Path baseDir = scope.getCatWorkPath().resolve("sessions").toAbsolutePath().normalize();
Path sessionDir = GetSkill.resolveAndValidateContainment(baseDir, sessionId, "session-id");
Path markerFile = GetSkill.resolveAndValidateContainment(sessionDir, markerName, "marker-name");
```

Then:
```java
return Files.readString(markerFile, UTF_8);
```

`Files.readString()` throws `NoSuchFileException` (a subtype of `IOException`) if the file does not
exist. Let this propagate — `main()` handles it specially with exit code 1.

**`main()` method:**
```java
public static void main(String[] args)
{
  try (ClaudeTool scope = new MainClaudeTool())
  {
    try
    {
      run(scope, args, System.out);
    }
    catch (NoSuchFileException e)
    {
      // File absent — exit 1 so callers can use the `|| echo ""` pattern.
      // Print to stderr; the skill suppresses it with 2>/dev/null.
      System.err.println("Marker file not found: " + e.getFile());
      System.exit(1);
    }
    catch (IllegalArgumentException | IOException e)
    {
      System.out.println(block(scope,
        Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
    }
    catch (RuntimeException | AssertionError e)
    {
      Logger log = LoggerFactory.getLogger(ReadSessionMarker.class);
      log.error("Unexpected error", e);
      System.out.println(block(scope,
        Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
    }
  }
}
```

**`run()` method:**
```java
public static void run(ClaudeTool scope, String[] args, PrintStream out) throws IOException
{
  requireThat(args, "args").isNotNull();
  requireThat(out, "out").isNotNull();
  String output = new ReadSessionMarker(scope).getOutput(args);
  if (!output.isEmpty())
    out.print(output);
}
```

**Imports required:**
- `io.github.cowwoc.cat.claude.hook.Strings.block` (static import)
- `io.github.cowwoc.cat.claude.tool.ClaudeTool`
- `io.github.cowwoc.cat.claude.tool.MainClaudeTool`
- `org.slf4j.Logger`
- `org.slf4j.LoggerFactory`
- `java.io.IOException`
- `java.io.PrintStream`
- `java.nio.file.Files`
- `java.nio.file.NoSuchFileException`
- `java.nio.file.Path`
- `java.util.Objects`
- `io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat` (static import)
- `java.nio.charset.StandardCharsets.UTF_8` (static import)

### Job 2: Register in build-jlink.sh

In `client/build-jlink.sh`, add the following entry to the `HANDLERS` array immediately after the
`write-session-marker` entry:

```bash
"read-session-marker:hook.util.ReadSessionMarker"
```

### Job 3: Create ReadSessionMarkerTest.java

Create `client/src/test/java/io/github/cowwoc/cat/client/test/ReadSessionMarkerTest.java`.

- Package: `io.github.cowwoc.cat.client.test`
- Test framework: TestNG
- License header: standard CAT Commercial License block comment
- Use `TestClaudeTool(tempDir, tempDir)` inside `try (ClaudeTool scope = ...)`

Tests:

1. **`readsMarkerFileContent()`** — happy path:
   - Creates temp dir and `TestClaudeTool(tempDir, tempDir)` in try-with-resources + finally cleanup
   - Writes `"squashed:abc123def"` to `tempDir.resolve(".cat/work/sessions/session-abc123/squash-complete-2.1-fix-foo")` (create parent dirs first)
   - Calls `new ReadSessionMarker(scope).getOutput(new String[]{"session-abc123", "squash-complete-2.1-fix-foo"})`
   - Asserts result equals `"squashed:abc123def"`

2. **`throwsWhenFileAbsent()`**:
   - `@Test(expectedExceptions = NoSuchFileException.class)`
   - Creates temp dir and `TestClaudeTool(tempDir, tempDir)` in try-with-resources + finally cleanup
   - Calls `new ReadSessionMarker(scope).getOutput(new String[]{"session-abc123", "squash-complete-missing-issue"})`
   - Expects `NoSuchFileException`

3. **`rejectsWhenArgCountIsNotTwo()`**:
   - `@Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".*2 arguments.*")`
   - Creates `TestClaudeTool(tempDir, tempDir)` in try-with-resources + finally cleanup
   - Calls `new ReadSessionMarker(scope).getOutput(new String[]{"session-id", "marker-name", "extra"})`

4. **`rejectsBlankSessionId()`**:
   - `@Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".*session-id.*blank.*")`
   - Creates `TestClaudeTool(tempDir, tempDir)` in try-with-resources + finally cleanup
   - Calls `new ReadSessionMarker(scope).getOutput(new String[]{"", "squash-complete-issue"})`

5. **`rejectsBlankMarkerName()`**:
   - `@Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".*marker-name.*blank.*")`
   - Creates `TestClaudeTool(tempDir, tempDir)` in try-with-resources + finally cleanup
   - Calls `new ReadSessionMarker(scope).getOutput(new String[]{"session-id", ""})`

### Job 4: Create ReadSessionMarkerMainTest.java

Create `client/src/test/java/io/github/cowwoc/cat/client/test/ReadSessionMarkerMainTest.java`.

- Package: `io.github.cowwoc.cat.client.test`
- License header: standard CAT Commercial License block comment
- Mirror the structure of `WriteSessionMarkerMainTest`

Tests:

1. **`noArgsThrowsException()`**:
   - `@Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "(?s).*Expected exactly 2 arguments.*")`
   - Creates `TestClaudeTool(tempDir, tempDir)` in try-with-resources + finally cleanup
   - Calls `ReadSessionMarker.run(scope, new String[]{}, out)`

2. **`nullArgsThrowsException()`**:
   - `@Test(expectedExceptions = NullPointerException.class, expectedExceptionsMessageRegExp = ".*args.*")`
   - Creates `TestClaudeTool(tempDir, tempDir)` in try-with-resources + finally cleanup
   - Calls `ReadSessionMarker.run(scope, null, out)`

3. **`nullOutThrowsException()`**:
   - `@Test(expectedExceptions = NullPointerException.class, expectedExceptionsMessageRegExp = ".*out.*")`
   - Creates `TestClaudeTool(tempDir, tempDir)` in try-with-resources + finally cleanup
   - Calls `ReadSessionMarker.run(scope, new String[]{}, null)`

### Job 5: Run Tests

```bash
mvn -f client/pom.xml verify -e
```

All tests must pass.

## Post-conditions

- `client/src/main/java/io/github/cowwoc/cat/claude/hook/util/ReadSessionMarker.java` exists with
  license header, 2-argument validation (`session-id`, `marker-name`), path containment check,
  `Files.readString()` return, `NoSuchFileException` handled with exit 1 in `main()`, and `run()` method
- `client/build-jlink.sh` HANDLERS array contains `"read-session-marker:hook.util.ReadSessionMarker"`
- `client/src/test/java/io/github/cowwoc/cat/client/test/ReadSessionMarkerTest.java` exists with 5 tests
- `client/src/test/java/io/github/cowwoc/cat/client/test/ReadSessionMarkerMainTest.java` exists with 3 tests
- `mvn -f client/pom.xml verify -e` exits 0 with all tests passing
- Note for skill update: callers pass the full marker filename as `marker-name`
  (e.g., `"squash-complete-${ISSUE_ID}"`) rather than a separate issue-id argument
