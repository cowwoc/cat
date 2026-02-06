# Plan: workflow-progress-display

## Goal
Add visual progress indicators to /cat:work that show the current task name and workflow step within the context of all
steps.

## Requirements

| ID | Requirement | Priority | Acceptance Criteria |
|----|-------------|----------|---------------------|
| REQ-001 | *Define requirement* | must-have | *How to verify* |


## Satisfies
None - UX improvement task

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Emoji rendering varies by terminal (cat emoji, checkmark)
- **Mitigation:** Test on common terminals (iTerm2, VS Code, Windows Terminal); use fallback ASCII if needed

## Files to Modify
- .claude/commands/cat/work.md - Add progress display calls at each workflow step
- .claude/commands/cat/skills/render-box.md - Leverage existing box rendering capability
- Related workflow files that need progress indicators

## Acceptance Criteria
- [ ] Header shows cat emoji and task name (e.g., "🐱 CAT › 2.0-variant2-diff-tool")
- [ ] Double-line separator under header (═══)
- [ ] Step indicators show all workflow phases (Preparing, Executing, Reviewing, Merging)
- [ ] Current step marked with `►` and `◀── current` pointer
- [ ] Completed steps show checkmark `✓`
- [ ] Pending steps show hollow circle `◦`

## Visual Format

Style E is implemented in work.md `<progress_output>` section. Key elements:

- Header: `🐱 CAT › {task-name}` in a bordered box
- Phase indicators using `▸▹◆✓✗` symbols
- 4 phases: Preparing, Executing, Reviewing, Merging

See work.md lines 42-192 for complete specification.

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
