<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Issue: add-write-session-marker-cli

## Type
feature

## Goal
Add a Java CLI tool `write-session-marker` that atomically creates the session directory and writes a
marker file given a session ID, issue ID, and marker content — replacing the 3-step bash pattern in
`plugin/skills/work-merge-agent/first-use.md` that uses `mkdir`, `git rev-parse`, and a bash redirect.

## Motivation
The current squash-complete marker in `work-merge-agent/first-use.md` uses 3 separate bash calls:

```bash
SQUASH_MARKER_DIR="${CLAUDE_PROJECT_DIR}/.cat/work/sessions/${CLAUDE_SESSION_ID}"
mkdir -p "${SQUASH_MARKER_DIR}"
SQUASH_COMMIT_HASH=$(cd "${WORKTREE_PATH}" && git rev-parse HEAD)
echo "squashed:${SQUASH_COMMIT_HASH}" > "${SQUASH_MARKER_DIR}/squash-complete-${ISSUE_ID}"
```

A single CLI tool call replaces 2 of these steps (mkdir + redirect), reducing round-trips and providing
consistent error handling.

## Execution Steps

### Step 1: Create WriteSessionMarker.java

Create the file
`client/src/main/java/io/github/cowwoc/cat/hooks/util/WriteSessionMarker.java` with the following
specification:

- Package: `io.github.cowwoc.cat.hooks.util`
- Class: `public final class WriteSessionMarker`
- Implements: `SkillOutput` (for consistency with other skill output classes)
- License header: standard CAT Commercial License block comment

**Constructor:**
```java
public WriteSessionMarker(JvmScope scope)
```
- Stores scope in a final field `scope`.
- Validates: `requireThat(scope, "scope").isNotNull()`

**`getOutput(String[] args)` method:**

Signature: `public String getOutput(String[] args) throws IOException`

Validates:
- `args` is not null
- `args.length == 3`; if not, throws `IllegalArgumentException`:
  `"Expected exactly 3 arguments (session-id, issue-id, marker-content), got " + args.length + ". " +
   "Usage: write-session-marker <session-id> <issue-id> <marker-content>"`
- `args[0]` (sessionId) is not blank; if blank, throws `IllegalArgumentException`:
  `"session-id is required as the first argument but was blank. " +
   "Usage: write-session-marker <session-id> <issue-id> <marker-content>"`
- `args[1]` (issueId) is not blank; if blank, throws `IllegalArgumentException`:
  `"issue-id is required as the second argument but was blank. " +
   "Usage: write-session-marker <session-id> <issue-id> <marker-content>"`
- `args[2]` (markerContent) is not null; empty string is valid content.

Path construction using `GetSkill.resolveAndValidateContainment()`:
```java
Path baseDir = scope.getCatWorkPath().resolve("sessions").toAbsolutePath().normalize();
Path sessionDir = GetSkill.resolveAndValidateContainment(baseDir, sessionId, "session-id");
Path markerFile = sessionDir.resolve("squash-complete-" + issueId);
```

Validate `markerFile` is contained within `sessionDir` using `resolveAndValidateContainment` or a
manual check: `markerFile.toAbsolutePath().normalize().startsWith(sessionDir)`. If the issue ID would
escape the session directory (path traversal), throw `IllegalArgumentException`.

Then:
```java
Files.createDirectories(sessionDir);
Files.writeString(markerFile, markerContent, UTF_8);
return "";
```

Returns empty string (no user-visible output needed — the marker file is the side effect).

**`main()` method** — follows the nested-try pattern (note: GetFile.java uses an older pattern without a `run()` helper; WriteSessionMarker uses the newer pattern below):

```java
public static void main(String[] args)
{
  try (MainJvmScope scope = new MainJvmScope())
  {
    try
    {
      run(scope, args, System.out);
    }
    catch (RuntimeException | AssertionError e)
    {
      Logger log = LoggerFactory.getLogger(WriteSessionMarker.class);
      log.error("Unexpected error", e);
      System.out.println(new HookOutput(scope).block(
        Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
    }
  }
}

public static void run(JvmScope scope, String[] args, PrintStream out) throws IOException
{
  requireThat(scope, "scope").isNotNull();
  requireThat(args, "args").isNotNull();
  requireThat(out, "out").isNotNull();
  String output = new WriteSessionMarker(scope).getOutput(args);
  if (!output.isEmpty())
    out.print(output);
}
```

**Imports required:**
- `io.github.cowwoc.cat.hooks.HookOutput`
- `io.github.cowwoc.cat.hooks.JvmScope`
- `io.github.cowwoc.cat.hooks.MainJvmScope`
- `java.io.IOException`
- `java.io.PrintStream`
- `java.nio.file.Files`
- `java.nio.file.Path`
- `java.util.Objects`
- `org.slf4j.Logger`
- `org.slf4j.LoggerFactory`
- `static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat`
- `static java.nio.charset.StandardCharsets.UTF_8`

### Step 2: Register in build-jlink.sh

In `client/build-jlink.sh`, add the following entry to the `HANDLERS` array (after the `record-learning`
entry at line ~88):

```bash
"write-session-marker:util.WriteSessionMarker"
```

### Step 3: Create WriteSessionMarkerTest.java

Create test file
`client/src/test/java/io/github/cowwoc/cat/hooks/test/WriteSessionMarkerTest.java`:

- Package: `io.github.cowwoc.cat.hooks.test`
- Test framework: TestNG
- License header: standard CAT Commercial License HTML comment block (at top of file)
- Use `TestJvmScope(tempDir, tempDir)` (not `MainJvmScope`)

**Test setup pattern** — every test that needs file I/O must follow this exact structure:
```java
Path tempDir = Files.createTempDirectory("write-session-marker-test");
try (JvmScope scope = new TestJvmScope(tempDir, tempDir))
{
  // ... test body ...
}
finally
{
  TestUtils.deleteDirectoryRecursively(tempDir);
}
```
`catWorkPath` resolves to `tempDir.resolve(".cat").resolve("work")` because `TestJvmScope(claudeProjectPath,
claudePluginRoot)` uses `claudeProjectPath` as the project root and `getCatWorkPath()` returns
`projectPath.resolve(".cat").resolve("work")`.

Tests to include:

1. **`writesMarkerFileWithCorrectContent()`** — happy path:
   - Creates temp dir and `TestJvmScope(tempDir, tempDir)` wrapped in try-with-resources + finally cleanup
   - Calls `new WriteSessionMarker(scope).getOutput(new String[]{"session-abc123", "2.1-fix-foo",
     "squashed:abc123def"})`
   - Asserts file `tempDir.resolve(".cat/work/sessions/session-abc123/squash-complete-2.1-fix-foo")`
     exists
   - Asserts file content equals `"squashed:abc123def"`

2. **`createsSessionDirectoryWhenAbsent()`** — directory creation:
   - Creates temp dir and `TestJvmScope(tempDir, tempDir)` wrapped in try-with-resources + finally cleanup
   - Asserts `tempDir.resolve(".cat/work/sessions/session-abc123")` does not exist before the call
   - Calls `getOutput(...)`, then asserts directory was created

3. **`rejectsWhenArgCountIsNotThree()`** — too few args:
   - `@Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp =
     ".*3 arguments.*")`
   - Creates `TestJvmScope()` (no-arg constructor, no file I/O needed) inside try-with-resources
   - Calls `new WriteSessionMarker(scope).getOutput(new String[]{"session-id", "issue-id"})`

4. **`rejectsBlankSessionId()`**:
   - `@Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp =
     ".*session-id.*blank.*")`
   - Creates `TestJvmScope()` (no-arg constructor) inside try-with-resources
   - Calls `new WriteSessionMarker(scope).getOutput(new String[]{"", "issue-id", "content"})`

5. **`rejectsBlankIssueId()`**:
   - `@Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp =
     ".*issue-id.*blank.*")`
   - Creates `TestJvmScope()` (no-arg constructor) inside try-with-resources
   - Calls `new WriteSessionMarker(scope).getOutput(new String[]{"session-id", "", "content"})`

6. **`acceptsEmptyMarkerContent()`** — empty string is valid:
   - Creates temp dir and `TestJvmScope(tempDir, tempDir)` wrapped in try-with-resources + finally cleanup
   - Calls `new WriteSessionMarker(scope).getOutput(new String[]{"session-id", "issue-id", ""})`
   - Asserts file exists with empty content (`Files.readString(...).isEmpty()` is true)

### Step 4: Update work-merge-agent/first-use.md

In `plugin/skills/work-merge-agent/first-use.md`, locate the squash marker code block:

```bash
SQUASH_MARKER_DIR="${CLAUDE_PROJECT_DIR}/.cat/work/sessions/${CLAUDE_SESSION_ID}"
mkdir -p "${SQUASH_MARKER_DIR}"
SQUASH_COMMIT_HASH=$(cd "${WORKTREE_PATH}" && git rev-parse HEAD)
echo "squashed:${SQUASH_COMMIT_HASH}" > "${SQUASH_MARKER_DIR}/squash-complete-${ISSUE_ID}"
```

Replace it with:

```bash
SQUASH_COMMIT_HASH=$(cd "${WORKTREE_PATH}" && git rev-parse HEAD)
"${CLAUDE_PLUGIN_ROOT}/client/bin/write-session-marker" "${CLAUDE_SESSION_ID}" "${ISSUE_ID}" "squashed:${SQUASH_COMMIT_HASH}"
```

### Step 5: Run Tests

Run the full Maven test suite to verify no regressions:
```bash
mvn -f client/pom.xml test
```

All tests must pass.

## Post-conditions

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WriteSessionMarker.java` exists with proper
  license header, correct package, constructor validation, `getOutput()` logic, and `main()`/`run()`
  methods
- `client/build-jlink.sh` HANDLERS array contains `"write-session-marker:util.WriteSessionMarker"`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WriteSessionMarkerTest.java` exists with 6
  tests covering happy path, directory creation, and argument validation
- `plugin/skills/work-merge-agent/first-use.md` squash marker block uses
  `write-session-marker` instead of `mkdir` + bash redirect
- `mvn -f client/pom.xml test` exits 0 with all tests passing
- `write-session-marker` binary is accessible in the jlink image after rebuilding
