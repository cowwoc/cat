# Plan: document-env-var-access-convention

## Goal

Add an `Environment Variable Access` section to `.claude/rules/java.md` that documents the three-way API split
for reading Claude environment variables in Java code: `ClaudeEnv` for CLI commands, `HookInput.getSessionId()`
for hook handlers, and `scope.getEnvironmentVariable(name)` for skill directive variable substitution.

## Background

Mistake M573 was recorded when a subagent used `scope.getEnvironmentVariable("CLAUDE_SESSION_ID")` in
`GetSkill.java` (a CLI command `main()` method) to fix a test isolation problem. The correct API is
`new ClaudeEnv().getClaudeSessionId()`. The architectural distinction was documented only in
`EnforceJvmScopeEnvAccessTest.java` comments and error messages, not in `java.md` where agents look for
coding conventions.

## Files to Modify

- `.claude/rules/java.md` — add `Environment Variable Access` section before `Exception Handling`

## Execution Plan

### Step 1: Read current java.md

Read `.claude/rules/java.md` to understand existing sections and find the insertion point before
`Exception Handling`.

### Step 2: Read EnforceJvmScopeEnvAccessTest

Read `client/src/test/java/io/github/cowwoc/cat/hooks/test/EnforceJvmScopeEnvAccessTest.java` to understand
what the test enforces and extract accurate API examples.

### Step 3: Add Environment Variable Access section

Insert a new section `## Environment Variable Access` before `## Exception Handling` in `java.md` containing:

1. A 3-row table mapping context → correct API:
   | Context | Correct API |
   |---------|-------------|
   | CLI commands (`main()` methods) | `new ClaudeEnv().getClaudeSessionId()` |
   | Hook handlers | `HookInput.getSessionId()` |
   | Skill directive variable substitution | `scope.getEnvironmentVariable(name)` |

2. Code examples showing correct vs incorrect usage in CLI commands.

3. A note referencing `EnforceJvmScopeEnvAccessTest` as the mechanism that enforces `System.getenv()` restrictions.

### Step 4: Verify and commit

- Verify the section is in the right location (before Exception Handling)
- Commit as `config: add environment variable access convention to java.md`

## Post-conditions

- [ ] `java.md` contains a new section `Environment Variable Access` before `Exception Handling`
- [ ] Section includes a 3-row table mapping context to API
- [ ] Section includes correct and incorrect code examples for CLI commands
- [ ] Section references `EnforceJvmScopeEnvAccessTest`
