# Plan: delegate-squash-to-subagent

## Goal
Delegate the squash phase (Phase 6) of /cat:work to a subagent, saving ~15% of parent agent context that currently
accumulates from git operations, commit analysis, and STATE.md closure logic.

## Satisfies
None - performance optimization

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Squash phase currently runs git-squash skill at parent level, which involves reading commit history,
  analyzing overlapping files, detecting unsquashed indicators, and potentially running multiple squash iterations.
  The subagent needs worktree access and must return the final commit hash reliably.
- **Mitigation:** Squash is a self-contained mechanical operation with clear inputs (commit list, worktree path) and
  outputs (final commit hash, brief summary). No nesting issues since git-squash doesn't spawn subagents.

## Design Principles
1. **Trust subagents.** If the squash subagent reports success, trust it.
2. **Return only what the parent needs.** Final commit hash, commit count, brief summary.

## Files to Modify
- `plugin/skills/work-with-issue/SKILL.md` - Replace inline squash logic with subagent delegation
- `plugin/agents/work-squash.md` - New agent definition for squash subagent

## Post-conditions
- [ ] Squash phase runs entirely within a subagent
- [ ] Parent agent receives only: final commit hash, commit count, brief summary
- [ ] Squash quality is maintained (overlapping files detected, unsquashed indicators caught)
- [ ] STATE.md closure is included in the squashed commit
- [ ] Parent agent context consumed by Phase 6 is reduced by at least 50% compared to current baseline

## Execution Steps
1. **Measure current baseline**
   - Record average parent-agent input tokens consumed by Phase 6 across 3+ sessions

2. **Create squash subagent definition**
   - Files: `plugin/agents/work-squash.md`
   - Agent accepts: commit list, worktree path, base branch, issue metadata
   - Agent responsibilities: invoke git-squash skill, handle multi-iteration squash, close STATE.md
   - Agent returns compact JSON:
     ```json
     {
       "status": "SUCCESS",
       "final_commit": "abc1234",
       "commit_count": 3,
       "summary": "Squashed 7 commits into 3 (feature, test, docs)"
     }
     ```

3. **Restructure Phase 6 in work-with-issue**
   - Files: `plugin/skills/work-with-issue/SKILL.md`
   - Replace inline squash logic with Task tool spawn of squash subagent
   - Parse compact JSON result

4. **Run tests**
   - Verify squash behavior preserved with new delegation

5. **Measure post-change context usage**
   - Compare parent-agent token consumption for Phase 6 against baseline

## Post-conditions
- [ ] Phase 6 parent-agent context usage reduced by >= 50%
- [ ] Squashed commits maintain correct messages and content
- [ ] STATE.md is properly closed in the final squashed commit
- [ ] E2E: A complete /cat:work run succeeds with the new squash delegation
