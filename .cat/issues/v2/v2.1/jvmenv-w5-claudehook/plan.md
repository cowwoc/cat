---
issue: 2.1-jvmenv-w5-claudehook
parent: 2.1-remove-jvmscope-claudeenv-duplicates
sequence: 5 of 5
---

# Plan: jvmenv-w5-claudehook

## Objective

Replace `HookInput` and `HookOutput` with a `ClaudeHook` interface; introduce
`AbstractClaudeHook`, `MainClaudeHook`, and `TestClaudeHook` to provide a hook-execution
scope that combines `JvmScope` with hook-specific input/output access.

## Dependencies

- `2.1-jvmenv-w1-claudeenv` must be merged first (`AbstractJvmScope(ClaudeEnv)` constructor must exist)

## Scope

### Create ClaudeHook interface

New file: `client/src/main/java/io/github/cowwoc/cat/hooks/ClaudeHook.java`

- `public interface ClaudeHook`
- Merge all public methods from `HookInput` and `HookOutput` into this interface.
- Methods from `HookInput`: input access methods (tool name, tool input JSON, session ID, etc.)
- Methods from `HookOutput`: decision/response methods (block, allow, warn, etc.)

### Create AbstractClaudeHook

New file: `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractClaudeHook.java`

- `public abstract class AbstractClaudeHook extends AbstractJvmScope implements ClaudeHook`
- Protected constructor: `AbstractClaudeHook(ClaudeEnv claudeEnv)` calling `super(claudeEnv)`.
- Provide implementations of all `ClaudeHook` methods that are common to both main and test.

### Create MainClaudeHook

New file: `client/src/main/java/io/github/cowwoc/cat/hooks/MainClaudeHook.java`

- `public final class MainClaudeHook extends AbstractClaudeHook`
- Replaces the pattern of instantiating `HookInput` + `HookOutput` in hook handler `main()` methods.
- Constructor: reads live environment from `ClaudeEnv` and hook stdin.

### Create TestClaudeHook

New file: `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestClaudeHook.java`

- `public class TestClaudeHook extends AbstractClaudeHook`
- Replaces `HookInput` + `HookOutput` in test contexts.
- Constructor accepts injected test values.

### Update hook handlers

- Update all hook handler `main()` methods to use `MainClaudeHook` instead of constructing
  separate `HookInput` and `HookOutput` instances.
- Update all hook handler tests to use `TestClaudeHook`.

### Delete HookInput and HookOutput

- Delete `client/src/main/java/io/github/cowwoc/cat/hooks/HookInput.java`
- Delete `client/src/main/java/io/github/cowwoc/cat/hooks/HookOutput.java`

## Post-conditions

- [ ] `ClaudeHook` interface exists combining `HookInput` + `HookOutput` methods
- [ ] `AbstractClaudeHook` exists, extends `AbstractJvmScope`, implements `ClaudeHook`
- [ ] `MainClaudeHook` exists, extends `AbstractClaudeHook`
- [ ] `TestClaudeHook` exists, extends `AbstractClaudeHook`
- [ ] `HookInput.java` and `HookOutput.java` are deleted
- [ ] All hook handlers use `MainClaudeHook` instead of `HookInput`/`HookOutput`
- [ ] `mvn -f client/pom.xml test` passes
- [ ] E2E: Invoke a hook handler and verify it receives and responds to hook events correctly
