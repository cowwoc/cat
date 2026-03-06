# Plan: merge-agent-skill-variants

## Current State
21 skills have paired user-facing (`cat:{name}`) and agent-facing (`cat:{name}-agent`) variants. The split
exists for per-agent state correctness: subagents must pass their own `catAgentId` (not
`${CLAUDE_SESSION_ID}`) when invoking skills that use per-agent marker files (see
`plugin/concepts/skill-loading.md` lines 266-303).

## Target State
Merge the `-agent` variants that are never invoked by subagents into their base skills. For these
main-agent-only skills, `${CLAUDE_SESSION_ID}` is always correct, making the `-agent` variant unnecessary.

## Satisfies
None

## Scope: 9 Pairs (Main-Agent-Only)
These skills are user-facing orchestration that no subagent would ever invoke:

1. `work` / `work-agent`
2. `status` / `status-agent`
3. `help` / `help-agent`
4. `config` / `config-agent`
5. `init` / `init-agent`
6. `statusline` / `statusline-agent`
7. `remove` / `remove-agent`
8. `feedback` / `feedback-agent`
9. `recover-from-drift` / `recover-from-drift-agent`

## Out of Scope (Keep Split)
These 12 skills must retain the split because subagents may invoke the `-agent` variant:
`load-skill`, `add`, `learn`, `get-output`, `optimize-doc`, `cleanup`, `retrospective`, `research`,
`empirical-test`, `optimize-execution`, `get-subagent-status`, `consolidate-doc`

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Skill invocation pattern changes; references in first-use.md files must be updated
- **Mitigation:** Update all references before deleting `-agent` directories; run tests after

## Files to Modify (per pair)
For each of the 9 pairs:
- `plugin/skills/{name}/SKILL.md` — remove `disable-model-invocation: true`
- `plugin/skills/{name}-agent/SKILL.md` — delete
- `plugin/skills/{name}-agent/first-use.md` — delete
- All `first-use.md` files referencing `cat:{name}-agent` — update to `cat:{name}`
- `plugin/concepts/skill-loading.md` — update documentation

## Pre-conditions
- [ ] 2.1-fix-work-skill-catagentid-priming is closed

## Execution Waves

### Wave 1
- For each of the 9 skill pairs:
  - Remove `disable-model-invocation: true` from `plugin/skills/{name}/SKILL.md`
  - Search all `plugin/skills/**/first-use.md` files for references to `cat:{name}-agent` and
    update them to `cat:{name}`
  - Delete `plugin/skills/{name}-agent/SKILL.md` and `plugin/skills/{name}-agent/first-use.md`
  - Files: `plugin/skills/*/SKILL.md`, `plugin/skills/*/first-use.md`

### Wave 2
- Update `plugin/concepts/skill-loading.md` to document the reduced set of paired variants
- Update SubagentStartHook or session instructions if they reference the deleted skills
  - Files: `plugin/concepts/skill-loading.md`, `client/src/main/java/**/InjectSessionInstructions.java`

## Post-conditions
- [ ] All 9 `-agent` skill directories are deleted
- [ ] All 9 base skills no longer have `disable-model-invocation: true`
- [ ] No references to the deleted `-agent` skill names remain in plugin files
- [ ] `skill-loading.md` reflects the reduced set of paired variants
- [ ] All tests pass (`mvn -f client/pom.xml test`)
- [ ] E2E: Invoke `/cat:work` and `/cat:status` to confirm they still work when invoked by
  both users and the main agent
