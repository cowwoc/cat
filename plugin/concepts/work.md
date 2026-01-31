# Workflow: Execute Issue

## Overview

Core issue execution workflow for CAT. Uses a **thin orchestrator** architecture where the main
agent delegates to 4 phase subagents, each with isolated context.

## Architecture

```
Main Agent (thin orchestrator: ~5-10K context)
    |
    +---> work-prepare subagent
    |     Loads: version-paths.md, discovery scripts
    |     Returns: {task_id, worktree_path, estimate}
    |
    +---> work-execute subagent
    |     Loads: subagent-delegation.md, delegate/SKILL.md
    |     Returns: {status, tokens, commits}
    |
    +---> work-review subagent
    |     Loads: stakeholder-review/SKILL.md, stakeholders/*.md
    |     Returns: {approval_status, concerns[]}
    |
    +---> work-merge subagent
          Loads: merge-and-cleanup.md, commit-types.md
          Returns: {merged, cleanup_status}
```

**Benefits:**
- Main agent context stays minimal (~5-10K vs ~60K+ previously)
- Each phase has fresh context for quality work
- Phase subagents load only docs they need
- User sees clean phase transitions, not internal tool calls

## Phase Subagent Skills

| Phase | Skill | Purpose |
|-------|-------|---------|
| Prepare | work-prepare | Find task, create worktree |
| Execute | work-execute | Spawn implementation subagent |
| Review | work-review | Run stakeholder reviews |
| Merge | work-merge | Squash, merge, cleanup |

Model selection follows `delegate/SKILL.md` criteria based on task complexity.

## Main Agent Responsibilities

The main agent ONLY handles:

| Area | Actions |
|------|---------|
| Orchestration | Spawn phase subagents, collect results |
| User interaction | Approval gates, questions, feedback |
| Error escalation | Surface failures, offer recovery |
| Progress display | Show phase banners |
| Decision routing | Handle status codes from subagents |

Main agent does NOT:
- Load full documentation (delegated to subagents)
- Perform implementation work
- Run stakeholder reviews directly
- Handle merge operations directly

## JSON Contracts

Each phase subagent returns structured JSON. Main agent parses and routes.

**Success statuses:** READY, SUCCESS, APPROVED, MERGED
**Failure statuses:** NO_TASKS, LOCKED, BLOCKED, FAILED, CONFLICT, ERROR

See individual skill files for full contracts:
- work-prepare/SKILL.md
- work-execute/SKILL.md
- work-review/SKILL.md
- work-merge/SKILL.md

## CRITICAL: Worktree Isolation (M101)

**ALL issue implementation work MUST happen in the issue worktree, NEVER in `/workspace` main.**

```
/workspace/                    <- MAIN WORKTREE - READ-ONLY during issue execution
+-- .worktrees/
|   +-- 0.5-issue-name/        <- ISSUE WORKTREE - All edits happen here
|       +-- parser/src/...
+-- parser/src/...             <- NEVER edit these files during issue execution
```

## Issue Discovery (M282)

**MANDATORY: Use get-available-issues.sh script. FAIL-FAST if script fails.**

The work-prepare subagent handles discovery internally. Main agent receives the result
as JSON with task_id, worktree_path, and other metadata.

## Lock Management (M097)

Locks are acquired by work-prepare subagent and released by work-merge subagent.
Main agent tracks lock status but doesn't manage locks directly.

## Error Recovery

| Error | Handler |
|-------|---------|
| Subagent returns ERROR | Main agent displays error, offers retry/abort |
| Merge conflict | work-merge returns CONFLICT, main agent asks user |
| Lock unavailable | work-prepare returns LOCKED, main agent tries next task |
| Token limit exceeded | work-execute returns warning, main agent offers decomposition |

## Parallel Execution

For independent issues, main agent can spawn multiple work-execute subagents:

```
Main Agent
    |
    +---> work-execute (issue-1)
    +---> work-execute (issue-2)
    +---> work-execute (issue-3)
    |
    v
Process completions as they arrive
```
