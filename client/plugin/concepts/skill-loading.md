<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Skill Loading Reference

CAT-specific guidance for structuring plugin skills and avoiding loading-path mistakes.

## Skill Directory Structure

```
{skill-name}/
  SKILL.md        — Frontmatter + directive/content
  first-use.md    — Full skill content (required)
```

## Invocation Rule

Invoke CAT skills through the Skill tool and follow the returned instructions exactly.
Do not replace a skill invocation with a manual substitute prompt.

## Skill Arguments

### The `argument-hint` Field

`argument-hint` documents arguments expected by the SKILL.md preprocessor command in Claude engine wrappers.
It is display-only and does not affect parsing.

| Syntax | Meaning | Example |
|--------|---------|---------|
| `<arg>` | Required argument | `<file>` |
| `[arg]` | Optional argument | `[open]` |
| `<arg...>` | Variable-length (one or more) | `<keywords...>` |

### Passing Arguments to Preprocessor Commands

| Pattern | Syntax | When to Use |
|---------|--------|-------------|
| Fixed arguments | `$N` positional references | Known number of arguments |
| Variable-length arguments | `$ARGUMENTS` | Unknown/variable number of arguments |

### Fixed Arguments (`$N` Pattern)

```yaml
---
argument-hint: "<severity> <stakeholder> <description> <location>"
---
[Claude preprocessor directive invoking my-tool with "$0" "$1" "$2" "$3"]
```

Quoting `"$N"` is recommended to preserve special characters.

### Variable-Length Arguments (`$ARGUMENTS`)

Use unbraced `$ARGUMENTS`.

**Do NOT use `${ARGUMENTS}`** (braced form). See
[claude-code#18044](https://github.com/anthropics/claude-code/issues/18044#issuecomment-3928291132).

| Style | Syntax | When to Use |
|-------|--------|-------------|
| Unquoted | `$ARGUMENTS` | Tool expects separate tokens |
| Quoted | `"$ARGUMENTS"` | Tool expects full text as one argument |

```yaml
---
argument-hint: "<keywords...>"
---
[Claude preprocessor directive invoking my-tool with $ARGUMENTS]
```

```yaml
---
argument-hint: "[description]"
---
[Claude preprocessor directive invoking my-tool with "$ARGUMENTS"]
```

## Creating a New Plugin Skill

1. Create `client/plugin/skills/common/{skill-name}/` for portable skills, or the matching engine-specific directory
2. Create `SKILL.md`
3. Create `first-use.md` with full skill content
4. If dispatching to Java, register the handler in the engine image build script and call the binary launcher from `SKILL.md`
5. Skill is available as `cat:{skill-name}`

`client/plugin/skills/**` is the development source layout. Engine installations are generated from these sources.

### Java Handler Requirement

For Java-dispatched skills:

- Add launcher entry in the engine image build script's HANDLERS array
- Call launcher from `SKILL.md`
- Do **not** use `${CAT_PLUGIN_ROOT}/rules/common/skill-loading.md` as if it were a command dispatcher

`${CAT_PLUGIN_ROOT}/rules/common/skill-loading.md` returns skill content from `first-use.md`; it does not invoke Java handlers.

## Referencing Files From Skills

Use `${CAT_PLUGIN_ROOT}` for cross-directory references after applying the active engine's CAT environment rule.
Claude Code receives these variables through CAT's SessionStart environment-file injection. Codex ordinary Bash
commands do not have a global future-shell injection mechanism, so Codex-facing Bash snippets that need CAT paths must
include the bootstrap block from
`plugin/rules/codex/cat-environment.md`.
Use relative paths only for files inside the same skill directory.

Engine CAT environment rules apply to instructions the agent executes after skill loading. They do not initialize the
environment for preprocessor directives themselves. Codex-invocable `SKILL.md` preprocessor commands must not depend on
`CAT_*` variables unless they invoke a wrapper that sets them first.

```markdown
# Good
See `${CAT_PLUGIN_ROOT}/templates/issue-index.json`.

# Good
See [workflow-output.md](workflow-output.md).

# Wrong
See `templates/issue-index.json`.
```

## Skill Failure Handling

If a skill returns malformed, empty, or preprocessing-error output:

1. Inspect the raw returned output
2. Verify prerequisites are satisfied
3. Report via `/cat:feedback` with skill name, exact output, and invocation context
4. Stop and inform the user; do not manually reconstruct the expected output

Do not bypass broken skill behavior with handcrafted substitutes.
