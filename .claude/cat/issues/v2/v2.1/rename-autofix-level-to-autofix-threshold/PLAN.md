# Plan

## Type

Refactor

## Goal

Rename all references to `AUTOFIX_LEVEL` to `AUTOFIX_THRESHOLD` across the codebase for improved clarity. The term "threshold" more accurately describes the semantic purpose: the severity level at or above which concerns are automatically fixed before presenting results to the user.

## Rationale

Current naming uses "level" which is ambiguous — it could refer to a severity classification or a logical level of operation. The correct term is "threshold" because it represents a minimum severity boundary:
- Users configure a threshold (e.g., "low", "medium", "high")
- Concerns at or above that severity are auto-fixed
- Concerns below that threshold are presented to the user

This rename improves code readability and aligns terminology with the actual behavior.

## Files to Modify

The following files contain references to `AUTOFIX_LEVEL`, `autofixLevel`, `autofix_level`, or `DEFAULT_AUTOFIX_LEVEL`:

### Java Implementation Files
- `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/Config.java`
  - `DEFAULT_AUTOFIX_LEVEL` constant
  - `getAutofixLevel()` method and its documentation
  - Error messages referencing "autofix level"

### Java Test Files
- `/workspace/client/src/test/java/io/github/cowwoc/cat/hooks/test/ConfigTest.java`
  - Test method names
  - Test assertions and comments

### Skill Documentation Files
- `/workspace/plugin/skills/stakeholder-review/first-use.md`
  - Documentation of AUTOFIX_LEVEL configuration
  - Code examples using the variable

- `/workspace/plugin/skills/work-with-issue/first-use.md`
  - References to AUTOFIX_LEVEL in execution steps
  - Code snippets and comments

## Specific Changes

### 1. Config.java
- Rename constant: `DEFAULT_AUTOFIX_LEVEL` → `DEFAULT_AUTOFIX_THRESHOLD`
- Rename method: `getAutofixLevel()` → `getAutofixThreshold()`
- Update Javadoc comments to use "threshold" terminology
- Update error messages to reference "autofix threshold"
- Update inline comments that mention "autofix level"

### 2. ConfigTest.java
- Update test method names that reference "autofixLevel"
- Update test documentation and assertions

### 3. Skill Documentation Files
- Update variable names in code examples: `AUTOFIX_LEVEL` → `AUTOFIX_THRESHOLD`
- Update variable names in bash code: `AUTOFIX_RAW` → `AUTOFIX_RAW` (naming context-dependent, keep if internal bash variable)
- Update documentation text that mentions "autofix level" → "autofix threshold"
- Update comments in code blocks

## Important Notes

- **DO NOT rename** the JSON configuration key `reviewThreshold` — it is already correctly named and represents the same concept. This issue is about renaming Java constants and method names, not the configuration interface.
- The config file uses `reviewThreshold` (correct) — the Java code was using inconsistent naming with `autofixLevel`
- This is a straightforward mechanical rename with zero behavioral change

## Risk Assessment

**Risk Level:** LOW

- Pure rename refactoring with no logic changes
- No method signatures change (except name)
- No configuration format changes
- All references are internal implementation details
- Public APIs (config file keys) remain unchanged

## Post-conditions

- [ ] All Java files renamed: `AUTOFIX_LEVEL` → `AUTOFIX_THRESHOLD`, `autofixLevel` → `autofixThreshold`, `getAutofixLevel()` → `getAutofixThreshold()`
- [ ] All documentation updated with consistent "threshold" terminology
- [ ] All test files updated with new names
- [ ] Code compiles without warnings
- [ ] All existing tests pass
- [ ] Build verification passes: `mvn -f client/pom.xml verify`

## Execution Steps

1. Rename the constant and method in `/workspace/client/src/main/java/io/github/cowwoc/cat/hooks/Config.java`:
   - `DEFAULT_AUTOFIX_LEVEL` → `DEFAULT_AUTOFIX_THRESHOLD`
   - `getAutofixLevel()` → `getAutofixThreshold()`
   - Update Javadoc comments and error messages

2. Update the test file `/workspace/client/src/test/java/io/github/cowwoc/cat/hooks/test/ConfigTest.java`:
   - Update test method names
   - Update references in test code

3. Update `/workspace/plugin/skills/stakeholder-review/first-use.md`:
   - Replace `AUTOFIX_LEVEL` with `AUTOFIX_THRESHOLD` in code examples
   - Update documentation text to use "threshold" terminology

4. Update `/workspace/plugin/skills/work-with-issue/first-use.md`:
   - Replace `AUTOFIX_LEVEL` with `AUTOFIX_THRESHOLD` in code examples
   - Replace variable references: `getAutofixLevel()` → `getAutofixThreshold()`
   - Update documentation and comments

5. Run tests to verify all changes work correctly:
   - `mvn -f client/pom.xml test`

6. Run full build verification:
   - `mvn -f client/pom.xml verify`

7. Commit changes with message:
   - `refactor: rename AUTOFIX_LEVEL to AUTOFIX_THRESHOLD for clarity`

## Post-Conditions

- All references to `AUTOFIX_LEVEL` in Java code replaced with `AUTOFIX_THRESHOLD`
- All method calls to `getAutofixLevel()` replaced with `getAutofixThreshold()`
- All documentation uses consistent "threshold" terminology
- Build verification passes without errors
- All tests pass
- Git history records the refactoring with a single atomic commit
