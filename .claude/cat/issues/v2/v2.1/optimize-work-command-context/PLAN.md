# Plan: optimize-work-command-context

## Goal
Reduce context usage of `/cat:work` command by ~40% through lazy-loading skills, consolidating duplicated content, and optimizing handler output generation.

## Satisfies
None - infrastructure/optimization task

## Current State
The `/cat:work` command loads ~5,500+ lines (~22-25K tokens) at invocation:
- Core command + phases: ~1,800 lines
- Always-loaded concepts: ~3,100 lines (including skills that may not be used)
- Handler templates: ~500 lines (all boxes generated regardless of state)

## Target State
- Skills loaded only when their phase is reached
- Duplicated content between `work.md` concept and phase files consolidated
- Handler generates only boxes relevant to current workflow state
- Estimated reduction: 30-40% of baseline context

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Breaking existing workflow behavior, missing edge cases
- **Mitigation:** All tests must pass, behavior verification through manual testing

## Files to Modify
- `plugin/commands/work.md` - Restructure execution_context to use conditional loading
- `plugin/concepts/work.md` - Remove duplication with phase files (consolidate or reference)
- `plugin/hooks/skill_handlers/work_handler.py` - Generate boxes conditionally based on workflow state

## Acceptance Criteria
- [ ] Behavior unchanged - all /cat:work flows produce same results
- [ ] All tests still pass - run_tests.py exits 0
- [ ] Code quality improved - measurable reduction in loaded context

## Execution Steps
1. **Analyze duplication:** Identify exact overlap between work.md concept and phase files
   - Files: plugin/concepts/work.md, plugin/commands/work/*.md
   - Verify: Document specific duplicated sections

2. **Consolidate work.md concept:** Remove duplicated content, keep only unique orchestration details
   - Files: plugin/concepts/work.md
   - Verify: Content referenced by phase files still accessible

3. **Implement lazy-loading for skills:** Move skill references to conditional_context section
   - Files: plugin/commands/work.md
   - Verify: Skills still load when needed (stakeholder-review, merge-subagent)

4. **Optimize handler output:** Generate only relevant boxes based on arguments/state
   - Files: plugin/hooks/skill_handlers/work_handler.py
   - Verify: Correct boxes still appear at appropriate workflow stages

5. **Run tests and verify behavior:**
   - Verify: python3 /workspace/run_tests.py exits 0
