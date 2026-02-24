# Plan: delegate-verify-to-subagent

## Goal
Delegate the verify-implementation phase (Phase 3a) and E2E testing (Phase 3b) to a subagent, saving ~4% of parent
agent context from acceptance criteria analysis and test output.

## Satisfies
None - performance optimization

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Verify phase reads PLAN.md acceptance criteria, checks changed files, and may spawn fix subagents
  for missing criteria. The fix subagent spawning creates a nesting concern if verify itself runs as a subagent.
  E2E testing can produce verbose output (build logs, stack traces) that inflates context.
- **Mitigation:** For the nesting issue: the verify subagent can return a structured report of missing criteria,
  and the parent can spawn fix subagents based on that report. This keeps nesting flat. E2E test output is
  naturally contained within the subagent.

## Files to Modify
- `plugin/skills/work-with-issue/SKILL.md` - Replace inline verify + E2E logic with subagent delegation
- `plugin/agents/` - New agent definition for verify subagent

## Acceptance Criteria
- [ ] Verify-implementation phase runs within a subagent
- [ ] E2E testing runs within a subagent (same or separate)
- [ ] Parent agent receives only a compact JSON summary (criteria status, test results)
- [ ] Fix iterations for missing criteria still function (parent spawns fix subagent based on verify report)
- [ ] Parent agent context consumed by Phases 3a+3b is reduced by at least 50% compared to current baseline

## Execution Steps
1. **Measure current baseline**
   - Record average parent-agent input tokens consumed by Phases 3a+3b across 3+ sessions

2. **Create verify subagent definition**
   - Files: `plugin/agents/work-verify.md`
   - Define agent that accepts: issue metadata, worktree path, PLAN.md path, execution result (commits, files)
   - Agent responsibilities: invoke verify-implementation skill, run E2E tests, return structured JSON with
     acceptance criteria status (Done/Missing/Partial per criterion) and E2E test results

3. **Restructure Phases 3a+3b in work-with-issue**
   - Files: `plugin/skills/work-with-issue/SKILL.md`
   - Replace inline verify and E2E logic with Task tool spawn of verify subagent
   - Parse compact JSON result
   - If missing criteria: parent spawns fix subagent (cat:work-execute), then re-invokes verify subagent

4. **Run tests**
   - Verify acceptance criteria checking still works with new delegation

5. **Measure post-change context usage**
   - Compare parent-agent token consumption for Phases 3a+3b against baseline

## Post-conditions
- [ ] Phases 3a+3b parent-agent context usage reduced by >= 50%
- [ ] All acceptance criteria still verified (no criterion skipped)
- [ ] E2E tests still run and results reported accurately
- [ ] Fix iteration loop still works for missing criteria
- [ ] E2E: A complete /cat:work run succeeds with the new verify delegation
