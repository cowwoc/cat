<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Rules Audience System

CAT uses runtime-specific and portable rule directories so Claude Code and Codex can coexist while sharing
runtime-neutral guidance.

## Directory Architecture

| Location | Loaded by | Purpose |
|----------|-----------|---------|
| `.cat/rules/common/*.md` | CAT runtime loaders | Portable rules shared by all supported runtimes |
| `.claude/rules/` | CAT Claude loader / Claude Code | Claude Code-specific rules |
| `.cat/rules/claude/` | CAT Claude loader | Claude-specific rules managed by CAT |
| `.cat/rules/codex/` | CAT Codex loader | Codex-specific rules |
| `client/plugin/rules/common/` | CAT runtime loaders | Shipped portable rules |
| `client/plugin/rules/claude/` | CAT Claude loader | Shipped Claude-specific rules |
| `client/plugin/rules/codex/` | CAT Codex loader | Shipped Codex-specific rules |

**Principle:** Put runtime-neutral guidance in `.cat/rules/common/*.md` files. Put product-specific behavior, APIs,
environment variables, or hook semantics in the matching runtime directory.

### Portable Loading (`.cat/rules/common/*.md`)

CAT loaders discover and inject files from `.cat/rules/common/*.md` for every supported runtime. Runtime-specific
siblings under `.cat/rules/` are not part of the portable rule set.

### Runtime-Specific Loading (`.claude/rules/`, `.cat/rules/claude/`, `.cat/rules/codex/`)

Runtime-specific loaders also discover rules from the active runtime directory:
- Claude Code loads `.cat/rules/common/*`, `.cat/rules/claude/*`, and `.claude/rules/*`, in that order
- Codex loads `.cat/rules/common/*` and `.cat/rules/codex/*`

Rules in these directories may rely on runtime-specific capabilities such as Claude hook payloads or Codex plugin
configuration.

For new CAT-managed Claude rules, prefer `.cat/rules/claude/*`. Keep `.claude/rules/*` for Claude-native project
conventions and existing user rules that should remain visible to Claude Code outside CAT. CAT does not deduplicate
same-named files across these directories; later directories append additional rules instead of overriding earlier
ones.

## Frontmatter Properties

Rule files support these optional frontmatter properties:

```yaml
---
mainAgent: false             # default: true (omit to inject into main agent)
subAgents: []                # default: all (omit to inject into all subagents)
paths: ["*.java"]            # default: always (omit to always inject)
---
```

All properties are optional. Omit any property to use its default.

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

CAT loaders implement `paths` filtering for every rule directory they load.

Use `paths:` for language-specific conventions (e.g., Java coding style) to avoid injecting them
into sessions that are not editing those file types.

## Decision Guide: Where to Put Rules

Use this table to decide where content belongs:

| Content type | Audience | Where |
|-------------|----------|-------|
| Runtime-neutral safety rules | All runtimes | `.cat/rules/common/*.md` |
| Common coding conventions | All runtimes | `.cat/rules/common/*.md` |
| Language-specific conventions | All runtimes, path-restricted | `.cat/rules/common/*.md` with `paths:` |
| Claude hook conventions | Claude only | `.claude/rules/` or `.cat/rules/claude/` |
| Codex plugin conventions | Codex only | `.cat/rules/codex/` |
| Approval gate protocols | Main agent only | `.cat/rules/common/*.md` with `subAgents: []` |
| Hook registration procedures | Main agent only | `.cat/rules/common/*.md` with `subAgents: []` |
| Subagent-specific instructions | Specific subagent type | `.cat/rules/common/*.md` with `mainAgent: false` |

## Agent Definition Locations

Agent role bodies follow the same runtime split:

| Agent content | Location |
|---------------|----------|
| Runtime-neutral role body | `client/plugin/agents/common/` |
| Claude Code custom subagent wrapper | `client/plugin/agents/claude/` |
| Codex custom subagent definition | `client/plugin/agents/codex/` |

Do not duplicate full agent bodies between runtimes. Add or update the shared role body once, then keep the wrapper
limited to runtime-specific metadata and invocation guidance.

Runtime-specific agent wrappers are the normal subagent mechanism. They are not a fallback to nested CLI runners:

- Claude Code uses `.claude/agents/{name}.md` wrappers as custom subagent definitions.
- Codex uses native `.codex/agents/{name}.toml` custom-agent definitions. Their `name` fields use a `cat-` prefix
  to avoid collisions with project-specific agents. Because Codex plugins do not currently
  register custom agents through `.codex-plugin/plugin.json`, the 2.1 migration copies them from the flattened
  installed plugin into project `.codex/agents/` as `cat-*.toml` when running under Codex.
  The Codex `SessionStart` hook re-runs the current-version migration once per installed plugin cache when the
  cache-local marker is missing, so uninstalling and reinstalling the plugin repairs the project copies. When the
  migration runs, CAT asks the user to restart, resume, or clear Codex because the running session may have already
  snapshotted available custom agents.
- Both runtime definitions instruct the subagent to read the matching neutral body from
  `client/plugin/agents/common/{name}.md`.

Use `cat:claude-runner` or `cat:codex-runner` only for isolated subprocess validation, not for routine CAT subagent
orchestration.

Plugin uninstall does not remove project-scoped Codex custom agent copies automatically. Use `cat:uninstall` to remove
`.codex/agents/cat-*.toml` before invoking Codex's built-in plugin uninstaller.

## Skill Definition Locations

Skill source files are split by runtime:

| Skill content | Canonical location |
|---------------|--------------------|
| Runtime-neutral skills | `client/plugin/skills/common/` |
| Claude Code-specific skills | `client/plugin/skills/claude/` |
| Codex-specific skills | `client/plugin/skills/codex/` |

Runtime installations are generated from these source directories.

## Examples

### Portable rule (`.cat/rules/common/*.md`)

```markdown
# Safety Rules
Never delete production databases without explicit user confirmation.
```

### Convention for all agents (`.cat/rules/common/naming-conventions.md`)

```markdown
# Naming Conventions
...
```

No frontmatter needed — all defaults apply (main agent + all subagents + always inject).
Files with no runtime-specific assumptions belong in `.cat/rules/common/*.md`.

### Language-specific convention (`.cat/rules/common/java.md`)

```yaml
---
paths: ["*.java"]
---
# Java Conventions
...
```

Files with only `paths:` restrictions usually belong in `.cat/rules/common/*.md` so every runtime receives the same
language convention.

### Main-agent-only rule (`.cat/rules/common/hooks.md`)

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
