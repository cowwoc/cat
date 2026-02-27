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
| Plugin skill | `plugin/skills/{name}/` | `cat:{name}` | `cat:git-squash` |
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
2. SKILL.md preprocessor directive (`!` backtick) calls `load-skill`
3. `load-skill` invokes `SkillLoader.java`
4. SkillLoader checks per-agent marker file, returns full content or a short reference
5. Variable substitution and `@path` expansion run on the returned content

```
Skill tool:
  skill: "cat:git-squash"
  args: "optional arguments"
```

**This works in both main agent and subagents.** The Skill tool is available to all agent types, and
SkillLoader runs correctly in subagent context.

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

The `argument-hint` frontmatter field is a display-only hint shown in the CLI prompt bar when users type the slash
command. It does not affect argument parsing. Follow standard CLI conventions:

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

For skills that accept a variable number of arguments (e.g., keyword lists), use the unbraced `$ARGUMENTS` variable.
This bypasses SkillLoader's variable substitution and is expanded by the shell, where Claude Code sets the `ARGUMENTS`
environment variable to the raw args string.

```yaml
---
argument-hint: "<keywords...>"
---
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/my-tool" $ARGUMENTS`
```

**Do NOT use `${ARGUMENTS}`** (braced form). SkillLoader's variable resolver intercepts braced variables and
`ARGUMENTS` is not a recognized built-in, causing it to resolve to empty. See
[claude-code#18044](https://github.com/anthropics/claude-code/issues/18044).

Leave `$ARGUMENTS` unquoted so the shell performs word-splitting, passing each whitespace-separated token as a separate
argument to the binary.

## Loading Paths

| Loader Path | How Invoked | Agent Marker Used? |
|-------------|-------------|----------------------|
| Main agent via Skill tool | `cat:{skill-name}` | Yes — per-agent marker file |
| Subagent via `skills:` frontmatter | `cat:{skill-name}` | Yes — subagent's own marker file |
| Subagent via SubagentStartHook | skill listing injected at spawn | On-demand via `load-skill` |

## Session Markers (First-Use vs Reference)

SkillLoader tracks which skills have been loaded via **per-agent** marker files:

```
~/.config/claude/projects/-workspace/{sessionId}/skills-loaded-{catAgentId}
```

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
| `cat:git-squash` | Yes | Matches `userFacingName()` |
| `git-squash` | Maybe | May match `name` property (depends on internal registration) |

**Best practice:** Always use the `cat:` prefix when referencing plugin skills in agent frontmatter or
Skill tool invocations to ensure reliable resolution.

## Patterns

### Subagent Needs Skill Content

**Both approaches work** — choose based on whether you want the content at spawn time or on demand.

```yaml
# ✅ Frontmatter: content injected at spawn (preprocessor runs)
---
skills:
  - cat:git-merge-linear
---

# ✅ Skill tool: content loaded on demand during execution
prompt: |
  Invoke /cat:git-merge-linear via the Skill tool before merging.
```

**Trade-offs:**
- **Frontmatter**: content is always available, no extra tool call needed. But uses tokens even if the
  subagent doesn't need the skill for every execution path.
- **Skill tool**: content loaded only when needed. But requires an extra tool call round-trip.

### Creating a New Plugin Skill

1. Create directory: `plugin/skills/{skill-name}/`
2. Create `SKILL.md` with frontmatter and preprocessor directive
3. Create `plugin/skills/{skill-name}/first-use.md` with full skill content
4. If the skill dispatches to a Java handler: register the handler in `client/build-jlink.sh`
   and invoke the binary launcher in `SKILL.md` (not `load-skill`)
5. The skill is automatically available as `cat:{skill-name}`

**Java handler registration (step 4):** Skills that execute Java code must have two things:

- A binary launcher entry in `client/build-jlink.sh` HANDLERS array:
  ```
  "get-output:skills.GetOutput"
  ```
- A `SKILL.md` preprocessor that calls the binary launcher — NOT `load-skill`:
  ```
  # CORRECT: calls the Java binary launcher directly
  !`"${CLAUDE_PLUGIN_ROOT}/client/target/jlink/bin/get-output" "${CLAUDE_PLUGIN_ROOT}" \
    "${CLAUDE_SESSION_ID}" "${CLAUDE_PROJECT_DIR}" $ARGUMENTS`

  # WRONG: calls load-skill, which loads skill content but does NOT invoke Java handlers
  !`"${CLAUDE_PLUGIN_ROOT}/client/bin/load-skill" "${CLAUDE_PLUGIN_ROOT}" get-output \
    "${CLAUDE_SESSION_ID}" "${CLAUDE_PROJECT_DIR}" $ARGUMENTS`
  ```

`load-skill` (SkillLoader) reads and returns skill content from `first-use.md`. It does not route to
Java handlers. Using `load-skill` when the intent is to invoke a Java handler produces empty or
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
