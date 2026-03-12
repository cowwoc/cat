# Bugfix: fix-add-skill-multiline-arguments

## Problem

When users invoke `/cat:add` with a multiline description, the shell `eval` breaks with a parse error because
`$ARGUMENTS` is unquoted in the SKILL.md preprocessor directive. Newlines in the argument text are interpreted as
command separators by the shell, causing errors like `(eval):5: parse error near 'When'`.

## Root Cause

The `add` and `add-agent` SKILL.md files use unquoted `$ARGUMENTS` in their `!` backtick preprocessor directives:

```
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" add "${CLAUDE_SESSION_ID}" $ARGUMENTS`
```

When the shell evaluates this, unquoted `$ARGUMENTS` undergoes word-splitting AND newline interpretation. Multiline
text causes the shell to treat each line as a separate command, producing parse errors.

## Satisfies

None - usability improvement

## Post-conditions

- [ ] `/cat:add` accepts multiline descriptions without shell parse errors
- [ ] `/cat:add-agent` accepts multiline descriptions without shell parse errors
- [ ] Single-line arguments continue to work as before
- [ ] The skill-loader Java code correctly receives the full multiline text as a single argument
- [ ] `skill-loading.md` documents when to use quoted vs unquoted `$ARGUMENTS`

## Implementation

1. Quote `$ARGUMENTS` in `plugin/skills/add/SKILL.md`:
   ```
   !`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" add "${CLAUDE_SESSION_ID}" "$ARGUMENTS"`
   ```

2. Quote `$ARGUMENTS` in `plugin/skills/add-agent/SKILL.md`:
   ```
   !`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" add-agent "$ARGUMENTS"`
   ```

3. Update `plugin/concepts/skill-loading.md` to document that skills accepting free-text descriptions should use
   quoted `"$ARGUMENTS"` to preserve multiline input, while skills expecting separate flag-style tokens should keep
   unquoted `$ARGUMENTS`.

4. Verify the skill-loader Java code handles the multiline single argument correctly (it receives the full text as
   one `args[]` element instead of word-split tokens).

## Pre-conditions

- [ ] All dependent issues are closed

## Files to Modify

- `plugin/skills/add/SKILL.md` — quote `$ARGUMENTS`
- `plugin/skills/add-agent/SKILL.md` — quote `$ARGUMENTS`
- `plugin/concepts/skill-loading.md` — document quoted vs unquoted guidance
