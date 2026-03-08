<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan: fix-testjvmscope-dry-violation

## Goal
Move the six duplicated `ConcurrentLazyReference` fields from `MainJvmScope` and `TestJvmScope` into
`AbstractJvmScope`, eliminating the DRY violation introduced when `JvmScope` was abstracted.

## Parent Requirements

None — code quality improvement

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Must verify all subclasses still compile and tests pass after moving fields
- **Mitigation:** `mvn verify` catches regressions

## Files to Modify

1. `client/src/main/java/io/github/cowwoc/cat/hooks/AbstractJvmScope.java` — add 6 lazy fields
2. `client/src/main/java/io/github/cowwoc/cat/hooks/MainJvmScope.java` — remove duplicated fields
3. `client/src/test/java/io/github/cowwoc/cat/hooks/test/TestJvmScope.java` — remove duplicated fields

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Move `jsonMapper`, `yamlMapper`, `displayUtils`, `detectSequentialTools`, `predictBatchOpportunity`,
  `userIssues` ConcurrentLazyReference fields and their getter implementations from `MainJvmScope` and
  `TestJvmScope` into `AbstractJvmScope`
- Keep test-specific fields in `TestJvmScope` (claudeProjectDir, claudePluginRoot, claudeSessionId, etc.)
- Run `mvn -f client/pom.xml verify` and confirm all tests pass
- Update STATE.md: status closed, progress 100%

## Post-conditions

- [ ] ConcurrentLazyReference fields for jsonMapper, yamlMapper, displayUtils, detectSequentialTools,
      predictBatchOpportunity, userIssues exist only in AbstractJvmScope (not duplicated in subclasses)
- [ ] `mvn -f client/pom.xml verify` passes with no errors
