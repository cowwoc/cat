# Plan

## Problem

StatuslineCommand.main() does not catch AssertionError thrown during resource initialization
(`new MainClaudeStatusline(System.in)`). The inner try/catch blocks (which catch `RuntimeException | AssertionError`)
are inside the try-with-resources scope — they do not cover exceptions thrown during resource initialization. The
uncaught AssertionError propagates to the JVM, which prints the stack trace to stderr and exits non-zero. Claude Code
only displays stdout for statusline commands, so the error is invisible to users — the statusline silently fails.

## Expected vs Actual

- **Expected:** When CLAUDE_PLUGIN_ROOT is unset, the statusline command outputs a warning on stdout
  (e.g., `⚠ CLAUDE_PLUGIN_ROOT is not set`) and exits with code 0.
- **Actual:** An AssertionError stack trace is printed to stderr (invisible to user) and the process exits with code 1.

## Root Cause

The `MainClaudeStatusline` constructor calls `getEnvVar("CLAUDE_PLUGIN_ROOT")` which throws `AssertionError` when the
env var is absent. This error is thrown in the try-with-resources resource expression, outside the inner try/catch.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Minimal — adding a catch block around existing code
- **Mitigation:** Existing tests verify normal operation; E2E verification covers error path

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/StatuslineCommand.java` — Add outer catch for
  `RuntimeException | AssertionError` around the try-with-resources in `main()`
- `.cat/issues/v2/v2.1/fix-statusline-uncaught-init-error/index.json` — Update status to `closed`

## Pre-conditions

(none)

## Jobs

### Job 1: Add outer catch to main()

- Read `client/src/main/java/io/github/cowwoc/cat/hooks/util/StatuslineCommand.java` lines 335-360 (the `main()`
  method)
- Wrap the entire try-with-resources in an additional try/catch for `RuntimeException | AssertionError`. The final
  structure of `main()` must be:
  ```java
  public static void main(String[] args)
  {
      try
      {
          try (ClaudeStatusline scope = new MainClaudeStatusline(System.in))
          {
              try
              {
                  run(scope, args, System.out);
              }
              catch (IllegalArgumentException | IOException e)
              {
                  System.out.println("⚠ " + Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
              }
              catch (RuntimeException | AssertionError e)
              {
                  Logger log = LoggerFactory.getLogger(StatuslineCommand.class);
                  log.error("Unexpected error", e);
                  System.out.println("⚠ " + Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
              }
          }
          catch (IOException e)
          {
              Logger log = LoggerFactory.getLogger(StatuslineCommand.class);
              log.error("Failed to read statusline input", e);
              System.out.println("⚠ " + Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
          }
      }
      catch (RuntimeException | AssertionError e)
      {
          Logger log = LoggerFactory.getLogger(StatuslineCommand.class);
          log.error("Failed to initialize statusline", e);
          System.out.println("⚠ " + Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
      }
  }
  ```

### Job 2: Verify and run tests

The outer catch in `main()` catches errors thrown during `new MainClaudeStatusline(System.in)` resource
initialization. Since `main()` hardcodes the `MainClaudeStatusline` constructor (which reads env vars via
`System.getenv`), the outer catch cannot be unit-tested through `run()`. The `TestClaudeStatusline` default
constructor initializes all fields to safe defaults ("unknown", Duration.ZERO, 0), so calling `run()` with a
default scope does not throw. No new unit test is needed for the outer catch -- the fix is a structural
wrapping of existing code, and correctness is verified by the E2E post-condition.

- Run `mvn -f client/pom.xml verify -e` to verify all existing tests still pass and linters are clean

### Job 3: Close the issue

- Update `.cat/issues/v2/v2.1/fix-statusline-uncaught-init-error/index.json`: change `"status" : "in-progress"`
  to `"status" : "closed"`

## Post-conditions

- [ ] Bug fixed: AssertionError during resource initialization is caught and handled, producing a user-visible
  warning on stdout (prefixed with `⚠ `) instead of an invisible stack trace on stderr
- [ ] No regressions: `mvn -f client/pom.xml verify -e` passes (exit code 0)
- [ ] E2E verification: Run the statusline command without CLAUDE_PLUGIN_ROOT set and verify a user-visible
  warning appears on stdout instead of an invisible stack trace on stderr
