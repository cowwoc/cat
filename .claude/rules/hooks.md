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
Claude Code's hook JSON format via `ClaudeHook`. Claude Code's hook engine parses this format.

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
// Good — use ClaudeHook, write to stdout, exit 0
catch (IOException e)
{
  ClaudeHook hookOutput = new ClaudeHook(scope);
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
and `message` fields directly. `ClaudeHook.block()` would produce `{"decision":"block",...}` which
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

Unexpected errors in `main()` must be caught, logged, and converted to a `ClaudeHook.block()` response on stdout.
They must NOT be rethrown, as non-zero exit or uncaught exceptions prevent Claude Code from parsing the JSON response.

Scope initialization itself can throw (e.g., missing `CLAUDE_PROJECT_DIR`). When scope creation is wrapped in
try-with-resources, an outer catch block handles failures that occur before the scope is available. Since scope
services (like `ClaudeHook`) are unavailable in the outer catch, use stderr or plain-text stdout as a fallback.

```java
public static void main(String[] args)
{
  try
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
        System.out.println(new ClaudeHook(scope).block(
          Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
      }
    }
  }
  // Scope creation failed (e.g., missing CLAUDE_PROJECT_DIR) - cannot use scope services
  catch (RuntimeException | AssertionError e)
  {
    Logger log = LoggerFactory.getLogger(ClassName.class);
    log.error("Failed to initialize scope", e);
    System.err.println("Failed to initialize scope: " +
      Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
  }
}
```

## Hook Output Guidance

**MANDATORY**: Every hook that blocks or warns an operation MUST include actionable guidance explaining what the
agent should do instead, OR explain what the hook protects when no safe alternative exists.

A block/warn message with only a reason (no next steps) leaves the agent with no recovery path. Guidance is
required because agents act on hook output; incomplete messages cause infinite retries or incorrect workarounds.

### Required Elements

Every block/warn message must include:

1. **What is blocked** — name the specific operation being prevented
2. **Why it is blocked** — the protection the hook provides (not just "blocked")
3. **How to proceed** — concrete next steps the agent should take

When no safe alternative exists (e.g., write to `/etc/gitconfig`), explain what the hook protects:
- What harmful effect the hook prevents
- What condition must be true before the agent may proceed (e.g., explicit user request)

### Good Patterns

**Pattern: git identity protection** (`BlockGitconfigFileWrite`)

Explains what the hook protects, the harmful effect, when the operation is allowed, and shows safe alternatives:

```
BLOCKED: Cannot write to canonical gitconfig file without explicit user request

Writing directly to git configuration files (~/.gitconfig, ...) silently overwrites
the author information on every future commit.

Only change git identity when the user explicitly asks you to (e.g., "set my git username to Alice").

To safely read or modify git identity:
  git config user.name        # read current name
  git config user.email       # read current email
  git config user.name Alice  # set new name (with explicit user request)
```

**Pattern: worktree path isolation** (`EnforceWorktreePathIsolation`)

Identifies the specific file, states the correct path the agent should use, and prohibits the bash bypass:

```
ERROR: Worktree isolation violation

You are working in worktree: /workspace/.cat/worktrees/my-issue
But attempting to access outside it: /workspace/plugin/skills/foo.md

Use the corrected worktree path instead:
  /workspace/.cat/worktrees/my-issue/plugin/skills/foo.md

Do NOT bypass this hook using Bash (cat, echo, tee, etc.) to access the file directly.
The worktree exists to isolate changes from the main workspace until merge.
```

**Pattern: uncommitted changes before subagent spawn** (`EnforceCommitBeforeSubagentSpawn`)

Includes the worktree path, the uncommitted file list, the rationale, and a required-fix instruction:

```
BLOCKED: Worktree has uncommitted changes. Commit all changes before spawning a subagent.

Worktree: /workspace/.cat/worktrees/my-issue
Uncommitted changes detected (git status --porcelain):
 M plugin/skills/foo.md

Rationale: Each subagent is spawned with isolation: "worktree", creating a new git worktree
branched from the current HEAD. Uncommitted changes are NOT visible in the subagent's
worktree. All changes must be committed so the subagent sees the complete implementation state.

Required fix: Commit all changes in the worktree, then retry spawning the subagent.
```

**Pattern: worktree isolation for plugin edits** (`EnforcePluginFileIsolation`)

States the file, provides numbered steps, explains why isolation matters, and covers the edge case:

```
BLOCKED: Cannot edit source files outside of an issue worktree.

File: plugin/skills/foo.md

Solution:
1. Create task: /cat:add-agent <task-description>
2. Work in isolated worktree: /cat:work
3. Make edits in the issue worktree

Why this matters:
- Keeps base branch stable
- Enables clean rollback
- Allows parallel work on multiple tasks

If this is truly maintenance work on the base branch:
1. Create an issue for it
2. Use /cat:work to create proper worktree
3. Make changes in isolated environment
```

**Pattern: schema violation** (`StateSchemaValidator`)

Names the invalid value, lists valid alternatives, and provides migration guidance:

```
STATE.md schema violation: Invalid Status value 'Done'.

Status must be one of: closed, in-progress, open

If migrating from older versions, run: plugin/migrations/2.1.sh
```

### Bad Patterns

**Bad — reason only, no guidance:**

```
Blocked: Only issue worktrees may modify plugin/ files.
```

Problem: The agent knows it is blocked but has no path forward.

**Bad — generic instruction:**

```
BLOCKED: Use the correct path.
```

Problem: Does not identify what the correct path is or how to determine it.

**Bad — partial guidance:**

```
BLOCKED: Worktree has uncommitted changes.
```

Problem: Does not explain why uncommitted changes are a problem or what the required fix is.

### Hooks With No Safe Alternative

When the hook protects an operation the agent should NEVER perform without explicit user instruction
(e.g., git identity changes), the guidance must:

1. State what explicit condition must be met (e.g., "the user must explicitly ask")
2. Give an example of what counts as explicit user instruction
3. Show read-only alternatives when they exist

### Project Hooks

Project hooks in `.claude/settings.json` must follow the same guidance pattern if they block or warn
operations. An empty hooks object (the current state) requires no changes. Any future project hooks
added to `.claude/settings.json` must include actionable guidance in their block/warn messages.
