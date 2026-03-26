# Plan

## Goal

Fix preprocessor error to show full stacktrace instead of first line only. When a preprocessor directive fails,
the error message currently shows only the exception class and method (e.g.,
`io.github.cowwoc.cat.hooks.skills.GetOutput.<init>()`). It should show the complete stack trace including all
frames and cause chains to aid debugging.

## Research Findings

### Root Cause

In `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java`, the method `invokeSkillOutput()`
(around lines 650-692) catches exceptions and extracts only `e.getMessage()` or falls back to
`e.getClass().getName()`. For `NoSuchMethodException`, `getMessage()` returns just the constructor signature
(e.g., `io.github.cowwoc.cat.hooks.skills.GetOutput.<init>()`), which is not human-readable.

The `buildPreprocessorErrorMessage()` method (around lines 704-720) formats the `**Preprocessor Error**`
block shown to agents. It currently includes only the short error message, not the full stack trace.

Logback is configured to write to `System.err` only (`client/src/main/resources/logback.xml`). Since the
binary runs as a subprocess, stderr is discarded — logging is useless for this error.

### Fix Approach

1. Capture the full stack trace via `StringWriter`/`e.printStackTrace(pw)` in both catch blocks in
   `invokeSkillOutput()`.
2. Update `buildPreprocessorErrorMessage()` to accept the stack trace string and include it as a
   `**Stack Trace:**` section in the `**Preprocessor Error**` block.
3. Use `e.toString()` instead of `e.getMessage()` as the primary error line (includes exception type).
4. For `InvocationTargetException`, unwrap to the cause first, then capture its stack trace.

### Key File

`client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java`

Current catch blocks in `invokeSkillOutput()`:
```java
catch (InvocationTargetException e)
{
    Throwable cause = e.getCause();
    if (cause == null)
        cause = e;
    String errorMsg = cause.getMessage();
    if (errorMsg == null)
        errorMsg = cause.getClass().getName();
    return buildPreprocessorErrorMessage(originalDirective, errorMsg);
}
catch (Exception e)
{
    String errorMsg = e.getMessage();
    if (errorMsg == null)
        errorMsg = e.getClass().getName();
    return buildPreprocessorErrorMessage(originalDirective, errorMsg);
}
```

Current `buildPreprocessorErrorMessage()`:
```java
private static String buildPreprocessorErrorMessage(String originalDirective, String errorMsg)
{
    return """
        ---
        **Preprocessor Error**

        A preprocessor directive failed while loading this skill.

        **Directive:** `%s`
        **Error:** %s

        To report this bug, run: `/cat:feedback`
        ---
        """.formatted(originalDirective, errorMsg);
}
```

### Updated Code

Updated catch blocks in `invokeSkillOutput()`:
```java
catch (InvocationTargetException e)
{
    Throwable cause = e.getCause();
    if (cause == null)
        cause = e;
    StringWriter sw = new StringWriter();
    cause.printStackTrace(new PrintWriter(sw));
    return buildPreprocessorErrorMessage(originalDirective, cause.toString(), sw.toString());
}
catch (Exception e)
{
    StringWriter sw = new StringWriter();
    e.printStackTrace(new PrintWriter(sw));
    return buildPreprocessorErrorMessage(originalDirective, e.toString(), sw.toString());
}
```

Updated `buildPreprocessorErrorMessage()`:
```java
private static String buildPreprocessorErrorMessage(String originalDirective, String errorMsg,
    String stackTrace)
{
    return """
        ---
        **Preprocessor Error**

        A preprocessor directive failed while loading this skill.

        **Directive:** `%s`
        **Error:** %s

        **Stack Trace:**
        ```
        %s
        ```

        To report this bug, run: `/cat:feedback`
        ---
        """.formatted(originalDirective, errorMsg, stackTrace.stripTrailing());
}
```

### Required Imports

Ensure these imports exist in `GetSkill.java`:
- `java.io.PrintWriter`
- `java.io.StringWriter`

### Test Location

Add regression test in:
`client/src/test/java/io/github/cowwoc/cat/hooks/util/GetSkillTest.java`

If that file doesn't exist, look for related test files near
`client/src/test/java/io/github/cowwoc/cat/hooks/` and add to the most appropriate one, or create
`GetSkillTest.java` with a license header.

The test should:
1. Simulate a failed directive invocation (or call `buildPreprocessorErrorMessage` directly if it's
   accessible, otherwise test via `invokeSkillOutput` at a higher level).
2. Assert the returned string contains a `**Stack Trace:**` section.
3. Assert the stack trace section is non-empty (contains at least one frame).

## Pre-conditions

(none)

## Post-conditions

- [ ] Bug fixed: preprocessor errors display the full stack trace, including all frames and cause chains
- [ ] Regression test added: test verifies full stacktrace is included in the error output
- [ ] No new issues introduced
- [ ] All existing tests pass (`mvn -f client/pom.xml test`)

## Sub-Agent Waves

### Wave 1

- Implement fix in `GetSkill.java` and add regression test

  Steps:
  1. Read `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java` to find exact line
     numbers and current code for `invokeSkillOutput()` and `buildPreprocessorErrorMessage()`.
  2. Add `java.io.PrintWriter` and `java.io.StringWriter` imports if not already present.
  3. Update the `InvocationTargetException` catch block: replace `getMessage()`/`getClass().getName()`
     logic with `StringWriter`/`PrintWriter` stack capture using `cause.toString()` as error string.
  4. Update the `Exception` catch block: replace `getMessage()`/`getClass().getName()` logic with
     `StringWriter`/`PrintWriter` stack capture using `e.toString()` as error string.
  5. Update `buildPreprocessorErrorMessage()` signature to accept `String stackTrace` as third
     parameter and include `**Stack Trace:**` section in the formatted output.
  6. Check whether `client/src/test/java/io/github/cowwoc/cat/hooks/util/GetSkillTest.java` exists.
     - If it exists, add the new test to that file.
     - If it does not exist, create it with a license header (Java block-comment format per
       `.claude/rules/license-header.md`) and a TestNG class skeleton. Do NOT search for other
       nearby test files — always use this exact path.
  7. Add a test that triggers a preprocessor error and asserts:
     - The returned string contains `**Stack Trace:**`
     - The stack trace section is non-empty (contains at least one frame line, i.e., `at `)
     - If `buildPreprocessorErrorMessage` is `private`, make it package-private (remove `private`,
       no access modifier) so the test class in the same package can call it directly. Do NOT use
       reflection.
  8. Run `mvn -f client/pom.xml test` — all tests must pass.
  9. Update `.cat/issues/v2/v2.1/fix-preprocessor-error-stacktrace/index.json` — set
     `"status": "closed"`.
  10. Commit all changes with message:
      `bugfix: show full stack trace in preprocessor error block`
