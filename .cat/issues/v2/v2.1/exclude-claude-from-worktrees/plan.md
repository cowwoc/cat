# Plan

## Goal

Add sparse-checkout configuration to worktree creation to exclude `.claude/` directory, preventing Claude Code from loading duplicate rules when walking up directory tree from worktrees.

## Pre-conditions

(none)

## Post-conditions

- [ ] New worktrees created by CAT exclude `.claude/` directory via sparse-checkout
- [ ] Sparse-checkout configuration applied correctly after `git worktree add`
- [ ] Existing worktrees remain unaffected
- [ ] Claude Code no longer loads duplicate rules from both worktree and main workspace `.claude/` directories
- [ ] Tests passing
- [ ] E2E verification: Create worktree, verify `.claude/` directory absent, confirm no duplicate rule loading
