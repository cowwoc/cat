# Changelog: v1.0 - Core Rewrite

> **PURPOSE**: This is a USER-FACING release notes document. Content should describe what
> END-USERS get from this version, NOT internal task names. When the version is released,
> this content is copied to the root CHANGELOG.md.

**Completed**: 2026-01-12

**Multi-Agent Orchestration Foundation**

CAT v1.0 introduces multi-agent orchestration for AI-assisted software development.

## New Features

- **MAJOR → MINOR → TASK Hierarchy**: Structured project organization with clear version boundaries
- **Multi-Agent Orchestration**: Main agent coordinates, subagents execute in dedicated worktrees
- **Token-Aware Decomposition**: Automatic task splitting to prevent context overflow
- **Parallel Execution**: Independent subtasks can run concurrently

## Commands

- `/cat:init` - Initialize CAT structure (new or existing project)
- `/cat:work` - Execute task (continues incomplete work)
- `/cat:status` - Show hierarchy status with visual tree
- `/cat:add-task`, `/cat:add-minor-version`, `/cat:add-major-version`
- `/cat:remove-task`, `/cat:remove-minor-version`, `/cat:remove-major-version`

## Skills

- `spawn-subagent` - Launch subagent with task context in isolated worktree
- `monitor-subagents` - Check status of running subagents including token usage
- `collect-results` - Gather results from completed subagents
- `merge-subagent` - Merge subagent branch into task branch
- `parallel-execute` - Orchestrate multiple independent subagents concurrently
- `decompose-task` - Split oversized tasks based on token analysis
- `token-report` - Generate detailed token usage report

## Configuration

- `cat-config.json` with yoloMode, contextLimit, targetContextUsage settings
- Interactive mode (default) with approval gates
- Yolo mode for automatic progression

---

## Internal Reference

*(This section is for development tracking only - do NOT copy to root CHANGELOG.md)*

Issues completed: 1 issue

```bash
git log --oneline --grep="Issue ID: v1.0-"
```
