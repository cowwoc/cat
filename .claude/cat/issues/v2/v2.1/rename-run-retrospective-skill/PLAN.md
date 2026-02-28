# Plan: rename-run-retrospective-skill

## Current State
The retrospective skill is registered as `cat:run-retrospective`, which is unnecessarily verbose and inconsistent with
the shorter naming style used by other CAT skills (e.g., `cat:learn`, `cat:status`, `cat:work`).

## Target State
The skill is registered as `cat:retrospective`. All references to `cat:run-retrospective` in skills, docs, and system
configurations are updated to use the new name. The old name no longer exists.

## Satisfies
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** `cat:run-retrospective` invocations will stop working; users must use `cat:retrospective`
- **Mitigation:** Update all internal references during the same commit; no external API surface affected

## Files to Modify
- `plugin/skills/run-retrospective/` - rename directory to `retrospective`
- `plugin/skills/run-retrospective/SKILL.md` - update skill name/description if needed
- `plugin/skills/run-retrospective/first-use.md` - update references
- `plugin/skills/learn/phase-record.md` - update `cat:run-retrospective` reference
- `plugin/skills/learn/first-use.md` - update `cat:run-retrospective` reference
- Any other files referencing `cat:run-retrospective` discovered during implementation

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Step 1:** Search codebase for all occurrences of `cat:run-retrospective` and `run-retrospective` to build a
   complete list of files requiring changes
   - Files: `plugin/`, `docs/`, `.claude/`
2. **Step 2:** Rename the skill directory from `plugin/skills/run-retrospective/` to `plugin/skills/retrospective/`
   - Files: `plugin/skills/run-retrospective/` → `plugin/skills/retrospective/`
3. **Step 3:** Update the skill registration name in `SKILL.md` frontmatter (description, argument-hint, etc.) if
   needed so it registers as `cat:retrospective`
   - Files: `plugin/skills/retrospective/SKILL.md`
4. **Step 4:** Update all references to `cat:run-retrospective` in other skill files to use `cat:retrospective`
   - Files: `plugin/skills/learn/phase-record.md`, `plugin/skills/learn/first-use.md`,
     `plugin/skills/retrospective/first-use.md`
5. **Step 5:** Search for any remaining references in docs, system prompts, or `.claude/` config and update them
   - Files: `docs/`, `.claude/`, any other discovered files
6. **Step 6:** Run all tests to verify no regressions
   - Command: `mvn -f client/pom.xml test`

## Post-conditions
- [ ] `cat:retrospective` successfully invokes the retrospective workflow (E2E: invoke the skill and confirm it runs)
- [ ] `cat:run-retrospective` no longer exists as a registered skill name
- [ ] All internal references use `cat:retrospective` — zero occurrences of `cat:run-retrospective` remain in
  `plugin/` and `docs/`
- [ ] All tests pass after the rename (`mvn -f client/pom.xml test` exits 0)
- [ ] No regressions in the learn → retrospective trigger flow
