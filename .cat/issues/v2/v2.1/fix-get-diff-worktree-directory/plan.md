# Fix GetDiffOutput Worktree Directory

## Goal

Fix `GetDiffOutput` to run git commands against the worktree directory instead of the main workspace when invoked
during `/cat:work` approval gates.

## Problem

`GetDiffOutput.getOutput(String[] args)` falls back to `scope.getClaudeProjectDir()` (which is always `/workspace`)
when no `--project-dir` flag is given. When running in a worktree (e.g.,
`/workspace/.cat/worktrees/2.1-some-issue/`):

- Target branch detection correctly reads CWD via `System.getProperty("user.dir")` and finds e.g. `v2.1`
- But all git commands (`git diff`, `git diff --stat`, `git diff --name-only`) run against `/workspace`
- In `/workspace`, HEAD is already `v2.1`, so `git diff v2.1..HEAD` returns zero changes
- The skill returns "No changes detected between v2.1 and HEAD" even though the worktree has changes

**Evidence:** Session `6095d028` JSONL line 6306 shows the skill returning "No changes detected" for an issue with 92
changed files.

## Root Cause

The skill is invoked as a preprocessor (`!` directive in `get-diff/first-use.md`) during skill loading. The
preprocessor inherits `CLAUDE_PROJECT_DIR` from Claude Code's environment, which is always the main workspace root.
Unlike git-* skills (which accept explicit directory parameters from subagents), `GetDiffOutput` has no way to receive
the worktree path.

## Scope

- **In scope:** Fix `GetDiffOutput` to use the correct directory for git operations in worktree contexts
- **Out of scope:** Other `Get*Output` classes (they use `CLAUDE_PROJECT_DIR` for file operations on project-level
  files, which is correct)

## Post-conditions

- [ ] When `GetDiffOutput` is invoked from a worktree, git commands run against the worktree (not `/workspace`)
- [ ] When `GetDiffOutput` is invoked from the main workspace, behavior is unchanged
- [ ] Target branch detection and git operations use the same directory
- [ ] Existing tests pass
- [ ] New test verifies worktree directory handling

## Implementation

When no `--project-dir` is provided, `getOutput(String[] args)` should prefer `user.dir` (the JVM's CWD) over
`scope.getClaudeProjectDir()`. The CWD is correct because the agent has already `cd`'d into the worktree before
invoking the skill. This aligns target branch detection (which already uses `user.dir`) with git operations.

**Change in `GetDiffOutput.getOutput(String[] args)`:**
```java
if (projectDir == null)
  projectDir = Path.of(System.getProperty("user.dir"));
```

This is safe because:
- In worktree context: CWD is the worktree path (correct)
- In main workspace context: CWD is `/workspace` (same as `CLAUDE_PROJECT_DIR`, so behavior unchanged)
- The `--project-dir` flag still overrides both (for tests and explicit callers)
