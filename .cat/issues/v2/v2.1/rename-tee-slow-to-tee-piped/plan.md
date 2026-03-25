# Plan

## Goal

Rename `plugin/rules/tee-slow-output.md` to `plugin/rules/tee-piped-output.md` and change the
guidance from requiring `tee` for "slow" commands to requiring `tee` for any command that contains
pipes. The `tee` should capture the full command output to a file before any pipes take effect,
so that the output can be re-filtered later without re-running the command.

The current heuristic ("slow commands") is ambiguous — agents cannot reliably determine what is
"slow" before running it. Piped commands are a concrete, unambiguous signal that output is being
filtered, and capturing pre-pipe output enables re-filtering without re-execution.

## Pre-conditions

(none)

## Post-conditions

- [ ] `plugin/rules/tee-slow-output.md` no longer exists
- [ ] `plugin/rules/tee-piped-output.md` exists with updated guidance
- [ ] The rule triggers on any Bash command containing a pipe (`|`), not on "slow" commands
- [ ] The `tee` is positioned to capture output before pipes take effect
- [ ] All references to `tee-slow-output` in the codebase are updated to `tee-piped-output`
- [ ] The corresponding session-level rule in `plugin/rules/` frontmatter is consistent
