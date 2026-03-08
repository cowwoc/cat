# Plan: parallelize-stakeholder-reviewers

## Current State

The `spawn_reviewers` step in `stakeholder-review-agent` spawns stakeholder subagents sequentially — one at a time via
a loop. This means the total wall time is the SUM of all reviewer times (e.g., 34s + 47s = 81s for 2 reviewers),
even though reviewers are fully independent (they read different files and produce independent results).

## Target State

Stakeholder subagents are all spawned in a single message using multiple parallel Agent/Task calls. Total wall time
becomes the MAX of reviewer times (~47s for 2 reviewers), cutting review time proportionally as reviewer count grows.

## Parent Requirements

None — performance optimization from session analysis (optimize-execution).

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** None — the review aggregation logic is unchanged; only dispatch order changes
- **Mitigation:** Collect results after all parallel tasks complete; existing aggregation handles any order

## Alternatives Considered

- **Task tool (background=false, sequential)**: Current approach. Sum of all reviewer times. Rejected for performance.
- **Task tool (background=true, polling)**: Would require polling loops, adding complexity without benefit.
- **Agent tool (parallel, single message)**: Correct approach. All reviewers dispatched simultaneously; caller
  awaits all before aggregating. This matches the existing collect_reviews step which iterates results regardless of
  arrival order.

## Files to Modify

- `plugin/skills/stakeholder-review-agent/SKILL.md` — The large loaded content, specifically the `spawn_reviewers`
  step. The content lives in the skill-loader's resolution path. In the workspace, this would be in the installed
  plugin cache. Since we cannot modify the cache directly, the source file is:
  `/workspace/plugin/skills/stakeholder-review-agent/first-use.md` (or the main SKILL.md source if different).

  **Verify actual source location:**
  ```bash
  ls /workspace/plugin/skills/stakeholder-review-agent/
  ```

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Read the current `spawn_reviewers` step from the actual source skill file to confirm the for-loop pattern
- Rewrite the `spawn_reviewers` step to dispatch all selected reviewers in a single message:
  - Replace "For each stakeholder" loop with: "Spawn all stakeholders simultaneously in one message"
  - The Task/Agent calls for each stakeholder must all appear in a single response (parallel dispatch)
  - Preserve existing per-stakeholder prompt content (conventions, diff summary, PLAN.md content, etc.)
  - Preserve the collect_reviews step — it already handles results in any order
- Update STATE.md (status: closed, progress: 100%) in the same commit

## Post-conditions

- [ ] `spawn_reviewers` step dispatches all selected reviewers in a single parallel message
- [ ] No sequential "For each" reviewer loop remains in the spawning step
- [ ] `collect_reviews` step unchanged — aggregation handles results in any order
- [ ] Documentation in the skill clarifies that reviewers run in parallel
