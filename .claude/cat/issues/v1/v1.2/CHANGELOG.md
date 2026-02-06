# Changelog: v1.2 - Auto-Decomposition

> **PURPOSE**: This is a USER-FACING release notes document. Content should describe what
> END-USERS get from this version, NOT internal task names. When the version is released,
> this content is copied to the root CHANGELOG.md.

**Completed**: 2026-01-13

**Auto-Decomposition and Parallel Execution**

Major enhancement enabling proactive task decomposition and parallel subagent execution.

## New Features

- **Auto-Decomposition**: Tasks exceeding context threshold (default 40% of 200K = 80K tokens) are automatically
  decomposed before execution
- **Parallel Execution**: Independent subtasks spawn concurrent subagents in sub-task-based execution
- **Task Size Estimation**: Pre-execution analysis estimates token requirements from PLAN.md
- **Mandatory Token Reporting**: Subagent execution reports always show token usage and compaction events

## Improvements

- Task-level locking prevents concurrent execution of same task
- Lightweight completion markers (`.completion.json`) for efficient monitoring
- Mandatory SESSION_ID verification before worktree creation (M057)
- Hook inheritance documentation for subagent prompts (A008)

## Bugfixes

- Fix subagent cleanup to happen BEFORE approval gate presentation (M053)
- Fix approval gate to require re-presentation after feedback (M052)
- Fix bugfix tests to be in same commit as fix (M051)
- Fix checkbox rendering in cat:status output (M056)

## Git Safety Hooks

- Block `git merge --no-ff` (enforce linear history)
- Warn on `git filter-branch` (recommend git-filter-repo)
- Block deletion of `.git/refs/original` without explicit request
- Block `git reflog expire --expire=now` (preserve recovery options)

---

## Internal Reference

*(This section is for development tracking only - do NOT copy to root CHANGELOG.md)*

Issues completed: 6 issues

```bash
git log --oneline --grep="Issue ID: v1.2-"
```
