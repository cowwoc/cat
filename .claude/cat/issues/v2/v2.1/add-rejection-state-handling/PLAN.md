# Plan: add-rejection-state-handling

## Goal
Add explicit rejection-state handling to work-with-issue so the workflow responds correctly when
verification is partial, stakeholder review is rejected, or the user rejects an approval gate.

## Satisfies
None - action item A026 from retrospective (PATTERN-022)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Must cover all three rejection cases clearly without adding excessive length
- **Mitigation:** Add a focused REJECTION HANDLING section with a table or numbered list

## Files to Modify
- `plugin/skills/work-with-issue/first-use.md` - add REJECTION HANDLING section covering three cases:
  (1) PARTIAL verify result, (2) rejected stakeholder review, (3) user rejects approval gate

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Read work-with-issue/first-use.md:** Examine current approval gate and review step structure
   - Files: `plugin/skills/work-with-issue/first-use.md`
2. **Add REJECTION HANDLING section:** Insert a section covering all three rejection cases with
   explicit, unambiguous instructions:
   - PARTIAL verify result → STOP, fix unmet post-conditions first, do NOT proceed to stakeholder review
   - Rejected stakeholder review → automatically enter re-work loop (fix issues, re-run review) without
     asking user permission
   - User rejects AskUserQuestion approval gate → re-present the gate in the NEXT response, do not skip
   - Files: `plugin/skills/work-with-issue/first-use.md`
3. **Run tests:** Execute `mvn -f client/pom.xml verify` to verify no regressions
   - Files: None (validation step)
4. **Commit:** Commit the changes
   - Files: `plugin/skills/work-with-issue/first-use.md`

## Post-conditions
- [ ] `work-with-issue/first-use.md` contains a REJECTION HANDLING section
- [ ] Section specifies: PARTIAL verify = stop and fix before proceeding to review
- [ ] Section specifies: rejected stakeholder review = automatic re-work loop, no user permission needed
- [ ] Section specifies: rejected approval gate = re-present gate in next response, do not skip
- [ ] All tests pass (`mvn -f client/pom.xml verify` exits 0)
