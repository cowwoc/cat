# Plan: rename-skillloader-fix-arg-forwarding

## Goal

Naming, argument-forwarding, and consistency fixes:

1. Rename binary script `load-skill` to `skill-loader` (keep Java class named `SkillLoader`)
2. Simplify skill-loader CLI: read `CLAUDE_PLUGIN_ROOT` and `CLAUDE_PROJECT_DIR` from JvmScope
   (environment variables) instead of requiring them as CLI arguments. New CLI:
   `skill-loader <skill-name> <catAgentId> [skill-args...]`
3. Update its usage documentation to clarify that `catAgentId` is mandatory
4. Fix `work/SKILL.md` to forward `$ARGUMENTS` to skill-loader
5. Add `-agent` suffix to all 36 agent-invoked skills that currently lack it
6. Audit every skill's argument pipeline end-to-end: verify that `argument-hint` args are
   forwarded correctly from SKILL.md → skill-loader → first-use.md → subsequent INVOKE calls

## Satisfies

None

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** Renaming 36 skill directories requires updating every reference across all
  skill files, agent docs, first-use.md files, Java handlers, session instructions, and
  CLAUDE.md. Missing a reference causes skill invocation failures.
- **Mitigation:** Systematic grep-based verification; all Maven tests must pass; grep
  post-conditions confirm no stale references remain

## Skills to Rename (add `-agent` suffix)

`batch-read`, `batch-write`, `collect-results`, `compare-docs`, `decompose-issue`, `delegate`,
`extract-investigation-context`, `format-documentation`, `get-diff`, `get-history`,
`get-session-id`, `git-amend`, `git-commit`, `git-merge-linear`, `git-rebase`,
`git-rewrite-history`, `git-squash`, `grep-and-read`, `merge-subagent`, `register-hook`,
`safe-remove-code`, `safe-rm`, `skill-builder`, `stakeholder-concern-box`,
`stakeholder-review`, `stakeholder-review-box`, `stakeholder-selection-box`,
`tdd-implementation`, `token-report`, `validate-git-safety`, `verify-implementation`,
`work-complete`, `work-merge`, `work-prepare`, `work-with-issue`, `write-and-commit`

## Files to Modify

### Binary rename + CLI simplification

- Rename binary `load-skill` to `skill-loader` in `client/build-jlink.sh` HANDLERS array
- Simplify `SkillLoader.main()`: read `pluginRoot` and `projectDir` from `JvmScope` (which reads
  `CLAUDE_PLUGIN_ROOT` and `CLAUDE_PROJECT_DIR` env vars) instead of CLI args
- New CLI: `skill-loader <skill-name> <catAgentId> [skill-args...]` (was 5+ args, now 2+)
- Update all SKILL.md preprocessor directives to remove `"${CLAUDE_PLUGIN_ROOT}"` and
  `"${CLAUDE_PROJECT_DIR}"` args: `!skill-loader <skill-name> "$0"` (or `"${CLAUDE_SESSION_ID}"`)
- Update all plugin doc references from `load-skill` to `skill-loader`
- Java class remains `SkillLoader`; usage Javadoc updated to:
  `Usage: skill-loader <skill-name> <catAgentId> [skill-args...]`

### Argument pipeline audit and fixes

For every skill with an `argument-hint`, verify the full argument chain:

1. **SKILL.md**: Does the `!`` preprocessor invocation forward `$ARGUMENTS` to skill-loader?
   - User-invocable skills: must use `"${CLAUDE_SESSION_ID}" $ARGUMENTS`
   - Agent-invoked skills: must use `$ARGUMENTS` (which already includes catAgentId)
   - Skills that redundantly pass `"$0" $ARGUMENTS` must drop `"$0"`
2. **first-use.md**: Do `$1`, `$2`, etc. map to the correct `argument-hint` params?
   (with `$0` always being catAgentId)
3. **INVOKE directives in first-use.md**: When first-use.md invokes other skills, are the
   correct positional variables forwarded?

Known issues:
- `work/SKILL.md` — missing `$ARGUMENTS` after `"${CLAUDE_SESSION_ID}"`
- `work-complete/SKILL.md` — redundant `"$0" $ARGUMENTS` (catAgentId passed twice)
- `get-output-agent/SKILL.md` — redundant `"$0" $ARGUMENTS` (catAgentId passed twice)
- `work-complete/first-use.md` — INVOKE uses `$0 $1` which maps to catAgentId, catAgentId(dup)
  instead of completedIssue, targetBranch

### Skill directory renames (36 directories)

- Rename each skill directory under `plugin/skills/` to add `-agent` suffix
- Update all `Skill("cat:<old-name>")` references across all skill files, agent docs,
  first-use.md files, and SKILL.md files
- Update `InjectSessionInstructions.java` if it references skill names
- Update `plugin/agents/*.md` if they reference skill names in `skills:` frontmatter
- Update `CLAUDE.md` if it references skill names
- Update `.claude/rules/*.md` and `.cat/rules/*.md` if they reference skill names

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1: Binary rename + arg forwarding

- Rename binary `load-skill` → `skill-loader` in `client/build-jlink.sh`
- Update all SKILL.md preprocessor directives: `client/bin/load-skill` → `client/bin/skill-loader`
- Update SkillLoader Javadoc to reference `skill-loader` binary name
- Update plugin doc references from `load-skill` to `skill-loader`
- Fix `plugin/skills/work/SKILL.md`: append `$ARGUMENTS`
- Run `mvn -f client/pom.xml test`

### Wave 2: Argument pipeline audit

- For each skill with `argument-hint` in its SKILL.md frontmatter, verify the argument chain:
  SKILL.md → skill-loader → first-use.md positional variables → INVOKE directives
- Fix any broken or redundant argument forwarding
- Run `mvn -f client/pom.xml test`

### Wave 3: Skill directory renames

- Rename all 36 skill directories to add `-agent` suffix
- Update all `cat:<old-name>` references across the entire plugin to `cat:<new-name>`
- Run `mvn -f client/pom.xml test`

## Post-conditions

- [ ] Binary renamed to `skill-loader`; Java class remains `SkillLoader`
- [ ] `SkillLoader.main()` reads `pluginRoot` and `projectDir` from `JvmScope` env vars
- [ ] New CLI: `skill-loader <skill-name> <catAgentId> [skill-args...]`
- [ ] Usage Javadoc reads:
  `Usage: skill-loader <skill-name> <catAgentId> [skill-args...]`
- [ ] All SKILL.md preprocessor directives use simplified invocation (no `${CLAUDE_PLUGIN_ROOT}`
  or `${CLAUDE_PROJECT_DIR}` args)
- [ ] `plugin/skills/work/SKILL.md` skill-loader invocation ends with
  `"${CLAUDE_SESSION_ID}" $ARGUMENTS`
- [ ] Every skill with `argument-hint` correctly forwards args through the full pipeline:
  SKILL.md → skill-loader → first-use.md → INVOKE directives
- [ ] No skill uses `"$0" $ARGUMENTS` (redundant catAgentId duplication)
- [ ] All `user-invocable: false` skills have `-agent` suffix in their directory name
- [ ] No remaining references to old skill names without `-agent` suffix (verified with grep)
- [ ] No remaining references to `load-skill` binary in plugin/ or client/
- [ ] All Maven tests pass
