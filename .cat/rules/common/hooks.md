---
agents: ["main"]
paths:
  - "client/plugin/hooks/**"
  - "client/**/hook/**"
  - "client/**/hooks/**"
  - ".claude/settings.json"
  - ".codex/**"
---
# Hook Guidance

CAT has two hook locations:

| Hook Type | Location | Use Case |
|-----------|----------|----------|
| **Portable plugin hook files** | `client/plugin/hooks/common/` | Shared hook documentation and helpers |
| **Engine plugin hooks** | `client/plugin/hooks/<engine>/hooks.json` | Engine-specific hook registration |

`client/plugin/hooks/common/` is documentation/helper content only. Engine hook registration lives in the
engine-specific `hooks.json` files.

## Plugin Hooks

Plugin hooks are pre-registered in engine-specific plugin hook configs and loaded automatically by the active plugin
system.

Do not attempt to register plugin hooks in project hook settings. When investigating whether a plugin hook is active,
check the active engine's `client/plugin/hooks/<engine>/hooks.json`.

## CLI Tool Output Categories

CAT has two categories of Java CLI tools, each with a different output contract:

| Tool category | Output consumer | Output contract |
|---------------|-----------------|-----------------|
| **Hook handlers** | The active engine hook engine | The engine's hook output format |
| **Skill CLI tools** | The skill that invoked the command | The skill-defined business format |

Skill CLI tools may use business-format JSON such as `{"status":"...", "message":"..."}` when the skill Markdown
defines and parses that schema. Hook handlers must use the active engine's hook output contract instead.

## Hook Output Guidance

**MANDATORY:** Every hook that blocks or warns an operation must include actionable guidance explaining what the agent
should do instead, or explain what the hook protects when no safe alternative exists.

A block/warn message with only a reason leaves the agent with no recovery path. Guidance is required because agents act
on hook output; incomplete messages cause infinite retries or incorrect workarounds.

### Required Elements

Every block/warn message must include:

1. **What is blocked** — name the specific operation being prevented
2. **Why it is blocked** — the protection the hook provides
3. **How to proceed** — concrete next steps the agent should take

When no safe alternative exists, explain what the hook protects:
- What harmful effect the hook prevents
- What condition must be true before the agent may proceed

### Good Patterns

**Git identity protection**

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

**Worktree path isolation**

Identifies the specific file, states the correct path the agent should use, and prohibits bypassing the hook:

```
ERROR: Worktree isolation violation

You are working in worktree: ${workPath}
But attempting to access outside it: ${mainWorkspacePath}/plugin/skills/common/foo/SKILL.md

Use the corrected worktree path instead:
  ${workPath}/plugin/skills/common/foo/SKILL.md

Do NOT bypass this hook using shell commands to access the file directly.
The worktree exists to isolate changes from the main workspace until merge.
```

**Uncommitted changes before subagent spawn**

Includes the worktree path, the uncommitted file list, the rationale, and a required-fix instruction:

```
BLOCKED: Worktree has uncommitted changes. Commit all changes before spawning a subagent.

Worktree: ${workPath}
Uncommitted changes detected (git status --porcelain):
 M plugin/skills/common/foo/SKILL.md

Rationale: Each subagent is spawned in an isolated worktree branched from the current HEAD.
Uncommitted changes are NOT visible in the subagent's worktree.

Required fix: Commit all changes in the worktree, then retry spawning the subagent.
```

**Plugin source isolation**

States the file, provides numbered steps, explains why isolation matters, and covers the edge case:

```
BLOCKED: Cannot edit source files outside of an issue worktree.

File: plugin/skills/common/foo/SKILL.md

Solution:
1. Create task: /cat:add <task-description>
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

**Schema violation**

Names the invalid value, lists valid alternatives, and provides migration guidance:

```
index.json schema violation: Invalid Status value 'Done'.

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
