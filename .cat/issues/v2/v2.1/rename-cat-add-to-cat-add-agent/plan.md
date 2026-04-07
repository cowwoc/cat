# Plan: rename-cat-add-to-cat-add-agent

## Current State

Several `plugin/` skill files and `client/` Java files still reference the old `/cat:add` skill name in
documentation, comments, and hook messages. The skill was renamed to `cat:add-agent` when user-invocable
wrappers were removed, but these references were not updated.

## Target State

All references to `/cat:add` in `plugin/` and `client/` files are updated to `/cat:add-agent`.

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** LOW
- **Breaking Changes:** None — documentation and comment changes only
- **Mitigation:** Verify no remaining `/cat:add` references in plugin and client files after update

## Files to Modify

- `plugin/skills/plan-builder-agent/first-use.md` — 2 occurrences of `/cat:add`
- `plugin/skills/work-implement-agent/first-use.md` — 1 occurrence of `/cat:add`
- `plugin/skills/learn/phase-prevent.md` — 1 occurrence of `/cat:add`
- `client/src/main/java/io/github/cowwoc/cat/hooks/write/EnforcePluginFileIsolation.java` — hook message
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/ItemType.java` — Javadoc
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetAddOutput.java` — Javadoc (2 occurrences)
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/HandlerOutputTest.java` — Javadoc
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/IndexJsonParsingTest.java` — Javadoc
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java` — URL encoding example in Javadoc

## Pre-conditions

- [ ] All dependent issues are closed

## Jobs

### Job 1

- In `plugin/skills/plan-builder-agent/first-use.md`, replace both occurrences of `` `/cat:add` `` with `` `/cat:add-agent` ``
  - Files: `plugin/skills/plan-builder-agent/first-use.md`
- In `plugin/skills/work-implement-agent/first-use.md`, replace `` `/cat:add` `` with `` `/cat:add-agent` ``
  - Files: `plugin/skills/work-implement-agent/first-use.md`
- In `plugin/skills/learn/phase-prevent.md`, replace `/cat:add` with `/cat:add-agent`
  - Files: `plugin/skills/learn/phase-prevent.md`
- In `EnforcePluginFileIsolation.java`, replace `` `/cat:add <task-description>` `` with `` `/cat:add-agent <task-description>` ``
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/write/EnforcePluginFileIsolation.java`
- In `ItemType.java`, update Javadoc `/cat:add` → `/cat:add-agent`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/ItemType.java`
- In `GetAddOutput.java`, update both Javadoc occurrences `/cat:add` → `/cat:add-agent`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetAddOutput.java`
- In `HandlerOutputTest.java`, update Javadoc `/cat:add` → `/cat:add-agent`
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/HandlerOutputTest.java`
- In `IndexJsonParsingTest.java`, update Javadoc `/cat:add` → `/cat:add-agent`
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/IndexJsonParsingTest.java`
- In `GetSkill.java`, update URL encoding example: `cat%3Aadd` → `cat%3Aadd-agent` and `cat:add` → `cat:add-agent`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/GetSkill.java`

## Post-conditions

- [ ] No occurrences of `/cat:add` (without `-agent`) remain in `plugin/` or `client/` files
- [ ] All tests pass: `mvn -f client/pom.xml verify -e`
