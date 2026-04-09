# Plan: expand-bash-vars-in-isolation-check

## Goal

Replace the conservative block on variable-expanded Bash redirect targets with an attempt to expand the
variables using `System.getenv()`. When all variables resolve, evaluate the expanded path through the
normal worktree isolation check. When any variable is unset (expansion fails), fall back to the existing
conservative block behavior.

**Trigger:** `WARNING: Cannot verify Bash redirect to variable-expanded path: "${BANNER_STDERR_FILE}"`

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Expanding `$VAR` references using the hook process's environment may not match the agent's
  shell environment. Paths in the agent's shell may be set to values not visible in the JVM environment.
  Mitigation: only allow the path when expansion succeeds AND the expanded path passes the isolation check —
  a false "allow" can only happen if the expanded path is truly outside the project directory or inside the
  worktree, which is safe. A false "block" (expansion succeeds but env value differs from shell) is always
  safe.
- **Backtick expressions** (`\`cmd\``) cannot be expanded without running a subshell and must continue to
  block conservatively. This change only addresses `$VAR` and `${VAR}` references.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/claude/hook/util/GetSkill.java` — add
  `public static String expandEnvVars(String target)` method
- `client/src/main/java/io/github/cowwoc/cat/claude/hook/bash/BlockWorktreeIsolationViolation.java` —
  call `GetSkill.expandEnvVars()` before blocking, proceed with expanded path when successful
- `client/src/test/java/io/github/cowwoc/cat/client/test/BlockWorktreeIsolationViolationTest.java` —
  add tests for the new expansion behavior

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1: Add `expandEnvVars()` to GetSkill.java

Add the following **public static** method to `GetSkill.java`. Place it after `resolveVariable()`.

```java
/**
 * Expands {@code $VAR} and {@code ${VAR}} environment variable references in {@code target}.
 * <p>
 * Each reference is replaced with its value from {@link System#getenv(String)}.
 * Returns {@code null} if any variable in {@code target} is undefined (not set in the environment),
 * so the caller can fall back to conservative behavior.
 *
 * @param target the string containing variable references to expand
 * @return the fully expanded string, or {@code null} if any variable was undefined
 * @throws NullPointerException if {@code target} is null
 */
public static String expandEnvVars(String target)
{
  requireThat(target, "target").isNotNull();
  // Match ${VAR} and $VAR patterns (the VAR_PATTERN already matches ${VAR}; add $VAR separately)
  Matcher varMatcher = ENV_VAR_EXPAND_PATTERN.matcher(target);
  StringBuilder result = new StringBuilder();
  int lastEnd = 0;
  while (varMatcher.find())
  {
    result.append(target, lastEnd, varMatcher.start());
    // group(1) is the name from ${VAR}; group(2) is the name from $VAR
    String varName = varMatcher.group(1) != null ? varMatcher.group(1) : varMatcher.group(2);
    String value = System.getenv(varName);
    if (value == null)
      return null;
    result.append(value);
    lastEnd = varMatcher.end();
  }
  result.append(target, lastEnd, target.length());
  return result.toString();
}
```

**New pattern constant** (add at the top of the class with the other pattern constants):

```java
// Matches ${VAR} (group 1) or $VAR (group 2) for environment variable expansion.
// Does not match $ARGUMENTS, $ARGUMENTS[N], or $N positional forms — those are skill-specific.
// The $VAR form (group 2) stops at non-word characters.
private static final Pattern ENV_VAR_EXPAND_PATTERN =
  Pattern.compile("\\$\\{([^}]+)}|\\$(\\w+)");
```

**Import required** (if not already present):
- `java.util.regex.Matcher`
- `java.util.regex.Pattern`

### Job 2: Update BlockWorktreeIsolationViolation.java

Replace the conservative block in `check()` at lines 106–116. The current logic:

```java
// Block variable-expanded paths conservatively — they cannot be verified statically
if (target.contains("$") || target.contains("`"))
{
  String message = """
    WARNING: Cannot verify Bash redirect to variable-expanded path: %s
    ...""".formatted(target, context.absoluteWorktreePath());
  return Result.block(message);
}
```

Replace with:

```java
if (target.contains("`"))
{
  String message = """
    WARNING: Cannot verify Bash redirect to backtick-expanded path: %s

    Backtick expressions cannot be evaluated statically.
    If this targets a path outside your worktree, it bypasses worktree isolation.
    Use the Edit or Write tools with an explicit absolute path instead:
      Use: %s/plugin/file.txt
      Not: `command`/plugin/file.txt""".formatted(target, context.absoluteWorktreePath());
  return Result.block(message);
}
if (target.contains("$"))
{
  String expanded = GetSkill.expandEnvVars(target);
  if (expanded == null)
  {
    String message = """
      WARNING: Cannot verify Bash redirect to variable-expanded path: %s

      One or more variables in the path are unset in the hook process environment.
      If this targets a path outside your worktree, it bypasses worktree isolation.
      Use the Edit or Write tools with an explicit absolute path instead:
        Use: %s/plugin/file.txt
        Not: $UNSET_VAR/plugin/file.txt""".formatted(target, context.absoluteWorktreePath());
    return Result.block(message);
  }
  // Variable expanded successfully — replace target with the concrete path and continue
  target = expanded;
}
```

**Import required**:
- `io.github.cowwoc.cat.claude.hook.util.GetSkill`

Note: `target` in the loop body is a local copy of the loop variable at this point (the `for` loop
iterates over a `List<String>` and `target` is introduced as a local `String` in the for-each). The
reassignment `target = expanded` is valid because `target` is a local variable in the loop body, not
the list element itself. No other change to the surrounding loop is needed.

### Job 3: Add tests to BlockWorktreeIsolationViolationTest.java

Locate the existing test class (search for it under `client/src/test/`). Add the following tests:

1. **`allowsRedirectWhenEnvVarExpandsToWorktreePath()`** — happy path, variable expands to a path
   inside the worktree:
   - Sets up a `WorktreeContext` scenario where `WORKTREE_PATH=/tmp/test-worktree` exists in env
   - Issues a command like `echo foo > ${WORKTREE_PATH}/file.txt`
   - Asserts `Result.isAllowed()` (no block)

2. **`blocksRedirectWhenEnvVarExpandsOutsideWorktree()`** — variable expands to a path outside
   the worktree but inside the project:
   - `SOME_PATH=/workspace/plugin/file.txt` with the project at `/workspace`
   - Command: `echo foo > ${SOME_PATH}`
   - Asserts `Result.isBlocked()` with message containing `"isolation violation"`

3. **`blocksRedirectWhenEnvVarIsUnset()`** — variable is not defined:
   - Command: `echo foo > ${BANNER_STDERR_FILE}` where `BANNER_STDERR_FILE` is not set
   - Asserts `Result.isBlocked()` with message containing `"unset"`

4. **`blocksRedirectToBacktickExpression()`** — backtick still blocks:
   - Command: `` echo foo > `pwd`/file.txt ``
   - Asserts `Result.isBlocked()` with message containing `"backtick"`

### Job 4: Run Tests

```bash
mvn -f client/pom.xml verify -e
```

All tests must pass.

## Post-conditions

- `GetSkill.java` contains `public static String expandEnvVars(String target)` with `ENV_VAR_EXPAND_PATTERN`
- `BlockWorktreeIsolationViolation.java` attempts `GetSkill.expandEnvVars()` for `$`-containing targets
  before blocking; backtick targets still block immediately
- New tests cover the four cases: allows-in-worktree, blocks-outside-worktree, blocks-unset-var,
  blocks-backtick
- `mvn -f client/pom.xml verify -e` exits 0 with all tests passing
- `EnforceJvmScopeEnvAccessTest` still passes — no new `System.getenv()` calls added outside the
  existing whitelisted files
