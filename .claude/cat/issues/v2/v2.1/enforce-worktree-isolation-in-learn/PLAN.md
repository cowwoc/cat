# Plan: Enforce Worktree Isolation in Learn Prevent Phase

## Goal
Prevent the learn skill's prevent phase from committing directly to protected branches (v2.1, main). Two gaps:
1. phase-prevent.md instructs "edit and commit" without requiring worktree isolation
2. The worktree isolation hook only protects `plugin/` files, not `client/` files

## Satisfies
None (infrastructure fix from M347 investigation)

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Learn subagents need to implement prevention quickly; requiring full /cat:work flow adds overhead
- **Mitigation:** For code_fix prevention, learn can set `prevention_implemented: false` and create a CAT issue instead
  of implementing directly. Documentation/config fixes can still be committed directly since those paths are safe.

## Approaches

### A: Learn creates issues instead of implementing code fixes
- **Risk:** LOW
- **Scope:** 1 file (phase-prevent.md)
- **Description:** When prevention_type is code_fix or hook, phase-prevent.md instructs the subagent to output
  `prevention_implemented: false` with `task_creation_info` for the parent to create an issue. Only documentation/config
  changes are committed directly.

### B: Expand worktree isolation hook to protect all source files
- **Risk:** MEDIUM
- **Scope:** 2 files (hook + phase-prevent.md)
- **Description:** Expand the Edit/Write hook to protect `client/` files in addition to `plugin/`. Also update
  phase-prevent.md to use worktree isolation for code changes.

## Files to Modify
- `plugin/skills/learn/phase-prevent.md` - Add instruction: for code_fix/hook prevention types, set
  `prevention_implemented: false` and populate `task_creation_info` instead of editing source files directly
- Optionally: `client/src/main/java/.../BlockUnsafeRemoval.java` or equivalent hook - Expand protected paths

## Acceptance Criteria
- [ ] Learn subagent does not commit code_fix/hook prevention directly to protected branches
- [ ] Learn subagent still commits documentation/config prevention directly (safe paths)
- [ ] `task_creation_info` is populated with enough detail to create a follow-up issue
- [ ] Parent agent creates the CAT issue from `task_creation_info` when `prevention_implemented: false`

## Execution Steps
1. **Update phase-prevent.md Step 9:** Add gate before implementation: if prevention_type is code_fix or hook AND
   current branch is protected (v2.1, main), set prevention_implemented=false and populate task_creation_info
2. **Update learn SKILL.md Step 3:** After subagent returns, if prevention_implemented is false, create a CAT issue
   from task_creation_info
3. **Test:** Run learn with a code_fix prevention scenario, verify issue is created instead of direct commit
