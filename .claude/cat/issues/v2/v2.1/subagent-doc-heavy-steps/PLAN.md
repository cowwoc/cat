# Plan: subagent-doc-heavy-steps

## Current State
Main agent loads all reference docs upfront for /cat:work, consuming ~60K tokens even when most docs aren't needed. Documentation-heavy steps like stakeholder review load 10+ stakeholder files.

## Target State
Delegate documentation-heavy steps to subagents who get fresh context. Main agent stays lean for orchestration.

## Satisfies
None - infrastructure/optimization task

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Workflow restructuring
- **Mitigation:** Test each step still works correctly when delegated

## Files to Modify
- plugin/commands/work.md - Remove eager doc loading, delegate to subagents
- plugin/skills/stakeholder-review/SKILL.md - Already uses subagents, verify isolation
- plugin/concepts/work.md - Document lazy-loading pattern

## Acceptance Criteria
- [ ] Main agent context reduced by 30-50K tokens
- [ ] Documentation loaded only when step is reached
- [ ] Subagents handle doc-heavy steps (stakeholder review, decomposition)
- [ ] Workflow still completes correctly

## Execution Steps
1. **Step 1:** Audit which docs are loaded eagerly vs on-demand
   - Verify: List of eager-loaded docs identified

2. **Step 2:** Refactor work.md to lazy-load reference docs
   - Verify: Docs only loaded when step requires them

3. **Step 3:** Verify stakeholder review uses subagent isolation
   - Verify: Stakeholder docs don't pollute main context

4. **Step 4:** Measure context savings
   - Verify: Main agent context reduced by 30%+
