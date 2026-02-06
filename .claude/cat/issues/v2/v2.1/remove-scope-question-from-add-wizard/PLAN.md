# Plan: remove-scope-question-from-add-wizard

## Current State
The add skill's `task_discuss` step asks the user "How many files will this issue likely touch?" via
AskUserQuestion. This is information the LLM can estimate from the issue description, wasting a
user interaction round-trip.

## Target State
The scope question is removed from the add skill. The LLM estimates file scope internally based on
the issue description and type, using it to decide whether to suggest splitting (6+ files estimate)
without asking the user.

## Satisfies
None - UX improvement

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - removes a question, doesn't change output format
- **Mitigation:** The 6+ files split suggestion still triggers based on LLM estimate

## Files to Modify

| File | Change |
|------|--------|
| `plugin/skills/add/SKILL.md` | Remove scope AskUserQuestion, add LLM estimation instruction |

## Acceptance Criteria
- [ ] Behavior unchanged for issue creation output
- [ ] Tests passing
- [ ] No AskUserQuestion about file count in add wizard

## Execution Steps

1. **Step 1:** Edit `plugin/skills/add/SKILL.md` step `task_discuss`
   - Remove the "Scope question (always ask)" section (substep 3) including the AskUserQuestion
   - Replace with instruction: "Estimate the number of files this issue will touch based on the
     description and type. If estimate is 6+ files, proceed to the split suggestion question.
     Otherwise continue to dependencies."
   - Keep the "6+ files" split suggestion AskUserQuestion (substep 5) but trigger it based on
     LLM estimate instead of user answer
   - Remove the "Unsure - need to research" path that added to UNKNOWNS list

2. **Step 2:** Run tests
   - `python3 /workspace/run_tests.py`

3. **Step 3:** Commit changes
   - Commit type: `config:` (Claude-facing skill modification)

## Success Criteria
- [ ] Add wizard no longer asks "How many files will this issue likely touch?"
- [ ] Large-scope issues (6+ files) still trigger the split suggestion
- [ ] All tests pass
