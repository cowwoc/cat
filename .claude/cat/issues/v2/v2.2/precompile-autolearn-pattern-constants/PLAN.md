# Plan: precompile-autolearn-pattern-constants

## Goal

Precompile all regex `Pattern.compile()` calls in `AutoLearnMistakes.java` as `private static final Pattern` constants
to avoid repeated compilation cost on every hook invocation and improve readability.

## Parent Requirements

None

## Background

All 16 `Pattern.compile()` calls inside `detectMistake()` recompile their regex on every hook invocation. These are
fixed-string patterns that should be precompiled once at class load time.

## Approaches

### A: Extract to private static final Pattern constants
- **Risk:** LOW
- **Scope:** 1 file (`AutoLearnMistakes.java`)
- **Description:** Move each `Pattern.compile()` call to a `private static final Pattern` field at the class level with
  a descriptive name (e.g., `BUILD_FAILURE_PATTERN`, `TEST_FAILURE_PATTERN`). The `detectMistake()` method references
  the constants instead of recompiling on each call.

> Approach A is recommended: straightforward refactor with clear naming benefit and measurable performance improvement.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** None — purely mechanical refactor; behavior is unchanged
- **Mitigation:** Existing tests verify behavior is preserved

## Files to Modify

- `plugin/hooks/src/main/java/.../AutoLearnMistakes.java` — Extract all `Pattern.compile()` calls to
  `private static final Pattern` constants

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

- Identify all `Pattern.compile()` calls inside `detectMistake()` in `AutoLearnMistakes.java`
  - Files: `plugin/hooks/src/main/java/.../AutoLearnMistakes.java`
- Extract each call to a descriptive `private static final Pattern` constant at the class level
  (e.g., `BUILD_FAILURE_PATTERN`, `TEST_FAILURE_PATTERN`, etc.)
  - Files: `plugin/hooks/src/main/java/.../AutoLearnMistakes.java`
- Update `detectMistake()` to reference the new constants
  - Files: `plugin/hooks/src/main/java/.../AutoLearnMistakes.java`
- Run `mvn -f client/pom.xml test` to verify no regressions
  - Files: (none — verification step)

## Post-conditions

- [ ] All `Pattern.compile()` calls previously inside `detectMistake()` are now `private static final Pattern`
  constants at the class level
- [ ] Each constant has a descriptive name that reflects its purpose
- [ ] `detectMistake()` references the constants instead of calling `Pattern.compile()` inline
- [ ] All existing tests pass
- [ ] No behavioral change — only mechanical refactor
