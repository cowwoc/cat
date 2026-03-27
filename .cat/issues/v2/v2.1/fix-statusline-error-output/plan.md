# Plan

## Problem

StatuslineCommand.main() catches errors and calls `block(scope, message)` from `Strings.block()`, which outputs Claude
Code hook JSON format (`{"decision":"block",...}`). But StatuslineCommand is NOT a hook handler — it is a CLI tool whose
stdout is displayed directly by Claude Code as the statusline. Per the Claude Code docs, "Scripts that exit with
non-zero codes or produce no output cause the status line to go blank." The error handling should output plain text and
always exit with code 0.

## Parent Requirements

None

## Reproduction Code

```java
// In StatuslineCommand.main(), when an IOException is caught:
catch (IllegalArgumentException | IOException e)
{
  System.out.println(block(scope,
    Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
}
// This outputs: {"decision":"block","reason":"...","continue":false,...}
// But Claude Code displays this JSON literally in the statusline
```

## Expected vs Actual

- **Expected:** On error, statusline shows a plain-text error indicator like `⚠ Error: message`
- **Actual:** On error, statusline shows raw hook JSON `{"decision":"block","reason":"...",...}`

## Root Cause

StatuslineCommand.main() error handling uses `Strings.block()` which produces hook JSON format. This method is designed
for hook handlers (PreToolUse, PostToolUse), not for CLI tools whose output is displayed directly. The statusline
command needs plain-text error output with exit code 0.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** The normal (non-error) path is unchanged. Only error handling changes.
- **Mitigation:** Tests verify both normal and error output paths.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/StatuslineCommand.java` — Change main() catch blocks to
  output plain text error indicators instead of hook JSON, and remove the `import static` of `Strings.block`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/StatuslineCommandMainTest.java` — Add tests verifying error
  output is plain text (not JSON) and that `run()` with unexpected args throws IllegalArgumentException

## Test Cases

- [ ] Expected error (IllegalArgumentException with unexpected args) — verify run() throws exception
- [ ] Normal operation — still works (existing tests cover this)
- [ ] Error output format — plain text, not JSON

## Pre-conditions

(none)

## Sub-Agent Waves

### Wave 1

- In `StatuslineCommand.java`, modify `main()` to replace hook JSON error output with plain-text error indicators:
  - Remove `import static io.github.cowwoc.cat.hooks.Strings.block;`
  - In the `catch (IllegalArgumentException | IOException e)` block, replace:
    ```java
    System.out.println(block(scope,
      Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
    ```
    with:
    ```java
    System.out.println("⚠ " + Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
    ```
  - In the `catch (RuntimeException | AssertionError e)` block, keep the logger line, but replace:
    ```java
    System.out.println(block(scope,
      Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
    ```
    with:
    ```java
    System.out.println("⚠ " + Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
    ```
  - The outermost `catch (IOException e)` block (for scope initialization failure) should also output plain text
    instead of just logging — add `System.out.println("⚠ " + Objects.toString(e.getMessage(), e.getClass().getSimpleName()));`
    so the statusline shows something rather than going blank
- In `StatuslineCommandMainTest.java`, add a test that verifies `run()` with unexpected arguments throws
  `IllegalArgumentException`:
  - Test name: `unexpectedArgsThrowsException`
  - Call `StatuslineCommand.run(scope, new String[]{"unexpected"}, printStream)` and verify it throws
    `IllegalArgumentException` with message containing "Unexpected arguments"
- Run `mvn -f client/pom.xml verify -e` to confirm all tests pass
- Commit type: `bugfix:`
- Update index.json: status to "closed", progress to 100%

## Post-conditions

- [ ] Bug fixed: StatuslineCommand error handling outputs plain text to stdout instead of hook JSON
- [ ] Regression test added: Tests verify error output format is plain text, not JSON
- [ ] No new issues: Error handling change doesn't break normal statusline output
- [ ] E2E verification: Run statusline-command with invalid input and confirm plain-text error output is displayed
