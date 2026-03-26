<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Parallel Subagent Execution

> See `plugin/concepts/execution-model.md` for the full execution model, hierarchy, and wave parallelism rules.

CAT supports running independent work items in parallel by spawning multiple implementation subagents, each working on
its own assigned wave of items. Parallelism is opt-in: it only activates when plan.md contains a `## Execution Waves`
section with multiple `### Wave N` subsections.

## How It Works

When the work skill starts executing an issue:

1. `work-with-issue` reads plan.md directly to detect `## Execution Waves` sections.
2. For each `### Wave N` subsection, the LLM counts the top-level bullet items.
3. **Parallel mode requires 2+ waves:** Plans with only 1 wave (or no waves at all) use single-subagent mode, spawning
   one implementation subagent with all items (default behavior). Only plans with 2 or more distinct waves spawn
   parallel subagents.
4. **Parallel execution (2+ waves):** All wave subagents spawn simultaneously in a single API response.
5. All subagents commit to the same issue branch (`v2.1-issue-name`).
6. After all waves complete, `work-with-issue` merges their commit lists and proceeds to review and merge.

## Wave Section Syntax

Create a `## Execution Waves` section with one `### Wave N` subsection per wave. Each wave contains top-level bullet
items (`- `) listing the work to be done. Sub-items (indented bullets with `  - `) are ignored and do not spawn
additional subagents.

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

In this example, Wave 1 and Wave 2 spawn simultaneously. If Wave 2 depends on Wave 1's output, declare that dependency
explicitly in plan.md so the orchestrator waits for Wave 1 before delegating Wave 2 items.

## When to Use Execution Waves

Use execution waves **only** when:

- Work items are genuinely independent (no data or file dependencies between waves)
- Items in different waves modify **different files** (overlapping files cause merge conflicts)
- The parallelism benefit justifies the added complexity

**Do NOT use waves when:**

- Items must run in sequence (later items consume output from earlier ones)
- Items modify the same files
- The issue is small enough that parallelism adds no benefit
- There is only one item (or one wave of items)

## index.json Ownership

index.json must be updated exactly once. The last wave alphabetically owns index.json updates:

- Wave 1: does NOT update index.json
- Wave 2 (last): updates index.json to `"status": "closed"` in its final commit

The `work-with-issue` skill communicates this ownership in each subagent's delegation prompt.

## Worktree Sharing

All wave subagents share the same worktree (`WORKTREE_PATH`). They commit and push to the same branch.
Each subagent must `git pull --rebase` before pushing to incorporate commits from other waves that completed first.

The `work-merge` phase is transparent to parallelism — it squashes all commits from `TARGET_BRANCH..HEAD` regardless of
how many subagents produced them.

## Push Coordination Protocol

When pushing commits to the shared issue branch, a subagent may encounter non-fast-forward rejection (when another
wave's commits have already been pushed). The coordination protocol is:

1. **Attempt to push** the local commits to the remote
2. **If rejected (non-fast-forward):**
   - Run `git pull --rebase` to incorporate remote commits
   - Retry the push (repeat up to 3 total push attempts)
3. **If all 3 push attempts fail:**
   - Return BLOCKED status with error details
   - The main agent will handle the deadlock and retry or fail the issue

This ensures that even when subagents complete in unpredictable order, each subagent can eventually push its commits
without forcing a merge or overwriting other waves' work.
