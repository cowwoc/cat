# Plan: restructure-learn-tiered

## Current State
The learn skill uses 4 sequential subagents (investigate, analyze, prevent, record), each via Task tool.
Each subagent starts fresh context, re-reads shared files, and receives growing JSON from prior phases.
Total token usage ~140K across all subagents. User sees no intermediate reasoning.

## Target State
Single subagent per tier with selective phase loading. Phase files become reference docs loaded
on-demand within the subagent rather than separate invocations. User sees phase summaries between
steps displayed by the main orchestrator.

## Satisfies
None

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Learn skill output format changes (phase summaries added)
- **Mitigation:** Verify learning records still created correctly, retrospective counter still updated

## Files to Modify
- plugin/skills/learn/SKILL.md - Restructure from 4-subagent orchestration to tiered single-subagent
- plugin/skills/learn/phase-investigate.md - Add user_summary output requirement
- plugin/skills/learn/phase-analyze.md - Add user_summary output requirement
- plugin/skills/learn/phase-prevent.md - Add user_summary output requirement
- plugin/skills/learn/phase-record.md - Add user_summary output requirement

## Execution Steps
1. **Read current SKILL.md and all 4 phase files** to understand current orchestration
2. **Define tier classification logic** in SKILL.md:
   - Quick tier: protocol_violation category OR recurrence_of is set
   - Deep tier: novel failures, architectural issues, context-related
3. **Create single-subagent prompt template** that:
   - Receives tier designation (quick/deep)
   - Loads only relevant phase files based on tier
   - Quick: loads phase-analyze.md + phase-prevent.md + phase-record.md (skips investigate)
   - Deep: loads all 4 phase files
   - Returns structured JSON with user_summary field per phase
4. **Update SKILL.md orchestration** to:
   - Classify mistake into quick/deep tier
   - Spawn single subagent with tier-appropriate prompt
   - Display each phase user_summary to user between processing
   - Parse final JSON result
5. **Add user_summary requirement** to each phase file output spec
6. **Update SKILL.md summary display** to show tier used and token savings

## Success Criteria
- [ ] Quick tier completes in single subagent invocation (~40K tokens)
- [ ] Deep tier completes in single subagent invocation (~80K tokens)
- [ ] User sees phase summaries (investigation findings, RCA, prevention description) during execution
- [ ] Learning records still created correctly in mistakes JSON
- [ ] Retrospective counter still updated
- [ ] All existing test scenarios produce equivalent learning records
