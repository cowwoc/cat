# Rules Audience System

CAT uses a two-tier rules system to control which rules reach which agents, reducing token waste from
orchestration rules being sent to implementation subagents.

## Two-Tier Architecture

| Location | Loaded by | Purpose |
|----------|-----------|---------|
| `.claude/rules/` | Claude Code (native) | Content for **all** agents unconditionally |
| `.claude/cat/rules/` | CAT hooks | Content with audience filtering |

**Principle:** `.claude/rules/` is for content that targets all agents (main + all subagents), with
optional `paths:` restrictions. `.claude/cat/rules/` is for content that needs `mainAgent` or
`subAgents` audience filtering.

### Native Claude Code Loading (`.claude/rules/`)

Claude Code natively loads `.claude/rules/` files for both the main agent and every subagent. Files here:
- Load automatically on every session start (including after compaction)
- Cannot be filtered by agent type (main vs subagent) - every agent receives every file
- Support a `paths:` frontmatter property (native Claude Code feature) to restrict injection to
  sessions where the agent is operating on matching files

Use `.claude/rules/` for content that targets all agents. Path restrictions via `paths:` frontmatter
are handled natively by Claude Code.

### CAT-Managed Loading (`.claude/cat/rules/`)

CAT hooks (`SessionStartHook`, `SubagentStartHook`) discover and inject files from this directory with
audience filtering. Files here support three frontmatter properties:

```yaml
---
mainAgent: false             # default: true (omit to inject into main agent)
subAgents: []                # default: all (omit to inject into all subagents)
paths: ["*.java"]            # default: always (omit to always inject)
---
```

All properties are optional. Omit any property to use its default.

## Frontmatter Properties

### `mainAgent`

Controls whether the main agent receives this rule.

| Value | Behavior |
|-------|----------|
| `true` (default) | Inject into main agent context |
| `false` | Do not inject into main agent context |

Use `mainAgent: false` for rules that are only relevant to specific subagent types and would waste
context in the main agent.

### `subAgents`

Controls which subagents receive this rule.

| Value | Behavior |
|-------|----------|
| Omitted (default) | Inject into all subagents |
| `[]` | Do not inject into any subagent |
| `["cat:work-execute", "Explore"]` | Inject only into matching subagents |

The subagent type is matched against the `subagent_type` field in the SubagentStart hook input. This
corresponds to the `subagent_type` parameter passed to the Task tool when spawning the subagent.

Use `subAgents: []` for orchestration rules that only the main agent should know about (e.g., approval
gate protocols, hook registration procedures).

### `paths`

Restricts injection to sessions where matching files are active.

| Value | Behavior |
|-------|----------|
| Omitted (default) | Always inject |
| `["*.java", "src/main/**"]` | Inject only when operating on matching files |

Path matching uses glob patterns:
- `*` matches any characters except path separator
- `**` matches any characters including path separator
- `?` matches any single character (except path separator)

CAT hooks implement `paths` filtering independently of Claude Code's native support, since files in
`.claude/cat/rules/` are not in `.claude/rules/` and thus not processed by Claude Code natively.

Use `paths:` for language-specific conventions (e.g., Java coding style) to avoid injecting them
into sessions that are not editing those file types.

## Decision Guide: Where to Put Rules

Use this table to decide where content belongs:

| Content type | Audience | Where |
|-------------|----------|-------|
| Critical safety rules | All agents, always | `.claude/rules/` |
| Common coding conventions | All agents, always | `.claude/rules/` |
| Language-specific conventions | All agents, path-restricted | `.claude/rules/` with `paths:` |
| Approval gate protocols | Main agent only | `.claude/cat/rules/` with `subAgents: []` |
| Hook registration procedures | Main agent only | `.claude/cat/rules/` with `subAgents: []` |
| Subagent-specific instructions | Specific subagent type | `.claude/cat/rules/` with `mainAgent: false` |

## Examples

### Universal rule (`.claude/rules/`)

```markdown
# Safety Rules
Never delete production databases without explicit user confirmation.
```

### Convention for all agents (`.claude/rules/common.md`)

```markdown
# Common Conventions
...
```

No frontmatter needed — all defaults apply (main agent + all subagents + always inject).
Files with no audience restrictions belong in `.claude/rules/` (native Claude Code rules).

### Language-specific convention (`.claude/rules/java.md`)

```yaml
---
paths: ["*.java"]
---
# Java Conventions
...
```

Files with only `paths:` restrictions belong in `.claude/rules/` — Claude Code natively supports
`paths:` frontmatter.

### Main-agent-only rule (`.claude/cat/rules/hooks.md`)

```yaml
---
subAgents: []
---
# Orchestration Rules
...
```

### Subagent-specific rule

```yaml
---
mainAgent: false
subAgents: ["cat:work-execute"]
---
# Implementation Agent Instructions
...
```
