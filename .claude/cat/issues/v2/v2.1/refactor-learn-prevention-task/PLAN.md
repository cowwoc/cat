# Plan: refactor-learn-prevention-task

## Current State
The /cat:learn skill's prevent phase (phase-prevent.md) requires the subagent to implement prevention directly
(edit files, commit changes). This fails when worktree isolation prevents edits on the base branch, and the subagent
cannot create CAT tasks. The result is that prevention implementation is lost or requires manual follow-up.

## Target State
The prevent phase subagent returns structured prevention proposal data (what to change, where, why) WITHOUT
implementing it. The parent agent in SKILL.md receives this proposal, presents the findings to the user, and offers
to create a CAT task for the prevention implementation via /cat:add.

## Satisfies
None - infrastructure improvement

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Changes the prevent phase output contract; subagent no longer implements prevention directly
- **Mitigation:** Update both phase-prevent.md output format and SKILL.md parent agent handling together

## Files to Modify
- plugin/skills/learn/phase-prevent.md - Remove direct implementation requirement (Steps 9, 9b, 9c, blocking gate);
  replace with structured proposal output that includes: prevention_type, prevention_level, target_files,
  description_of_changes, and task_creation_info (title, description, acceptance criteria)
- plugin/skills/learn/SKILL.md - Update Step 3 (Display Phase Summaries) to: parse prevention proposal from subagent,
  present findings to user, use AskUserQuestion to offer task creation for prevention, invoke /cat:add if user accepts

## Acceptance Criteria
- [ ] Prevent phase subagent returns prevention proposal without implementing changes
- [ ] Proposal includes: prevention_type, prevention_level, target_files, description_of_changes, task_creation_info
- [ ] Parent agent presents prevention findings to user after subagent completes
- [ ] Parent agent asks user via AskUserQuestion whether to create a task for prevention
- [ ] If user accepts, parent agent invokes /cat:add with pre-populated description from proposal
- [ ] If user declines, findings are still recorded in mistakes JSON for future reference
- [ ] Existing learn workflow still functions for all mistake tiers (quick and deep)
- [ ] Tests passing
- [ ] No regressions to existing skills

## Execution Steps
1. **Step 1:** Refactor plugin/skills/learn/phase-prevent.md
   - Keep Steps 5-8 (context degradation, prevention level, quality evaluation, existing check) as analysis-only
   - Remove Step 9 (Implement Prevention), Step 9b (Priming Verification), Step 9c (Related Files Check)
   - Remove the blocking gate (M134/A022) that requires file edits
   - Add new Step 9: "Propose Prevention" that constructs a structured proposal object
   - Update output format to include task_creation_info with: suggested_title, suggested_description,
     suggested_acceptance_criteria, files_to_modify, prevention_type, prevention_level
   - Keep scenario_verified, existing_prevention_failed, prevention_quality in output
2. **Step 2:** Update plugin/skills/learn/SKILL.md Step 3 (Display Phase Summaries)
   - After displaying phase summaries, check if prevent phase returned task_creation_info
   - Present prevention proposal to user: what needs to change, where, why
   - Use AskUserQuestion: "Create a task for this prevention?" with options:
     - "Yes, create task" - Invoke /cat:add with pre-populated description
     - "No, skip" - Record learning without prevention task
   - If user accepts, invoke /cat:add skill with the suggested description
3. **Step 3:** Update the prevent phase output JSON format in phase-prevent.md
   - Replace files_modified (list of edited files) with files_to_modify (list of files that need changes)
   - Replace prevention_description (what was changed) with prevention_proposal (what should be changed)
   - Add task_creation_info object to output schema
4. **Step 4:** Run existing tests to verify no regressions
   - Run: mvn -f hooks/pom.xml verify

## Success Criteria
- [ ] Prevent phase no longer requires direct file edits
- [ ] Parent agent successfully proposes task creation to user
- [ ] All existing tests pass with no regressions