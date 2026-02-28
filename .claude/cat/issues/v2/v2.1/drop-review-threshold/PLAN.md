# Plan: Drop reviewThreshold — Patience Matrix Alone Decides Fix vs Defer

## Current State
The concern pipeline has three sequential filters: `minSeverity` drops invisible concerns, the patience matrix marks
survivors as FIX or DEFER, and `reviewThreshold` controls which FIX concerns enter the auto-fix loop. This creates an
inconsistency: a concern marked FIX (worth fixing now) can still be silently dropped if its severity falls below
`reviewThreshold`, while DEFER concerns always persist as follow-up issues. The "FIX but below threshold" path is a
concern that survives all previous filters but then dies at user discretion — a logical gap.

## Target State
Remove `reviewThreshold` entirely. The concern pipeline becomes two steps: `minSeverity` drops invisible concerns,
then the patience matrix routes survivors to FIX (auto-fix loop) or DEFER (follow-up issue). Every concern above
`minSeverity` is guaranteed to be addressed — either immediately or tracked for later. The user's only two knobs are
"what do I care about?" (minSeverity) and "how aggressively to fix now vs defer?" (patience).

## Satisfies
None (infrastructure improvement)

## Breaking Change
Users who configured `reviewThreshold` to a non-default value had custom auto-fix filtering. After this change, the
patience matrix alone decides timing. Config.java will be updated to fail-fast on unknown keys, so existing
`cat-config.json` files containing `reviewThreshold` would error at session start. A `CheckDataMigration` handler
automatically removes the key at SessionStart before validation runs.

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** `reviewThreshold` config key now causes a fail-fast error (without migration)
- **Mitigation:** `CheckDataMigration` automatically removes `reviewThreshold` before Config validation runs

## Files to Modify

**Java (commit together):**
- `client/src/main/java/io/github/cowwoc/cat/hooks/Config.java`
  — Remove `DEFAULT_AUTOFIX_THRESHOLD` constant, `getAutofixThreshold()` method, and `reviewThreshold` entry from
  defaults map; add unknown-key validation in `load()` that throws `IllegalArgumentException` listing unknown keys
- `client/src/main/java/io/github/cowwoc/cat/hooks/session/CheckDataMigration.java`
  — Add migration handler that removes `reviewThreshold` from `cat-config.json` if present; runs at SessionStart
  before Config validation
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetConfigOutput.java`
  — Remove `reviewThreshold` from the settings display output
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/ConfigTest.java`
  — Remove all `reviewThreshold`/`autofixThreshold` test methods (7 methods); add test for unknown-key validation
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetConfigOutputTest.java`
  — Remove `reviewThreshold` from test fixture data
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/CheckDataMigrationTest.java`
  — Add test verifying `reviewThreshold` is removed from config when present

**Skills/config (commit together):**
- `plugin/skills/work-with-issue/first-use.md`
  — Remove AUTOFIX_THRESHOLD reading (lines reading `reviewThreshold` from config); change auto-fix loop condition from
  "concerns at/above AUTOFIX_THRESHOLD" to "all FIX-marked concerns"; route DEFER concerns to follow-up issue creation
- `plugin/skills/stakeholder-review/first-use.md`
  — Remove references to `reviewThreshold` in aggregate and decide steps
- `plugin/skills/config/first-use.md`
  — Remove reviewThreshold menu entry, its persistence step, and the `reviewThreshold` row from the config reference
  table
- `.claude/cat/e2e-config-test/.claude/cat/cat-config.json`
  — Remove `reviewThreshold` key from test fixture

**Docs (commit together):**
- `docs/patience.md`
  — Update pipeline description: remove `reviewThreshold auto-fix loop` step, document that FIX → auto-fix and
  DEFER → follow-up issue
- `docs/severity.md`
  — Update pipeline reference: `minSeverity filter → patience fix/defer decision` (remove reviewThreshold segment)

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps

1. **Step 1:** Update `Config.java` — remove `DEFAULT_AUTOFIX_THRESHOLD` constant, remove `reviewThreshold` from
   the defaults map (`defaults.put("reviewThreshold", ...)`), remove `getAutofixThreshold()` method entirely.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/Config.java`

2. **Step 2:** Update `GetConfigOutput.java` — find the line that reads
   `config.getAutofixThreshold()` (currently assigned to `reviewThreshold` local variable) and the line that formats
   it into the settings display (`"  🔍 Review: " + reviewThreshold`). Remove both lines.
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetConfigOutput.java`

3. **Step 3:** Update `ConfigTest.java` — remove all test methods that reference `autofixThreshold` or
   `reviewThreshold`: `autofixThresholdDefaultsToLow`, `reviewThresholdReadFromConfigFile`,
   `asMapIncludesReviewThresholdInDefaults`, `getAutofixThresholdThrowsForInvalidValue`,
   `autofixThresholdMediumReadFromConfigFile`, `autofixThresholdHighReadFromConfigFile`,
   `autofixThresholdFallsBackForNonStringValue`, `autofixThresholdThrowsForUppercaseValue`.
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/ConfigTest.java`

4. **Step 4:** Update `GetConfigOutputTest.java` — remove `reviewThreshold` from all test fixture JSON strings.
   Specifically remove: the test method `getCurrentSettingsIncludesReviewThreshold` and the `reviewThreshold` key
   from any multi-field JSON fixtures.
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetConfigOutputTest.java`

5. **Step 5:** Commit Java changes.
   - Commit type: `refactor:`
   - Message: `refactor: remove reviewThreshold config — patience matrix alone decides fix vs defer`

6. **Step 6:** Update `work-with-issue/first-use.md` — remove the AUTOFIX_THRESHOLD block (lines reading
   `reviewThreshold` from config and the `AUTOFIX_THRESHOLD` variable). Change the auto-fix loop condition from
   checking severity against AUTOFIX_THRESHOLD to: run for all concerns where patience decision = FIX. Update loop
   description accordingly.
   - Files: `plugin/skills/work-with-issue/first-use.md`

7. **Step 7:** Update `stakeholder-review/first-use.md` — in the aggregate step, remove the sentence about
   `reviewThreshold`. In the decide step, remove the reference to the calling skill reading `reviewThreshold`;
   replace with: the calling skill routes FIX concerns to auto-fix loop and DEFER concerns to follow-up issues.
   - Files: `plugin/skills/stakeholder-review/first-use.md`

8. **Step 8:** Update `config/first-use.md` — remove the reviewThreshold question from the AskUserQuestion wizard,
   its persistence block (the step that writes `reviewThreshold` to config), and the `reviewThreshold` row from the
   configuration reference table at the bottom.
   - Files: `plugin/skills/config/first-use.md`

9. **Step 9:** Remove `reviewThreshold` from e2e config fixture.
   - Files: `.claude/cat/e2e-config-test/.claude/cat/cat-config.json`

10. **Step 10:** Commit skills/config changes.
    - Commit type: `refactor:`
    - Message: `refactor: update skills and config wizard to remove reviewThreshold`

11. **Step 11:** Update `docs/patience.md` — rewrite the pipeline section to remove the `reviewThreshold auto-fix
    loop` step. Document the new two-step pipeline: `minSeverity filter → patience fix/defer decision`. Add a note
    under the FIX outcome: "FIX concerns enter the auto-fix loop immediately." Add a note under DEFER: "DEFER
    concerns are tracked as follow-up issues."
    - Files: `docs/patience.md`

12. **Step 12:** Update `docs/severity.md` — find the concern pipeline line and update from:
    `` `minSeverity` filter → `patience` fix/defer decision → `reviewThreshold` auto-fix loop ``
    to:
    `` `minSeverity` filter → `patience` fix/defer decision (FIX → auto-fix now, DEFER → follow-up issue) ``
    - Files: `docs/severity.md`

13. **Step 13:** Commit docs changes.
    - Commit type: `refactor:`
    - Message: `refactor: update docs to reflect simplified concern pipeline (no reviewThreshold)`

14. **Step 14:** Run `mvn -f client/pom.xml verify` and confirm all tests pass.

## Post-conditions

- [ ] No references to `reviewThreshold` remain in production code (Config.java, GetConfigOutput.java,
  work-with-issue/first-use.md, stakeholder-review/first-use.md, config/first-use.md)
- [ ] `Config.java` has no `getAutofixThreshold()` method and no `DEFAULT_AUTOFIX_THRESHOLD` constant
- [ ] All ConfigTest methods referencing `autofixThreshold` or `reviewThreshold` are removed
- [ ] `docs/patience.md` and `docs/severity.md` describe the two-step pipeline without `reviewThreshold`
- [ ] `.claude/cat/e2e-config-test/.claude/cat/cat-config.json` has no `reviewThreshold` key
- [ ] All tests pass (`mvn -f client/pom.xml verify`)
