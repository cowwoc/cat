<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
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
    |     Receives: plan.md steps, pre-invoked skill results
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
- Skills requiring spawning (instruction-builder-agent, stakeholder-review) work correctly
- Each subagent has fresh context for quality work
- User sees clean phase transitions, not internal tool calls
- Eliminates nested subagent spawning (architecturally impossible)

## Phase Orchestration

| Phase | Handler | Purpose |
|-------|---------|---------|
| Prepare | work-prepare subagent | Find issue, create worktree |
| Execute | Inline implementation subagent | Implement issue per plan.md |
| Review | stakeholder-review skill | Run stakeholder reviews |
| Merge | work-merge subagent | Squash, merge, cleanup |

Model selection follows `delegate/SKILL.md` criteria based on issue complexity.

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
**Failure statuses:** NO_ISSUES, LOCKED, BLOCKED, FAILED, CONFLICT, ERROR

See individual skill files for full contracts:
- work-prepare/SKILL.md
- work-with-issue/SKILL.md (orchestrates execute/review/merge)
- work-merge/SKILL.md

## CRITICAL: Fix Pre-existing Problems Within Scope

When working on an issue, fix pre-existing problems if they fall within the issue's goal, scope, and post-conditions.
Do not dismiss problems as "out of scope" merely because they existed before the current commit.

The issue's acceptance criteria define what must be true when the issue is closed. If a pre-existing problem violates
those criteria, it must be fixed — regardless of when it was introduced.

**Example:** If an issue's goal is "remove all Python from the project" and pre-existing shell scripts contain inline
`python3` calls, those must be addressed.

**When editing any file, actively scan for cross-cutting rule violations in surrounding code** — not just the
lines relevant to the plan.md goal. Common violations to look for in every file you read:

- **Fail-fast violations:** Silent fallbacks (`catch (X _) { return fallback; }`, `|| "default"`, `getOrDefault`
  returning non-error values for required config). Every required value must block with a clear error, never silently
  use a fallback.
- **Null return for errors:** Methods that return `null` to signal failure instead of throwing a typed exception.
- **Fallback comments as hints:** Comments like `// fall back to X`, `// fallback:`, or `// default to` are red flags.
  Each one is a potential fail-fast violation — read the surrounding code to determine if the fallback masks an error.

Treat these like compilation errors: they must be fixed before the issue closes, regardless of whether they were
introduced before the current session.

**Automated enforcement:** The `cat:work` verify phase scans modified files for violations using grep patterns defined
in convention files' `## Enforcement` sections (as `cat-rules` blocks). Scan scope is parameterized by the `effort`
setting in `.cat/config.json` — `low` skips scanning, `medium` greps only added/modified diff lines, and `high` greps
the full content of all changed files. Detected violations appear as `Missing` criteria in the verify output and must
be resolved before the issue can be marked complete.

## CRITICAL: Worktree Isolation

**ALL issue implementation work MUST happen in the issue worktree, NEVER in `/workspace` main.**

```
/workspace/                    <- MAIN WORKTREE - READ-ONLY during issue execution
+-- .worktrees/
|   +-- 0.5-issue-name/        <- ISSUE WORKTREE - All edits happen here
|       +-- parser/src/...
+-- parser/src/...             <- NEVER edit these files during issue execution
```

**Keep the source branch isolated until the merge phase.** Do NOT merge the source branch into the target branch before
the full merge phase (squash + approval gate) completes. Stakeholder review concerns and their fixes must be committed
to the source branch in the worktree. Fast-forwarding the source branch to the target branch before review completes
bypasses isolation and causes subsequent fixes to land directly on the target branch.

**Planning commits created mid-implementation belong on the source branch.** When the add skill is invoked during
implementation (e.g., to track a newly discovered issue), the resulting `planning:` commit lands on the current
branch — the source branch. This is correct behavior. Do NOT treat such a commit as a mistake or attempt to move it to
the target branch. The planning commit will flow through the normal squash+merge process along with the implementation
commits.

## CRITICAL: Commit Before Stopping in Worktrees

**When working in a worktree, commit all changes before requesting user review or stopping work.**

The user environment cannot access uncommitted changes in your worktree. If you stop with uncommitted
changes, the user has no way to review your work.

**Required workflow:**
1. Make code changes in the worktree
2. Commit changes with descriptive message
3. THEN present work for review or stop

**Verification:** Before stopping, run `git status` in the worktree. If it shows modified files,
commit them first.

This requirement applies to all worktree work, including implementation subagents and
manual debugging sessions.

## Issue Discovery

**MANDATORY: Use `IssueDiscovery` Java class (via `work-prepare` launcher). FAIL-FAST if tool fails.**

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
| Lock unavailable | work-prepare returns LOCKED, main agent tries next issue |
| Token limit exceeded | Implementation subagent returns warning, main agent offers decomposition |

## Work-Phase Output File Paths

Work phases write output files to two distinct locations based on ownership and lifecycle:

**Verify files** — `${CLAUDE_PROJECT_DIR}/.cat/work/verify/${CLAUDE_SESSION_ID}/`
- Owner: verify subagent; Scope: session-scoped
- Ephemeral scratch files (`criteria-analysis.json`, `e2e-test-output.json`) written during confirm phase
- Read by fix subagents to understand what failed; never committed to the issue branch

**Review files** — `${WORKTREE_PATH}/.cat/work/review/`
- Owner: stakeholder agents; Scope: worktree-scoped
- Stakeholder concern detail files (e.g., `security-concerns.json`)
- Belong to the issue's worktree context; accessed by the work-review phase

**Why two locations?**

- **Verify files** are session-scoped because they are transient scratch space for a single confirm iteration. They
  don't represent issue work — they represent the result of inspecting it. Multiple verify iterations in the same
  session reuse the same directory (files are overwritten). They are never committed to the branch.
- **Review files** are worktree-scoped because they represent reviewer analysis of this specific issue's changes.
  Placing them inside `${WORKTREE_PATH}` keeps them with the issue context.

**Do NOT conflate these paths.** Review files do NOT go in `.cat/work/review/`. Verify files do NOT go in
the worktree's `.cat/` directory.

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
