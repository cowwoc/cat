# Plan: subagent-doc-heavy-steps

## Current State
Main agent loads ~60K tokens of reference docs upfront for /cat:work:
- work.md (47K chars)
- merge-and-cleanup.md, agent-architecture.md, subagent-delegation.md
- commit-types.md, stakeholder docs, templates

Even with lazy-loading, main agent eventually consumes all docs if full workflow runs.

## Target State
"Thin orchestrator" architecture where main agent delegates entire phases to subagents.
Main agent only needs ~5-10K orchestration protocol. Reference docs stay in subagent contexts.

## Architecture

```
Main Agent (thin orchestrator: ~5-10K)
    │
    ├── Preparation Subagent
    │   Loads: version-paths.md, find-task logic
    │   Returns: {task_id, worktree_path, estimate, lock_status}
    │
    ├── Execution Subagent
    │   Loads: spawn-subagent/SKILL.md, subagent-delegation.md
    │   Returns: {status, tokens, commits, files_changed}
    │
    ├── Review Subagent
    │   Loads: stakeholder-review/SKILL.md, stakeholders/*.md
    │   Returns: {approval_status, concerns[], recommendation}
    │
    └── Merge Subagent
        Loads: merge-and-cleanup.md, commit-types.md
        Returns: {merged, branch, cleanup_status}
```

Main agent responsibilities:
- Phase orchestration (spawn → collect → decide)
- User interaction (approval gates, questions)
- Error escalation
- Progress display

## Satisfies
None - infrastructure/optimization task

## Risk Assessment
- **Risk Level:** HIGH
- **Breaking Changes:** Complete workflow restructuring
- **Mitigation:** Incremental migration, one phase at a time; extensive testing

## Files to Modify
- plugin/commands/work.md - Reduce to thin orchestration protocol
- plugin/concepts/work.md - Document phase subagent architecture
- plugin/skills/work-prepare/SKILL.md (new) - Preparation phase
- plugin/skills/work-execute/SKILL.md (new) - Execution phase
- plugin/skills/work-review/SKILL.md (new) - Review phase
- plugin/skills/work-merge/SKILL.md (new) - Merge phase

## Acceptance Criteria
- [ ] Main agent context reduced from ~60K to ~10K reference docs
- [ ] Each phase runs in isolated subagent with fresh context
- [ ] Subagents return structured JSON for main agent decisions
- [ ] User interactions still work (approval gates)
- [ ] Workflow produces same results as before
- [ ] Error handling preserved

## Execution Steps
1. **Step 1:** Define phase subagent JSON contracts
   - Verify: Input/output schemas documented

2. **Step 2:** Create work-prepare skill (find task, create worktree)
   - Verify: Returns {task_id, worktree_path, estimate}

3. **Step 3:** Create work-execute skill (spawn implementation subagent)
   - Verify: Returns {status, tokens, commits}

4. **Step 4:** Create work-review skill (stakeholder review)
   - Verify: Returns {approval_status, concerns[]}

5. **Step 5:** Create work-merge skill (squash, merge, cleanup)
   - Verify: Returns {merged, branch, cleanup_status}

6. **Step 6:** Refactor work.md to thin orchestrator
   - Verify: Main agent only loads orchestration protocol

7. **Step 7:** Measure context savings
   - Verify: Main agent context reduced by 50K+
