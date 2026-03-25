# Plan

## Goal

Rename `plugin/rules/tee-slow-output.md` to `plugin/rules/tee-piped-output.md` and change the
guidance from requiring `tee` for "slow" commands to requiring `tee` for any command that contains
pipes. The `tee` should capture the full command output to a file before any pipes take effect,
so that the output can be re-filtered later without re-running the command.

The current heuristic ("slow commands") is ambiguous — agents cannot reliably determine what is
"slow" before running it. Piped commands are a concrete, unambiguous signal that output is being
filtered, and capturing pre-pipe output enables re-filtering without re-execution.

## Approach

Single-file rename and content rewrite. The old file `plugin/rules/tee-slow-output.md` is deleted
and replaced by `plugin/rules/tee-piped-output.md` with updated guidance. No other files in `plugin/`
reference the old filename, so no cross-file updates are needed outside the issue directory.

## Pre-conditions

(none)

## Post-conditions

- [ ] `plugin/rules/tee-slow-output.md` no longer exists
- [ ] `plugin/rules/tee-piped-output.md` exists with updated guidance
- [ ] The rule triggers on any Bash command containing a pipe (`|`), not on "slow" commands
- [ ] The `tee` is positioned to capture output before pipes take effect
- [ ] All references to `tee-slow-output` in the codebase are updated to `tee-piped-output`
- [ ] The corresponding session-level rule in `plugin/rules/` frontmatter is consistent

## Sub-Agent Waves

### Wave 1

1. Delete `plugin/rules/tee-slow-output.md` using `git rm`.

2. Create `plugin/rules/tee-piped-output.md` with the following exact content:

   - YAML frontmatter (identical to old file):
     ```yaml
     ---
     mainAgent: true
     subAgents: [all]
     ---
     ```

   - Heading: `## Tee Piped Process Output`

   - Rule body — rewrite the guidance to trigger on piped commands instead of slow commands.
     The mandatory rule: When running a Bash command that contains a pipe (`|`), insert `tee` to
     capture the full output of the first command in the pipeline to a temporary log file. This
     allows re-filtering the output later without re-running the command.

     The pattern should show:
     ```bash
     # 1. Create a temporary log file
     LOG_FILE=$(mktemp /tmp/cmd-output-XXXXXX.log)

     # 2. Capture full output before the pipe
     some-command 2>&1 | tee "$LOG_FILE" | grep "pattern"

     # 3. Later, re-filter without re-running the command
     grep -i "error" "$LOG_FILE"
     tail -50 "$LOG_FILE"
     ```

     "When NOT to tee" section:
     - **Simple pipes with no re-filter need** — e.g., `echo "hello" | wc -c` where re-filtering is unnecessary
     - **Commands in `run_in_background`** — background task output is already captured and retrievable

     Cleanup section: Delete the log file when no longer needed.

   - IMPORTANT: `plugin/rules/*.md` files are exempt from license headers (per `.claude/rules/license-header.md`
     exemptions). Do NOT add a license header to this file.

3. Update `index.json` in the issue directory to set `status` to `closed`.

4. Commit all changes with message: `refactor: rename tee-slow-output to tee-piped-output with pipe-based trigger`

   The commit type is `refactor:` because this restructures existing guidance without adding new capability.
