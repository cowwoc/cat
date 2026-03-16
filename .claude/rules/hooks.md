# Hook Registration Locations (M406)

CAT uses two distinct hook registration systems. See `.cat/rules/hooks.md` for full documentation
(injected via CAT hooks for the main agent).

| Hook Type | Registration Location |
|-----------|----------------------|
| **Project hooks** | `.claude/settings.json` |
| **Plugin hooks** | `plugin/hooks/hooks.json` |

## Two Categories of CLI Tool Output

CAT has two categories of Java CLI tools, each with a different output contract:

### Hook Handlers (PreToolUse, PostToolUse, etc.)

Hook handlers are invoked directly by Claude Code's hook execution engine. They must produce
Claude Code's hook JSON format via `HookOutput`. Claude Code's hook engine parses this format.

**Standard hook JSON output fields:**
- `decision` (string) — e.g., `"block"` to indicate a blocked operation
- `reason` (string) — human-readable explanation of the decision
- `continue` (bool) — whether processing should continue
- `stopReason` (string) — reason for stopping
- `suppressOutput` (bool) — whether to suppress output
- `systemMessage` (string) — message for the system context

**Why:** Skills parse JSON from stdout. Exit code 0 tells Claude Code to parse stdout as JSON. Non-zero exit codes
cause stderr to be fed to Claude as plain text, losing the structured error.

**Pattern for expected errors (IOException, IllegalArgumentException) in hook handlers:**
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

### Skill CLI Tools (invoked by skills via `main()`)

Skill CLI tools are invoked by skill scripts (e.g., `work-prepare-output`). Their output is parsed
by the **skill itself**, not by Claude Code's hook engine. These tools may use a business-format JSON
schema (e.g., `{"status":"...", "message":"..."}`) that the skill Markdown defines and parses.

`{"status":"ERROR","message":"..."}` is correct for skill CLI tools — the skill parser reads `status`
and `message` fields directly. `HookOutput.block()` would produce `{"decision":"block",...}` which
skill parsers do not recognize.

**Pattern for expected errors (IOException) in skill CLI tools:**
```java
catch (IOException e)
{
  // Use business-format JSON (status + message) because the skill parses this output directly,
  // not via Claude Code's hook output parser.
  String message = Objects.toString(e.getMessage(), e.getClass().getSimpleName());
  out.println(WorkPrepare.toErrorJson(scope, message));
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
    try
    {
      run(scope, args, System.out);
    }
    catch (RuntimeException | AssertionError e)
    {
      Logger log = LoggerFactory.getLogger(ClassName.class);
      log.error("Unexpected error", e);
      System.out.println(new HookOutput(scope).block(
        Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
    }
  }
}
```
