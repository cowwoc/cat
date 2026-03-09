# Plan: fix-commit-message-shell-expansion

## Problem

When commit messages contain bracket-enclosed words (e.g., `[BANG]`), zsh treats them as character class glob
patterns. If no files match the pattern, zsh fails with `no matches found: [BANG]`, causing git commit commands
or Skill tool arg passing to break silently or with confusing errors.

## Parent Requirements

None

## Reproduction Code

```bash
# This fails in zsh when no files match [BANG]
git commit -m "refactor: remove false prohibition on \$ARGUMENTS in [BANG] commands"
# Error: zsh: no matches found: [BANG]

# Also fails when passing as Skill tool args containing [BANG] substring
# (args are shell-interpolated, triggering glob expansion)
```

## Expected vs Actual

- **Expected:** Commit succeeds with the literal message text including bracket-enclosed words
- **Actual:** zsh expands `[BANG]` as a glob pattern; if no files match, command fails with
  `no matches found: [BANG]`

## Root Cause

zsh glob expansion is applied to unquoted or double-quoted strings containing `[...]` sequences. When commit
messages are passed via `-m "..."` or as Skill tool args, bracket-enclosed words like `[BANG]` trigger pattern
matching. The safe fix is to always use single-quoted HEREDOC delimiters (`<<'EOF'`) when constructing commit
messages that may contain bracket-enclosed content.

## Research Findings

Root cause: zsh treats `[...]` as a character class in glob patterns (same as filename expansion). This applies
to `-m "..."` flag and to any shell variable containing brackets when used unquoted. Safe patterns:
1. **HEREDOC with single-quoted delimiter** (`<<'EOF'` not `<<EOF`): prevents ALL variable/glob expansion in
   heredoc body — most reliable approach
2. **printf with explicit escaping**: `printf '%s' "message"` avoids some expansion but not glob expansion of
   the string argument itself when passed to the shell
3. **Bracket escaping**: `\[BANG\]` or `'[BANG]'` within the message string

The HEREDOC pattern is the authoritative fix because it prevents both variable and glob expansion in a single
construct. All other patterns require careful per-character escaping.

## Impact Notes

This caused failures when passing JSON arrays with commit messages through Skill tool args during the
`work-merge-agent` phase. The `[BANG]` substring in commit messages triggered zsh glob expansion, requiring
workarounds like passing empty arrays instead of actual commit data.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** None — documentation-only change; no behavioral changes to the commit skill itself
- **Mitigation:** Verify HEREDOC pattern works in zsh by running the example command

## Files to Modify

- `plugin/skills/git-commit-agent/first-use.md` — add "Shell Safety for Commit Messages" section
  documenting zsh glob expansion issue and the HEREDOC pattern as the safe alternative

## Test Cases

- [ ] HEREDOC with single-quoted delimiter correctly commits message containing `[BANG]` literal text
- [ ] Documentation is accurate and actionable

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Add "Shell Safety for Commit Messages" section to `plugin/skills/git-commit-agent/first-use.md`
  - Files: `plugin/skills/git-commit-agent/first-use.md`
  - Place the new section after "Anti-Patterns to Avoid" and before "Pre-Commit Efficiency"
  - Section content:
    - Explain that zsh treats `[...]` in commit messages as glob patterns
    - Show the failure: `git commit -m "... [BANG] ..."` → `no matches found: [BANG]`
    - Show the safe alternative: use HEREDOC with single-quoted delimiter
    - Code examples: wrong pattern vs correct HEREDOC pattern
    - Add checklist item: "If message contains `[` characters, use HEREDOC with single-quoted delimiter"
  - Update the "Checklist Before Committing" section to add the bracket/HEREDOC reminder
  - Commit with message: `bugfix: document safe HEREDOC pattern to prevent zsh glob expansion in commit messages`

## Post-conditions

- [ ] `plugin/skills/git-commit-agent/first-use.md` contains a "Shell Safety for Commit Messages" section
- [ ] Section documents that bracket-enclosed words (e.g., `[BANG]`) cause zsh glob expansion failures
- [ ] Section provides the HEREDOC single-quoted delimiter pattern as the safe alternative
- [ ] "Checklist Before Committing" includes a reminder about HEREDOC when message contains brackets
- [ ] E2E: Verify the documented HEREDOC pattern can commit a message containing `[BANG]` in zsh
