# Plan: add-preconditions-postconditions

## Goal
Add pre-conditions and post-conditions sections to PLAN.md, replacing acceptance criteria / success criteria with
post-conditions and entry/exit gates with pre/post-conditions. Update the workflow so preparing verifies
pre-conditions and confirming verifies post-conditions.

## Satisfies
None - workflow infrastructure improvement

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Cross-cutting rename touches templates, Java parsers, skills, and existing data
- **Mitigation:** TDD approach for Java parser changes; migration script for existing PLAN.md files

## Terminology
- Use hyphenated form: **Pre-conditions** and **Post-conditions**
- Section headers: `## Pre-conditions` and `## Post-conditions`
- Config UI labels: `Pre-conditions:` and `Post-conditions:` (full words, not abbreviated)

## Files to Modify

### Templates (source of truth for new PLAN.md files)
- `plugin/templates/issue-plan.md` - Add `## Pre-conditions` section; merge `## Acceptance Criteria` and `## Success Criteria` into `## Post-conditions`
- `plugin/templates/plan.md` - Same changes (older template variant)
- `plugin/templates/minor-plan.md` - `## Gates / ### Entry / ### Exit` → `## Pre-conditions / ## Post-conditions`
- `plugin/templates/major-plan.md` - Same
- `plugin/templates/patch-plan.md` - Same

### Java Parsers
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/VerifyAudit.java` - Parse `## Post-conditions` instead of `## Acceptance Criteria`; rename `extractAcceptanceCriteria()` → `extractPostconditions()`
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/VerifyAuditTest.java` - Update all test fixtures
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/IssueDiscovery.java` - Rename exit gate methods; parse `## Post-conditions` for version-level post-condition issues
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/IssueDiscoveryTest.java` - Update gate-related tests
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` - Add pre-condition verification logic
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetConfigOutput.java` - Rename gate display to pre/post-conditions
- `client/src/main/java/io/github/cowwoc/cat/hooks/AotTraining.java` - Update training data references
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/ProgressBanner.java` - Update acceptanceCriteria reference

### Skills
- `plugin/skills/work-with-issue/first-use.md` - Preparing step: add pre-condition verification; Confirming step: rename to post-conditions
- `plugin/skills/verify-implementation/first-use.md` - Rename acceptance criteria → post-conditions throughout
- `plugin/skills/add/first-use.md` - Update gate creation to use pre/post-conditions terminology; update issue creation for `## Pre-conditions` section
- `plugin/skills/config/first-use.md` - Update Version Gates menu to Version Conditions with pre/post-conditions labels

### Concepts
- `plugin/concepts/version-completion.md` - Update gate terminology

### Migration
- `plugin/migrations/2.1.sh` - Add migration step to rename sections in existing PLAN.md files

## Pre-conditions
- [ ] All dependencies closed (default)

## Execution Steps

### Step 1: Update issue-level PLAN.md templates
1. In `plugin/templates/issue-plan.md`:
   - Add `## Pre-conditions` section before `## Execution Steps` (default: `- [ ] All dependent issues are closed`)
   - Merge `## Acceptance Criteria` and `## Success Criteria` into single `## Post-conditions` section
   - Update HTML comments to explain post-conditions
2. In `plugin/templates/plan.md`: Apply same changes

### Step 2: Update version-level PLAN.md templates
1. In `plugin/templates/minor-plan.md`: Replace `## Gates / ### Entry / ### Exit` with `## Pre-conditions / ## Post-conditions`
2. In `plugin/templates/major-plan.md`: Same
3. In `plugin/templates/patch-plan.md`: Same

### Step 3: Update VerifyAudit.java
1. Rename `extractAcceptanceCriteria()` → `extractPostconditions()`
2. Change section header search from `## Acceptance Criteria` to `## Post-conditions`
3. Update all internal references
4. Update VerifyAuditTest.java test fixtures

### Step 4: Update IssueDiscovery.java
1. Rename `parseExitGateIssues()` → `parsePostconditionIssues()` (or similar)
2. Rename `isExitGateIssue()` → appropriate new name
3. Rename `exitGateSatisfied()` → appropriate new name
4. Update EXIT_ISSUE_PATTERN to parse `## Post-conditions` section
5. Update SearchOptions.overrideGate field name
6. Update IssueDiscoveryTest.java

### Step 5: Update WorkPrepare.java
1. Add pre-condition verification logic that runs during the prepare phase
2. Read `## Pre-conditions` from issue PLAN.md
3. Default pre-condition: all STATE.md dependencies are closed
4. Return pre-condition check results in prepare output

### Step 6: Update GetConfigOutput.java
1. Rename `getVersionGatesOverview()` → `getVersionConditionsOverview()`
2. Rename `getGatesForVersion()` → `getConditionsForVersion()`
3. Rename `getGatesUpdated()` → `getConditionsUpdated()`
4. Update box titles: VERSION GATES → VERSION CONDITIONS, GATES FOR → CONDITIONS FOR
5. Update parameter names: entryGateDescription → preconditionsDescription, exitGateDescription → postconditionsDescription
6. Update display labels to `Pre-conditions:` / `Post-conditions:`
7. Update GetInitOutputTest.java if affected

### Step 7: Update AotTraining.java and ProgressBanner.java
1. Update any `acceptanceCriteria` references to `postconditions`

### Step 8: Update skills
1. `work-with-issue/first-use.md`: Update Step 4 (Confirming) to reference post-conditions; add pre-condition verification to preparing phase
2. `verify-implementation/first-use.md`: Rename acceptance criteria → post-conditions
3. `add/first-use.md`: Update `version_configure_gates` step to use pre/post-conditions; update task creation for `## Pre-conditions`
4. `config/first-use.md`: Update Version Gates → Version Conditions menu and all gate references

### Step 9: Update concepts
1. `plugin/concepts/version-completion.md`: Update gate terminology to conditions terminology

### Step 10: Update v2.1 migration script
1. Add migration logic to `plugin/migrations/2.1.sh` that renames sections in all existing PLAN.md files:
   - `## Acceptance Criteria` → `## Post-conditions`
   - `## Success Criteria` → merge into `## Post-conditions`
   - `## Gates` / `### Entry` / `### Exit` → `## Pre-conditions` / `## Post-conditions`
   - `## Entry Gate` / `## Exit Gate` → `## Pre-conditions` / `## Post-conditions`
   - `## Exit Gate Tasks` → `## Post-conditions`

### Step 11: Run tests
Run `mvn -f client/pom.xml test` and confirm all tests pass.

### Step 12: Smoke test
Run at least one jlink binary (e.g., `verify-audit`, `get-config-output`) and confirm it produces reasonable output.

## Post-conditions
- [ ] Issue PLAN.md template has `## Pre-conditions` and `## Post-conditions` sections (no more Acceptance/Success Criteria)
- [ ] Version PLAN.md templates have `## Pre-conditions` and `## Post-conditions` sections (no more Gates/Entry/Exit)
- [ ] VerifyAudit.java parses `## Post-conditions` section correctly
- [ ] IssueDiscovery.java uses post-conditions terminology for version-level conditions
- [ ] WorkPrepare.java verifies pre-conditions during prepare phase
- [ ] GetConfigOutput.java displays pre/post-conditions in config boxes
- [ ] All skills reference pre/post-conditions terminology consistently
- [ ] Migration script renames sections in existing PLAN.md files
- [ ] All tests pass (`mvn -f client/pom.xml test` exit code 0)
- [ ] E2E: Run verify-audit on an issue with `## Post-conditions` and confirm it extracts criteria correctly
- [ ] E2E: Run work-prepare and confirm pre-condition verification output is included
