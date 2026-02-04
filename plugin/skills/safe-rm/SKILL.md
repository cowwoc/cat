---
description: "MANDATORY: Use instead of rm -rf or rm -r to prevent shell session breakage"
user-invocable: false
---

# Safe Remove Skill

**Purpose**: Prevent shell session breakage by verifying working directory before `rm -rf` operations.

## Critical Issue

If you delete the directory you're currently in, **all subsequent Bash commands will fail** with "Exit code 1" and Claude Code must be restarted. This is unrecoverable without restart.

## Mandatory Pre-Delete Checklist

**BEFORE any `rm -rf` command:**

```bash
# 1. Check current working directory
pwd

# 2. Verify target is NOT current directory or ancestor
# If deleting /path/to/workspace/test and pwd shows /path/to/workspace/test -> DANGER!

# 3. If in danger, change directory first
cd /path/to/workspace  # or another safe location

# 4. Then delete
rm -rf /path/to/workspace/test
```

## Safe Patterns

```bash
# SAFE - Explicit cd before delete
cd /path/to/workspace && rm -rf /path/to/workspace/test-dir

# SAFE - Delete from parent directory
cd /path/to/workspace && rm -rf test-dir

# SAFE - Use absolute paths after confirming pwd
pwd  # Shows /path/to/workspace (not /path/to/workspace/test-dir)
rm -rf /path/to/workspace/test-dir

# DANGEROUS - Deleting without checking pwd
rm -rf /path/to/workspace/test-dir  # If pwd is /path/to/workspace/test-dir, shell breaks!

# DANGEROUS - Deleting current directory
rm -rf .  # Always breaks shell

# DANGEROUS - Deleting parent of current directory
# pwd: /path/to/workspace/test-dir/subdir
rm -rf /path/to/workspace/test-dir  # Breaks shell
```

## Recovery

If shell breaks (all commands return "Exit code 1"):
1. **Restart Claude Code** - this is the only fix
2. The shell session cannot recover from a deleted working directory

## Quick Reference

| Situation | Action |
|-----------|--------|
| Deleting temp directory | `pwd` first, `cd` if needed |
| Cleaning up test files | Verify not inside target directory |
| Removing build artifacts | Use parent directory as working dir |
| Any `rm -rf` operation | **Always check `pwd` first** |
| `git worktree remove` | **Same rules apply - verify not inside worktree** |

## Git Worktree Removal

The same danger applies to `git worktree remove` - if your shell's cwd is inside the worktree being removed, all subsequent commands will fail.

```bash
# SAFE - cd to main workspace first
cd /workspace && git worktree remove /workspace/.worktrees/issue-name --force

# DANGEROUS - removing worktree while inside it
# pwd: /workspace/.worktrees/issue-name
git worktree remove /workspace/.worktrees/issue-name --force  # Shell breaks!
```

**When merging task work:**
1. Complete all git operations (merge, commit)
2. `cd /workspace` (or main workspace path)
3. THEN run `git worktree remove`

## When to Use `cd` (M398)

General guidance says "avoid `cd` to maintain working directory" - but this skill is an **exception**.

**Use `cd` when:**
- About to delete the current directory (rm -rf, git worktree remove)
- About to delete an ancestor of the current directory
- Shell state must survive a destructive operation

**Do NOT use workarounds like `git -C`:**
```bash
# WRONG - git -C changes git's cwd, NOT the shell's cwd
# Shell still breaks when worktree is deleted!
git -C /workspace worktree remove /workspace/.worktrees/task --force

# RIGHT - cd changes the shell's cwd
cd /workspace && git worktree remove /workspace/.worktrees/task --force
```

The `git -C` flag tells git to run from a different directory, but it doesn't change where
the shell thinks it is. When the worktree is deleted, the shell's cwd becomes invalid.
