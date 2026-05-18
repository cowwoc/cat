---
subAgents: []
---
# Convention File Locations

Two audiences exist for conventions. Using the wrong location causes rules to reach the wrong audience
or miss their intended target.

| Audience | Location | Injected Into |
|----------|----------|---------------|
| **End-users** (all CAT users) | `client/plugin/rules/{common,claude,codex}/`, `client/plugin/` files | Every CAT session via SessionStartHook |
| **Plugin developers** (CAT contributors) | `.cat/rules/{common,claude,codex}/` | Development sessions on this repo |

## End-User Conventions (plugin)

Behavioral rules that apply to anyone using the CAT plugin — tool usage patterns, workflow protocols,
approval gates, delegation policies. These ship with the plugin and are injected into every session.

**Where to add:**
- `client/plugin/rules/common/*.md` — portable session-level behavioral rules (injected on every SessionStart)
- `client/plugin/rules/claude/*.md` and `client/plugin/rules/codex/*.md` — engine-specific behavioral rules
- `client/plugin/agents/common/*.md` — portable agent bodies
- `client/plugin/agents/claude/*.md` and `client/plugin/agents/codex/*.md` — engine-specific agent wrappers/definitions
- `client/plugin/concepts/*.md` — reference documentation loaded by skills on demand

**Decision rule:**
- Put conventions under `client/plugin/rules/common/` when every installed engine must receive them.
- Put conventions under `client/plugin/rules/claude/` or `client/plugin/rules/codex/` when only one installed
  engine must receive them.
- Put shared agent bodies under `client/plugin/agents/common/`; put engine wrappers under
  `client/plugin/agents/claude/` or `client/plugin/agents/codex/`.
- Do not put CAT repository development standards under `client/plugin/`; that would ship them to end-users.

## Plugin Development Conventions (project)

Coding standards, style guides, and testing rules that apply only when developing the CAT plugin itself.
These are checked into this repository's `.cat/rules/` directory and are NOT distributed to end-users.

**Where to add:**
- `.cat/rules/common/*.md` — portable development conventions with audience filtering (main-only, subagent-only)
- `.cat/rules/claude/*.md` and `.cat/rules/codex/*.md` — engine-specific development conventions

**Decision rule:**
- Put portable CAT repository conventions under `.cat/rules/common/` when both Claude and Codex agents should receive
  them while working on this repository.
- Put repository-only engine exceptions under `.cat/rules/claude/` or `.cat/rules/codex/`.
- Do not put end-user workflow rules under `.cat/rules/`; they will not ship in flattened plugin artifacts.
