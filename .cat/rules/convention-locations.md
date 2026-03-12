---
subAgents: []
---
# Convention File Locations

Two audiences exist for conventions. Using the wrong location causes rules to reach the wrong audience
or miss their intended target.

| Audience | Location | Injected Into |
|----------|----------|---------------|
| **End-users** (all CAT users) | `plugin/rules/`, `plugin/` files | Every CAT session via SessionStartHook |
| **Plugin developers** (CAT contributors) | `.claude/rules/`, `.cat/rules/` | Development sessions on this repo |

## End-User Conventions (plugin)

Behavioral rules that apply to anyone using the CAT plugin — tool usage patterns, workflow protocols,
approval gates, delegation policies. These ship with the plugin and are injected into every session.

**Where to add:**
- `plugin/rules/*.md` — session-level behavioral rules (injected on every SessionStart)
- `plugin/agents/*.md` — agent-specific behavioral rules (injected into that agent type only)
- `plugin/concepts/*.md` — reference documentation loaded by skills on demand

## Plugin Development Conventions (project)

Coding standards, style guides, and testing rules that apply only when developing the CAT plugin itself.
These are checked into this repository's `.claude/` directory and are NOT distributed to end-users.

**Where to add:**
- `.claude/rules/common.md` — cross-cutting development conventions (all agents)
- `.claude/rules/java.md` — Java coding standards (path-restricted to `*.java`)
- `.cat/rules/*.md` — development conventions with audience filtering (main-only, subagent-only)
