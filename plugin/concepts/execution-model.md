<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# CAT Execution Model

This document is the canonical reference for CAT's execution hierarchy and parallel job execution model.

## Hierarchy

CAT organizes work in a five-level hierarchy:

```
version
  └── issue
        └── sub-issue          (decomposed from a parent issue at a structural delivery boundary)
              └── job           (a batch of work items executed by one implementation subagent)
                    └── subagent  (executes one job in an isolated worktree)
```

| Level | Description |
|-------|-------------|
| **version** | A major or minor release (e.g., `v2.1`). See `plugin/concepts/hierarchy.md`. |
| **issue** | An atomic unit of work within a version. |
| **sub-issue** | A child issue created by decomposing a parent issue at a structural delivery boundary. |
| **job** | A batch of work items defined in a `### Job N` section of plan.md and executed by one implementation subagent. |
| **subagent** | An isolated Claude instance that executes one job in an isolated worktree. |

## Jobs

A **job** is defined by a `### Job N` subsection under `## Jobs` in plan.md.
Each job contains one or more top-level bullet items (`- `) listing the work to be done. Sub-items
(indented bullets with `  - `) are informational and do not spawn additional subagents.

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

### Job Parallelism

**All jobs spawn simultaneously in a single API response.** There is no sequential waiting between
jobs unless an explicit dependency between them is declared. Within each job, all items also run in
parallel (each item is handled by the same subagent).

Ordering is sequential **only when a dependency exists** between jobs (e.g., Job 2 requires output
produced by Job 1). When no dependency exists, treat all jobs as launching at the same time.

### When to Use Multiple Jobs

Use multiple jobs as the **default structure** for issues with independent work items. Separate work into jobs
whenever items can run simultaneously without conflict:

- Work items are independent (no data or file dependencies between jobs)
- Items in different jobs modify **different files** (overlapping files cause merge conflicts)

**Add sequential ordering (explicit dependency) only when:**

- A later job consumes output or side-effects produced by an earlier job
- Items in the later job require files committed by an earlier job before they can begin

**Use a single job (or no jobs) only when:**

- All items must touch the same files (parallelism would cause merge conflicts)
- The issue is small enough that a single subagent handles everything efficiently

Plans with only 1 job (or no jobs at all) use single-subagent mode, spawning one implementation
subagent with all items. Only plans with 2 or more distinct jobs spawn parallel subagents.

## Job-Based Context Management

High subagent context usage is handled by job splitting — not by decomposing the issue into sub-issues.

### Proactive Job Sizing

When writing plan.md jobs, estimate the scope of each job using file count and change complexity.
If a job would exceed 40% of the subagent context budget, split it into two jobs of roughly equal
scope before plan.md is written. A rule-of-thumb heuristic: each job should touch no more than
5 files with medium-complexity changes, or 10 files with trivial changes (rename, formatting).

### Reactive Job Splitting

After a subagent completes and returns its result, check `percent_of_context`:
- **If `percent_of_context > 40`:** Before spawning the next job, split every remaining job in
  plan.md in half. Move the second half of each remaining job's bullet items into a new job inserted
  immediately after it. Renumber all subsequent jobs to keep the sequence gapless.
- **If `percent_of_context <= 40`:** Proceed without modification.

See `plugin/concepts/token-warning.md` for how compaction events alter this flow when context is
fully exhausted.

## Sub-Issue Decomposition

A **sub-issue** is a child issue created by splitting a parent when the work spans genuinely separate
delivery boundaries. Decomposition is a structural decision — it is **never triggered by context usage
alone**. Use job splitting to manage context; use decomposition only when:

- A **merge boundary** is required between phases (phase B cannot start until phase A's code is merged
  and visible to reviewers)
- Components are **independently deliverable and reviewable** (each child issue can be reviewed, merged,
  and shipped without the other)
- Work spans **genuinely disjoint subsystems** where no shared file or interface connects them

After decomposition, the sub-issues are organized into jobs following the same dependency analysis rules
described above. See `plugin/skills/decompose-issue-agent/first-use.md` for the full decomposition workflow.

## Worktree Sharing and Push Coordination

All implementation subagents share the same worktree (`WORKTREE_PATH`). They commit and push to the same issue branch.
Each subagent must `git pull --rebase` before pushing to incorporate commits from other agents that completed
first.

When pushing encounters a non-fast-forward rejection, the subagent retries up to 3 times, rebasing before
each retry. See `plugin/concepts/parallel-execution.md` for the full push coordination protocol.

## index.json Ownership

`index.json` must be updated exactly once per issue run. The last job alphabetically owns the index.json
update:

- All jobs except the last: do NOT update `index.json`
- Last job: updates `index.json` to `"status": "closed"` in its final commit

## Architecture Summary

```
work-with-issue (main agent)
    |
    +---> Read plan.md directly
    |     Detect ## Jobs / ### Job N sections
    |     Count top-level bullet items per job
    |
    +---> [if 2+ jobs] Spawn all implementation subagents simultaneously
    |         Job 1: subagent handles all items in Job 1 (parallel)
    |         Job 2: subagent handles all items in Job 2 (parallel)
    |         Worktree: shared
    |         index.json: NO (all jobs except last) / YES (last job)
    |
    +---> Collect commits from all jobs
    |
    +---> stakeholder-review (single review of combined work)
    |
    +---> work-merge (squashes all commits, single merge)
```
