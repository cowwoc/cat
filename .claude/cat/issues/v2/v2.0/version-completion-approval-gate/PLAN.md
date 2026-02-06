# Plan: version-completion-approval-gate

## Current State
When `/cat:work` completes all tasks in a version, it automatically continues to the next version without user
confirmation. This can be problematic because the user may need to publish/release the completed version before moving
on.

## Target State
Add an approval gate that triggers in two scenarios:
1. When all tasks in the current version are completed
2. When `/cat:work` is about to pick up a task from a different version

The gate should:
- Display a version completion summary
- Remind user they may want to publish the completed version
- Require explicit user approval to continue to the next version
- Give user the option to exit the work loop

## Satisfies
None - workflow improvement

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - adds user confirmation, doesn't change existing behavior
- **Mitigation:** Gate is informational and user can always choose to continue

## Files to Modify
- `plugin/commands/work.md` - Add version boundary detection and approval gate logic
- `plugin/concepts/work.md` (if exists) - Document the approval gate behavior

## Acceptance Criteria
- [ ] Approval gate triggers when all tasks in current version are completed
- [ ] Approval gate triggers when next available task is in a different version
- [ ] Gate displays version completion summary
- [ ] Gate reminds user about publishing/releasing
- [ ] Gate requires explicit approval to continue
- [ ] Gate offers option to exit work loop
- [ ] Behavior unchanged when staying within same version

## Execution Steps
1. **Step 1:** Identify where version boundary is detected in work.md
   - Files: plugin/commands/work.md
   - Verify: Understand current task selection flow
2. **Step 2:** Add version tracking to work loop
   - Track the version of the current/previous task
   - Detect when next task is in different version
3. **Step 3:** Implement approval gate UI
   - Show version completion summary
   - Display reminder about publishing
   - Present continue/exit options via AskUserQuestion
4. **Step 4:** Handle user response
   - Continue: proceed to next version
   - Exit: break out of work loop gracefully
5. **Step 5:** Test approval gate behavior
   - Verify: Gate appears at version boundaries
   - Verify: User can continue or exit
