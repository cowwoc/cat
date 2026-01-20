# Plan: optimize-add-wizard

## Goal

Reduce user interaction round-trips in /cat:add by batching independent questions into single
AskUserQuestion calls. Currently asks 4+ sequential questions that don't depend on each other,
resulting in unnecessary latency.

## Satisfies

None - infrastructure optimization

## Current State

The /cat:add task workflow asks questions sequentially:
1. Task type (independent)
2. Version selection (depends on my analysis, not user's prior answer)
3. Task name (freeform - must remain separate)
4. Scope (independent)
5. Dependencies (independent)
6. Blocks (independent)

Each question requires a full round-trip, adding latency.

## Target State

Batch independent questions:
- **Batch 1:** Task type + Scope + Dependencies + Blocks (4 questions → 1 call)
- **Then:** Version selection (requires analysis)
- **Then:** Task name (freeform)

Reduces 6 round-trips to 3.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Conditional follow-ups (e.g., "6+ files" → split suggestion) won't work in batch
- **Mitigation:** Handle "6+ files" response after batch returns; ask split question separately

## Files to Modify

- `commands/add.md` - Restructure task workflow to batch independent questions

## Acceptance Criteria

- [ ] Task type, scope, dependencies, blocks asked in single AskUserQuestion call
- [ ] Version selection still occurs after my analysis of task description
- [ ] Task name still asked separately (freeform text)
- [ ] "6+ files" scope still triggers split consideration (as follow-up)
- [ ] All existing functionality preserved

## Execution Steps

1. **Restructure task_ask_type and task_discuss steps**
   - Combine task_ask_type with scope/dependencies/blocks questions
   - Move conditional "6+ files" handling to after batch response
   - Verify: Run /cat:add, confirm single question dialog for batched items

2. **Test edge cases**
   - Verify "6+ files" still prompts for task splitting
   - Verify version suggestion still works correctly
   - Verify: Full /cat:add flow completes successfully
