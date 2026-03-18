# Plan: refactor-curiosity-to-effort

## Current State
The `curiosity` config option controls how thoroughly implementation subagents investigate unrelated code quality issues. This is ineffective because implementation subagents use haiku and follow mechanical plans ��� they cannot meaningfully identify such issues.

## Target State
Rename `curiosity` to `effort` and redirect its behavior to where it can be effective:
- **Planning (`/cat:add`):** Controls depth of investigation when creating PLAN.md
- **Stakeholder review:** Controls depth of analysis and scope of review
- **Implementation subagents:** Always focus only on assigned issue (no effort-based behavior)

## Satisfies
None (internal restructuring)

## Effort Behavior Matrix

| Level | Planning (`/cat:add`) | Stakeholder Review | Implementation |
|---|---|---|---|
| `low` | Concise, direct plan. Assume obvious approach. | Changed lines only, obvious issues. No pre-existing issue flagging. | Focus only on assigned issue |
| `medium` | Explore alternatives, note trade-offs in plan. | Changed lines + surrounding context. No pre-existing issue flagging. | Focus only on assigned issue |
| `high` | Deep research, edge cases, document reasoning for chosen vs rejected approaches. | Broader impact on surrounding code. Flag pre-existing issues in reviewed files. | Focus only on assigned issue |

`patience` continues to govern what happens with concerns raised by stakeholder review (fix inline, defer to current version, defer to later version), including pre-existing issues flagged at effort=high.

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Config key rename (`curiosity` ��� `effort`), Java API rename (`getCuriosity()` ��� `getEffort()`, `CuriosityLevel` ��� `EffortLevel`)
- **Mitigation:** Migration script handles config rename; all references updated atomically

## Files to Modify

### Java Source (rename CuriosityLevel ��� EffortLevel)
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/CuriosityLevel.java` ��� Rename file and class to `EffortLevel`, update all Javadoc
- `client/src/main/java/io/github/cowwoc/cat/hooks/Config.java` ��� Rename import, method `getCuriosity()` ��� `getEffort()`, default key `"curiosity"` ��� `"effort"`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetConfigOutput.java` ��� Update method call and display label `"Curiosity"` ��� `"Effort"`
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetInitOutput.java` ��� Update display label

### Java Tests
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/CuriosityLevelTest.java` ��� Rename file and class to `EffortLevelTest`, update all references
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/ConfigTest.java` ��� Update imports, JSON keys, display label assertions, test method names
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/HandlerOutputTest.java` ��� Update JSON keys and display label assertions
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetInitOutputTest.java` ��� Update display label assertions

### Configuration
- `.cat/cat-config.json` ��� Rename key `"curiosity"` ��� `"effort"`
- `plugin/templates/config.json` ��� Rename key `"curiosity"` ��� `"effort"`

### Migration
- `plugin/migrations/2.1.sh` ��� Add Phase 5: rename `"curiosity"` key to `"effort"` in cat-config.json
- `plugin/migrations/1.0.9.sh` ��� Update variable names and JSON output to use `"effort"` instead of `"curiosity"`
- `plugin/migrations/registry.json` ��� Update 1.0.9 description text

### Skills (behavioral changes)
- `plugin/skills/delegate/SUBAGENT-PROMPT-CHECKLIST.md` ��� Remove curiosity section from implementation subagent prompts; clean up `.completion.json` discovered-issues mechanism
- `plugin/skills/delegate/first-use.md` ��� Rename reference
- `plugin/skills/config/first-use.md` ��� Rename menu label, step name, question text, config values
- `plugin/skills/init/first-use.md` ��� Rename JSON template key
- `plugin/skills/help/first-use.md` ��� Rename table rows, JSON examples, description section
- `plugin/skills/collect-results/first-use.md` ��� Update text about subagent curiosity findings

### Skills (effort-based planning and review ��� NEW behavior)
- `plugin/skills/add/first-use.md` ��� Add effort-based planning depth instructions to `issue_create` step
- `plugin/skills/stakeholder-review/first-use.md` ��� Add effort-based review depth/scope instructions to stakeholder prompts

### Documentation
- `README.md` ��� Rename all `curiosity` references to `effort`
- `CHANGELOG.md` ��� Leave historical entries as-is (they describe what happened at that time)
- `.cat/PROJECT.md` ��� Rename reference

### Planning Files (historical ��� rename for consistency)
- `.cat/issues/v2/v2.1/create-config-property-enums/PLAN.md` ��� Update CuriosityLevel references
- `.cat/issues/v2/v2.0/simplify-display-formats/PLAN.md` ��� Update text
- `.cat/issues/v1/v1.9/config-menu-improvements/PLAN.md` ��� Update text
- `.cat/issues/v1/v1.10/config-driven-approach/PLAN.md` ��� Update text

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

### Step 1: Rename Java enum and update all Java source
- Rename `CuriosityLevel.java` to `EffortLevel.java`, update class name and all Javadoc
- Update `Config.java`: import, method name, default key
- Update `GetConfigOutput.java` and `GetInitOutput.java`: method calls and display labels

### Step 2: Update Java tests
- Rename `CuriosityLevelTest.java` to `EffortLevelTest.java`, update class and all references
- Update `ConfigTest.java`, `HandlerOutputTest.java`, `GetInitOutputTest.java`: JSON keys, assertions, method names

### Step 3: Update configuration files
- Rename key in `cat-config.json` and `templates/config.json`

### Step 4: Update migration scripts
- Add Phase 5 to `2.1.sh`: rename `"curiosity"` ��� `"effort"` in cat-config.json using sed/awk
- Update `1.0.9.sh`: variable names and JSON output
- Update `registry.json` description

### Step 5: Update skills ��� rename references
- Update all skill markdown files: `SUBAGENT-PROMPT-CHECKLIST.md`, `delegate/first-use.md`, `config/first-use.md`, `init/first-use.md`, `help/first-use.md`, `collect-results/first-use.md`
- Remove curiosity section and `.completion.json` discovered-issues mechanism from `SUBAGENT-PROMPT-CHECKLIST.md`

### Step 6: Add effort-based planning depth to /cat:add
- In `add/first-use.md` at the `issue_create` step, add instructions that read `effort` from config and adjust plan detail level:
  - `low`: Generate concise plan, assume obvious approach
  - `medium`: Explore alternatives, note trade-offs
  - `high`: Deep research, document reasoning for chosen vs rejected approaches

### Step 7: Add effort-based review depth to stakeholder review
- In `stakeholder-review/first-use.md`, add instructions that read `effort` from config and inject review scope into each stakeholder prompt:
  - `low`: Review changed lines only, flag obvious issues
  - `medium`: Review changed lines + surrounding context
  - `high`: Review broader impact on surrounding code, flag pre-existing issues in reviewed files

### Step 8: Update documentation
- Update `README.md`: all curiosity ��� effort references
- Update `.cat/PROJECT.md`: rename reference
- Leave `CHANGELOG.md` historical entries as-is

### Step 9: Update historical planning files
- Update PLAN.md files in closed issues for consistency

### Step 10: Build and test
- Run `mvn -f client/pom.xml verify` to confirm all tests pass

### Step 11: Delete orphaned CuriosityLevel files
- Delete `client/src/main/java/io/github/cowwoc/cat/hooks/util/CuriosityLevel.java`
- Delete `client/src/test/java/io/github/cowwoc/cat/hooks/test/CuriosityLevelTest.java`
- These files were superseded by EffortLevel.java and EffortLevelTest.java and should not exist alongside the new implementation

### Step 12: Update add/first-use.md with effort-based planning instructions
- In the `issue_create` step, add instructions that read the `effort` config value
- Include guidance for each effort level:
  - `low`: Generate concise plan, assume obvious approach
  - `medium`: Explore alternatives, note trade-offs in plan
  - `high`: Deep research, document reasoning for chosen vs rejected approaches
- This redirects the former curiosity behavior to the planning phase where it is effective

### Step 13: Update stakeholder-review/first-use.md with effort-based review instructions
- In the stakeholder review prompts, add instructions that read the `effort` config value
- Include guidance for each effort level:
  - `low`: Review changed lines only, flag obvious issues
  - `medium`: Review changed lines + surrounding context
  - `high`: Review broader impact on surrounding code, flag pre-existing issues in reviewed files
- This redirects the former curiosity behavior to the review phase where it is effective


## Post-conditions
- [ ] No references to `curiosity` remain in Java source, tests, config, skills, or documentation (except CHANGELOG.md historical entries and v1.9 CHANGELOG.md)
- [ ] `EffortLevel` enum exists with LOW/MEDIUM/HIGH values
- [ ] `Config.getEffort()` returns `EffortLevel`
- [ ] `cat-config.json` uses `"effort"` key
- [ ] `2.1.sh` migration renames `curiosity` ��� `effort` in existing configs
- [ ] `add/first-use.md` includes effort-based planning depth instructions
- [ ] `stakeholder-review/first-use.md` includes effort-based review scope instructions
- [ ] Implementation subagent prompts no longer reference effort/curiosity (always focus on assigned issue)
- [ ] `.completion.json` discovered-issues mechanism removed from `SUBAGENT-PROMPT-CHECKLIST.md`
- [ ] All tests pass (`mvn -f client/pom.xml verify` exits 0)
- [ ] E2E: Running `/cat:config` displays `Effort` (not `Curiosity`) with correct current value
