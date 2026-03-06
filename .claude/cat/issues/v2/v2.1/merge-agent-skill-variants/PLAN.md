# Plan: merge-agent-skill-variants

## Goal

Remove redundant skill variants. Every skill should have exactly the variants it needs:
- Base variant (no `-agent` suffix): user-invocable, `disable-model-invocation: true`
- `-agent` variant: model-invocable, `user-invocable: false`
- Both: when both users and the model invoke the skill

## Satisfies
None

## Background

The user/agent skill split exists for per-agent state correctness: subagents must pass their own
`catAgentId` (not `${CLAUDE_SESSION_ID}`) when invoking skills that use per-agent marker files
(see `plugin/concepts/skill-loading.md` lines 266-303).

## Scope: 4 Changes

### Drop `-agent` variant (user-only skills — model never invokes)

1. `init` / `init-agent` — initialization is always user-driven
2. `statusline` / `statusline-agent` — statusline setup is always user-driven

For these: delete the `-agent` directory, remove `disable-model-invocation: true` from base SKILL.md
(so it remains user-invocable only, which is the default), update any references.

### Drop base variant (agent-only skills — user never invokes)

3. `get-output` / `get-output-agent` — internal output engine, model-only
4. `recover-from-drift` / `recover-from-drift-agent` — model self-diagnostic, model-only

For these: delete the base directory, update any references to use the `-agent` name.

## Out of Scope (Correctly Paired)

These 16 skills correctly have both variants because both users and the model invoke them:
`add`, `cleanup`, `config`, `empirical-test`, `feedback`, `get-subagent-status`, `help`, `learn`,
`load-skill`, `optimize-doc`, `optimize-execution`, `remove`, `research`, `retrospective`, `status`,
`work`

These 33 agent-only skills correctly have only an `-agent` variant:
`batch-read`, `batch-write`, `collect-results`, `compare-docs`, `consolidate-doc`, `decompose-issue`,
`delegate`, `extract-investigation-context`, `format-documentation`, `get-diff`, `get-history`,
`get-session-id`, `git-amend`, `git-commit`, `git-merge-linear`, `git-rebase`, `git-rewrite-history`,
`git-squash`, `grep-and-read`, `merge-subagent`, `register-hook`, `safe-remove-code`, `safe-rm`,
`skill-builder`, `stakeholder-concern-box`, `stakeholder-review`, `stakeholder-review-box`,
`stakeholder-selection-box`, `tdd-implementation`, `token-report`, `validate-git-safety`,
`verify-implementation`, `work-complete`, `work-merge`, `work-prepare`, `work-with-issue`,
`write-and-commit`

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** References to deleted skill names in other skills' first-use.md files
- **Mitigation:** Grep all plugin files for references before and after deletion

## Files to Modify

### Drop `-agent` (init, statusline)
- Delete `plugin/skills/init-agent/` directory
- Delete `plugin/skills/statusline-agent/` directory
- Edit `plugin/skills/init/SKILL.md` — remove `disable-model-invocation: true`
- Edit `plugin/skills/statusline/SKILL.md` — remove `disable-model-invocation: true`
- Update any `first-use.md` references from `cat:init-agent` to `cat:init`
- Update any `first-use.md` references from `cat:statusline-agent` to `cat:statusline`

### Drop base (get-output, recover-from-drift)
- Delete `plugin/skills/get-output/` directory
- Delete `plugin/skills/recover-from-drift/` directory
- Update any `first-use.md` references from `cat:get-output` to `cat:get-output-agent`
- Update any `first-use.md` references from `cat:recover-from-drift` to `cat:recover-from-drift-agent`

### Documentation
- Update `plugin/concepts/skill-loading.md` to reflect the reduced set of paired variants

## Pre-conditions
- [ ] 2.1-fix-work-skill-catagentid-priming is closed

## Execution Waves

### Wave 1
- Delete the 4 redundant skill directories listed above
  - Files: `plugin/skills/init-agent/`, `plugin/skills/statusline-agent/`,
    `plugin/skills/get-output/`, `plugin/skills/recover-from-drift/`
- Remove `disable-model-invocation: true` from `init` and `statusline` base SKILL.md files
  - Files: `plugin/skills/init/SKILL.md`, `plugin/skills/statusline/SKILL.md`
- Grep all `plugin/skills/**/first-use.md` for references to the 4 deleted skill names and update them
  - Files: any `first-use.md` with stale references

### Wave 2
- Update `plugin/concepts/skill-loading.md` to document the corrected variant sets
- Update `InjectSessionInstructions.java` if it references the deleted skills
  - Files: `plugin/concepts/skill-loading.md`, `client/src/main/java/**/InjectSessionInstructions.java`

## Post-conditions
- [ ] `plugin/skills/init-agent/` does not exist
- [ ] `plugin/skills/statusline-agent/` does not exist
- [ ] `plugin/skills/get-output/` does not exist
- [ ] `plugin/skills/recover-from-drift/` does not exist
- [ ] `plugin/skills/init/SKILL.md` does not contain `disable-model-invocation`
- [ ] `plugin/skills/statusline/SKILL.md` does not contain `disable-model-invocation`
- [ ] No references to `cat:init-agent`, `cat:statusline-agent`, `cat:get-output` (without -agent),
  or `cat:recover-from-drift` (without -agent) remain in plugin files
- [ ] `skill-loading.md` reflects the corrected variant sets
- [ ] All tests pass (`mvn -f client/pom.xml test`)
