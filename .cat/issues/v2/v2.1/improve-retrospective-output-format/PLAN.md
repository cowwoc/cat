# Plan: improve-retrospective-output-format

## Goal
Replace the plain-text dump produced by `/cat:retrospective` with a structured display box that matches
the visual style used by other CAT skills.

## Satisfies
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Box width must respect `displayWidth` config; category/pattern counts may vary
- **Mitigation:** Reuse existing `DisplayUtils` box-drawing helpers; add tests for typical and edge-case data

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetRetrospectiveOutput.java` - replace
  string-concatenation output with `DisplayUtils`-based box layout
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetRetrospectiveOutputTest.java` - update/extend
  tests to cover new formatted output

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Update `GetRetrospectiveOutput.generateAnalysis()` to produce a formatted display box:
  - Header: "RETROSPECTIVE ANALYSIS"
  - Trigger line and period/mistake count as subtitle rows
  - Sections: Category Breakdown, Action Item Effectiveness, Pattern Status, Open Action Items
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetRetrospectiveOutput.java`
- Add/update tests for the new formatted output
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/GetRetrospectiveOutputTest.java`
- Run `mvn -f client/pom.xml verify` and confirm all tests pass

## Post-conditions
- [ ] `/cat:retrospective` output is rendered inside a display box consistent with other CAT skills
- [ ] All existing `GetRetrospectiveOutputTest` tests pass with updated expectations
- [ ] `mvn -f client/pom.xml verify` exits 0
