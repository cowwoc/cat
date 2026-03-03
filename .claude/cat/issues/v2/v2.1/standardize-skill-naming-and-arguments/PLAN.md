# Plan: rename-skillloader-fix-arg-forwarding

## Goal

Naming, argument-forwarding, and consistency fixes:

1. Rename `SkillLoader` class to `LoadSkill` to match the binary name
2. Update its usage documentation to clarify that `catAgentId` is mandatory
3. Fix `work/SKILL.md` to forward `$ARGUMENTS` to load-skill
4. Add `-agent` suffix to all 36 agent-invoked skills that currently lack it
5. Audit every skill's argument pipeline end-to-end: verify that `argument-hint` args are
   forwarded correctly from SKILL.md → load-skill → first-use.md → subsequent INVOKE calls

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

### Java class rename

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java` — rename class and
  file to `LoadSkill.java`; update usage Javadoc to:
  `Usage: load-skill <plugin-root> <skill-name> <project-dir> <catAgentId> [skill-args...]`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillLoaderTest.java` — rename to
  `LoadSkillTest.java`
- `ClearSkillMarker.java`, `AotTraining.java`, `TestSkillOutputNoTag.java`,
  `TestSkillOutputWithTag.java`, `InjectCatAgentId.java` — update imports

### Plugin doc references to SkillLoader

- `plugin/skills/git-squash/first-use.md`, `plugin/skills/skill-builder/first-use.md`,
  `plugin/skills/learn/first-use.md`, `plugin/concepts/skill-loading.md`,
  `plugin/hooks/README.md` — update `SkillLoader` → `LoadSkill`

### Argument pipeline audit and fixes

For every skill with an `argument-hint`, verify the full argument chain:

1. **SKILL.md**: Does the `!`` preprocessor invocation forward `$ARGUMENTS` to load-skill?
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
- Update `.claude/rules/*.md` and `.claude/cat/rules/*.md` if they reference skill names

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1: Java class rename + arg forwarding

- Rename `SkillLoader.java` → `LoadSkill.java`; update class name, self-references, Javadoc
- Rename `SkillLoaderTest.java` → `LoadSkillTest.java`
- Update all Java imports from `SkillLoader` to `LoadSkill`
- Update plugin doc references from `SkillLoader` to `LoadSkill`
- Fix `plugin/skills/work/SKILL.md`: append `$ARGUMENTS`
- Run `mvn -f client/pom.xml test`

### Wave 2: Argument pipeline audit

- For each skill with `argument-hint` in its SKILL.md frontmatter, verify the argument chain:
  SKILL.md → load-skill → first-use.md positional variables → INVOKE directives
- Fix any broken or redundant argument forwarding
- Run `mvn -f client/pom.xml test`

### Wave 3: Skill directory renames

- Rename all 36 skill directories to add `-agent` suffix
- Update all `cat:<old-name>` references across the entire plugin to `cat:<new-name>`
- Run `mvn -f client/pom.xml test`

## Post-conditions

- [ ] No remaining references to `SkillLoader` in `client/src/` or `plugin/`
- [ ] Usage Javadoc reads:
  `Usage: load-skill <plugin-root> <skill-name> <project-dir> <catAgentId> [skill-args...]`
- [ ] `plugin/skills/work/SKILL.md` load-skill invocation ends with
  `"${CLAUDE_SESSION_ID}" $ARGUMENTS`
- [ ] Every skill with `argument-hint` correctly forwards args through the full pipeline:
  SKILL.md → load-skill → first-use.md → INVOKE directives
- [ ] No skill uses `"$0" $ARGUMENTS` (redundant catAgentId duplication)
- [ ] All `user-invocable: false` skills have `-agent` suffix in their directory name
- [ ] No remaining references to old skill names without `-agent` suffix (verified with grep)
- [ ] All Maven tests pass
