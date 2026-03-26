<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# CAT Execution Model

This document is the canonical reference for CAT's execution hierarchy and wave-based parallelism model.

## Hierarchy

CAT organizes work in a five-level hierarchy:

```
version
  └── issue
        └── sub-issue          (decomposed from a parent issue at a structural delivery boundary)
              └── wave          (a batch of work items executed by one subagent)
                    └── subagent  (executes one wave in an isolated worktree)
```

| Level | Description |
|-------|-------------|
| **version** | A major or minor release (e.g., `v2.1`). See `plugin/concepts/hierarchy.md`. |
| **issue** | An atomic unit of work within a version. |
| **sub-issue** | A child issue created by decomposing a parent issue at a structural delivery boundary. |
| **wave** | A batch of work items assigned to one subagent. Items within a wave run in parallel; waves run sequentially only when a dependency exists between them. |
| **subagent** | An isolated Claude instance that executes one wave inside a dedicated git worktree. |

## Waves

A **wave** is defined by a `### Wave N` subsection under `## Execution Waves` (or `## Sub-Agent Waves`) in plan.md.
Each wave contains one or more top-level bullet items (`- `) listing the work to be done. Sub-items
(indented bullets with `  - `) are informational and do not spawn additional subagents.

```markdown
## Execution Waves

### Wave 1
- Implement parser module
- Add parser tests

### Wave 2
- Implement formatter module
- Add formatter tests
- Run full test suite
```

### Wave Parallelism

**All waves spawn simultaneously in a single API response.** There is no sequential waiting between
waves unless an explicit dependency between them is declared. Within each wave, all items also run in
parallel (each item is handled by the same subagent).

Ordering is sequential **only when a dependency exists** between waves (e.g., Wave 2 requires output
produced by Wave 1). When no dependency exists, treat all waves as launching at the same time.

### When to Use Multiple Waves

Use multiple waves **only** when:

- Work items are genuinely independent (no data or file dependencies between waves)
- Items in different waves modify **different files** (overlapping files cause merge conflicts)
- The parallelism benefit justifies the added complexity

**Do NOT use multiple waves when:**

- Items must run in sequence (later items consume output from earlier ones)
- Items modify the same files
- The issue is small enough that parallelism adds no benefit

Plans with only 1 wave (or no waves at all) use single-subagent mode, spawning one implementation
subagent with all items. Only plans with 2 or more distinct waves spawn parallel subagents.

## Wave-Based Context Management

High subagent context usage is handled by splitting waves — not by decomposing the issue into sub-issues.

### Proactive Wave Sizing

When writing plan.md waves, estimate the scope of each wave using file count and change complexity.
If a wave would exceed 40% of the subagent context budget, split it into two waves of roughly equal
scope before plan.md is written. A rule-of-thumb heuristic: each wave should touch no more than
5 files with medium-complexity changes, or 10 files with trivial changes (rename, formatting).

### Reactive Wave Re-Splitting

After a subagent completes and returns its result, check `percent_of_context`:
- **If `percent_of_context > 40`:** Before spawning the next wave, split every remaining wave in
  plan.md in half. Move the second half of each remaining wave's bullet items into a new wave inserted
  immediately after it. Renumber all subsequent waves to keep the sequence gapless.
- **If `percent_of_context <= 40`:** Proceed without modification.

See `plugin/concepts/token-warning.md` for how compaction events alter this flow when context is
fully exhausted.

## Sub-Issue Decomposition

A **sub-issue** is a child issue created by splitting a parent when the work spans genuinely separate
delivery boundaries. Decomposition is a structural decision — it is **never triggered by context usage
alone**. Use wave re-splitting to manage context; use decomposition only when:

- A **merge boundary** is required between phases (phase B cannot start until phase A's code is merged
  and visible to reviewers)
- Components are **independently deliverable and reviewable** (each child issue can be reviewed, merged,
  and shipped without the other)
- Work spans **genuinely disjoint subsystems** where no shared file or interface connects them

After decomposition, the sub-issues are organized into waves following the same dependency analysis rules
described above. See `plugin/skills/decompose-issue-agent/first-use.md` for the full decomposition workflow.

## Worktree Sharing and Push Coordination

All wave subagents share the same worktree (`WORKTREE_PATH`). They commit and push to the same issue branch.
Each subagent must `git pull --rebase` before pushing to incorporate commits from other waves that completed
first.

When pushing encounters a non-fast-forward rejection, the subagent retries up to 3 times, rebasing before
each retry. See `plugin/concepts/parallel-execution.md` for the full push coordination protocol.

## index.json Ownership

`index.json` must be updated exactly once per issue run. The last wave alphabetically owns the index.json
update:

- All waves except the last: do NOT update `index.json`
- Last wave: updates `index.json` to `"status": "closed"` in its final commit

## Architecture Summary

```
work-with-issue (main agent)
    |
    +---> Read plan.md directly
    |     Detect ## Execution Waves / ### Wave N sections
    |     Count top-level bullet items per wave
    |
    +---> [if 2+ waves] Spawn all wave subagents simultaneously
    |         Wave 1: subagent handles all items in Wave 1 (parallel)
    |         Wave 2: subagent handles all items in Wave 2 (parallel)
    |         Worktree: shared
    |         index.json: NO (all waves except last) / YES (last wave)
    |
    +---> Collect commits from all waves
    |
    +---> stakeholder-review (single review of combined work)
    |
    +---> work-merge (squashes all commits, single merge)
```
