<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Skill Loading Reference

How to register, invoke, and reference skills for main agents and subagents in Claude Code.

## Skill Types

| Type | Location | Name Format | Example |
|------|----------|-------------|---------|
| Plugin skill | `plugin/skills/{name}/` | `cat:{name}` | `cat:git-squash-agent` |
| Project skill | `.claude/skills/{name}/` | `{name}` | `my-validator` |
| User skill | `~/.config/claude/skills/{name}/` | `{name}` | `code-review` |

**Linux path note:** User skills are at `~/.config/claude/skills/`, not `~/.claude/skills/`.

## Skill Directory Structure

```
{skill-name}/
  SKILL.md        — Frontmatter (description, user-invocable) + preprocessor directive or content
  first-use.md    — Full skill content (required)
```

## Invoking Skills

### Via Skill Tool (recommended for all skill types)

The Skill tool triggers the full SkillLoader pipeline:

1. Claude Code routes to the skill's SKILL.md
2. SKILL.md preprocessor directive (`!` backtick) calls `skill-loader`
3. `skill-loader` invokes `SkillLoader.java`
4. SkillLoader checks per-agent marker file, returns full content or a short reference
5. Variable substitution and `@path` expansion run on the returned content

```
Skill tool:
  skill: "cat:git-squash-agent"
  args: "optional arguments"
```

**This works in both main agent and subagents.** The Skill tool is available to all agent types, and
SkillLoader runs correctly in subagent context.

**CRITICAL: Skill tool execution is SYNCHRONOUS, not asynchronous.** The Skill tool loads the skill
content into the agent's context in the SAME conversation turn. The skill content is returned immediately
as part of the tool response. The agent must then execute the skill steps within this same conversation
flow — there is no background subprocess spawning. After the Skill tool returns with content, the agent
should immediately proceed with the skill's instructions rather than waiting for a background process to
complete.

### Via `skills:` Frontmatter (agent definitions only)

The `skills:` field in agent YAML frontmatter injects skill content into a subagent's context at spawn
time.

```yaml
---
name: my-agent
skills:
  - cat:my-plugin-skill
---
```

**How it works:**
1. Claude Code looks up the skill name using `name`, `userFacingName()`, or `aliases`
2. Reads the skill's SKILL.md content
3. Runs the `!` backtick preprocessor on the content (expanding directives)
4. Injects the processed result into the subagent's system prompt

**Confirmed via empirical test:** A plugin skill with `!`echo "MARKER"`` in its SKILL.md had the
directive expanded when injected via frontmatter. The subagent saw the expanded output, not the raw
directive. Debug log: `[Agent: cat:preprocess-tester] Preloaded skill 'cat:preprocess-test'`.

**Limitations:**
- Only works when agent is spawned via the **Task tool** (not `--agent` CLI flag)
- Agent must be discovered at session start (cannot be created mid-session)

**When to use:** For preloading skill content into subagents at spawn time. Works for both plain-text
and preprocessor-based plugin skills.

## Skill Arguments

### The `argument-hint` Field

The `argument-hint` frontmatter field documents the arguments passed to the SKILL.md preprocessor command
(the `!` backtick directive), not the arguments received by `first-use.md`. For skills using `skill-loader`,
this includes `catAgentId` as the first argument — `skill-loader` consumes `catAgentId` internally and passes
the remaining arguments to `first-use.md`.

The field is also shown as a display-only hint in the CLI prompt bar when users type the slash command.
It does not affect argument parsing. Follow standard CLI conventions:

| Syntax | Meaning | Example |
|--------|---------|---------|
| `<arg>` | Required argument | `<file>` |
| `[arg]` | Optional argument | `[open]` |
| `<arg...>` | Variable-length (one or more) | `<keywords...>` |

### Passing Arguments to Preprocessor Commands

Skills that use `!` backtick preprocessor commands can receive arguments from the caller. Two patterns exist:

| Pattern | Syntax | When to Use |
|---------|--------|-------------|
| Fixed arguments | `$N` positional references | Known number of arguments |
| Variable-length arguments | `$ARGUMENTS` | Unknown/variable number of arguments |

### Fixed Arguments (`$N` Pattern)

For skills with a known number of arguments, use `argument-hint` frontmatter to document expected arguments and `$N`
positional references (`$0`, `$1`, `$2`, ...) in the preprocessor command. SkillLoader splits the caller's args string
on whitespace and maps tokens to positional indices.

```yaml
---
argument-hint: "<severity> <stakeholder> <description> <location>"
---
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/my-tool" "$0" "$1" "$2" "$3"`
```

Quoting `"$N"` is recommended to preserve arguments containing special characters after substitution.

**Behavior for out-of-range indices:** If `$N` references an index beyond the argument count, the literal `$N` string
is passed through unchanged — this will likely cause errors in the target binary.

### Variable-Length Arguments (`$ARGUMENTS`)

For skills that accept a variable number of arguments or free-text descriptions, use the unbraced `$ARGUMENTS` variable.
This bypasses SkillLoader's variable substitution and is expanded by the shell, where Claude Code sets the `ARGUMENTS`
environment variable to the raw args string.

**Do NOT use `${ARGUMENTS}`** (braced form). SkillLoader's variable resolver intercepts braced variables and
`ARGUMENTS` is not a recognized built-in, causing it to resolve to empty. See
[claude-code#18044](https://github.com/anthropics/claude-code/issues/18044#issuecomment-3928291132).

Two quoting styles exist depending on whether the tool expects tokens or free text:

| Style | Syntax | When to Use |
|-------|--------|-------------|
| Unquoted | `$ARGUMENTS` | Tool expects separate tokens (e.g., keyword list, flag-style args) |
| Quoted | `"$ARGUMENTS"` | Tool expects the full text as one argument (e.g., free-text description, multiline input) |

**Unquoted** — shell performs word-splitting; each whitespace-separated token becomes a separate argument:

```yaml
---
argument-hint: "<keywords...>"
---
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/my-tool" $ARGUMENTS`
```

**Quoted** — shell passes the entire value as a single argument, preserving spaces and newlines:

```yaml
---
argument-hint: "[description]"
---
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/my-tool" "$ARGUMENTS"`
```

Use quoted `"$ARGUMENTS"` whenever the input may contain spaces, newlines, or punctuation that the shell would otherwise
split or interpret. Unquoted `$ARGUMENTS` is appropriate only when word-splitting into separate tokens is the intended
behavior.

## Loading Paths

| Loader Path | How Invoked | Agent Marker Used? |
|-------------|-------------|----------------------|
| Main agent via Skill tool | `cat:{skill-name}` | Yes — per-agent marker file |
| Subagent via `skills:` frontmatter | `cat:{skill-name}` | Yes — subagent's own marker file |
| Subagent via SubagentStartHook | skill listing injected at spawn | On-demand via `skill-loader` |

## Session Markers (First-Use vs Reference)

SkillLoader tracks which skills have been loaded via **per-agent** marker files:

```
${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/{sessionId}/skills-loaded               ← main agent
${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/{sessionId}/subagents/{agent_id}/skills-loaded  ← subagent
```

The `catAgentId` encodes the full relative path from `projects/${ENCODED_PROJECT_DIR}/`:

| Agent | catAgentId value | Resolved marker path |
|-------|------------------|----------------------|
| Main agent | `{sessionId}` | `${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/{sessionId}/skills-loaded` |
| Subagent | `{sessionId}/subagents/{agent_id}` | `${CLAUDE_CONFIG_DIR}/projects/${ENCODED_PROJECT_DIR}/{sessionId}/subagents/{agent_id}/skills-loaded` |

Each agent instance (main agent, each subagent) has its own marker file. Parent and subagents track
skill loading independently — a skill invoked by the parent does not affect a subagent's first-use
behavior, and vice versa.

- **First invocation:** Returns full content from `first-use.md`, writes skill name to marker file
- **Subsequent invocations:** Returns a short reference directing the model to reuse the instructions
  already present in the conversation history (generated dynamically, not from a file)

### Why First-Use/Reference Exists: Context Window, Not Caching

This optimization targets **context window conservation**, not prompt caching. The distinction matters:

**Prompt caching** works by prefix matching — the API caches everything from the start of the request
up to each cache breakpoint. When a skill is invoked the first time, its full content lands in the
conversation as a tool result. On the next API call, that tool result is part of the cached
conversation prefix. Re-injecting the same content on a second invocation would not cost additional
cache computation — it's already cached.

**Context window space** is the real constraint. Every token in the conversation — cached or not —
counts against the context window limit. A 2,000-token skill body invoked 5 times would consume
10,000 tokens. Returning a short reference on subsequent calls saves ~8,000 tokens, delaying
compaction and leaving room for actual work.

| Concern | Does first-use/reference help? |
|---------|-------------------------------|
| Prompt cache computation | **No** — first invocation is cached in the prefix automatically |
| Context window space | **Yes** — avoids duplicating skill content per re-invocation |
| Delaying compaction | **Yes** — fewer tokens consumed means more room before hitting the limit |

### Interaction with Claude Code's Caching Model

Skill loading via the Skill tool is **cache-safe** because:
- Content flows through **conversation messages** (tool results), not system prompt modifications
- The tool set is never changed — skills are loaded as message content, not tool definitions
- Re-invocations append a short reference at the tail of the conversation (always uncached), while
  the original full content remains in the cached prefix

## Plugin Skill Name Resolution

Plugin skills are registered with the `cat:` prefix. The lookup function matches against three
properties:

```
A.name === query || A.userFacingName() === query || A.aliases?.includes(query)
```

| Query | Matches Plugin Skill? | Notes |
|-------|----------------------|-------|
| `cat:git-squash-agent` | Yes | Matches `userFacingName()` |
| `git-squash-agent` | Maybe | May match `name` property (depends on internal registration) |

**Best practice:** Always use the `cat:` prefix when referencing plugin skills in agent frontmatter or
Skill tool invocations to ensure reliable resolution.

## Patterns

### Subagent Needs Skill Content

**Both approaches work** — choose based on whether you want the content at spawn time or on demand.

```yaml
# ✅ Frontmatter: content injected at spawn (preprocessor runs)
---
skills:
  - cat:git-merge-linear-agent
---

# ✅ Skill tool: content loaded on demand during execution
prompt: |
  Invoke /cat:git-merge-linear-agent via the Skill tool before merging.
```

**Trade-offs:**
- **Frontmatter**: content is always available, no extra tool call needed. But uses tokens even if the
  subagent doesn't need the skill for every execution path.
- **Skill tool**: content loaded only when needed. But requires an extra tool call round-trip.

### User-Facing vs Agent-Facing Skill Variants

Skills that can be invoked by both users and the model exist as paired directories:

| Directory | Audience | Frontmatter flag | catAgentId source |
|-----------|----------|-----------------|------------------|
| `{skill-name}/` | Users (slash command) | `disable-model-invocation: true` | `${CLAUDE_SESSION_ID}` |
| `{skill-name}-agent/` | Model (Skill tool) | `user-invocable: false` | `$0` (caller-supplied) |

**Why the split is necessary:** Skills that use a catAgentId to locate per-agent state (skill markers, subagent
context) must receive the correct ID for the caller. When a user invokes a skill, `${CLAUDE_SESSION_ID}` always
identifies the main session correctly. When the model invokes a skill — especially from a subagent — it must pass
its own agent ID via `$0`. Using `${CLAUDE_SESSION_ID}` in a subagent context writes to the wrong marker file,
causing skills to misbehave (e.g., first-use check passes for the wrong agent).

**Flags:**
- `disable-model-invocation: true` — excludes the skill from the model's skill listing AND blocks invocation via the
  Skill tool. Attempting to invoke such a skill via Skill tool produces:
  `"Skill {name} cannot be used with Skill tool due to disable-model-invocation"`.
  Use the `-agent` variant (e.g., `cat:add-agent` instead of `cat:add`) when the model must invoke the skill.
- `user-invocable: false` — hides the skill from the slash command menu. Use on agent-facing variants so users
  never see the `cat:{skill-name}-agent` command.

**catAgentId in preprocessor commands:**
```yaml
# User-facing: always the main session
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" add-agent "${CLAUDE_SESSION_ID}"`

# Agent-facing: caller-supplied (may be subagent)
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" add-agent "$0"`
```

**CRITICAL:** `$0` receives the agent ID injected by SubagentStartHook as the first positional argument. It must be a
`${CLAUDE_SESSION_ID}` (main agent) or `${CLAUDE_SESSION_ID}/subagents/{agent_id}` (subagent). Passing any other value
(e.g., a branch name like `v2.1`, a session variable, or a user-supplied string) produces:
`"catAgentId 'v2.1' does not match a valid format. Expected: UUID"`. Do NOT construct or substitute this value —
pass `$0` directly and ensure the invoking context received it from SubagentStartHook.

**Descriptions:**
- User-facing: describe what the skill does in plain language (e.g., "Add a new issue or version to the project").
- Agent-facing: trigger-oriented — describe when the model should invoke it, including keyword triggers and usage
  conditions (e.g., "Use when user says 'add an issue'. IMPORTANT: forward AskUserQuestion verbatim.").

**When to create variants:** Create a paired `-agent` variant whenever the skill uses `catAgentId` to locate
per-agent state. Skills that perform stateless operations (e.g., format a document) do not need variants — use
`disable-model-invocation: true` or `user-invocable: false` alone based on the intended audience.

**Variant sets by audience:**

| Category | Example Skills (logical name) | Has `{name}/` directory? | Has `{name}-agent/` directory? |
|----------|-------------------------------|--------------------------|-------------------------------|
| User-only (model never invokes) | `init`, `statusline` | Yes | No |
| Agent-only (user never invokes) | `get-output`, `recover-from-drift` | No | Yes (only) |
| Both (user and model invoke) | `add`, `status`, `work`, `learn`, `help` | Yes | Yes |

- **User-only**: Base directory only (`{name}/`). No paired `-agent` directory.
- **Agent-only**: `-agent` directory only (`{name}-agent/`, `user-invocable: false`). No base directory.
- **Both**: Paired directories — `{name}/` with `disable-model-invocation: true`, `{name}-agent/` with `user-invocable: false`.

### Creating a New Plugin Skill

1. Create directory: `plugin/skills/{skill-name}/`
2. Create `SKILL.md` with frontmatter and preprocessor directive
3. Create `plugin/skills/{skill-name}/first-use.md` with full skill content
4. If the skill dispatches to a Java handler: register the handler in `client/build-jlink.sh`
   and invoke the binary launcher in `SKILL.md` (not `skill-loader`)
5. The skill is automatically available as `cat:{skill-name}`

**Java handler registration (step 4):** Skills that execute Java code must have two things:

- A binary launcher entry in `client/build-jlink.sh` HANDLERS array:
  ```
  "get-output:skills.GetOutput"
  ```
- A `SKILL.md` preprocessor that calls the binary launcher — NOT `skill-loader`:
  ```
  # CORRECT: calls the Java binary launcher directly
  !`"${CLAUDE_PLUGIN_ROOT}/client/target/jlink/bin/get-output" "${CLAUDE_PLUGIN_ROOT}" \
    "${CLAUDE_PROJECT_DIR}" "$0" $ARGUMENTS`

  # WRONG: calls skill-loader, which loads skill content but does NOT invoke Java handlers
  !`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" get-output "$0" $ARGUMENTS`
  ```
  **Quoting `$ARGUMENTS`:** The examples above use unquoted `$ARGUMENTS`, which is appropriate when the tool
  expects separate tokens (e.g., keyword lists or flag-style arguments). If the skill's `argument-hint` accepts
  free-text input (e.g., `[description]`), use `"$ARGUMENTS"` (quoted) so spaces and newlines are passed as one
  argument rather than being word-split by the shell. See the [Variable-Length Arguments](#variable-length-arguments-arguments)
  section for the full quoting guide.

`skill-loader` (SkillLoader) reads and returns skill content from `first-use.md`. It does not route to
Java handlers. Using `skill-loader` when the intent is to invoke a Java handler produces empty or
incorrect output — the skill appears to work (no error) but generates no computed output.

### Creating a New Project Skill

1. Create directory: `.claude/skills/{skill-name}/`
2. Create `SKILL.md` with content (no preprocessor needed)
3. The skill is available as `{skill-name}` within the project

## Output Path Changes Convention

**MANDATORY:** When modifying a skill's output mechanism, changes must be validated with empirical testing before and after.

### What Constitutes an Output Path Change

An output path change is any modification to:
- The `<output>` block in a skill's `first-use.md`
- The `!` preprocessor directive that generates output
- Migration between static output (via `<output>` + `!` block) and dynamic output (via `INVOKE: Skill`)
- Refactoring of Java handlers that generate skill output (e.g., `GetStatusOutput`, `GetDiffOutput`)

### Testing Requirement

For each output path change:

1. **Before modification:** Run `/cat:empirical-test` on the affected skill to establish baseline behavior
2. **After modification:** Run `/cat:empirical-test` again to verify the skill produces output
3. **Success criteria:** Both tests must achieve ≥95% compliance on the haiku model

### Example Workflow

```
# 1. Baseline test
/cat:empirical-test /cat:status

# 2. Modify the skill (e.g., change from INVOKE: to <output> pattern)
# Edit: plugin/skills/status/first-use.md
# Edit: client/src/main/java/.../GetStatusOutput.java

# 3. Verify fix
/cat:empirical-test /cat:status

# 4. If both tests pass, proceed to commit
# If second test fails, investigate output generation and re-test
```

### Why This Matters

Output paths affect core user workflows. Silent regressions (skill runs but produces no computed output, or output
format changes unexpectedly) can propagate to dependent skills. Empirical testing detects these regressions early,
before merging to main.
