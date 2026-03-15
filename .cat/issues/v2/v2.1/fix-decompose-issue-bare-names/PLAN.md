# Plan: fix-decompose-issue-bare-names

## Problem
The `decompose-issue-agent` skill creates STATE.md files with bare sub-issue names (e.g., `rename-config-java-core`) in the "Decomposed Into" section instead of fully-qualified names (e.g., `2.1-rename-config-java-core`). This causes `allSubissuesClosed()` in `IssueDiscovery.java` to silently skip these entries via `continue`, incorrectly concluding that all sub-issues are closed.

## Expected vs Actual
- **Expected:** "Decomposed Into" section uses qualified names matching the format recognized by `QUALIFIED_NAME_PATTERN` (e.g., `2.1-rename-config-java-core`)
- **Actual:** Skill creates bare names (e.g., `rename-config-java-core`), which are skipped by the pattern matcher

## Root Cause
In the decompose-issue-agent skill workflow (step "9. Update Original Issue for Decomposition"), the code generates sub-issue names without version prefixes. The parallel execution plan and related steps use bare names instead of constructing fully-qualified issue IDs.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** None — this is a code generation issue, not a runtime logic change
- **Mitigation:** Write tests verifying generated STATE.md files use qualified names in "Decomposed Into" sections

## Files to Modify
- `plugin/skills/decompose-issue-agent/SKILL.md` — Update the skill to construct and use qualified sub-issue names throughout the decomposition workflow

## Test Cases
- [ ] Decompose an issue and verify all entries in "## Decomposed Into" section are fully qualified (match pattern `\d+\.\d+[a-z]?-\S+`)
- [ ] Verify parent STATE.md parallel execution plan references use qualified names
- [ ] Edge case: issue names with letters (e.g., `2.1a-sub-issue`) are handled correctly

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Update `decompose-issue-agent/SKILL.md` to construct qualified sub-issue names using parent version context
  - Files: `plugin/skills/decompose-issue-agent/SKILL.md`

### Wave 2
- Write automated TestNG tests in `IssueDiscoveryTest` (or a new test class) that verify `allSubissuesClosed()`
  correctly processes qualified sub-issue names and silently skips bare names: (1) create a STATE.md with
  fully-qualified "Decomposed Into" entries (e.g., `2.1-parser-lexer`) and assert `allSubissuesClosed()` reads them;
  (2) create a STATE.md with bare names (e.g., `parser-lexer`) and assert those entries are skipped; (3) edge case
  with letter-suffixed version prefix (e.g., `2.1a-sub-issue`) is handled correctly. Run `mvn -f client/pom.xml test`
  and confirm all tests pass.
  - Files: `client/src/test/java/` (IssueDiscoveryTest.java or equivalent)

## Post-conditions
- [ ] All generated STATE.md "Decomposed Into" sections use fully-qualified issue names
- [ ] No regressions in existing decomposition workflows
- [ ] E2E: Create a new decomposed issue and verify `allSubissuesClosed()` correctly detects sub-issue status (not fooled by bare names)
