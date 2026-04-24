# Plan: fix-pre-bash-chained-variable-resolution

## Current State

`ShellParser.parseScriptAssignments` only captures double-quoted assignment values that are
pure literals (no `$`, backtick, or backslash). The regex pattern excludes `$`:

```
(?m)^\s*([A-Za-z_][A-Za-z0-9_]*)=(?:"([^"$`\\]*)"|'([^']*)')
```

When an agent writes a multi-line script like:
```bash
WORKTREE_PATH="/home/node/.cat/worktrees/my-issue"
OUTPUT_FILE="${WORKTREE_PATH}/.cat/work/sprt-output.log"
cmd > "${OUTPUT_FILE}" 2>&1
```

`OUTPUT_FILE` is not captured because its value contains `$`. When the hook tries to verify
the redirect target `${OUTPUT_FILE}`, the variable is unresolvable and the hook emits a false
positive: "WARNING: Cannot verify Bash redirect to variable-expanded path: `${OUTPUT_FILE}` —
Undefined variable(s): OUTPUT_FILE".

The final path `/home/node/.cat/worktrees/my-issue/.cat/work/sprt-output.log` is fully
within the worktree and should be allowed without any warning.

## Target State

`ShellParser.parseScriptAssignments` processes assignments in script order and expands
variable references within double-quoted values against previously accumulated assignments.
The pattern allows `$` in double-quoted values (excluding backticks for command substitution);
values containing `$(` are skipped. The chained assignment resolves to a concrete path and
the redirect is allowed.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — existing behaviour for pure literals and command substitutions is
  unchanged; only chained literal references gain resolution support
- **Mitigation:** New unit tests in `ShellParserTest` and `BlockWorktreeIsolationViolationTest`
  verify the fix and protect against regression

## Implementation Steps

### Step 1: Write failing tests

**`ShellParserTest.parseScriptAssignmentsResolvesChainedLiteralVariables`**
```java
String script = """
    WORKTREE_PATH="/tmp/worktrees/my-issue"
    OUTPUT_FILE="${WORKTREE_PATH}/.cat/work/output.log"
    """;
Map<String, String> assignments = ShellParser.parseScriptAssignments(script);
requireThat(assignments.get("OUTPUT_FILE"), "OUTPUT_FILE").
  isEqualTo("/tmp/worktrees/my-issue/.cat/work/output.log");
```

**`BlockWorktreeIsolationViolationTest.shellRedirectViaChainedVariablesInsideWorktreeIsAllowed`**
```java
String command = """
    WORKTREE_PATH="%s"
    OUTPUT_FILE="${WORKTREE_PATH}/.cat/work/output.log"
    some_command > "${OUTPUT_FILE}" 2>&1
    """.formatted(worktreeDir.toString());
// assert: result.blocked() == false
```

### Step 2: Fix `ShellParser`

**`SCRIPT_ASSIGNMENT_PATTERN`** — remove `$` from the double-quoted exclusion set:
```
// Old: [^"$`\\]*  (excludes $)
// New: [^"`\\]*   (allows $)
```

**`parseScriptAssignments`** — process sequentially, expand chained refs:
```java
if (isSingleQuoted || !rawValue.contains("$"))
    assignments.put(varName, rawValue);          // pure literal
else if (rawValue.contains("$("))
    /* skip — command substitution */;
else {
    String expanded = expandEnvVars(rawValue, assignments::get);
    if (expanded != null)
        assignments.put(varName, expanded);      // resolved chain
}
```

### Step 3: Run full test suite

```bash
mvn -f client/pom.xml verify -e
```

All tests must pass before committing.

### Step 4: Commit

Single commit: `bugfix: resolve chained literal variable references in pre-bash hook`
