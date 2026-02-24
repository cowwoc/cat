# Plan: delegate-stakeholder-review-to-subagent

## Goal
Wrap the entire stakeholder review + auto-fix loop (Phase 3c) in a single subagent that returns a compact summary,
saving ~65% of parent agent context.

## Satisfies
None - performance optimization

## Risk Assessment
- **Risk Level:** HIGH
- **Concerns:** Subagent nesting constraint — the review subagent needs to spawn individual reviewer subagents
  (architecture, security, testing, etc.) and fix subagents (cat:work-execute). Current Claude Code architecture
  prohibits nested subagent spawning. Workaround options: (a) run reviewers as skill invocations inside the subagent
  instead of sub-subagents, trading isolation for nestability; (b) flatten architecture so the review subagent uses
  direct tool calls instead of spawning children; (c) pre-spawn reviewers from parent, pass results into review
  subagent for aggregation + fix loop only.
- **Mitigation:** Start with approach (c) as lowest-risk: parent spawns reviewers in parallel, then passes aggregated
  results to a new "review-fix-loop" subagent that handles concern triage, fix iterations, and re-review. This avoids
  the nesting problem entirely.

## Files to Modify
- `plugin/skills/work-with-issue/SKILL.md` - Restructure Phase 3c to delegate review aggregation + fix loop
- `plugin/agents/` - New agent definition for the review-fix-loop subagent
- `plugin/skills/stakeholder-review/SKILL.md` - May need interface changes to support pre-spawned reviewer results

## Acceptance Criteria
- [ ] StakeholderReview phase runs entirely within a subagent (or with minimal parent context footprint)
- [ ] Parent agent receives only a compact JSON summary of concerns
- [ ] Auto-fix iterations happen inside the subagent without accumulating in parent context
- [ ] Reviewer quality is not degraded (same stakeholder perspectives are consulted)
- [ ] Total parent agent context consumed by Phase 3c is reduced by at least 50% compared to current baseline

## Execution Steps
1. **Measure current baseline**
   - Files: session transcripts
   - Record average parent-agent input tokens consumed by Phase 3c across 3+ sessions

2. **Create review-fix-loop agent definition**
   - Files: `plugin/agents/review-fix-loop.md`
   - Define agent that accepts: aggregated reviewer concerns, issue metadata, worktree path
   - Agent responsibilities: triage concerns by severity, spawn fix subagent for high+ concerns, re-invoke
     stakeholder-review skill, loop up to 3 iterations, return compact JSON summary

3. **Restructure Phase 3c in work-with-issue**
   - Files: `plugin/skills/work-with-issue/SKILL.md`
   - Keep initial stakeholder-review invocation at parent level (spawns reviewer subagents)
   - Pass reviewer results to new review-fix-loop subagent via Task tool
   - Replace inline fix loop with subagent delegation
   - Parse compact JSON result from subagent

4. **Run tests**
   - Files: test suites
   - Verify existing stakeholder review tests pass with restructured flow

5. **Measure post-change context usage**
   - Compare parent-agent token consumption for Phase 3c against baseline

## Post-conditions
- [ ] Phase 3c parent-agent context usage reduced by >= 50%
- [ ] All stakeholder perspectives still consulted (no reviewer dropped)
- [ ] Auto-fix loop still functions (concerns above threshold get fixed)
- [ ] E2E: A complete /cat:work run succeeds with the new review delegation
