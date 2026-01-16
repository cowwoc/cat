# Plan: workflow-progress-display

## Goal
Add visual progress indicators to /cat:work that show the current task name and workflow step within the context of all steps.

## Satisfies
None - UX improvement task

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Output formatting consistency across terminals
- **Mitigation:** Use proven box-drawing characters and test rendering

## Files to Modify
- .claude/commands/cat/work.md - Add progress display calls at each workflow step
- .claude/commands/cat/skills/render-box.md - Leverage existing box rendering capability
- Related workflow files that need progress indicators

## Acceptance Criteria
- [ ] Progress box shows task name (e.g., "CAT > 2.0-variant2-diff-tool")
- [ ] Step indicators show all workflow phases (Preparing, Executing, Reviewing, Merging)
- [ ] Current step is highlighted (filled) while future steps show (hollow)
- [ ] Completed steps show appropriate indicator

## Visual Format

```
 ╭───────────────────────────────────────────────────────────────╮
  │  CAT > 2.0-variant2-diff-tool                                 │
  ╰───────────────────────────────────────────────────────────────╯

  > Preparing <
  . Executing
  . Reviewing
  . Merging
```

## Execution Steps
1. **Step 1:** Identify all workflow steps in /cat:work
   - Files: .claude/commands/cat/work.md
   - Verify: List of steps documented

2. **Step 2:** Create progress display helper or integrate with render-box
   - Files: Skill files as needed
   - Verify: Box renders correctly with task name

3. **Step 3:** Add progress display calls at each workflow step transition
   - Files: .claude/commands/cat/work.md
   - Verify: Progress shows during workflow execution

4. **Step 4:** Test rendering across workflow phases
   - Verify: Visual output matches acceptance criteria
