<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan: fix-diff-against-target-branch

## Problem

`cat:get-diff-agent` produces a 244K+ line diff (2219 files) when an issue branch only changed ~10 files.
The skill ignores the `target_branch` argument passed by the agent and instead auto-detects the base branch by
running `TargetBranchDetector.detectTargetBranch(projectRoot)` on the main workspace directory (`/workspace`), which
is on branch `v2.1`. The `VERSION_BRANCH_PATTERN` check recognizes `v2.1` as a version branch and returns `"main"` as
the target, producing `git diff main..HEAD` instead of `git diff v2.1..HEAD`.

## Parent Requirements

None — infrastructure/tooling bugfix

## Root Cause

The `SKILL.md` preprocessor directive for `get-diff-agent` only passes `$0` (session ID) to `skill-loader`, not `$1`
(worktree path) or `$2` (target branch):

```
!`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" get-diff-agent "$0"`
```

`GetDiffOutput.getOutput(String[] args)` consequently receives no `--project-dir` and no `--target-branch`.
It falls back to `scope.getClaudeProjectDir()` (`/workspace`) as the project root.
`TargetBranchDetector.detectTargetBranch(/workspace)` then:
1. `detectFromWorktreePath` fails (NPE: `Path.of("/workspace").getParent().getFileName()` is null)
2. `detectFromBranchName` gets branch `v2.1`, matches `VERSION_BRANCH_PATTERN`, returns `"main"`

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Changes argument parsing in `GetDiffOutput`; other callers that use the no-args form must
  still work
- **Mitigation:** Existing `GetDiffOutputTest` plus new regression tests

## Files to Modify

- `plugin/skills/get-diff-agent/SKILL.md` — pass `--project-dir "$1"` and `--target-branch "$2"` to skill-loader
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetDiffOutput.java` — accept `--target-branch BRANCH`
  argument in `getOutput(String[] args)`; when provided, use it directly (bypass auto-detection)

## Test Cases

- [ ] When `--target-branch v2.1` is provided, diff uses `v2.1..HEAD` (not `main..HEAD`)
- [ ] When `--project-dir` is provided but `--target-branch` is absent, auto-detection still runs (backward compat)
- [ ] When neither flag is provided, auto-detection runs as before
- [ ] `detectFromWorktreePath` null-safety: calling with `/workspace` (no "worktrees" parent) returns null safely

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- In `plugin/skills/get-diff-agent/SKILL.md`, update the `!` preprocessor directive from:
  ```
  !`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" get-diff-agent "$0"`
  ```
  to:
  ```
  !`"${CLAUDE_PLUGIN_ROOT}/client/bin/skill-loader" get-diff-agent "$0" --project-dir "$1" --target-branch "$2"`
  ```
  - Files: `plugin/skills/get-diff-agent/SKILL.md`

- In `GetDiffOutput.getOutput(String[] args)`, add handling for `--target-branch BRANCH`:
  - Add a `targetBranch` local variable (initially null)
  - In the arg-parsing loop, handle `--target-branch`: read the next arg as the branch name, store in `targetBranch`
  - In `getOutput(Path projectDir)`, add an overload `getOutput(Path projectDir, String targetBranch)` that accepts
    an explicit branch (or null to use auto-detection)
  - When `targetBranch` is non-null (explicitly provided), skip `TargetBranchDetector.detectTargetBranch()` entirely
  - Update `getOutput(String[] args)` to call `getOutput(projectDir, targetBranch)` instead of `getOutput(projectDir)`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetDiffOutput.java`

- In `GetDiffOutput.GitHelper.detectFromWorktreePath`, add null check:
  - Before calling `projectRoot.getParent().getFileName().toString()`, check that both
    `projectRoot.getParent()` and `projectRoot.getParent().getFileName()` are non-null
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetDiffOutput.java`

- In `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetDiffOutputTest.java`, add tests:
  - `targetBranchArgOverridesDetection()`: create a temp git repo with both `main` and `v2.1` branches;
    invoke `getOutput(worktreePath, "v2.1")` and verify the diff base is `v2.1`, not `main`
  - `detectFromWorktreePath_nullSafe()`: call `TargetBranchDetector.detectTargetBranch(Path.of("/tmp"))` and
    verify it does not throw NPE (returns null or a branch name without exception)
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetDiffOutputTest.java`

### Wave 2

- Run the full test suite and fix any failures:
  ```bash
  mvn -f client/pom.xml test
  ```
- Update STATE.md to mark implementation complete
  - Files: `.claude/cat/issues/v2/v2.1/fix-diff-against-target-branch/STATE.md`

## Post-conditions

- [ ] `GetDiffOutput.getOutput(String[] args)` parses `--target-branch BRANCH` and uses it as the diff base
- [ ] `SKILL.md` passes `--target-branch "$2"` so the agent-provided target branch is forwarded to Java handler
- [ ] `detectFromWorktreePath` does not throw NPE when called with non-worktree paths (e.g., `/workspace`, `/tmp`)
- [ ] All new test cases pass
- [ ] All existing `GetDiffOutputTest` tests still pass (`mvn -f client/pom.xml test` exits 0)
- [ ] E2E: `cat:get-diff-agent SESSION_ID /path/to/worktree/2.1-some-issue v2.1` produces a diff showing
  only the issue's commits against `v2.1`, not all changes between `main` and `HEAD`
