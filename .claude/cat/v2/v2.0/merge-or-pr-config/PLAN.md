# Plan: merge-or-pr-config

## Goal
Add a configuration option that controls whether task branches are merged directly to the base branch or pushed as a PR after receiving user approval.

## Satisfies
- None (infrastructure task)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Ensuring backward compatibility with existing `merge` workflow
- **Mitigation:** Default to current behavior (merge), only change if explicitly configured

## Files to Modify
- `cat-config.json` schema - Add `completionWorkflow` option
- `commands/config.md` - Add wizard option for setting completion workflow
- `skills/git-merge-linear/SKILL.md` - Check config before merging
- `workflows/execute-task.md` - Branch on config value at approval gate

## Acceptance Criteria
- [ ] New config option `completionWorkflow` with values: `merge` or `pr`
- [ ] Config wizard updated to allow setting this option
- [ ] Approval gate respects the setting (merge to main OR create PR)
- [ ] Default value is `merge` (current behavior)

## Execution Steps
1. **Step 1:** Add `completionWorkflow` to cat-config.json schema
   - Files: references/config-schema.md, cat-config.json
   - Verify: Schema validation passes

2. **Step 2:** Update config wizard
   - Files: commands/config.md
   - Verify: Wizard shows new option

3. **Step 3:** Update approval gate workflow
   - Files: workflows/execute-task.md, skills/git-merge-linear/SKILL.md
   - Verify: Merge vs PR behavior changes based on config

4. **Step 4:** Test both workflows
   - Verify: `merge` mode works as before
   - Verify: `pr` mode creates PR instead of merging
