<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Rules Audience System

CAT uses engine-specific and portable rule directories so Claude Code and Codex can coexist while sharing
engine-neutral guidance.

## Directory Architecture

| Location | Loaded by | Purpose |
|----------|-----------|---------|
| `.cat/rules/common/*.md` | CAT engine loaders | Portable rules shared by all supported engines |
| `.claude/rules/` | CAT Claude loader / Claude Code | Claude Code-specific rules |
| `.cat/rules/claude/` | CAT Claude loader | Claude-specific rules managed by CAT |
| `.cat/rules/codex/` | CAT Codex loader | Codex-specific rules |
| `client/plugin/rules/common/` | CAT engine loaders | Shipped portable rules |
| `client/plugin/rules/claude/` | CAT Claude loader | Shipped Claude-specific rules |
| `client/plugin/rules/codex/` | CAT Codex loader | Shipped Codex-specific rules |

**Principle:** Put engine-neutral guidance in `.cat/rules/common/*.md` files. Put product-specific behavior, APIs,
environment variables, or hook semantics in the matching engine directory.

**Reference boundary rule:** Files under `plugin/rules/common/` must not reference any engine-specific rule files
under `plugin/rules/<engine>/` (or `.cat/rules/<engine>/`). Keep common rules self-contained and engine-neutral.

**Heading convention for engine-specific files:** Do not label headings with engine names. Engine scope is implied
by directory location. Use neutral headings (for example, `## Worktree Isolation`, not `## Worktree Isolation (Claude)`).

### Portable Loading (`.cat/rules/common/*.md`)

CAT loaders discover and inject files from `.cat/rules/common/*.md` for every supported engine. Engine-specific
siblings under `.cat/rules/` are not part of the portable rule set.

### Engine-Specific Loading (`.claude/rules/`, `.cat/rules/claude/`, `.cat/rules/codex/`)

Engine-specific loaders also discover rules from the active engine directory:
- Claude Code loads `.cat/rules/common/*`, `.cat/rules/claude/*`, and `.claude/rules/*`, in that order
- Codex loads `.cat/rules/common/*` and `.cat/rules/codex/*`

Rules in these directories may rely on engine-specific capabilities such as Claude hook payloads or Codex plugin
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
subAgents: []                # default: all (omit to inject into all agents)
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

Use `mainAgent: false` for rules that are only relevant to specific agent types and would waste
context in the main agent.

### `subAgents`

Controls which agents receive this rule.

| Value | Behavior |
|-------|----------|
| Omitted (default) | Inject into all agents |
| `[]` | Do not inject into any agent |
| `["cat:work-execute", "Explore"]` | Inject only into matching agents |

The agent type is matched against the `subagent_type` field in the SubagentStart hook input. This
corresponds to the `subagent_type` parameter passed to the Task tool when spawning the agent.

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
| Engine-neutral safety rules | All engines | `.cat/rules/common/*.md` |
| Common coding conventions | All engines | `.cat/rules/common/*.md` |
| Language-specific conventions | All engines, path-restricted | `.cat/rules/common/*.md` with `paths:` |
| Claude hook conventions | Claude only | `.claude/rules/` or `.cat/rules/claude/` |
| Codex plugin conventions | Codex only | `.cat/rules/codex/` |
| Approval gate protocols | Main agent only | `.cat/rules/common/*.md` with `subAgents: []` |
| Hook registration procedures | Main agent only | `.cat/rules/common/*.md` with `subAgents: []` |
| Agent-specific instructions | Specific agent type | `.cat/rules/common/*.md` with `mainAgent: false` |

## Agent Definition Locations

Agent role bodies follow the same engine split:

| Agent content | Location |
|---------------|----------|
| Engine-neutral role body | `client/plugin/agents/common/` |
| Claude Code custom agent wrapper | `client/plugin/agents/claude/` |
| Codex custom agent definition | `client/plugin/agents/codex/` |

Do not duplicate full agent bodies between engines. Add or update the shared role body once, then keep the wrapper
limited to engine-specific metadata and invocation guidance.

Engine-specific agent wrappers are the normal agent mechanism. They are not a fallback to nested CLI runners:

- Claude Code uses `.claude/agents/{name}.md` wrappers as custom agent definitions.
- Codex uses native `.codex/agents/{name}.toml` custom-agent definitions. Their `name` fields use a `cat-` prefix
  to avoid collisions with project-specific agents. Because Codex plugins do not currently
  register custom agents through `.codex-plugin/plugin.json`, the 2.1 migration copies them from the flattened
  installed plugin into project `.codex/agents/` as `cat-*.toml` when running under Codex.
  The Codex `SessionStart` hook re-runs the current-version migration once per installed plugin cache when the
  cache-local marker is missing, so uninstalling and reinstalling the plugin repairs the project copies. When the
  migration runs, CAT asks the user to restart, resume, or clear Codex because the running session may have already
  snapshotted available custom agents.
- Both engine definitions instruct the agent to read the matching neutral body from
  `client/plugin/agents/common/{name}.md`.

Use `cat:spawn-engine` only for isolated subprocess validation, not for routine CAT agent
orchestration.

Plugin uninstall does not remove project-scoped Codex custom agent copies automatically. Use `cat:uninstall` to remove
`.codex/agents/cat-*.toml` before invoking Codex's built-in plugin uninstaller.

## Skill Definition Locations

Skill source files are split by engine:

| Skill content | Canonical location |
|---------------|--------------------|
| Engine-neutral skills | `client/plugin/skills/common/` |
| Claude Code-specific skills | `client/plugin/skills/claude/` |
| Codex-specific skills | `client/plugin/skills/codex/` |

Engine installations are generated from these source directories.

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

No frontmatter needed — all defaults apply (main agent + all agents + always inject).
Files with no engine-specific assumptions belong in `.cat/rules/common/*.md`.

### Language-specific convention (`.cat/rules/common/java.md`)

```yaml
---
paths: ["*.java"]
---
# Java Conventions
...
```

Files with only `paths:` restrictions usually belong in `.cat/rules/common/*.md` so every engine receives the same
language convention.

### Main-agent-only rule (`.cat/rules/common/hooks.md`)

```yaml
---
subAgents: []
---
# Orchestration Rules
...
```

### Agent-specific rule

```yaml
---
mainAgent: false
subAgents: ["cat:work-execute"]
---
# Implementation Agent Instructions
...
```
