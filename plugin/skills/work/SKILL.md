---
description: Work on tasks (auto-continues when trust >= medium)
argument-hint: "[version | taskId] [--override-gate]"
allowed-tools:
  - Read
  - Write
  - Edit
  - Bash
  - Glob
  - Grep
  - Task
  - AskUserQuestion
  - SlashCommand
---

<objective>

Execute a task with worktree isolation, subagent orchestration, and quality gates.

**Concurrent Execution:** This command uses task-level locking to prevent multiple Claude instances
from executing the same task simultaneously. Locks persist until explicitly released.

This is CAT's core execution command. It:
1. Finds the next executable task (pending + dependencies met)
2. Acquires exclusive task lock (prevents concurrent execution)
3. Creates a task worktree and branch
4. Executes the PLAN.md (spawn subagent or work directly)
5. Monitors token usage throughout
6. Runs stakeholder review gate (multi-perspective quality review)
7. Loops back to fix concerns if review rejects
8. Squashes commits by type
9. Runs user approval gate (interactive mode)
10. Merges task branch to main
11. Cleans up worktrees
12. Updates STATE.md
13. Updates changelogs (minor/major CHANGELOG.md)
14. Offers next task

</objective>

<progress_output>

## Pre-rendered Progress Banners

!`${CLAUDE_PLUGIN_ROOT}/scripts/get-progress-banner.sh $ARGUMENTS --project-dir "${CLAUDE_PROJECT_DIR}" --session-id "${CLAUDE_SESSION_ID}"`

**INSTRUCTION:** Output the appropriate phase banner EXACTLY as shown above.
- Do NOT modify the banner content
- Do NOT wrap in code blocks
- Do NOT manually construct banners

### Phase Mapping

| Phase | Steps Included | Complete When |
|-------|----------------|---------------|
| Preparing | verify, find_task, acquire_lock, load_task, validate_requirements, analyze_task_size, choose_approach, create_worktree | Worktree created, ready to execute |
| Executing | execute, collect_and_report, token_check, handle_discovered_issues, verify_changes | Subagent complete, changes verified |
| Reviewing | stakeholder_review, approval_gate | Review passed, user approved |
| Merging | squash_commits, merge, cleanup, update_state, commit_metadata, update_changelogs, next_task | Merged to main, cleanup done |

### Key Principles

1. **Output banners verbatim** - Pre-rendered banners require NO modification
2. **4 phases, not 17 steps** - Users see meaningful stages, not micro-steps
3. **Update at transitions** - Display progress banner when phase changes

</progress_output>

<execution_context>

<!-- SKILL.md vs PLAN.md (A015/M172): Always reference SKILL.md for skill usage.
     PLAN.md = what to build (task planning). SKILL.md = how to use it (authoritative). -->

<!-- Core concepts always needed for orchestration -->
@${CLAUDE_PLUGIN_ROOT}/concepts/work.md
@${CLAUDE_PLUGIN_ROOT}/concepts/agent-architecture.md
@${CLAUDE_PLUGIN_ROOT}/concepts/subagent-delegation.md
@${CLAUDE_PLUGIN_ROOT}/concepts/commit-types.md

</execution_context>

<conditional_context>

**Load on demand when specific phases or scenarios occur:**

| Phase/Scenario | Load Context |
|----------------|--------------|
| Execute phase (spawning subagent) | @${CLAUDE_PLUGIN_ROOT}/skills/delegate/SKILL.md |
| Review phase (stakeholder review) | @${CLAUDE_PLUGIN_ROOT}/skills/stakeholder-review/SKILL.md, @${CLAUDE_PLUGIN_ROOT}/stakeholders/index.md |
| Merge phase (merging subagent) | @${CLAUDE_PLUGIN_ROOT}/skills/merge-subagent/SKILL.md, @${CLAUDE_PLUGIN_ROOT}/concepts/merge-and-cleanup.md |
| Merge phase (changelog update) | @${CLAUDE_PLUGIN_ROOT}/templates/changelog.md |
| Minor/major version completes | @${CLAUDE_PLUGIN_ROOT}/concepts/version-completion.md |
| Task discovered as duplicate | @${CLAUDE_PLUGIN_ROOT}/concepts/duplicate-task.md |
| Compaction events or high token usage | @${CLAUDE_PLUGIN_ROOT}/concepts/token-warning.md |

</conditional_context>

<context>

Task path: $ARGUMENTS

**Load project state first:**
@.claude/cat/cat-config.json
@.claude/cat/PROJECT.md
@.claude/cat/ROADMAP.md

</context>

<process>

## Phase 1: Prepare

Steps: verify, find_task, acquire_lock, load_task, validate_requirements, analyze_task_size, choose_approach, create_worktree

@${CLAUDE_PLUGIN_ROOT}/commands/work/phase-prepare.md

## Phase 2: Execute

Steps: execute, collect_and_report, aggregate_token_report, token_check, handle_discovered_issues, verify_changes

@${CLAUDE_PLUGIN_ROOT}/commands/work/phase-execute.md

## Phase 3: Review

Steps: stakeholder_review, approval_gate

@${CLAUDE_PLUGIN_ROOT}/commands/work/phase-review.md

## Phase 4: Merge

Steps: squash_commits, finalization, next_task

@${CLAUDE_PLUGIN_ROOT}/commands/work/phase-merge.md

</process>

<deviation_rules>

@${CLAUDE_PLUGIN_ROOT}/commands/work/deviation-rules.md

</deviation_rules>

<main_agent_boundaries>

@${CLAUDE_PLUGIN_ROOT}/commands/work/anti-patterns.md

</main_agent_boundaries>

<commit_rules>

@${CLAUDE_PLUGIN_ROOT}/commands/work/commit-rules.md

</commit_rules>

<success_criteria>

- [ ] **Task lock acquired BEFORE offering task (M097)**
- [ ] Task identified and loaded
- [ ] **Entry gate evaluated (blocked if unmet, unless --override-gate)**
- [ ] **Task size analyzed (estimate vs threshold)**
- [ ] **Pre-spawn validation: estimate < hard limit (A018)**
- [ ] **If oversized: auto-decomposition triggered**
- [ ] **If decomposed: parallel execution plan generated**
- [ ] Worktree(s) created with correct branch(es)
- [ ] PLAN.md executed successfully via subagent(s)
- [ ] **Token metrics collected and reported to user**
- [ ] **Aggregate token report generated (multi-subagent tasks)**
- [ ] **Context limit violations flagged and learn-from-mistakes triggered**
- [ ] **Compaction events evaluated (decomposition offered if > 0)**
- [ ] **Stakeholder review passed (or concerns addressed)**
- [ ] Approval gate passed (if interactive)
- [ ] Commits squashed by type
- [ ] Branch(es) merged to main
- [ ] Worktree(s) cleaned up
- [ ] Lock released
- [ ] STATE.md files updated
- [ ] **Next task offered (lock checked first)**

</success_criteria>
