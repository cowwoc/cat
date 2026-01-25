# Plan: branching-strategy-config

## Goal
Add branching strategy configuration to /cat:init and enforce it in /cat:work, allowing users to
define their preferred git workflow (main-based, version-branch-based, etc.) and receive appropriate
warnings when working on tasks that don't match the current branch.

## Satisfies
None - workflow improvement

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Must handle various branching strategies without breaking existing workflows
- **Mitigation:** Default to current behavior if no strategy configured; add clear warnings not blocks

## Files to Modify
- plugin/commands/init.md - Add branching strategy questions
- plugin/commands/work.md - Add branch validation before task execution
- .claude/cat/PROJECT.md template - Add Branching Strategy section

## Execution Steps
1. **Step 1:** Define branching strategy options
   - Files: plugin/commands/init.md
   - Options: main-based (merge to main, tag releases), version-branch-based (merge to version branches, merge to main for release), trunk-based (all on main)
   - Verify: AskUserQuestion presents options during init

2. **Step 2:** Store strategy in PROJECT.md
   - Files: plugin/commands/init.md, templates
   - Add "## Branching Strategy" section with user's choice and rules
   - Verify: PROJECT.md contains branching strategy after init

3. **Step 3:** Add branch validation to /cat:work
   - Files: plugin/commands/work.md
   - Before executing task, check current branch against task's version
   - If version-branch strategy and branch mismatch, warn user
   - Verify: Warning shown when on v2.0 branch but task is v2.1

4. **Step 4:** Add branch guidance messages
   - Files: plugin/commands/work.md
   - Provide actionable guidance: "Switch to branch vX.Y to work on this task"
   - Verify: Clear instructions shown to user

5. **Step 5:** Add version completion merge prompt
   - Files: plugin/commands/work.md
   - When all tasks for a version are completed and version-branch strategy is configured:
     - Detect that version tasks are all done
     - Ask user if they want to merge version branch back to main/master
     - If yes, offer to create new branch for next version
   - Verify: Prompt appears after completing last task of a version

## Acceptance Criteria
- [ ] /cat:init asks about branching strategy
- [ ] Strategy saved to PROJECT.md
- [ ] /cat:work validates branch before execution
- [ ] Clear warnings when branch doesn't match task version
- [ ] When version tasks complete, prompt to merge version branch to main/master
- [ ] Offer to create new branch for next version after merge
- [ ] Existing projects without strategy config continue to work (backward compatible)
