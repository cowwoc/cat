# Claude Hook Guidance

## Registration Locations

| Hook Type | Registration Location |
|-----------|----------------------|
| **Project hooks** | `.claude/settings.json` |
| **Claude plugin hooks** | `client/plugin/hooks/claude/hooks.json` |

Project hooks are project-specific behavior and custom validation. Plugin hooks are pre-registered in
`client/plugin/hooks/claude/hooks.json`.

Do not attempt to register plugin hooks in `.claude/settings.json`; they are already registered by the plugin.

## Matcher Field Bug

To match all tools, omit the `matcher` field entirely. Do not use `"matcher": ""` — despite the docs claiming an empty
string matches all, it silently fails to match anything (empirically verified 2026-02-19).

```json
// Correct: omit matcher entirely
{ "hooks": [{ "type": "command", "command": "my-hook.sh" }] }

// Wrong: empty string does not match
{ "matcher": "", "hooks": [{ "type": "command", "command": "my-hook.sh" }] }
```

## Hook Handlers

Hook handlers are invoked directly by Claude Code's hook execution engine. They must produce Claude Code's hook JSON
format via `ClaudeHook`. Claude Code's hook engine parses this format.

Standard hook JSON output fields include:
- `decision` (string) — e.g., `"block"` to indicate a blocked operation
- `reason` (string) — human-readable explanation of the decision
- `continue` (bool) — whether processing should continue
- `stopReason` (string) — reason for stopping
- `suppressOutput` (bool) — whether to suppress output
- `systemMessage` (string) — message for the system context

Exit code 0 tells Claude Code to parse stdout as JSON. Non-zero exit codes cause stderr to be fed to Claude as plain
text, losing the structured error.

**Pattern for expected errors in hook handlers:**

```java
catch (IOException e)
{
  ClaudeHook hookOutput = new ClaudeHook(scope);
  System.out.println(hookOutput.block(e.getMessage()));
  System.exit(0);
}
```

Do not emit custom JSON to stderr with exit 1 from a Claude hook handler.

## Skill CLI Tools

Skill CLI tools are invoked by skill scripts. Their output is parsed by the skill itself, not by Claude Code's hook
engine. These tools may use a business-format JSON schema that the skill Markdown defines and parses.

`{"status":"ERROR","message":"..."}` is correct for skill CLI tools when the skill parser reads `status` and
`message` fields directly. `ClaudeHook.block()` would produce `{"decision":"block",...}` which skill parsers do not
recognize.

## Unexpected Errors

Unexpected errors in `main()` must be caught, logged, and converted to a `ClaudeHook.block()` response on stdout when
the scope is available. They must not be rethrown, as non-zero exit or uncaught exceptions prevent Claude Code from
parsing the JSON response.

Scope initialization itself can throw. When scope creation is wrapped in try-with-resources, an outer catch block
handles failures that occur before the scope is available. Since scope services like `ClaudeHook` are unavailable in
the outer catch, use stderr or plain-text stdout as a fallback.

## Project Hooks

Project hooks in `.claude/settings.json` must follow the common hook guidance pattern if they block or warn operations.
An empty hooks object requires no changes. Any future project hooks added to `.claude/settings.json` must include
actionable guidance in their block/warn messages.
