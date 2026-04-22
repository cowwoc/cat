# Plan: fix-claude-runner-native-binary

## Problem

`ClaudeRunner.buildCommand()` appends `cli.js` as the second command element after `CLAUDE_CODE_EXECPATH`.
This was correct when Claude Code was a Node.js package (`node cli.js [args...]`), but Claude Code now ships as a
native bun binary (`claude.exe`). The binary does not accept `cli.js` as an argument, and `cli.js` no longer exists
in the installation. This causes the build to fail: three tests in `EmpiricalTestRunnerTest` assert that the first
command element ends with `"node"` and the second ends with `"cli.js"`, neither of which is true anymore.

## Parent Requirements

None — infrastructure fix to restore a broken build

## Reproduction Code

```
mvn -f client/pom.xml verify -Djlink.extra.args=--enable-assertions
# FAILS:
# EmpiricalTestRunnerTest.buildCommandWithSystemPrompt
# EmpiricalTestRunnerTest.buildCommandWithoutSystemPrompt
# EmpiricalTestRunnerTest.buildCommandWithAgentType
# Reason: "nodeExecutable" must end with "node".
# nodeExecutable: "/usr/local/.../claude-code/bin/claude.exe"
```

## Expected vs Actual

- **Expected:** `buildCommand` returns `["/path/to/claude.exe", "-p", "--model", ...]`
- **Actual:** `buildCommand` returns `["/path/to/claude.exe", "/path/to/nonexistent/cli.js", "-p", "--model", ...]`

## Root Cause

`buildCommand` unconditionally appends `npmPrefix + "/lib/node_modules/@anthropic-ai/claude-code/cli.js"` as the
second element. `CLAUDE_CODE_EXECPATH` now points to the native bun binary rather than the Node.js executable,
so the binary+script pair no longer makes sense. The `cli.js` file does not exist in the current Claude Code
installation.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Tests that check command structure need updating; no runtime behavior change for callers
- **Mitigation:** Update failing tests to assert correct structure; all other tests unchanged

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/claude/hook/skills/ClaudeRunner.java` — remove `cli.js` second
  argument and the `NPM_CONFIG_PREFIX` read that was only used to build it; update `resolveClaudeBinary` Javadoc
  (no longer "Node.js executable")
- `client/src/test/java/io/github/cowwoc/cat/client/test/EmpiricalTestRunnerTest.java` — update the three
  failing test methods to assert the first element equals `CLAUDE_CODE_EXECPATH` and the second element is `-p`

## Test Cases

- [ ] `buildCommandWithSystemPrompt` — first element is `CLAUDE_CODE_EXECPATH`, second is `-p`
- [ ] `buildCommandWithoutSystemPrompt` — first element is `CLAUDE_CODE_EXECPATH`, second is `-p`
- [ ] `buildCommandWithAgentType` — first element is `CLAUDE_CODE_EXECPATH`, second is `-p`

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1: Fix buildCommand to use native binary directly

- In `ClaudeRunner.buildCommand()`, remove the line that reads `NPM_CONFIG_PREFIX` and the line that appends
  `cli.js` as the second command element. The command now starts with the `CLAUDE_CODE_EXECPATH` binary
  followed immediately by `-p`.
- Update the Javadoc on `resolveClaudeBinary` to remove the "Node.js executable" language; it returns the
  path to the Claude CLI binary.
  - Files: `client/src/main/java/io/github/cowwoc/cat/claude/hook/skills/ClaudeRunner.java`

### Job 2: Update failing tests

- In `EmpiricalTestRunnerTest`, update `buildCommandWithSystemPrompt`, `buildCommandWithoutSystemPrompt`, and
  `buildCommandWithAgentType` to:
  - Assert `command.getFirst()` ends with `"claude"` or `"claude.exe"` (matches the binary name regardless
    of platform suffix), OR assert it equals `System.getenv("CLAUDE_CODE_EXECPATH")`
  - Assert `command.get(1)` equals `"-p"` (no longer `cli.js`)
  - Update the comment from "First element should be the node executable path" to reflect native binary
  - Files: `client/src/test/java/io/github/cowwoc/cat/client/test/EmpiricalTestRunnerTest.java`

### Job 3: Remove CLAUDE_CODE_EXECPATH from production code and Javadoc

- In `ClaudeRunner.resolveClaudeBinary`, replace the `System.getenv("CLAUDE_CODE_EXECPATH")` lookup
  and its AssertionError with a simple return of `"claude"`, relying on PATH resolution at process
  launch time. Remove the `@throws AssertionError` Javadoc tag since the method no longer throws.
- Remove the `@throws AssertionError` Javadoc tag from `buildCommand` for the same reason.
- Files: `client/src/main/java/io/github/cowwoc/cat/claude/hook/skills/ClaudeRunner.java`

## Post-conditions

- [ ] `mvn -f client/pom.xml verify -e` exits 0 with no test failures
- [ ] `buildCommand` returns a list whose first element is `"claude"` and second element is `-p`
- [ ] No `cli.js` path appears anywhere in the command list
- [ ] No `CLAUDE_CODE_EXECPATH` references appear anywhere in `ClaudeRunner.java`
