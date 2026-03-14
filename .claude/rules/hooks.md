# Hook Registration Locations (M406)

CAT uses two distinct hook registration systems. See `.cat/rules/hooks.md` for full documentation
(injected via CAT hooks for the main agent).

| Hook Type | Registration Location |
|-----------|----------------------|
| **Project hooks** | `.claude/settings.json` |
| **Plugin hooks** | `plugin/hooks/hooks.json` |

## CLI Tool Output Convention

CLI tools invoked by skills (via `main()` methods) must:

1. Write ALL structured JSON output to `System.out` — including error responses
2. Exit with code 0 so Claude Code parses stdout as JSON
3. Use the standard Claude Code hook JSON output format via `HookOutput` utility

`System.err` is reserved for non-structured diagnostics (e.g., debug logging, stack traces during development) that are
not intended for skill consumption.

**Standard hook JSON output fields:**
- `decision` (string) — e.g., `"block"` to indicate a blocked operation
- `reason` (string) — human-readable explanation of the decision
- `continue` (bool) — whether processing should continue
- `stopReason` (string) — reason for stopping
- `suppressOutput` (bool) — whether to suppress output
- `systemMessage` (string) — message for the system context

**Why:** Skills parse JSON from stdout. Exit code 0 tells Claude Code to parse stdout as JSON. Non-zero exit codes
cause stderr to be fed to Claude as plain text, losing the structured error. Custom JSON formats like
`{"status":"ERROR","message":"..."}` are not recognized by Claude Code's hook output parsing.

**Pattern for expected errors (IOException, IllegalArgumentException):**
```java
// Good — use HookOutput, write to stdout, exit 0
catch (IOException e)
{
  HookOutput hookOutput = new HookOutput(scope);
  System.out.println(hookOutput.block(e.getMessage()));
  System.exit(0);
}

// Avoid — custom JSON format to stderr with exit 1
catch (IOException e)
{
  System.err.println("""
    {
      "status": "ERROR",
      "message": "%s"
    }""".formatted(e.getMessage().replace("\"", "\\\"")));
  System.exit(1);
}
```

**Pattern for unexpected errors (RuntimeException | AssertionError) in `main()`:**

Unexpected errors in `main()` must be caught, logged, and converted to a `HookOutput.block()` response on stdout.
They must NOT be rethrown, as non-zero exit or uncaught exceptions prevent Claude Code from parsing the JSON response.

```java
public static void main(String[] args)
{
  try (MainJvmScope scope = new MainJvmScope())
  {
    run(scope, args, System.out);
  }
  catch (RuntimeException | AssertionError e)
  {
    Logger log = LoggerFactory.getLogger(ClassName.class);
    log.error("Unexpected error", e);
    try (MainJvmScope errorScope = new MainJvmScope())
    {
      System.out.println(new HookOutput(errorScope).block(
        Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
    }
  }
}
```
