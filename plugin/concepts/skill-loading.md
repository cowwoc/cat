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

`argument-hint` documents arguments expected by the SKILL.md preprocessor command (`!` backtick directive).
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
!`"${CLAUDE_PLUGIN_DATA}/client/bin/my-tool" "$0" "$1" "$2" "$3"`
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
!`"${CLAUDE_PLUGIN_DATA}/client/bin/my-tool" $ARGUMENTS`
```

```yaml
---
argument-hint: "[description]"
---
!`"${CLAUDE_PLUGIN_DATA}/client/bin/my-tool" "$ARGUMENTS"`
```

## Creating a New Plugin Skill

1. Create `plugin/skills/{skill-name}/`
2. Create `SKILL.md`
3. Create `first-use.md` with full skill content
4. If dispatching to Java, register handler in `client/build-jlink.sh` and call the binary launcher from `SKILL.md`
5. Skill is available as `cat:{skill-name}`

### Java Handler Requirement

For Java-dispatched skills:

- Add launcher entry in `client/build-jlink.sh` HANDLERS array
- Call launcher from `SKILL.md`
- Do **not** use `plugin/rules/skill-loading.md` as if it were a command dispatcher

`plugin/rules/skill-loading.md` returns skill content from `first-use.md`; it does not invoke Java handlers.

## Referencing Files From Skills

Use `${CLAUDE_PLUGIN_ROOT}` for cross-directory references.
Use relative paths only for files inside the same skill directory.

```markdown
# Good
See `${CLAUDE_PLUGIN_ROOT}/templates/issue-index.json`.

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