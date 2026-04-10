# Plan: improve-undefined-var-warning

## Problem
When a Bash redirect target contains a variable that cannot be statically resolved,
`BlockWorktreeIsolationViolation` emits "One or more variables in the path could not be resolved"
without naming the specific undefined variable(s), making the warning harder to act on.

## Parent Requirements
None

## Reproduction Code
```
# Script with unresolvable variable
cmd > "${SESSION_DIR}/squash-complete-${ISSUE_ID}"
```

## Expected vs Actual
- **Expected:** `WARNING: Cannot verify Bash redirect to variable-expanded path: "${SESSION_DIR}/squash-complete-${ISSUE_ID}"\n\nUndefined variable(s): SESSION_DIR, ISSUE_ID\n...`
- **Actual:** `WARNING: Cannot verify Bash redirect to variable-expanded path: "${SESSION_DIR}/squash-complete-${ISSUE_ID}"\n\nOne or more variables in the path could not be resolved.\n...`

## Root Cause
`ShellParser.expandEnvVars()` returns `null` on the first undefined variable but does not report which
variable failed. The caller in `BlockWorktreeIsolationViolation` has no way to name the undefined
variable(s) without re-scanning the target string.

The fix adds `ShellParser.findUndefinedVars()` which performs a second pass to collect the names,
called only when `expandEnvVars` already returned `null`.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Only changes the text of a warning message and adds a new method. No logic changes to existing methods.
- **Mitigation:** Unit tests for `findUndefinedVars`; full `mvn verify` confirms no regressions.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/claude/hook/ShellParser.java` — add `findUndefinedVars(String, Function<String,String>)` method
- `client/src/main/java/io/github/cowwoc/cat/claude/hook/bash/BlockWorktreeIsolationViolation.java` — replace "One or more variables..." with specific variable names from `findUndefinedVars`
- `client/src/test/java/io/github/cowwoc/cat/client/test/ShellParserTest.java` — add tests for `findUndefinedVars`

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1
Steps must be executed sequentially (each step depends on the previous):

1. **Write failing tests first** (TDD) in `client/src/test/java/io/github/cowwoc/cat/client/test/ShellParserTest.java`:
   - `findUndefinedVars_oneUndefinedVariable` — path `"${SESSION_DIR}/file"` with no env lookup hits → returns `["SESSION_DIR"]`
   - `findUndefinedVars_twoUndefinedVariables` — path `"${SESSION_DIR}/squash-complete-${ISSUE_ID}"` with no env lookup hits → returns `["SESSION_DIR", "ISSUE_ID"]`
   - `findUndefinedVars_allDefined` — path `"${SESSION_DIR}/file"` with lookup returning `/tmp` for `SESSION_DIR` → returns empty list
   - Tests call `ShellParser.findUndefinedVars(target, envLookup)` with a lambda for `envLookup`
   - Tests will fail to compile until Step 2 adds the method

2. **Add `findUndefinedVars` to `ShellParser`** in `client/src/main/java/io/github/cowwoc/cat/claude/hook/ShellParser.java`:
   - Signature: `public static List<String> findUndefinedVars(String target, Function<String, String> envLookup)`
   - Javadoc: document parameters, return value, NullPointerException for null inputs
   - Body: use `ENV_VAR_EXPAND_PATTERN` (already defined in the class) to scan `target`:
     ```java
     List<String> undefined = new ArrayList<>();
     Matcher varMatcher = ENV_VAR_EXPAND_PATTERN.matcher(target);
     while (varMatcher.find())
     {
       String varName;
       if (varMatcher.group(1) != null)
         varName = varMatcher.group(1);
       else
         varName = varMatcher.group(2);
       if (envLookup.apply(varName) == null)
         undefined.add(varName);
     }
     return undefined;
     ```
   - Add `requireThat` validation for `target` and `envLookup` (same pattern as `expandEnvVars`)
   - Place the method directly after the existing `expandEnvVars(String, Function<String,String>)` method
   - The `List` import is already present in the file (`import java.util.List;`); verify before adding imports

3. **Update warning message** in `client/src/main/java/io/github/cowwoc/cat/claude/hook/bash/BlockWorktreeIsolationViolation.java`:
   - Find the block at line ~162 where `expanded == null` is handled
   - After `String expanded = ShellParser.expandEnvVars(target, mergedLookup);` returns null, call:
     ```java
     List<String> undefinedVars = ShellParser.findUndefinedVars(target, mergedLookup);
     String undefinedList = String.join(", ", undefinedVars);
     ```
   - Build the second line of the warning from `undefinedList`:
     - If `undefinedList` is non-empty (the common case): use `"Undefined variable(s): " + undefinedList`
     - If `undefinedList` is empty (e.g., the `$` comes from `$(...)` command substitution, which the pattern does not match): fall back to `"One or more variables in the path could not be resolved."`
     - Implement this as:
       ```java
       String variableLine;
       if (undefinedList.isEmpty())
         variableLine = "One or more variables in the path could not be resolved.";
       else
         variableLine = "Undefined variable(s): " + undefinedList;
       ```
   - The full updated message template (replacing lines 162-175) should be:
     ```java
     List<String> undefinedVars = ShellParser.findUndefinedVars(target, mergedLookup);
     String undefinedList = String.join(", ", undefinedVars);
     String variableLine;
     if (undefinedList.isEmpty())
       variableLine = "One or more variables in the path could not be resolved.";
     else
       variableLine = "Undefined variable(s): " + undefinedList;
     String message = """
       WARNING: Cannot verify Bash redirect to variable-expanded path: %s

       %s
       Variables must be defined earlier in the same script as a simple literal assignment, e.g.:
         VAR="/absolute/path"
         cmd > "${VAR}"

       Variables set via command substitution ($(...)) or unset variables cannot be resolved statically.
       If this targets a path outside your worktree, it bypasses worktree isolation.
       Use the Edit or Write tools with an explicit absolute path instead:
         Use: %s/plugin/file.txt
         Not: $UNSET_VAR/plugin/file.txt""".formatted(target, variableLine, context.absoluteWorktreePath());
     ```
   - Add `import java.util.List;` if not already present (check existing imports first)

4. **Run `mvn -f client/pom.xml verify -e`** and confirm exit code 0

5. **Commit all changes** in a single commit:
   - Stage: `ShellParser.java`, `BlockWorktreeIsolationViolation.java`, `ShellParserTest.java`, `index.json`
   - Commit type: `bugfix:`
   - Message: `bugfix: name undefined variables in worktree isolation warning`
   - Update `index.json` in same commit: `status: "closed"`, `resolution: "implemented"`

## Post-conditions
- [ ] Warning message names the specific undefined variable(s) (e.g., `Undefined variable(s): SESSION_DIR, ISSUE_ID`) instead of the generic "One or more variables..."
- [ ] `ShellParser.findUndefinedVars` tests pass: one undefined var, two undefined vars, all defined
- [ ] `mvn -f client/pom.xml verify -e` exits 0 with no new failures
