# Plan

## Type

feature

## Goal

Change caution config from high to medium. Update CautionLevel enum semantics: medium should additionally run
issue-specific E2E tests (not just compile and unit tests), while high runs all E2E tests. For issues that touch
skills, E2E tests means running the skill's benchmark/e2e tests. Update all places where caution level descriptions
appear (Java enum Javadoc, config wizard, skill docs) to reflect the new semantics.

## Pre-conditions

(none)

## Post-conditions

- [ ] CautionLevel.MEDIUM updated to enable issue-specific E2E tests (not just compile and unit tests)
- [ ] CautionLevel.HIGH updated to run all E2E tests (full test suite)
- [ ] .cat/config.json caution value changed from "high" to "medium"
- [ ] All caution level descriptions consistent across CautionLevel.java Javadoc, config wizard, and skill documentation
- [ ] CautionLevelTest updated to reflect new semantics
- [ ] All tests pass (mvn verify)
- [ ] No regressions in existing functionality
- [ ] E2E: Confirm caution medium triggers issue-specific E2E tests during /cat:work verify phase

## Jobs

### Job 1: Update CautionLevel.java Enum Documentation and Logic

**File:** `client/src/main/java/io/github/cowwoc/cat/claude/hook/util/CautionLevel.java`

**Changes required:**

1. **Update MEDIUM enum Javadoc** (lines 22-24):
   - Current: `"Compile and unit tests (default)."`
   - New: `"Compile, unit tests, and issue-specific E2E tests (default)."`

2. **Update HIGH enum Javadoc** (lines 26-28):
   - Current: `"Compile, unit tests, and E2E tests (maximum confidence)."`
   - New: `"Compile, unit tests, and all E2E tests (maximum confidence)."`

3. **Update isE2eEnabled() method** (lines 55-63):
   - Current logic: `return this == HIGH;`
   - New logic: `return this != LOW;`
   - Update method Javadoc to reflect that both MEDIUM and HIGH enable E2E tests
   - Current Javadoc: `"Returns whether end-to-end tests should run at this caution level."`
   - Update to: `"Returns whether end-to-end tests should run at this caution level. MEDIUM runs issue-specific E2E tests; HIGH runs all E2E tests."`
   - Update `@return` doc: `"{@code true} if E2E tests are enabled (MEDIUM or HIGH), {@code false} otherwise"`

**Rationale:** The core semantic change is that MEDIUM now enables E2E testing (scoped to issue-specific tests), while HIGH runs the full E2E suite. The isE2eEnabled() method currently returns true only for HIGH, but must return true for both MEDIUM and HIGH to enable E2E test execution at both levels.

**Testing impact:** This change will cause `mediumIsE2eDisabled` test to fail (expects false, will get true).

---

### Job 2: Update CautionLevelTest to Match New Semantics

**File:** `client/src/test/java/io/github/cowwoc/cat/client/test/CautionLevelTest.java`

**Changes required:**

1. **Update mediumIsE2eDisabled test** (lines 165-171):
   - Current test name: `mediumIsE2eDisabled`
   - Current expectation: `requireThat(CautionLevel.MEDIUM.isE2eEnabled(), "isE2eEnabled").isFalse();`
   - New test name: `mediumIsE2eEnabled`
   - New expectation: `requireThat(CautionLevel.MEDIUM.isE2eEnabled(), "isE2eEnabled").isTrue();`
   - Update Javadoc: Change "Verifies that MEDIUM disables E2E tests." to "Verifies that MEDIUM enables E2E tests."

**Rationale:** Test must validate the new behavior where MEDIUM enables E2E tests. The test name and assertion must flip to match the new semantics.

**Verification:** After this change, all CautionLevelTest tests should pass when run with the updated CautionLevel.java.

---

### Job 3: Update Project Configuration to Use Medium Caution

**File:** `.cat/config.json`

**Changes required:**

1. Change line 5 from:
   ```json
   "caution": "high",
   ```
   to:
   ```json
   "caution": "medium",
   ```

**Rationale:** The project currently runs with caution=high (full E2E test suite on every verify). With the new MEDIUM semantics (issue-specific E2E tests), medium provides sufficient validation while being faster than running the full test suite every time.

**Impact:** Future /cat:work verify phases will run issue-specific E2E tests instead of the full E2E suite (faster feedback loop).

---

### Job 4: Update Config Wizard Caution Level Descriptions

**File:** `plugin/skills/config/first-use.md`

**Changes required:**

1. **Update Manual Settings - Page 1 - Caution option descriptions** (lines 244-250):
   - Line 248 (Medium description):
     - Current: `"Compile and unit tests (default){' (current)' if caution=='medium'}"`
     - New: `"Compile, unit tests, and issue-specific E2E tests (default){' (current)' if caution=='medium'}"`
   
   - Line 250 (High description):
     - Current: `"Compile, unit tests, and E2E tests (maximum confidence){' (current)' if caution=='high'}"`
     - New: `"Compile, unit tests, and all E2E tests (maximum confidence){' (current)' if caution=='high'}"`

2. **Update Questionnaire - Question 2 mapping** (lines 120-133):
   - Line 131 (Medium explanation text):
     - Current: `"The tests for what you changed — close enough" → CAUTION=medium`
     - Verify that the explanation text in `plugin/templates/questionnaire.md` aligns with the new MEDIUM semantics
     - The question asks: "It's 4:55pm on a Friday and production is down. You've found the fix. Before you push and head out, you run:"
     - Middle answer: "The tests for what you changed — close enough"
     - This maps conceptually to "issue-specific E2E tests" (tests for what you changed)
     - No change needed to question text; the semantic alignment is already correct

3. **Update Configuration Reference table** (lines 627-630):
   - Line 629:
     - Current: `- \`medium\` — Compile and unit tests (default).`
     - New: `- \`medium\` — Compile, unit tests, and issue-specific E2E tests (default).`
   
   - Line 630:
     - Current: `- \`high\` — Compile, unit tests, and E2E tests (maximum confidence).`
     - New: `- \`high\` — Compile, unit tests, and all E2E tests (maximum confidence).`

**Rationale:** All user-facing caution level descriptions must accurately reflect the new semantics. The config wizard is the primary UX surface for setting caution levels, so consistency here is critical.

**Testing verification:** After changes, manually inspect the config wizard output or add a skill test to verify the updated descriptions appear correctly.

---

### Job 5: Run Full Test Suite

**Command:**

```bash
cd /workspace/.cat/work/worktrees/2.1-add-issue-specific-e2e-to-caution-medium
mvn -f client/pom.xml verify -e
```

**Expected outcome:** All tests pass (exit code 0).

**Common failure modes:**
1. CautionLevelTest failure if Job 2 was not completed correctly
2. Checkstyle/PMD violations if Javadoc formatting is incorrect
3. Compilation errors if enum changes broke any calling code

**Resolution:** Fix all errors before proceeding. Do not skip linters.

---


## Research Summary

### Current State Analysis

1. **CautionLevel.java** (lines 16-70):
   - Three enum values: LOW, MEDIUM, HIGH
   - Current semantics:
     - LOW: Compile only (isUnitTestEnabled()=false, isE2eEnabled()=false)
     - MEDIUM: Compile + unit tests (isUnitTestEnabled()=true, isE2eEnabled()=false)
     - HIGH: Compile + unit tests + E2E (isUnitTestEnabled()=true, isE2eEnabled()=true)
   - Core change needed: isE2eEnabled() must return true for MEDIUM

2. **CautionLevelTest.java** (lines 129-180):
   - Six test methods validate current behavior
   - Tests that will need updating:
     - mediumIsE2eDisabled (line 165-171) - must flip to mediumIsE2eEnabled
   - Tests that remain valid:
     - lowIsE2eDisabled (still false)
     - highIsE2eEnabled (still true)

3. **.cat/config.json** (line 5):
   - Current value: "caution": "high"
   - Needs change to: "caution": "medium"

4. **plugin/skills/config/first-use.md**:
   - Manual settings wizard (lines 244-250) - needs description updates
   - Configuration reference table (lines 627-630) - needs description updates
   - Questionnaire (lines 120-133) - mapping is conceptually correct, no changes needed

5. **Additional files with caution references:**
   - 19 files found with "caution.*medium" pattern
   - 81 files found with "isE2eEnabled|E2E.*test" pattern
   - Most are in .cat/issues/ (planning files, not source)
   - Key files: README.md, work-verify.md, work-confirm-agent, work-review-agent

### Semantic Change Summary

| Level | Current Behavior | New Behavior | Change |
|-------|-----------------|--------------|--------|
| LOW | Compile only | Compile only | None |
| MEDIUM | Compile + unit tests | Compile + unit tests + issue-specific E2E | Add E2E |
| HIGH | Compile + unit + all E2E | Compile + unit + all E2E | None (clarify "all") |

### Implementation Complexity

**Low complexity:**
- Single boolean method change (isE2eEnabled)
- One test method flip (mediumIsE2eDisabled → mediumIsE2eEnabled)
- Config file value change
- Documentation string updates

**No breaking changes:**
- Existing code calling isE2eEnabled() will work correctly (method signature unchanged)
- MEDIUM becoming more cautious (adding tests) is backward-compatible
- Config value "medium" is valid in old and new versions

**Testing strategy:**
- Unit tests validate enum behavior (CautionLevelTest)
- Integration testing requires manual E2E verification post-merge
- No new test files needed; existing tests cover the functionality

### Risk Assessment

**Low risk:**
- Change is additive (MEDIUM gains functionality)
- All behavior changes are intentional and documented
- Test coverage exists for all affected code paths
- Config change is isolated to this project (no downstream impact)

**Mitigation:**
- Run mvn verify before commit (catch test failures early)
- Manual E2E verification post-merge (confirm behavior in practice)
- Document the change in commit message (clear intent for future reference)
