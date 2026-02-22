# Plan: Simplify Review Thresholds

## Goal

Replace the two-part `reviewThresholds` configuration (`autofix` + `proceed`) with a single `autofix` field that
accepts severity levels (LOW, MEDIUM, HIGH, CRITICAL). The value indicates the minimum severity at which the agent will
automatically iterate to fix concerns. Default is LOW (fix all concerns automatically).

## Satisfies

- None (simplification/refactor of existing feature)

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** Breaking change for any existing `reviewThresholds` config with `proceed` settings
- **Mitigation:** The `proceed` settings are rarely customized; migration is straightforward

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/Config.java` — Remove `proceed` constants/methods, change autofix
  values to LOW/MEDIUM/HIGH/CRITICAL, update default to LOW
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/ConfigTest.java` — Update tests for new autofix values, remove
  all proceed-related tests
- `plugin/skills/work-with-issue/first-use.md` — Remove proceed config reading, simplify autofix loop to use new
  severity values
- `plugin/skills/config/first-use.md` — Update config wizard UI for new autofix values, remove proceed step
- `plugin/skills/stakeholder-review/first-use.md` — Update any references to proceed limits

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Steps

1. **Update Config.java:**
   - Remove `DEFAULT_PROCEED_LIMITS` constant
   - Remove `DEFAULT_REVIEW_THRESHOLDS` combined map
   - Change `DEFAULT_AUTOFIX_LEVEL` from `"high_and_above"` to `"low"`
   - Change valid autofix values from `{"all", "high_and_above", "critical", "none"}` to
     `{"low", "medium", "high", "critical"}`
   - Change `reviewThresholds` from a nested object to a simple string field (just the autofix value)
   - Remove `getReviewThresholds()` method
   - Remove `getProceedLimit()` method
   - Update `getAutofixLevel()` to read from the simplified config structure
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/Config.java`

2. **Update ConfigTest.java:**
   - Update `reviewThresholdsReturnsDefaultsWhenConfigMissing` → test new default "low"
   - Update `autofixLevelDefaultsToHighAndAbove` → default is now "low"
   - Remove `proceedLimitDefaultsMatchCurrentBehavior`
   - Update `reviewThresholdsReadFromConfigFile` → test simple string value
   - Update `reviewThresholdsPartialConfigFallsBackToDefaults` → simplify
   - Remove `getProceedLimitThrowsForNullSeverity`
   - Remove `getProceedLimitThrowsForBlankSeverity`
   - Update `getAutofixLevelThrowsForInvalidValue` → test with old value like "all"
   - Remove `getProceedLimitThrowsForNegativeValue`
   - Remove `reviewThresholdsPartialProceedMapFallsBackToDefaults`
   - Update `configAsMapIncludesReviewThresholds` if needed
   - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/ConfigTest.java`

3. **Update work-with-issue skill:**
   - Remove bash code reading PROCEED_* values from config (lines 553-587)
   - Simplify AUTOFIX_LEVEL reading to parse simple string value
   - Update auto-fix loop comments to reference new severity values
   - Update loop condition mapping: LOW=fix all, MEDIUM=fix MEDIUM+, HIGH=fix HIGH+, CRITICAL=fix CRITICAL only
   - Remove proceed-limit-based blocking logic
   - Files: `plugin/skills/work-with-issue/first-use.md`

4. **Update config wizard skill:**
   - Change autofix options from "all/high_and_above/critical/none" to "LOW/MEDIUM/HIGH/CRITICAL"
   - Remove Step 2 (proceed limits configuration)
   - Update config reference section to remove proceed fields
   - Update "Review Thresholds autofix Values" reference
   - Files: `plugin/skills/config/first-use.md`

5. **Update stakeholder-review skill and any other skill references:**
   - Search all skills for proceed/proceed limit references and update
   - Files: `plugin/skills/stakeholder-review/first-use.md` and others found by grep

6. **Run tests to verify:**
   - `mvn -f client/pom.xml test`

## Post-conditions

- [ ] `Config.getAutofixLevel()` returns one of: "low", "medium", "high", "critical"
- [ ] `Config.getAutofixLevel()` defaults to "low" when no config is present
- [ ] `Config` class has no `getProceedLimit()` method
- [ ] `Config` class has no `getReviewThresholds()` method returning a map
- [ ] Setting `"reviewThresholds": "high"` in cat-config.json causes `getAutofixLevel()` to return "high"
- [ ] All tests pass (`mvn -f client/pom.xml test` exit code 0)
- [ ] The config wizard offers LOW/MEDIUM/HIGH/CRITICAL options (no proceed step)
- [ ] The work-with-issue auto-fix loop uses the new severity values
