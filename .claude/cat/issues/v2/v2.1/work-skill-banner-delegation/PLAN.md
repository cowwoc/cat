# Plan: work-skill-banner-delegation

## Current State
The `/cat:work` skill uses `!`-preprocessing to render progress banners, but the task ID isn't known
at preprocessing time (discovered during Phase 1 via subagent). This results in a placeholder message
and requires manual bash calls to generate banners post-discovery (per M328 workaround).

## Target State
Refactor `/cat:work` to use a two-phase skill invocation pattern:
1. Main skill discovers the task and collects metadata
2. Main skill invokes a secondary skill (e.g., `work-with-task`) with task ID as argument
3. Secondary skill uses `!`-preprocessing which now has task ID available to render banners

This makes banner rendering automatic and consistent with other skills.

## Satisfies
None - infrastructure improvement

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** None - external behavior unchanged
- **Mitigation:** Tests verify banner output matches current behavior

## Files to Modify
- `plugin/skills/work/SKILL.md` - Becomes discovery/routing skill
- `plugin/skills/work-with-task/SKILL.md` - New skill with task-aware preprocessing
- Audit other skills for similar patterns

## Acceptance Criteria
- [ ] Behavior unchanged - users see same output
- [ ] All tests still pass
- [ ] Progress banners render automatically via !-preprocessing
- [ ] Other skills audited for similar delegation pattern needs

## Execution Steps
1. **Step 1:** Audit existing skills for preprocessing patterns
   - Identify which skills could benefit from task-aware preprocessing
   - Document findings
   
2. **Step 2:** Create work-with-task skill
   - Extract execution phases from work/SKILL.md
   - Add !-preprocessing with ${task_id} available
   
3. **Step 3:** Refactor work/SKILL.md
   - Keep discovery logic (Phase 1)
   - After READY status, invoke work-with-task skill with task ID
   
4. **Step 4:** Run tests
   - Verify: python3 /workspace/run_tests.py
