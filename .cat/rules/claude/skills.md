---
paths: ["client/plugin/skills/**"]
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Claude Skill Directives

## Preprocessor Directive Syntax

Preprocessor directives (`` !`...` `` in `SKILL.md`) are parsed by Claude Code, not executed through Bash. Bash
parameter expansion syntax does not work in directives.

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
