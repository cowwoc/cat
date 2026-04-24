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

## Implementation Steps

### Step 1: Write failing tests

**`ShellParserTest.parseScriptAssignmentsCapturesUnquotedLiteral`**
```java
String script = "WORKTREE_PATH=/tmp/worktrees/my-issue\n";
Map<String, String> assignments = ShellParser.parseScriptAssignments(script);
requireThat(assignments.get("WORKTREE_PATH"), "WORKTREE_PATH")
    .isEqualTo("/tmp/worktrees/my-issue");
```

**`ShellParserTest.parseScriptAssignmentsResolvesChainedFromUnquoted`**
```java
String script = """
    WORKTREE_PATH=/tmp/worktrees/my-issue
    OUTPUT_FILE="${WORKTREE_PATH}/.cat/work/output.log"
    """;
Map<String, String> assignments = ShellParser.parseScriptAssignments(script);
requireThat(assignments.get("OUTPUT_FILE"), "OUTPUT_FILE")
    .isEqualTo("/tmp/worktrees/my-issue/.cat/work/output.log");
```

**`BlockWorktreeIsolationViolationTest.teeViaUnquotedWorktreeVarIsAllowed`**
```java
String command = """
    WORKTREE_PATH=%s
    cmd 2>&1 | tee "${WORKTREE_PATH}/.cat/work/output.log"
    """.formatted(worktreeDir.toString());
// assert: result.blocked() == false
```

### Step 2: Fix `ShellParser`

Add a second pattern for unquoted values, or extend `SCRIPT_ASSIGNMENT_PATTERN` with a
third alternative group that captures `[^\s"'$`\\|&;()<>]` (no whitespace, no shell
metacharacters):

```
// New third alternative: unquoted value — no whitespace or shell metacharacters
(?m)^\s*([A-Za-z_][A-Za-z0-9_]*)=(?:"([^"`\\]*)"|'([^']*)'|([^\s"'`\\|&;()<>\n]+))
```

Group 4 captures the unquoted value. In `parseScriptAssignments`, treat group 4 the
same as group 2/3: if it contains `$` and no `$(`, try `expandEnvVars`; otherwise
treat as a pure literal.

### Step 3: Run full test suite

```bash
mvn -f client/pom.xml verify -e
```

All tests must pass before committing.

### Step 4: Commit

Single commit: `bugfix: support unquoted variable assignments in pre-bash hook`
