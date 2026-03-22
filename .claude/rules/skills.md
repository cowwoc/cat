# Skill Conventions

## Skill Instruction Location (M580)

Skill instructions (agent-facing guidance) belong in `first-use.md`, not in `SKILL.md`. The `SKILL.md` file contains
only frontmatter and preprocessor directives. Do NOT embed agent instructions directly in `SKILL.md` — they will not
be loaded correctly on subsequent invocations and bypass the per-agent deduplication logic in `GetSkill`.

## Preprocessor Directive Syntax (M581)

Preprocessor directives (`` !`...` `` in `SKILL.md`) are parsed by `GetSkill`/Claude Code, NOT executed through Bash.
Bash parameter expansion syntax does not work in directives.

**NOT supported in directives:**
- `${1:?error message}` — Bash parameter expansion with error on empty/unset
- `${VAR:-default}` — Bash parameter expansion with default value
- `${#VAR}` — Bash string length
- `${VAR%pattern}` — Bash pattern removal
- Any other `${...}` form beyond simple variable references

**Supported variable forms:**
- `$0` — agent ID (first positional arg when invoked via `$ARGUMENTS`)
- `$1`, `$2`, ... — positional arguments from the `args:` field
- `${CLAUDE_PLUGIN_ROOT}`, `${CLAUDE_SESSION_ID}`, `${CLAUDE_PROJECT_DIR}` — built-in variables
- `$ARGUMENTS` — all skill args joined with space (includes `$0` through last arg)

**Correct pattern:**
```
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-output" "$0" get-diff "$1"`
```

**Incorrect pattern:**
```
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-output" "$0" get-diff "${1:?issue path argument is required}"`
```
