# Plan: parallel-subagent-implementation

## Goal

Enable the CAT workflow to parallelize independent implementation work by spawning multiple implementation subagents,
each working in isolated worktrees on the same issue branch. The planning agent should identify parallelizable work,
and the main agent should coordinate subagent execution with proper work distribution.

## Satisfies

None (infrastructure / workflow improvement)

## Problem Statement

Currently, the `/cat:work` workflow spawns a single implementation subagent. All execution steps in PLAN.md are
executed sequentially by that one agent. This serializes work that could be parallelized—for example, implementing
multiple independent features in different files, or applying changes across loosely-coupled modules.

The proposal enables:
1. Planning agent to identify independent work groups
2. Main agent to spawn multiple subagents for parallelizable groups
3. Each subagent works in its own isolated worktree
4. All subagents operate on the same issue branch
5. Main agent merges subagent work back sequentially

## Approaches

### A: Execution-Step Grouping
- **Risk:** LOW
- **Scope:** 4-5 files (moderate)
- **Description:** Planning agent annotates groups of steps with `group: A`, `group: B`, etc. Main agent parses
  groups and spawns one subagent per group. Each subagent executes only its assigned group.

### B: Dependency Graph Analysis
- **Risk:** MEDIUM
- **Scope:** 6-8 files (moderate to comprehensive)
- **Description:** Planning agent builds a dependency graph of steps (which files each step modifies, which steps
  depend on each other). Main agent analyzes the graph and automatically detects parallelizable groups.

**Chosen:** A (execution-step grouping) — simpler, explicit, easier to understand and debug.

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:**
  - Worktree isolation: each subagent needs its own worktree but shares the issue branch
  - File conflict: if multiple subagents modify the same file, merge conflicts may occur
  - Commit ordering: commits from multiple subagents may interleave, affecting squash order
  - Agent coordination: main agent must wait for all subagents before merging
- **Mitigation:**
  - Require explicit grouping in PLAN.md to ensure intent is clear
  - Document that subagents modifying the same file must coordinate
  - Implement worktree naming scheme: `{main-worktree}-{agent-id}` for clarity
  - Collect commits from all subagents before squashing
  - Implement locking/coordination if multiple subagents try to work on same branch

## Files to Modify

### Phase 1: Planning Agent Enhancement
1. `plugin/skills/add/SKILL.md`
   - Update wizard to include optional "grouping" question
   - Explain grouping in help text

2. `plugin/skills/add/first-use.md`
   - Add guidance on grouping independent work

3. `client/src/main/java/io/github/cowwoc/cat/skills/add/AddWizard.java`
   - Extend planning questions to support grouping specification
   - Validate groups are contiguous and non-overlapping in execution steps

### Phase 2: Main Agent Enhancement
4. `plugin/skills/work-with-issue/SKILL.md`
   - Update to explain multi-subagent coordination
   - Document group-based spawning

5. `client/src/main/java/io/github/cowwoc/cat/skills/work/WorkWithIssue.java`
   - Parse grouping annotations from PLAN.md
   - Spawn multiple subagents (one per group)
   - Pass group assignment to each subagent
   - Wait for all subagents to complete before proceeding to review/merge

### Phase 3: Subagent Enhancements
6. `client/src/main/java/io/github/cowwoc/cat/skills/work/WorkPrepare.java`
   - Detect when invoked as subagent (check for group assignment in binding)
   - Create worktree with naming: `{parent-worktree}-{agent-id}`
   - Fetch the issue branch instead of creating a new one
   - Run only assigned execution steps

7. `client/src/main/java/io/github/cowwoc/cat/hooks/subagent/SubagentWorktreeCreation.java` (NEW)
   - Subagent-side hook to detect worktree creation needs
   - Ensure subagent creates its own worktree directory structure

### Phase 4: Merge Coordination
8. `client/src/main/java/io/github/cowwoc/cat/skills/work/WorkMerge.java`
   - Detect multi-subagent scenario (multiple work branches)
   - Collect commits from all subagent worktrees
   - Squash into unified commit
   - Handle potential merge conflicts gracefully

### Phase 5: Testing & Documentation
9. `client/src/test/java/io/github/cowwoc/cat/skills/work/ParallelSubagentTest.java` (NEW)
   - Test: PLAN.md with groups parses correctly
   - Test: Two subagents spawn for two groups
   - Test: Each subagent executes only its assigned steps
   - Test: Commits from both subagents are collected and squashed
   - Test: Subagents work in isolated worktrees with correct naming
   - Test: Merge back to issue branch succeeds

10. `plugin/concepts/parallel-execution.md` (NEW)
    - Document the parallel subagent workflow
    - Explain grouping syntax
    - Provide examples

## Pre-conditions

- [ ] All dependent issues are closed
- [ ] PLAN.md template supports optional grouping annotations

## Execution Waves

### Wave 1
- Remove wave-annotation parsing from WorkPrepare.java (STEP_NUMBER_PATTERN, WAVE_ANNOTATION_PATTERN, readExecutionWavesFromPlan method)
- Remove "waves" field from READY JSON output
- Add EXECUTION_WAVES_PATTERN to match ## Execution Waves sections in PLAN.md
- Update token estimation to count wave items (top-level bullets in ### Wave N sections)

### Wave 2
- Update work-with-issue skill first-use.md to detect parallel execution from PLAN.md directly (not READY JSON)
- Update add skill first-use.md to document ## Execution Waves / ### Wave N syntax
- Update work-merge skill first-use.md references (groups → waves)

### Wave 3
- Rewrite parallel-execution.md with new wave-section syntax
- Update issue-plan.md template to use ## Execution Waves format
- Update existing test file to test new wave-section token estimation

### Wave 4
- Run full test suite to ensure no regressions
  - Command: `mvn -f client/pom.xml verify`

## Post-conditions

- [ ] Planning agent can optionally specify execution waves in PLAN.md
- [ ] `/cat:work` detects waves and spawns multiple subagents (one per wave)
- [ ] Each subagent runs only its assigned execution steps
- [ ] All subagents share the issue worktree and serialize git operations via pull --rebase
- [ ] All subagents operate on the same issue branch
- [ ] Multiple subagent commits are collected and squashed into single unified commit
- [ ] Parallel execution is transparent to user (same review/merge workflow)
- [ ] No regressions: existing single-subagent workflows still work
- [ ] All tests pass (`mvn -f client/pom.xml verify` exit code 0)
