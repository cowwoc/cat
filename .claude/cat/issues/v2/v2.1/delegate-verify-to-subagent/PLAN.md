# Plan: delegate-verify-to-subagent

## Goal
Minimize parent agent context consumed by the verify-implementation phase (Phase 3a) and E2E testing (Phase 3b) by
delegating verification to a subagent that writes detailed analysis to files and returns only a compact summary.

## Satisfies
None - performance optimization

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Verify phase reads PLAN.md, checks changed files, and runs E2E tests — all of which can produce
  verbose output (build logs, stack traces, file contents). E2E testing is especially noisy.
- **Mitigation:** Verify subagent writes detailed criterion analysis and test output to files. Returns only pass/fail
  status per criterion with brief explanations. Main agent delegates fixes based on file references without reading
  the files.

## Design Principles
1. **Trust subagents.** If the verify subagent says a criterion passed, trust it.
2. **Return only what the parent needs.** Per-criterion: status (Done/Missing/Partial), brief explanation, detail file.
3. **File-based handoff.** Comprehensive analysis and test output go to files. Fix subagents read them directly.

## Files to Modify
- `plugin/skills/work-with-issue/SKILL.md` - Restructure Phases 3a+3b to use verify subagent with file-based output
- `plugin/agents/work-verify.md` - New agent definition for verify subagent

## Acceptance Criteria
- [ ] Verify subagent writes detailed criterion analysis to a file in the worktree
- [ ] Verify subagent writes E2E test output to a file in the worktree
- [ ] Verify subagent returns only: per-criterion status (Done/Missing/Partial), brief explanation, detail file path
- [ ] Main agent never reads verify detail files or test output files
- [ ] For missing criteria: main agent delegates to a planning subagent to revise PLAN.md, then delegates the revised
  plan to an implementation subagent. Fix subagents read detail files directly.
- [x] Parent agent context consumed by Phases 3a+3b is substantially reduced: verbose output (criterion analysis,
  test output) is written to files; parent agent receives only compact JSON with per-criterion status and 1-2
  sentence explanations

## Execution Steps
1. **Measure current baseline**
   - Record average parent-agent input tokens consumed by Phases 3a+3b across 3+ sessions

2. **Create verify subagent definition**
   - Files: `plugin/agents/work-verify.md`
   - Agent accepts: issue metadata, worktree path, PLAN.md path, execution result (commits, files changed)
   - Agent writes detailed analysis to `<worktree>/.claude/cat/verify/criteria-analysis.json`
   - Agent writes E2E test output to `<worktree>/.claude/cat/verify/e2e-test-output.json`
   - Agent returns compact JSON:
     ```json
     {
       "status": "PARTIAL",
       "criteria": [
         {
           "name": "hooks.json contains _description field",
           "status": "Done",
           "explanation": "Field exists with correct value"
         },
         {
           "name": "All tests pass",
           "status": "Missing",
           "explanation": "2 test failures in UserDaoTest",
           "detail_file": ".claude/cat/verify/criteria-analysis.json"
         }
       ],
       "e2e": {
         "status": "PASSED",
         "explanation": "CLI tool produces expected output",
         "detail_file": ".claude/cat/verify/e2e-test-output.json"
       }
     }
     ```

3. **Restructure Phases 3a+3b in work-with-issue**
   - Files: `plugin/skills/work-with-issue/SKILL.md`
   - Replace inline verify and E2E logic with Task tool spawn of verify subagent
   - Parse compact JSON result
   - If missing criteria:
     a. Spawn **planning subagent** with detail file paths → revises PLAN.md with fix steps
     b. Spawn **implementation subagent** with revised PLAN.md → implements fixes, reads detail files directly
   - Re-invoke verify subagent after fixes (max 2 iterations)

4. **Run tests**
   - Verify acceptance criteria checking still works with new delegation

5. **Measure post-change context usage**
   - Compare parent-agent token consumption for Phases 3a+3b against baseline

## Post-conditions
- [x] Phases 3a+3b parent-agent context usage substantially reduced (file-based handoff eliminates inline verbose
  output from parent context)
- [ ] All acceptance criteria still verified (no criterion skipped)
- [ ] E2E tests still run and results reported accurately
- [ ] Fix iteration loop works via planning subagent → implementation subagent delegation
- [ ] Detail files exist in worktree after verification
- [ ] E2E: A complete /cat:work run succeeds with the new verify delegation
