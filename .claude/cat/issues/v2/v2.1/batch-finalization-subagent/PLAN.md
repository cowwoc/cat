# Plan: batch-finalization-subagent

## Goal
Replace individual post-approval steps (merge, cleanup, update_state, commit_metadata, update_changelogs) with a single
`finalization` step that spawns a Finalization subagent, hiding tool calls from the user.

## Current State
- `plugin/commands/work.md` has 5 individual `<step>` elements after approval gate
- Main agent executes these directly, showing tool calls to user
- Inconsistent with other phases (Exploration, Implementation) that use subagent batching

## Target State
- Single `<step name="finalization">` that spawns a subagent
- Subagent executes merge, cleanup, state updates, changelog updates internally
- User sees only the Task tool invocation and result summary
- Errors surfaced via subagent return value

## Satisfies
None - UX improvement / architecture consistency task

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:**
  - Error handling must surface failures properly
  - Merge conflicts need user intervention path
  - Lock release must happen even on failure
- **Mitigation:**
  - Subagent returns structured JSON with status/errors
  - Main agent handles errors based on return value
  - Include fail-fast and cleanup instructions in subagent prompt

## Files to Modify
- `plugin/commands/work.md` - Replace steps merge/cleanup/update_state/commit_metadata/update_changelogs with single
  finalization step

## Acceptance Criteria
- [ ] Single `<step name="finalization">` spawns subagent for post-approval work
- [ ] Subagent prompt includes all merge/cleanup/state/changelog logic
- [ ] Subagent returns structured result for error handling
- [ ] `next_task` step remains separate (user interaction)
- [ ] Concept work.md "Batched into Finalization subagent" remains accurate

## Execution Steps

1. **Step 1:** Create new finalization step structure
   - Replace `<step name="merge">` through `<step name="update_changelogs">` with single `<step name="finalization">`
   - Structure: spawn subagent with comprehensive prompt
   - Keep `<step name="next_task">` unchanged

2. **Step 2:** Build subagent prompt with all finalization logic
   - Include merge logic (ff-only, merge-commit, squash based on config)
   - Include cleanup logic (worktree removal, branch deletion, lock release)
   - Include STATE.md updates (parent rollup)
   - Include CHANGELOG.md updates
   - Include fail-fast conditions and error reporting

3. **Step 3:** Add error handling in main agent
   - Parse subagent return for success/failure
   - Handle merge conflicts (escalate to user)
   - Ensure lock release even on failure

4. **Step 4:** Update STATE.md and test
   - Verify command structure is valid
   - Run tests to check for regressions
