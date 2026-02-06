# Plan: batch-add-skill-context

## Problem
Each `/cat:add` invocation loads the full 1,421-line (38KB) skill text into the conversation context via the
`<command-name>` expansion. When creating multiple issues in a single session (common pattern: user describes
2-3 related issues), each invocation re-loads the entire skill text.

Evidence from session 3f82d1d1: Two `/cat:add` invocations loaded ~76KB of duplicated skill text. With the
200K context window, this consumed ~38% of available context just for skill instructions.

## Target State
Add a "batch add" or "add another" continuation pattern so that after the first `/cat:add` completes, the
agent can create additional issues without re-loading the full skill text. Options:
- **Option A:** Add a "Create another issue?" prompt at the end of task_done step that loops back to
  task_gather_intent without re-invoking the skill
- **Option B:** Support multiple descriptions in a single invocation:
  `/cat:add issue1 description | issue2 description`

## Satisfies
None - context optimization

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - additive change, existing single-issue flow unchanged
- **Mitigation:** The loop-back pattern preserves all existing validation and creation steps

## Files to Modify
- `plugin/skills/add/SKILL.md` - Add continuation prompt in task_done step

## Execution Steps
1. **Step 1:** Read the task_done step in `plugin/skills/add/SKILL.md`
2. **Step 2:** After the render-add-complete.sh output, add an AskUserQuestion:
   - header: "Continue?"
   - question: "Would you like to add another issue?"
   - options: "Yes, add another issue" / "No, done"
3. **Step 3:** If "Yes", loop back to task_gather_intent (ask for new description). All version/type context
   from the current invocation is preserved in conversation memory.
4. **Step 4:** If "No", display the existing completion message and end normally.
5. **Step 5:** Run `python3 /workspace/run_tests.py` to verify no regressions

## Success Criteria
- [ ] After creating an issue, user is offered option to create another without re-invoking the skill
- [ ] Second issue creation reuses existing conversation context (no skill re-expansion)
- [ ] Single-issue flow unchanged when user selects "No, done"
- [ ] All tests pass
