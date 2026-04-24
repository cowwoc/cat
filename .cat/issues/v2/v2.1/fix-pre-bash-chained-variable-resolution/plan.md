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

## Goal

Fix `ShellParser.parseScriptAssignments` so that chained variable references within
double-quoted assignment values are resolved, eliminating false-positive "undefined variable"
warnings from the pre-bash hook when the final resolved path is within the worktree.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None — existing behaviour for pure literals and command substitutions is
  unchanged; only chained literal references gain resolution support
- **Mitigation:** New unit tests in `ShellParserTest` and `BlockWorktreeIsolationViolationTest`
  verify the fix and protect against regression

## Post-conditions

- `ShellParser.parseScriptAssignments` resolves chained double-quoted variable references
  (e.g., `OUTPUT_FILE="${WORKTREE_PATH}/subpath"` where `WORKTREE_PATH` was set earlier in the
  same script) to their concrete values.
- Values containing `$(` (command substitution) continue to be skipped.
- Pure literal assignments (no `$`) continue to be captured unchanged.
- Single-quoted assignments continue to be captured verbatim.
- The existing `BlockWorktreeIsolationViolation` hook no longer emits a false-positive warning
  when a redirect target resolves to a path within the worktree via a chained variable.
- All tests in `client/` pass (`mvn -f client/pom.xml verify -e` exits 0).

## Jobs

### Job 1

- **Write failing test `ShellParserTest.parseScriptAssignmentsResolvesChainedLiteralVariables`**
  in `client/src/test/java/io/github/cowwoc/cat/client/test/ShellParserTest.java`:
  ```java
  String script = """
      WORKTREE_PATH="/tmp/worktrees/my-issue"
      OUTPUT_FILE="${WORKTREE_PATH}/.cat/work/output.log"
      """;
  Map<String, String> assignments = ShellParser.parseScriptAssignments(script);
  requireThat(assignments.get("OUTPUT_FILE"), "OUTPUT_FILE").
    isEqualTo("/tmp/worktrees/my-issue/.cat/work/output.log");
  ```
  Confirm it fails before the fix.

- **Write failing test `BlockWorktreeIsolationViolationTest.shellRedirectViaChainedVariablesInsideWorktreeIsAllowed`**
  in `client/src/test/java/io/github/cowwoc/cat/client/test/BlockWorktreeIsolationViolationTest.java`.
  Follow the same structure as `redirectAllowedWhenVariableDefinedLiterallyInScript` (lines 853–876): use
  `Files.createTempDirectory`, `TestClaudeHook`, `TestUtils.writeLockFile`, `TestUtils.createWorktreeDir`,
  and `TestUtils.bashHook`. The command string must embed the chained assignment pattern:
  ```java
  String command = "WORKTREE_PATH=\"" + worktreeDir + "\"\n" +
                   "OUTPUT_FILE=\"${WORKTREE_PATH}/.cat/work/output.log\"\n" +
                   "some_command > \"${OUTPUT_FILE}\" 2>&1";
  Map<String, String> env = Map.of();  // variable not in env — must come from script
  // assert: result.blocked() == false
  requireThat(result.blocked(), "blocked").isFalse();
  ```
  Confirm it fails before the fix.

- **Fix `ShellParser`** in `client/src/main/java/io/github/cowwoc/cat/claude/hook/ShellParser.java`:
  1. Update `SCRIPT_ASSIGNMENT_PATTERN` — remove `$` from the double-quoted exclusion set:
     ```
     // Old: [^"$`\\]*  (excludes $)
     // New: [^"`\\]*   (allows $)
     ```
  2. Update `parseScriptAssignments` — replace the existing `assignments.put(varName, value)`
     call inside the `while (assignmentMatcher.find())` loop with sequential expansion logic.
     Group 2 is the double-quoted capture; group 3 is the single-quoted capture. Derive
     `isSingleQuoted` from `assignmentMatcher.group(3) != null`. The replacement block:
     ```java
     String varName = assignmentMatcher.group(1);
     String rawValue;
     boolean isSingleQuoted;
     if (assignmentMatcher.group(2) != null)
     {
         rawValue = assignmentMatcher.group(2);
         isSingleQuoted = false;
     }
     else
     {
         rawValue = assignmentMatcher.group(3);
         isSingleQuoted = true;
     }
     if (isSingleQuoted || !rawValue.contains("$"))
         assignments.put(varName, rawValue);          // pure literal
     else if (rawValue.contains("$("))
         /* skip — command substitution */;
     else
     {
         String expanded = expandEnvVars(rawValue, assignments::get);
         if (expanded != null)
             assignments.put(varName, expanded);      // resolved chain
     }
     ```

- **Run full test suite** to confirm all tests pass:
  ```bash
  mvn -f client/pom.xml verify -e
  ```

- **Commit** with message:
  `bugfix: resolve chained literal variable references in pre-bash hook`
  Update `index.json`: `status: closed`, `progress: 100%`.
