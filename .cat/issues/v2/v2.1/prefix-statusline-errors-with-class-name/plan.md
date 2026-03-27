# Plan

## Goal

Prefix statusline error messages with the originating class name so that when an error is displayed, the user can
immediately identify which class produced the error.

## Pre-conditions

(none)

## Post-conditions

- [ ] Error message semantics preserved (same errors reported, class name prefix added)
- [ ] All statusline error messages include the originating class name as a prefix
- [ ] Tests passing, no regressions
- [ ] Code quality improved (consistent error message format across statusline classes)
- [ ] E2E verification: Trigger a statusline error condition and confirm the message includes the originating class
      name as a prefix

## Research Findings

### Current error message patterns

Three classes produce statusline error messages. Each uses a different format:

**`StatuslineCommand.java`** (lines 335-364, `main()` method):
- `catch (IllegalArgumentException | IOException e)` at line 345-348: outputs
  `"⚠ " + Objects.toString(e.getMessage(), e.getClass().getSimpleName())` — no originating class prefix
- `catch (RuntimeException | AssertionError e)` at line 349-352: delegates to `printError()` which outputs
  `"⚠ " + Objects.toString(throwable.getMessage(), throwable.getClass().getSimpleName())` — no originating class
  prefix
- `catch (IOException e)` at line 354-357 (scope creation): delegates to `printError()` — same format, no prefix
- Outer `catch (RuntimeException | AssertionError e)` at line 360-363 (scope init failure): delegates to
  `printError()` — same format, no prefix
- `getActiveIssue()` at line 203-207: outputs
  `"⚠ " + e.getClass().getSimpleName() + ": " + e.getMessage()` — uses **exception** class name, not originating
  class name

**`GetStatuslineOutput.java`** (lines 127-148, `main()` method):
- `catch (IllegalArgumentException | IOException e)` at line 135-139: outputs via `block(scope, ...)` with
  `Objects.toString(e.getMessage(), e.getClass().getSimpleName())` — no originating class prefix
- `catch (RuntimeException | AssertionError e)` at line 140-146: same pattern via `block(scope, ...)` — no prefix
- `getOutput()` method at lines 79-87 and 94-102: returns JSON error with messages like
  `"Failed to read settings.json: ..."` and `"Invalid JSON in settings.json: ..."` — no prefix (these are
  business-format JSON, not statusline display messages)

**`StatuslineInstall.java`** (lines 182-203, `main()` method):
- `catch (IllegalArgumentException e)` at line 191-193: outputs via `block(scope, ...)` — no originating class prefix
- `catch (RuntimeException | AssertionError e)` at line 195-200: outputs via `block(scope, ...)` — no prefix
- `run()` method at line 243-244, `catch (IOException e)`: outputs via `block(scope, ...)` — no prefix
- `buildError()` at lines 167-175: returns JSON error format `{"status":"ERROR","message":"..."}` — these are
  business-format JSON responses, not statusline display messages; the skill parses these

### Scope of changes

Only `main()` and `run()` catch blocks that produce user-visible error output need the class name prefix. The
business-format JSON error responses in `getOutput()` and `buildError()` are parsed by skill Markdown, not displayed
directly to users, so they do not need the prefix.

The `getActiveIssue()` method in `StatuslineCommand` already prefixes errors with the exception class name
(`e.getClass().getSimpleName()`), but the issue requires the **originating** class name (`StatuslineCommand`), not
the exception class name.

### Consistent format

The target format for all user-visible error messages is: `ClassName: descriptive message`

For `StatuslineCommand.main()` which outputs to stdout directly (not via `block()`), the format is:
`⚠ StatuslineCommand: descriptive message`

For `GetStatuslineOutput.main()` and `StatuslineInstall.main()`/`run()` which output via `Strings.block()`, the
format is: `GetStatuslineOutput: descriptive message` or `StatuslineInstall: descriptive message` (the `block()`
method wraps this in hook JSON).

## Files to Modify

### 1. `client/src/main/java/io/github/cowwoc/cat/hooks/util/StatuslineCommand.java`

**`main()` catch blocks (lines 345-363):**

Before (line 347):
```java
System.out.println("⚠ " + Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
```
After:
```java
System.out.println("⚠ StatuslineCommand: " + Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
```

Before (`printError()` method, line 376):
```java
System.out.println("⚠ " + Objects.toString(throwable.getMessage(), throwable.getClass().getSimpleName()));
```
After:
```java
System.out.println("⚠ StatuslineCommand: " + Objects.toString(throwable.getMessage(), throwable.getClass().getSimpleName()));
```

**`getActiveIssue()` catch block (line 205):**

Before:
```java
String errorMsg = "⚠ " + e.getClass().getSimpleName() + ": " + e.getMessage();
```
After:
```java
String errorMsg = "⚠ StatuslineCommand: " + e.getMessage();
```

Also update the Javadoc for `getActiveIssue()` (line 162) to reflect the new format: change
`{@code "⚠ <ExceptionClass>: <message>"}` to `{@code "⚠ StatuslineCommand: <message>"}`.

### 2. `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetStatuslineOutput.java`

**`main()` catch blocks (lines 135-146):**

Before (line 137-138):
```java
System.out.println(block(scope,
  Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
```
After:
```java
System.out.println(block(scope,
  "GetStatuslineOutput: " + Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
```

Before (line 144-145):
```java
System.out.println(block(scope,
  Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
```
After:
```java
System.out.println(block(scope,
  "GetStatuslineOutput: " + Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
```

### 3. `client/src/main/java/io/github/cowwoc/cat/hooks/util/StatuslineInstall.java`

**`main()` catch blocks (lines 191-200):**

Before (line 192-193):
```java
System.out.println(block(scope,
  Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
```
After:
```java
System.out.println(block(scope,
  "StatuslineInstall: " + Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
```

Before (line 199-200):
```java
System.out.println(block(scope,
  Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
```
After:
```java
System.out.println(block(scope,
  "StatuslineInstall: " + Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
```

**`run()` catch block (line 244):**

Before:
```java
out.println(block(scope, Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
```
After:
```java
out.println(block(scope, "StatuslineInstall: " + Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
```

### 4. Test files (update expected error message patterns)

- `client/src/test/java/io/github/cowwoc/cat/hooks/test/StatuslineCommandTest.java` — update any assertions on
  `getActiveIssue()` error output to expect `"⚠ StatuslineCommand: "` prefix instead of
  `"⚠ " + exceptionClassName + ": "`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/StatuslineCommandMainTest.java` — update expected error
  message patterns to include `StatuslineCommand:` prefix
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetStatuslineOutputMainTest.java` — update expected error
  message patterns to include `GetStatuslineOutput:` prefix
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/StatuslineInstallMainTest.java` — update expected error
  message patterns to include `StatuslineInstall:` prefix
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/StatuslineInstallTest.java` — update expected error
  message patterns in `run()` tests to include `StatuslineInstall:` prefix

## Jobs

### Job 1: Write/update tests (TDD - tests first)

1. Read all five test files to understand current assertions
2. Update `StatuslineCommandTest` assertions for `getActiveIssue()` to expect
   `"⚠ StatuslineCommand: "` prefix
3. Update `StatuslineCommandMainTest` assertions to expect `"⚠ StatuslineCommand: "` prefix
4. Update `GetStatuslineOutputMainTest` assertions to expect `"GetStatuslineOutput: "` prefix in block output
5. Update `StatuslineInstallMainTest` assertions to expect `"StatuslineInstall: "` prefix in block output
6. Update `StatuslineInstallTest` assertions for `run()` error output to expect `"StatuslineInstall: "` prefix
7. Run `mvn -f client/pom.xml verify -e` — tests should FAIL (red phase)

### Job 2: Implement class name prefixes

1. Update `StatuslineCommand.java`:
   - Add `"StatuslineCommand: "` prefix in `printError()` method (line 376)
   - Add `"⚠ StatuslineCommand: "` prefix in `main()` expected-error catch (line 347)
   - Change `getActiveIssue()` catch block (line 205) from exception class name to `"StatuslineCommand: "`
   - Update `getActiveIssue()` Javadoc (line 162) to document new format
2. Update `GetStatuslineOutput.java`:
   - Add `"GetStatuslineOutput: "` prefix in both `main()` catch blocks (lines 137-138 and 144-145)
3. Update `StatuslineInstall.java`:
   - Add `"StatuslineInstall: "` prefix in both `main()` catch blocks (lines 192-193 and 199-200)
   - Add `"StatuslineInstall: "` prefix in `run()` catch block (line 244)
4. Run `mvn -f client/pom.xml verify -e` — all tests should PASS (green phase)

### Job 3: Fix verification failures (iteration 1)

1. Fix Checkstyle LineLength violation in `StatuslineCommand.java` line 376: break the `printError()` method's
   `System.out.println` statement across multiple lines to stay within the 128-character limit
2. Fix Checkstyle LineLength violation in `StatuslineInstall.java` line 109: break the long line to stay within
   the 128-character limit
3. Fix `getActiveIssue()` catch block (line 205 in `StatuslineCommand.java`): remove the exception class name
   (`e.getClass().getSimpleName()`) so the error message uses only the `StatuslineCommand` prefix, not both the
   class name and the exception class name
4. Update `getActiveIssue()` Javadoc (line 162 in `StatuslineCommand.java`) to reflect the new error format:
   `{@code "⚠ StatuslineCommand: <message>"}` instead of the old format with the exception class name
5. Run `mvn -f client/pom.xml verify -e` — all tests and Checkstyle must pass

## Commit Type

`bugfix:` — fixes inconsistent error message formatting across statusline classes
