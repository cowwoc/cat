# Plan: chain-add-wizard-bash-calls

## Current State
The `/cat:add` skill instructs the agent to run many small Bash calls sequentially during issue creation:
- Name format validation (1 call)
- Name uniqueness check (1 call)
- Version validation (1 call)
- Existing issues count (1 call)
- Branch strategy check (1 call)
- mkdir (1 call)
- Parent STATE.md update (1 call)
- Verification grep (1 call)
- git add + commit (1 call)
- Render script (1 call)

Each Bash call triggers 3-6 hooks (PreToolUse + PostToolUse), adding ~500-1000 tokens of hook overhead per call.
With ~10 Bash calls per issue creation, that's ~5-10K tokens of pure hook overhead.

## Target State
Chain independent Bash operations using `&&` to reduce round-trips. Group into logical batches:
1. **Validation batch:** format check && uniqueness check && version validation (1 call instead of 3)
2. **Setup batch:** mkdir && branch strategy detection (1 call instead of 2)
3. **Update batch:** STATE.md update && verification (1 call instead of 2)
4. **Commit batch:** git add && git commit (already 1 call)
5. **Render:** render script (1 call)

Target: 5 Bash calls instead of ~10, saving ~5K tokens of hook overhead per issue creation.

## Satisfies
None - efficiency optimization

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - same operations, just chained
- **Mitigation:** Each chained command uses `&&` so failure stops the chain (same error behavior)

## Files to Modify
- `plugin/skills/add/SKILL.md` - Update Bash code blocks in steps: task_validate_name, task_discuss,
  task_create, task_update_parent, task_commit to chain related operations

## Execution Steps
1. **Step 1:** Read `plugin/skills/add/SKILL.md` and identify all Bash code blocks in the issue creation workflow
   (steps task_validate_name through task_commit)
2. **Step 2:** Combine the two validation Bash blocks in task_validate_name into a single `&&`-chained call
3. **Step 3:** Combine the existing issues count check from task_discuss with any preceding setup commands
4. **Step 4:** Combine the STATE.md update and verification grep in task_update_parent into a single call
5. **Step 5:** Run `python3 /workspace/run_tests.py` to verify no regressions

## Success Criteria
- [ ] Issue creation workflow uses fewer Bash calls (target: ~5 instead of ~10)
- [ ] All validation, setup, and update operations still execute correctly
- [ ] Error handling preserved (&&-chaining stops on failure)
- [ ] All tests pass
