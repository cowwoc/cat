---
paths: ["plugin/skills/**"]
---
# Skill Conventions

## Skill Instruction Location

Skill instructions (agent-facing guidance) belong in `first-use.md`, not in `SKILL.md`. The `SKILL.md` file contains
only frontmatter and preprocessor directives. Do NOT embed agent instructions directly in `SKILL.md` — otherwise
those instructions can be re-loaded multiple times within the same conversation.

**Exception — frontmatter-only skills:** Skills that are exclusively loaded via agent frontmatter `skills:` field
(never invoked dynamically via the Skill tool) may place content directly in `SKILL.md`. Deduplication logic is
irrelevant for frontmatter-loaded skills because they are injected once per agent spawn, not on repeated
invocations. Example: `stakeholder-common`, which is listed in agent frontmatter and never called via the Skill
tool at runtime.

## Preprocessor Directive Syntax

Preprocessor directives (`` !`...` `` in `SKILL.md`) are parsed by Claude Code, NOT executed through Bash.
Bash parameter expansion syntax does not work in directives.

**NOT supported in directives:**
- `${1:?error message}` — Bash parameter expansion with error on empty/unset
- `${VAR:-default}` — Bash parameter expansion with default value
- `${#VAR}` — Bash string length
- `${VAR%pattern}` — Bash pattern removal
- Any other `${...}` form beyond simple variable references

**Supported variable forms:**
- `$0` — first positional argument from the caller
- `$1`, `$2`, ... — positional arguments from the `args:` field
- `${CLAUDE_PLUGIN_ROOT}`, `${CLAUDE_SESSION_ID}`, `${CLAUDE_PROJECT_DIR}` — built-in variables
- `$ARGUMENTS` — all skill args joined with space (includes `$0` through last arg)

**Correct pattern:**
```
!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-output" "$0" get-diff "$1"`
```

**Incorrect pattern:**
```
!`"${CLAUDE_PLUGIN_DATA}/client/bin/get-output" "$0" get-diff "${1:?issue path argument is required}"`
```
