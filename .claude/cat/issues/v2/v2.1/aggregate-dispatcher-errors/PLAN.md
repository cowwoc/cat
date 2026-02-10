# Plan: aggregate-dispatcher-errors

## Problem
`GetSessionStartOutput` catches handler exceptions (line 116-120) and prints them to stderr, but stderr from hooks is
not visible to the agent. This means handler failures like `InjectEnv` throwing
`AssertionError("CLAUDE_SESSION_ID is not set")` are silently lost — neither the user nor the agent sees them.

The current catch-and-log pattern:
```java
catch (RuntimeException | AssertionError e)
{
  stderr.println("GetSessionStartOutput: handler error (" +
    handler.getClass().getSimpleName() + "): " + e.getMessage());
}
```

**Evidence from debug hooks (2026-02-10):**
- `CLAUDE_ENV_FILE` IS provided and non-empty in SessionStart hooks
- `CLAUDE_SESSION_ID` is empty string in hook env
- InjectEnv throws AssertionError on empty CLAUDE_SESSION_ID → caught → printed to stderr → lost
- Result: no env file written, CLAUDE_PROJECT_DIR unavailable in Bash tool calls

## Satisfies
None - infrastructure/bugfix

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Must not break working handlers when one handler fails
- **Mitigation:** Handler isolation preserved; errors aggregated alongside success output

## Solution
Redesign `GetSessionStartOutput` to queue handler errors and include them in the aggregated response:

1. **Continue running all handlers** even when some fail (current behavior, preserved)
2. **Collect errors** into a list alongside successful results
3. **Include errors in additionalContext** so the agent sees them
4. **Print errors to stderr** so the user sees them in terminal (current behavior, preserved)
5. **Exit with non-zero code** when any handler failed, so Claude Code knows something went wrong

The additionalContext output includes an error section:
```
## SessionStart Handler Errors
- InjectEnv: CLAUDE_SESSION_ID is not set
```

## Files to Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/GetSessionStartOutput.java` - Collect errors, include in output
- `hooks/src/test/java/io/github/cowwoc/cat/hooks/GetSessionStartOutputTest.java` - Test error aggregation

## Acceptance Criteria
- [ ] Handler exceptions are collected, not just caught-and-logged
- [ ] Error messages appear in additionalContext (visible to agent)
- [ ] Error messages appear on stderr (visible to user in terminal)
- [ ] Working handlers still produce their output when other handlers fail
- [ ] Process exits with non-zero code when any handler failed
- [ ] All existing tests pass

## Execution Steps
1. **Modify GetSessionStartOutput.run()** to collect errors into a list
   - Replace catch block's stderr-only logging with error collection
   - After handler loop, append error section to combinedContext
   - Print collected errors to stderr
   - Track whether any handler failed
   - Files: `hooks/src/main/java/io/github/cowwoc/cat/hooks/GetSessionStartOutput.java`
2. **Update main() exit code** to return non-zero when errors occurred
   - Change `run()` to return a boolean (success/failure) or similar
   - Call `System.exit(1)` when any handler failed
   - Files: `hooks/src/main/java/io/github/cowwoc/cat/hooks/GetSessionStartOutput.java`
3. **Add/update tests** for error aggregation behavior
   - Test: failing handler error appears in additionalContext
   - Test: failing handler error appears on stderr
   - Test: other handlers still produce output when one fails
   - Test: all handlers succeed produces no error section
   - Files: `hooks/src/test/java/io/github/cowwoc/cat/hooks/GetSessionStartOutputTest.java`
4. **Run tests** to verify no regressions
   - `mvn -f hooks/pom.xml test`
