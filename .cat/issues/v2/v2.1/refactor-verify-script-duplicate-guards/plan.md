## Type

refactor

## Goal

Remove duplicate guard conditions in `VerifyDeferPlanGeneration.java`. The class contains redundant file-existence
checks and redundant `Files.readString()` calls that make it harder to maintain and understand. Consolidate the
duplicates without changing the observable verification behavior.

## Pre-conditions

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/VerifyDeferPlanGeneration.java` contains duplicate guard logic
- The verification behavior is correct and should be preserved
- Existing tests in `VerifyDeferPlanGenerationTest.java` pass

## Research Findings

The file `VerifyDeferPlanGeneration.java` performs 4 checks across 2 skill files:

- **addSkill** (`plugin/skills/add-agent/first-use.md`): Check 1 (no plan-builder-agent) and Check 2 (has
  planTempFile mktemp)
- **workImplementSkill** (`plugin/skills/work-implement-agent/first-use.md`): Check 3 (has hasSteps) and Check 4
  (has plan-builder-agent)

Current duplicate guards:
1. `Files.notExists(addSkill)` is checked at lines 103 and 126 — once per check instead of once per file
2. `Files.readString(addSkill)` is called at lines 111 and 134 — content read twice for same file
3. `Files.notExists(workImplementSkill)` is checked at lines 149 and 172 — once per check instead of once per file
4. `Files.readString(workImplementSkill)` is called at lines 157 and 180 — content read twice for same file

The refactoring reads each file once, then runs all checks against the cached content. When a file is missing, all
checks for that file report FAIL with "File not found" — preserving the exact current behavior.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/VerifyDeferPlanGeneration.java` — consolidate duplicate
  file-existence checks and `Files.readString()` calls

## Sub-Agent Waves

### Wave 1
1. **Refactor `VerifyDeferPlanGeneration.run()` to read each file once:**
   - Read `addSkill` content once into a local variable (or null if file missing)
   - Read `workImplementSkill` content once into a local variable (or null if file missing)
   - Run Check 1 and Check 2 against the cached `addSkill` content (null means file missing → FAIL)
   - Run Check 3 and Check 4 against the cached `workImplementSkill` content (null means file missing → FAIL)
   - Remove the 4 separate `Files.notExists()` guards and the 4 separate `Files.readString()` calls
   - Replace with 2 reads: one for `addSkill`, one for `workImplementSkill`
   - The output format (PASS/FAIL messages, "File not found" text, result counts) must remain identical
   - File: `client/src/main/java/io/github/cowwoc/cat/hooks/util/VerifyDeferPlanGeneration.java`

2. **Run `mvn -f client/pom.xml verify -e`** to confirm all existing tests still pass and linters are clean

3. **Update `index.json`** to set status to `closed` and progress to 100%

## Post-conditions

- Duplicate guard conditions are identified and removed or consolidated
- The class produces identical output to the current version on all inputs
- Code is simpler and easier to maintain (2 file reads instead of 4, 2 existence checks instead of 4)
- All existing tests still pass
- E2E verification: run `mvn -f client/pom.xml verify -e` and confirm exit code 0
