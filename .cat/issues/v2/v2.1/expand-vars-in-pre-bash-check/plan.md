## Goal

Improve the `pre-bash` hook's redirect-path checker so that simple literal shell variable
assignments defined earlier in the same script block are expanded before the worktree-isolation
check runs.

**Current behaviour:** Any redirect (`>`, `>>`, `2>`) whose target contains a `${VAR}` or `$VAR`
reference is unconditionally rejected with:

```
WARNING: Cannot verify Bash redirect to variable-expanded path: "${VAR}"
One or more variables in the path are unset in the hook process environment.
```

This fires even when the variable is clearly defined as a literal path in the same script, e.g.:

```bash
OUT="/workspace/.cat/work/worktrees/my-issue/.cat/work/out.txt"
some-command > "${OUT}"   # ← blocked despite OUT being a known literal
```

**Desired behaviour:** Before rejecting, the hook performs a lightweight static scan of the
script text for assignments of the form `VAR="<literal>"` or `VAR='<literal>'` that precede the
redirect. If the variable resolves to a literal path, the hook substitutes it and continues with
the normal worktree-isolation check on the resolved path.

Paths that contain command substitutions (`$(...)`, `` `...` ``), arithmetic expansions, or other
non-literal forms remain rejected as before — they cannot be safely evaluated statically.

## Scope

- `client/src/main/java/io/github/cowwoc/cat/claude/hook/ShellParser.java` — add
  `parseScriptAssignments(String script)` static method
- `client/src/main/java/io/github/cowwoc/cat/claude/hook/bash/BlockWorktreeIsolationViolation.java`
  — merge script assignments into the `envLookup` before calling `expandEnvVars`
- `client/src/test/java/io/github/cowwoc/cat/client/test/BlockWorktreeIsolationViolationTest.java`
  — add three new test cases

## Research Findings

### Existing expansion flow

`BlockWorktreeIsolationViolation.check()` already handles `$VAR` references via:

```java
if (target.contains("$"))
{
  String expanded = ShellParser.expandEnvVars(target, envLookup);
  if (expanded == null)
  {
    // block with warning
  }
  target = expanded;
}
```

`ShellParser.expandEnvVars(target, envLookup)` returns `null` when any referenced variable is
undefined (lookup returned `null`), triggering the conservative block.

### Merged lookup approach

Build a merged lookup from script-level literal assignments + the existing `envLookup`:

```java
Map<String, String> scriptVars = ShellParser.parseScriptAssignments(command);
Function<String, String> mergedLookup = varName ->
{
  String scriptValue = scriptVars.get(varName);
  if (scriptValue != null)
    return scriptValue;
  return envLookup.apply(varName);
};
String expanded = ShellParser.expandEnvVars(target, mergedLookup);
```

Script-level bindings take precedence; if the variable is not in the script, fall back to the
process environment.

### Pattern for literal assignments

Match lines that begin with a variable assignment of a pure-literal value:

```
(?m)^\s*([A-Za-z_][A-Za-z0-9_]*)=(?:"([^"$`\\]*)"|'([^']*)')
```

- `(?m)` — multiline: `^` anchors at each line start
- `[A-Za-z_][A-Za-z0-9_]*` — valid shell identifier
- `"([^"$`\\]*)"` — double-quoted literal: no `$`, no backtick, no backslash (excludes
  expansions and escape sequences)
- `'([^']*)'` — single-quoted literal: always literal, no single-quote inside

Groups: 1 = variable name, 2 = value from double-quoted form, 3 = value from single-quoted form.

Command substitution `$(...)` is excluded because the first character inside `$` is `(`, not a
word character, so it does not match `$VAR`. Unquoted assignments (`VAR=value`) are intentionally
excluded — they may contain spaces or special characters that are ambiguous without full shell
parsing.

### Updated warning message

After the change, when expansion still fails (the variable is genuinely unresolvable), the warning
should guide the agent to use literals or to define the variable as a literal:

```
WARNING: Cannot verify Bash redirect to variable-expanded path: %s

One or more variables in the path could not be resolved.
Variables must be defined earlier in the same script as a simple literal assignment, e.g.:
  VAR="/absolute/path"
  cmd > "${VAR}"

Variables set via command substitution ($(..)) or unset variables cannot be resolved statically.
If this targets a path outside your worktree, it bypasses worktree isolation.
Use the Edit or Write tools with an explicit absolute path instead:
  Use: %s/plugin/file.txt
  Not: $UNSET_VAR/plugin/file.txt
```

### Test pattern (injectable env)

Existing tests that exercise env-var expansion use the two-arg constructor:
```java
BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope, env);
```

New tests for script-level assignments use an empty (or irrelevant) env map and embed the
assignment in the command string. The script text and redirect are passed as a single multi-line
`command` string.

## Post-conditions

- [ ] A redirect to `> "${VAR}"` where `VAR="/workspace/.cat/work/worktrees/my-issue/file.txt"` is
  defined earlier in the same script is allowed (path is inside the active worktree)
- [ ] A redirect to `> "${VAR}"` where `VAR` is genuinely undefined remains blocked
- [ ] A redirect to `> "${VAR}"` where `VAR` is set via command substitution (`VAR=$(mktemp)`)
  remains blocked (cannot statically resolve)
- [ ] A redirect to `> "${VAR}"` where `VAR` resolves to a path outside the active worktree is
  blocked with the existing worktree-isolation violation message
- [ ] All existing tests pass (`mvn -f client/pom.xml verify -e`)
- [ ] New unit tests cover the three new cases above

## Jobs

### Job 1

**Step 1 — Add `ShellParser.parseScriptAssignments(String script)`**

File: `client/src/main/java/io/github/cowwoc/cat/claude/hook/ShellParser.java`

Add a new private pattern constant after the existing `ENV_VAR_EXPAND_PATTERN`:

```java
// Matches a literal shell variable assignment at the start of a line:
//   VAR="value"  (double-quoted: no $, backtick, or backslash — ensures pure literal)
//   VAR='value'  (single-quoted: always literal)
// Groups: 1=name, 2=double-quoted value, 3=single-quoted value
private static final Pattern SCRIPT_ASSIGNMENT_PATTERN =
  Pattern.compile("(?m)^\\s*([A-Za-z_][A-Za-z0-9_]*)=(?:\"([^\"$`\\\\]*)\"|'([^']*)')");
```

Add the new public static method after `expandEnvVars(String, Function)`:

```java
/**
 * Scans {@code script} for simple literal variable assignments and returns them as a map.
 * <p>
 * Only assignments whose value is a pure literal — double-quoted strings containing no
 * {@code $}, backtick, or backslash characters, or single-quoted strings — are captured.
 * Assignments via command substitution ({@code VAR=$(...)}) and unquoted assignments are
 * ignored because they cannot be evaluated statically.
 * <p>
 * When the same variable is assigned multiple times, the last assignment wins (matching
 * bash semantics where each assignment shadows the previous).
 *
 * @param script the full bash command or script text to scan
 * @return a mutable map from variable name to its literal value; empty if no literal
 *         assignments are found
 * @throws NullPointerException if {@code script} is null
 */
public static Map<String, String> parseScriptAssignments(String script)
{
  requireThat(script, "script").isNotNull();
  Map<String, String> assignments = new LinkedHashMap<>();
  Matcher assignmentMatcher = SCRIPT_ASSIGNMENT_PATTERN.matcher(script);
  while (assignmentMatcher.find())
  {
    String varName = assignmentMatcher.group(1);
    String value;
    if (assignmentMatcher.group(2) != null)
      value = assignmentMatcher.group(2);
    else
      value = assignmentMatcher.group(3);
    assignments.put(varName, value);
  }
  return assignments;
}
```

Add `import java.util.LinkedHashMap;` and `import java.util.Map;` to the imports section.

**Step 2 — Merge script assignments into `BlockWorktreeIsolationViolation.check()`**

File: `client/src/main/java/io/github/cowwoc/cat/claude/hook/bash/BlockWorktreeIsolationViolation.java`

Locate the block that handles `$`-containing targets (around line 147–164 in the current file).
Replace the existing `if (target.contains("$"))` block with:

```java
if (target.contains("$"))
{
  // First, extract any literal variable assignments defined earlier in the same script
  // (e.g. OUT="/path/file.txt") and merge them with the process environment so that
  // redirects to "${OUT}" can be statically resolved without blocking needlessly.
  Map<String, String> scriptVars = ShellParser.parseScriptAssignments(command);
  Function<String, String> mergedLookup = varName ->
  {
    String scriptValue = scriptVars.get(varName);
    if (scriptValue != null)
      return scriptValue;
    return envLookup.apply(varName);
  };
  String expanded = ShellParser.expandEnvVars(target, mergedLookup);
  if (expanded == null)
  {
    String message = """
      WARNING: Cannot verify Bash redirect to variable-expanded path: %s

      One or more variables in the path could not be resolved.
      Variables must be defined earlier in the same script as a simple literal assignment, e.g.:
        VAR="/absolute/path"
        cmd > "${VAR}"

      Variables set via command substitution ($(...)) or unset variables cannot be resolved statically.
      If this targets a path outside your worktree, it bypasses worktree isolation.
      Use the Edit or Write tools with an explicit absolute path instead:
        Use: %s/plugin/file.txt
        Not: $UNSET_VAR/plugin/file.txt""".formatted(target, context.absoluteWorktreePath());
    return Result.block(message);
  }
  // Replacing the variable reference with the concrete path lets the worktree isolation
  // check below evaluate it the same way it handles any literal redirect target.
  target = expanded;
}
```

Add `import java.util.Map;` to the imports (it may already be present for the constructor overload).

**Step 3 — Add three new test cases to `BlockWorktreeIsolationViolationTest.java`**

File: `client/src/test/java/io/github/cowwoc/cat/client/test/BlockWorktreeIsolationViolationTest.java`

Add the following three test methods. Each is self-contained with its own temporary directory:

**Test 1: literal assignment inside the script allows the redirect**

```java
/**
 * Verifies that a redirect is allowed when the variable is defined as a literal path earlier
 * in the same script block and that path is inside the active worktree.
 * <p>
 * The hook must scan the script for {@code VAR="/path"} assignments and use them to resolve
 * the redirect target without relying on the process environment.
 *
 * @throws IOException if test setup fails
 */
@Test
public void redirectAllowedWhenVariableDefinedLiterallyInScript() throws IOException
{
  Path projectPath = Files.createTempDirectory("bwiv-test-");
  try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
  {
    TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
    Path worktreeDir = TestUtils.createWorktreeDir(scope, ISSUE_ID);
    String insidePath = worktreeDir.resolve("plugin/file.txt").toString();
    // Embed the literal assignment in the same script block as the redirect
    String command = "OUT=\"" + insidePath + "\"\nsome-command > \"${OUT}\"";
    Map<String, String> env = Map.of();  // variable not in env — must come from script

    BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope, env);
    BashHandler.Result result = handler.check(
      TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

    requireThat(result.blocked(), "blocked").isFalse();
  }
  finally
  {
    TestUtils.deleteDirectoryRecursively(projectPath);
  }
}
```

**Test 2: literal assignment resolves to a path outside the worktree — blocked**

```java
/**
 * Verifies that a redirect is blocked when the variable is defined as a literal path earlier
 * in the same script but that path is outside the active worktree (inside the project directory).
 * <p>
 * Resolving the variable successfully does not grant permission — the resolved path must still
 * pass the worktree-isolation check.
 *
 * @throws IOException if test setup fails
 */
@Test
public void redirectBlockedWhenLiteralVariableResolvesOutsideWorktree() throws IOException
{
  Path projectPath = Files.createTempDirectory("bwiv-test-");
  try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
  {
    TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
    TestUtils.createWorktreeDir(scope, ISSUE_ID);
    Path outsidePath = projectPath.resolve("plugin/file.txt");
    String command = "OUT=\"" + outsidePath + "\"\nsome-command > \"${OUT}\"";
    Map<String, String> env = Map.of();

    BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope, env);
    BashHandler.Result result = handler.check(
      TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

    requireThat(result.blocked(), "blocked").isTrue();
    requireThat(result.reason(), "reason").contains("Worktree isolation violation");
  }
  finally
  {
    TestUtils.deleteDirectoryRecursively(projectPath);
  }
}
```

**Test 3: command-substitution assignment remains blocked**

```java
/**
 * Verifies that a redirect remains blocked when the variable is assigned via command
 * substitution ({@code VAR=$(mktemp)}) rather than a literal value.
 * <p>
 * Command substitutions cannot be evaluated statically, so the hook must conservatively
 * block the redirect even if the variable name appears in the script.
 *
 * @throws IOException if test setup fails
 */
@Test
public void redirectRemainsBlockedWhenVariableAssignedViaCommandSubstitution() throws IOException
{
  Path projectPath = Files.createTempDirectory("bwiv-test-");
  try (TestClaudeHook scope = new TestClaudeHook(projectPath, projectPath, projectPath))
  {
    TestUtils.writeLockFile(scope, ISSUE_ID, SESSION_ID);
    TestUtils.createWorktreeDir(scope, ISSUE_ID);
    // Command substitution: $(mktemp) cannot be statically resolved
    String command = "OUT=$(mktemp)\nsome-command > \"${OUT}\"";
    Map<String, String> env = Map.of();

    BlockWorktreeIsolationViolation handler = new BlockWorktreeIsolationViolation(scope, env);
    BashHandler.Result result = handler.check(
      TestUtils.bashHook(command, projectPath.toString(), SESSION_ID, scope));

    requireThat(result.blocked(), "blocked").isTrue();
    requireThat(result.reason(), "reason").contains("could not be resolved");
  }
  finally
  {
    TestUtils.deleteDirectoryRecursively(projectPath);
  }
}
```

**Step 4 — Update the existing `variableExpansionDollarSignIsBlocked` test**

The existing test checks that the reason contains `"unset in the hook process environment"`. Since
the warning message is being updated, update the assertion to match the new text
`"could not be resolved"`:

```java
requireThat(result.reason(), "reason").contains("could not be resolved");
```

(The test itself does not change — it already verifies that a genuinely-undefined variable blocks
the redirect. Only the expected message substring changes.)

**Step 5 — Run the full build and fix any issues**

```bash
mvn -f client/pom.xml verify -e
```

All tests must pass before committing. Fix any Checkstyle or PMD violations.

**Step 6 — Commit and update index.json**

Commit all changes with type `bugfix:`. Update
`.cat/issues/v2/v2.1/expand-vars-in-pre-bash-check/index.json` to:
```json
{
  "status": "closed",
  "resolution": "implemented",
  "dependencies": [],
  "blocks": [],
  "target_branch": "v2.1"
}
```

Include the `index.json` update in the **same commit** as the implementation changes.
