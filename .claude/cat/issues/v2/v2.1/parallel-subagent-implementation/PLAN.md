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

## Execution Steps

1. **Extend add/SKILL.md and AddWizard.java** to support optional grouping in planning questions
   - Files: `plugin/skills/add/SKILL.md`, `client/src/main/java/io/github/cowwoc/cat/skills/add/AddWizard.java`

2. **Update WorkWithIssue.java** to parse PLAN.md groups and spawn multiple subagents
   - Files: `client/src/main/java/io/github/cowwoc/cat/skills/work/WorkWithIssue.java`
   - Spawn one subagent per group with group assignment binding

3. **Update WorkPrepare.java** to detect subagent context and create isolated worktree
   - Files: `client/src/main/java/io/github/cowwoc/cat/skills/work/WorkPrepare.java`
   - Check for group assignment, create worktree with naming scheme

4. **Update WorkMerge.java** to collect and merge multi-subagent commits
   - Files: `client/src/main/java/io/github/cowwoc/cat/skills/work/WorkMerge.java`
   - Detect multiple subagent work branches, collect commits, squash

5. **Create tests** in ParallelSubagentTest.java
   - Files: `client/src/test/java/io/github/cowwoc/cat/skills/work/ParallelSubagentTest.java`

6. **Create documentation** explaining parallel execution
   - Files: `plugin/concepts/parallel-execution.md`, `plugin/skills/add/first-use.md`

7. **Run full test suite** to ensure no regressions
   - Command: `mvn -f client/pom.xml verify`

## Post-conditions

- [ ] Planning agent can optionally specify execution groups in PLAN.md
- [ ] `/cat:work` detects groups and spawns multiple subagents (one per group)
- [ ] Each subagent runs only its assigned execution steps
- [ ] Each subagent works in isolated worktree named `{parent-worktree}-{agent-id}`
- [ ] All subagents operate on the same issue branch
- [ ] Multiple subagent commits are collected and squashed into single unified commit
- [ ] Parallel execution is transparent to user (same review/merge workflow)
- [ ] No regressions: existing single-subagent workflows still work
- [ ] All tests pass (`mvn -f client/pom.xml verify` exit code 0)
