# Plan

## Goal

Fix `GetSkill.main()` which uses `new MainClaudeHook()` as its scope. `MainClaudeHook` reads hook
JSON from stdin at construction time, but `GetSkill` is an infrastructure CLI tool invoked by the
Skill tool preprocessor with no stdin data, causing `IllegalStateException: Hook input is blank`
on every skill invocation.

## Root Cause

`client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java` line 879:

```java
try (AbstractClaudeHook scope = new MainClaudeHook())
```

`MainClaudeHook` reads hook JSON from stdin. The Skill tool invokes `get-skill` directly (not as a
hook handler), so stdin is empty → "Hook input is blank" error.

Per `java.md § Environment Variable Access`, `MainJvmScope` is the correct scope for infrastructure
CLI tools that do NOT require session vars (e.g., `GetSkill`).

## Pre-conditions

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java` exists

## Post-conditions

- [ ] A failing test exists in `GetSkillTest` that verifies `GetSkill` can be constructed with a
  `MainJvmScope`-compatible scope (i.e., tests do not depend on hook stdin)
- [ ] `GetSkill.main()` uses `new MainJvmScope()` (not `new MainClaudeHook()`)
- [ ] The declared variable type is `JvmScope` (interface) not `AbstractClaudeHook` (concrete)
- [ ] The `scope.block()` call is updated to work with `JvmScope` (use stderr + `System.exit(1)`
  for unexpected errors, since `JvmScope` has no `block()` method and `GetSkill` outputs plain text
  skill content, not hook JSON)
- [ ] Javadoc on `GetSkill` constructor (line 148) is corrected from "`MainClaudeHook`" to
  "`MainJvmScope`"
- [ ] `mvn -f client/pom.xml verify -e` passes

## TDD Approach

1. Write a failing test in `GetSkillTest` that calls `GetSkill.main(new String[]{"some-skill", agentId})`
   via a refactored `run(JvmScope, String[])` method — the test verifies the call does NOT throw
   `IllegalStateException: Hook input is blank`
2. Run the test — it fails because `main()` uses `MainClaudeHook`
3. Fix `main()` to use `MainJvmScope`
4. Run the test again — it passes

## Implementation Notes

- `AbstractClaudeHook.block()` is not available on `JvmScope`; for unexpected errors in
  infrastructure CLI tools that output plain skill text, write to stderr and exit non-zero
- Refactor `GetSkill` to expose a `run(JvmScope scope, String[] args, PrintStream out)` method
  that `main()` delegates to — this enables testability without stdin
