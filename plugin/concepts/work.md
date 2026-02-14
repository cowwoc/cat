# Workflow: Execute Issue

## Overview

Core issue execution workflow for CAT. Uses a **direct orchestration** architecture where the main
agent (work-with-issue skill) orchestrates phases by spawning subagents and invoking skills directly.

## Architecture

```
Main Agent (work-with-issue skill)
    |
    +---> work-prepare subagent
    |     Loads: version-paths.md, discovery scripts
    |     Returns: {issue_id, worktree_path, estimate}
    |
    +---> Implementation subagent (inline)
    |     Receives: PLAN.md steps, pre-invoked skill results
    |     Returns: {status, tokens, commits}
    |
    +---> Skill: stakeholder-review (invoked directly)
    |     Spawns: reviewer subagents internally
    |     Returns: {approval_status, concerns[]}
    |
    +---> work-merge subagent
          Loads: merge-and-cleanup.md, commit-types.md
          Returns: {merged, cleanup_status}
```

**Benefits:**
- Main agent can invoke skills directly (Skill tool available)
- Skills requiring spawning (shrink-doc, stakeholder-review) work correctly
- Each subagent has fresh context for quality work
- User sees clean phase transitions, not internal tool calls
- Eliminates nested subagent spawning (architecturally impossible)

## Phase Orchestration

| Phase | Handler | Purpose |
|-------|---------|---------|
| Prepare | work-prepare subagent | Find task, create worktree |
| Execute | Inline implementation subagent | Implement task per PLAN.md |
| Review | stakeholder-review skill | Run stakeholder reviews |
| Merge | work-merge subagent | Squash, merge, cleanup |

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
- work-with-issue/SKILL.md (orchestrates execute/review/merge)
- work-merge/SKILL.md

## CRITICAL: Worktree Isolation

**ALL issue implementation work MUST happen in the issue worktree, NEVER in `/workspace` main.**

```
/workspace/                    <- MAIN WORKTREE - READ-ONLY during issue execution
+-- .worktrees/
|   +-- 0.5-issue-name/        <- ISSUE WORKTREE - All edits happen here
|       +-- parser/src/...
+-- parser/src/...             <- NEVER edit these files during issue execution
```

## Issue Discovery

**MANDATORY: Use get-available-issues.sh script. FAIL-FAST if script fails.**

The work-prepare subagent handles discovery internally. Main agent receives the result
as JSON with issue_id, worktree_path, and other metadata.

## Lock Management

Locks are acquired by work-prepare subagent and released by work-merge subagent.
Main agent tracks lock status but doesn't manage locks directly.

## Error Recovery

| Error | Handler |
|-------|---------|
| Subagent returns ERROR | Main agent displays error, offers retry/abort |
| Merge conflict | work-merge returns CONFLICT, main agent asks user |
| Lock unavailable | work-prepare returns LOCKED, main agent tries next task |
| Token limit exceeded | Implementation subagent returns warning, main agent offers decomposition |

## Parallel Execution

For independent issues, main agent can spawn multiple work-with-issue skills:

```
Main Agent (work skill)
    |
    +---> Skill: work-with-issue (issue-1)
    +---> Skill: work-with-issue (issue-2)
    +---> Skill: work-with-issue (issue-3)
    |
    v
Process completions as they arrive
```
