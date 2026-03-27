<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Parallel Subagent Execution

> See `plugin/concepts/execution-model.md` for the full execution model, hierarchy, and parallel job execution model.

CAT supports running independent work items in parallel by spawning multiple implementation subagents, each working on
its own set of items. Parallelism is the default: when plan.md contains a `## Jobs` section with
multiple `### Job N` subsections, all jobs spawn simultaneously. Sequential ordering applies only when an explicit
dependency between jobs requires one job to complete before the next begins.

## How It Works

When the work skill starts executing an issue:

1. `work-with-issue` reads plan.md directly to detect `## Jobs` sections.
2. For each `### Job N` subsection, the LLM counts the top-level bullet items.
3. **Single-subagent mode (1 job or no jobs):** Plans with only 1 job (or no jobs at all) use single-subagent
   mode, spawning one implementation subagent with all items. Plans with 2 or more distinct jobs spawn parallel
   subagents.
4. **Parallel execution (2+ jobs):** All implementation subagents spawn simultaneously in a single API response. Sequential
   ordering applies only when an explicit dependency is declared between jobs.
5. All subagents commit to the same issue branch (`v2.1-issue-name`).
6. After all jobs complete, `work-with-issue` merges their commit lists and proceeds to review and merge.

## Job Section Syntax

Create a `## Jobs` section with one `### Job N` subsection per job. Each job contains top-level bullet
items (`- `) listing the work to be done. Sub-items (indented bullets with `  - `) are ignored and do not spawn
additional subagents.

```markdown
## Jobs

### Job 1
- Implement parser module
- Add parser tests

### Job 2
- Implement formatter module
- Add formatter tests
- Run full test suite
```

In this example, Job 1 and Job 2 spawn simultaneously. If Job 2 depends on Job 1's output, declare that
dependency explicitly in plan.md so the orchestrator waits for Job 1 before delegating Job 2 items.

## When to Use Multiple Jobs

Use multiple jobs as the **default structure** for issues with independent work items. Separate work into jobs
whenever items can run simultaneously without conflict:

- Work items are independent (no data or file dependencies between jobs)
- Items assigned to different jobs modify **different files** (overlapping files cause merge conflicts)

**Use sequential ordering (explicit dependency) only when:**

- A later job consumes output or side-effects produced by an earlier job
- Items assigned to the later job require files committed by an earlier job before they can begin

**Use a single job (or no jobs) only when:**

- All items must touch the same files (parallelism would cause merge conflicts)
- The issue is small enough that a single subagent handles everything efficiently

## index.json Ownership

index.json must be updated exactly once. Ownership is determined by job position:

- **Single-job plans (Job 1 only):** Job 1 updates index.json to `"status": "closed"` in its final commit
- **Multi-job plans:** Only the last job (by alphabetical label) updates index.json to `"status": "closed"` in its
  final commit. All other jobs do NOT update index.json.

Examples:

- Plan with only Job 1: Job 1 owns and updates index.json
- Plan with Job 1 and Job 2: Job 2 owns and updates index.json; Job 1 does NOT
- Plan with Job 1, Job 2, and Job 3: Job 3 owns and updates index.json; Job 1 and Job 2 do NOT

The `work-with-issue` skill communicates this ownership in each subagent's delegation prompt.

## Worktree Sharing

All implementation subagents share the same worktree (`WORKTREE_PATH`). They commit and push to the same branch.
Each subagent must `git pull --rebase` before pushing to incorporate commits from other jobs that completed first.

The `work-merge` phase is transparent to parallelism — it squashes all commits from `TARGET_BRANCH..HEAD` regardless of
how many subagents produced them.

## Push Coordination Protocol

When pushing commits to the shared issue branch, a subagent may encounter non-fast-forward rejection (when another
agent's commits have already been pushed). The coordination protocol is:

1. **Attempt to push** the local commits to the remote
2. **If rejected (non-fast-forward):**
   - Run `git pull --rebase` to incorporate remote commits
   - Retry the push (repeat up to 3 total push attempts)
3. **If all 3 push attempts fail:**
   - Return BLOCKED status with error details
   - The main agent will handle the deadlock and retry or fail the issue

This ensures that even when subagents complete in unpredictable order, each subagent can eventually push its commits
without forcing a merge or overwriting other jobs' work.
