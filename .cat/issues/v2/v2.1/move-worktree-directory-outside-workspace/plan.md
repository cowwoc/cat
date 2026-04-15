# Plan: Move Worktree Directory Outside Workspace

## Goal
Prevent duplicate .claude/ rule loading by moving CAT worktree directory from `/workspace/.cat/work/` to `~/.cat/`.

## Problem
Currently worktrees are created at `/workspace/.cat/work/worktrees/<issue-id>/`. Each worktree contains a full git checkout including the `.claude/` directory. Claude Code loads rules from both `/workspace/.claude/` (main workspace) and `/workspace/.cat/work/worktrees/<issue-id>/.claude/` (worktree copy), causing duplicate rule loading and potential conflicts.

## Solution
Move worktree base directory to `~/.cat/worktrees/<issue-id>/` (outside `/workspace`). Testing confirms Claude Code cannot read `.claude/` directories outside `/workspace`, preventing duplicate loading while maintaining full git worktree functionality.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Existing worktrees in old location, path references in documentation
- **Mitigation:** Add migration notes, update all path references, test worktree operations

## Files to Modify
- `plugin/skills/work-prepare-agent/first-use.md` - Update WORKTREE_PATH from `${CLAUDE_PROJECT_DIR}/.cat/work/worktrees/` to `${HOME}/.cat/worktrees/`
- `plugin/concepts/worktree-isolation.md` - Update path references
- `.cat/.gitignore` - Remove `work/` entry
- All skills that reference worktree paths (grep for `.cat/work/worktrees`)

## Pre-conditions
- [ ] All dependent issues are closed

## Jobs

### Job 1: Update worktree path configuration
- Update work-prepare skill to use `${HOME}/.cat/worktrees/` instead of `${CLAUDE_PROJECT_DIR}/.cat/work/worktrees/`
  - Files: `plugin/skills/work-prepare-agent/first-use.md`
- Grep for all references to `.cat/work/worktrees` and update them
  - Files: `plugin/**/*.md`

### Job 2: Update gitignore
- Remove `work/` entry from `.cat/.gitignore`
  - Files: `.cat/.gitignore`

### Job 3: Add migration documentation
- Document the path change and cleanup steps for existing worktrees
  - Files: `plugin/concepts/worktree-isolation.md` or similar

## Post-conditions
- [ ] Worktrees created in `~/.cat/worktrees/` instead of `/workspace/.cat/work/worktrees/`
- [ ] No `.claude/` directories loaded from worktrees (verified by testing)
- [ ] All documentation updated with new paths
- [ ] `work/` removed from `.cat/.gitignore`
- [ ] All tests passing
