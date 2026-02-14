# Plan: optimize-verify-subagent-count

## Goal
Optimize the verify-implementation skill to use a single verification subagent when all acceptance criteria reference the same files, instead of spawning one subagent per criterion. Reduces token usage from ~187K to ~30-40K for typical single-file issues.

## Satisfies
None - infrastructure optimization

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Single agent might miss criteria that a dedicated agent would catch
- **Mitigation:** The single agent receives all criteria at once and verifies each; output format is unchanged

## Files to Modify
- `plugin/skills/verify-implementation/first-use.md` - Change spawn_verifiers step to group criteria by file dependencies and spawn single agent for same-file criteria, parallel agents only for genuinely independent criteria

## Acceptance Criteria
- [ ] Criteria referencing the same files (from PLAN.md file specs and execution steps) are verified by a single subagent
- [ ] Criteria referencing completely different files are verified by separate parallel subagents
- [ ] Verification report output format (Summary, Acceptance Criteria Verification, File Specifications, Overall Assessment) remains unchanged
- [ ] Verification still correctly identifies missing, partial, and complete criteria
- [ ] Token usage reduced for same-file scenarios (single agent reads file once instead of N times)
- [ ] No functionality regression

## Execution Steps
1. **Step 1:** Read current verify-implementation skill definition
   - Files: `plugin/skills/verify-implementation/first-use.md`
2. **Step 2:** Modify the spawn_verifiers step:
   - Extract file references from each acceptance criterion and from the PLAN.md file specifications
   - Group criteria by their file dependencies (criteria that reference the same set of files go in one group)
   - For each group: spawn a single verification subagent that receives ALL criteria in that group
   - Update the subagent prompt template to accept multiple criteria and return an array of results
3. **Step 3:** Update the collect_results step to handle both single-result and multi-result responses from subagents
4. **Step 4:** Verify the report step still produces identical output format

## Success Criteria
- [ ] For a 7-criterion issue touching 1 file, only 1 verification subagent is spawned (not 7)
- [ ] For an issue with criteria touching 3 different files, up to 3 subagents are spawned
- [ ] Report format is identical to current output