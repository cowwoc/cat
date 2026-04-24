# Plan: fix-pre-bash-unquoted-variable-support

## Current State

`ShellParser.SCRIPT_ASSIGNMENT_PATTERN` only captures quoted assignment values:

```
(?m)^\s*([A-Za-z_][A-Za-z0-9_]*)=(?:"([^"`\\]*)"|'([^']*)')
```

Unquoted assignments such as:

```bash
WORKTREE_PATH=/home/node/.cat/worktrees/my-issue
```

are not captured. When a script uses an unquoted variable and then references it in a
`tee` path, the hook incorrectly reports the variable as undefined:

```
WARNING: Cannot verify Bash redirect to variable-expanded path: "${WORKTREE_PATH}/.cat/work/sprt-output.log"
Undefined variable(s): WORKTREE_PATH
```

This is a false positive — the path is fully inside the worktree.

## Target State

`ShellParser.parseScriptAssignments` also captures unquoted assignments whose values
are simple absolute or relative paths (no whitespace, no shell metacharacters). The
value is captured as-is (no quoting to strip). Chained resolution works the same way
as for quoted values.

Concretely, a script like:

```bash
WORKTREE_PATH=/home/node/.cat/worktrees/my-issue
JLINK_BIN="${WORKTREE_PATH}/client/target/jlink/bin"
cmd 2>&1 | tee "${WORKTREE_PATH}/.cat/work/output.log"
```

passes the worktree isolation check without warnings.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — extends an additive parsing path; existing quoted-literal
  and chained-reference handling is unchanged
- **Mitigation:** Unit tests in `ShellParserTest` verify the new unquoted capture, and
  integration tests in `BlockWorktreeIsolationViolationTest` verify end-to-end

## Commit Type

`bugfix:`

## Jobs

### Job 1

- In `client/src/test/java/io/github/cowwoc/cat/client/test/ShellParserTest.java` add two new test methods:
  - `parseScriptAssignmentsCapturesUnquotedLiteral`: parse `"WORKTREE_PATH=/tmp/worktrees/my-issue\n"`, assert `assignments.get("WORKTREE_PATH")` equals `"/tmp/worktrees/my-issue"`
  - `parseScriptAssignmentsResolvesChainedFromUnquoted`: parse a two-line script with `WORKTREE_PATH=/tmp/worktrees/my-issue` and `OUTPUT_FILE="${WORKTREE_PATH}/.cat/work/output.log"`, assert `assignments.get("OUTPUT_FILE")` equals `"/tmp/worktrees/my-issue/.cat/work/output.log"`
- In `client/src/test/java/io/github/cowwoc/cat/client/test/BlockWorktreeIsolationViolationTest.java` add one new test method:
  - `teeViaUnquotedWorktreeVarIsAllowed`: use a command string with `WORKTREE_PATH=<actual-worktree-dir>` (unquoted) and `cmd 2>&1 | tee "${WORKTREE_PATH}/.cat/work/output.log"`, assert `result.blocked() == false`
- Run `mvn -f client/pom.xml verify -e` — expect the 3 new tests to FAIL (they test code not yet written)
- In `client/src/main/java/io/github/cowwoc/cat/claude/hook/ShellParser.java` find `SCRIPT_ASSIGNMENT_PATTERN`. Extend the regex to add a third alternative group for unquoted values:
  - New pattern: `(?m)^\s*([A-Za-z_][A-Za-z0-9_]*)=(?:"([^"`\\]*)"|'([^']*)'|([^\s"'\`\\|&;()<>\n]+))`
  - Group 4 captures the unquoted value (no whitespace or shell metacharacters)
  - In `parseScriptAssignments`, after handling groups 2 and 3, add handling for group 4: if the captured value contains `$` and no `$(`, try `expandEnvVars`; otherwise treat as a pure literal — same logic as for quoted groups
- Run `mvn -f client/pom.xml verify -e` — all tests must pass
- Commit all changes: `bugfix: support unquoted variable assignments in pre-bash hook`
- Update index.json: set `status` to `closed` and `progress` to `100`

## Post-conditions

- `ShellParserTest.parseScriptAssignmentsCapturesUnquotedLiteral` passes
- `ShellParserTest.parseScriptAssignmentsResolvesChainedFromUnquoted` passes
- `BlockWorktreeIsolationViolationTest.teeViaUnquotedWorktreeVarIsAllowed` passes
- All existing tests continue to pass (`mvn -f client/pom.xml verify -e` exits 0)
- A script with an unquoted `WORKTREE_PATH=...` assignment followed by a `tee "${WORKTREE_PATH}/..."` no longer triggers a worktree isolation warning
