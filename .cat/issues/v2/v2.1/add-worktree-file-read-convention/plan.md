# Plan

## Goal

Add a plugin rule documenting that files inside a locked worktree are accessed by exactly one Claude
instance at a time. An agent that holds the issue lock may treat any file it has already read as stable:
its content will not change under the agent's feet, so the agent should reference the in-context copy
instead of re-reading the file from disk.

This eliminates redundant Read tool calls and is safe because:
- The issue lock guarantees single-instance access to the worktree
- No other agent can write to the worktree while the lock is held

The rule applies only inside a worktree the agent has locked. It does not apply to shared paths (main
workspace, plugin cache, shared config) that multiple instances can write concurrently.

## Pre-conditions

(none)

## Post-conditions

- [ ] A new plugin rule file `plugin/rules/worktree-file-reads.md` exists describing the single-read
      convention
- [ ] The rule states: within a locked worktree, each file should be read at most once per conversation;
      subsequent references should use the in-context copy
- [ ] The rule explicitly scopes itself to files inside the locked worktree (not shared paths)
- [ ] No regressions in existing plugin rules
