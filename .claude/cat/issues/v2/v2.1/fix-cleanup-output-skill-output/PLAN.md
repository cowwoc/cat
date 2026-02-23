# Plan: fix-cleanup-output-skill-output

## Problem
`/cat:cleanup` fails with: `Class io.github.cowwoc.cat.hooks.skills.GetCleanupOutput does not implement SkillOutput`.
The SKILL.md `!` backtick directive triggers SkillLoader which instantiates `GetCleanupOutput` and casts to
`SkillOutput`, but the class doesn't implement the interface.

## Satisfies
None

## Reproduction Code
```
/cat:cleanup
```

## Expected vs Actual
- **Expected:** Cleanup skill loads and displays survey results
- **Actual:** `Error loading skill: Class io.github.cowwoc.cat.hooks.skills.GetCleanupOutput does not implement SkillOutput`

## Root Cause
`GetCleanupOutput` was written as a standalone CLI tool with `main()` before the `SkillOutput` interface pattern was
established. The `!` backtick directive in `first-use.md` line 316 routes through `SkillLoader.invokeSkillOutput()`
which requires the `SkillOutput` interface.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** GetCleanupOutput handles 3 phases (survey/plan/verify); only survey goes through SkillOutput
- **Mitigation:** `getOutput()` delegates to survey phase; plan/verify still use CLI binary with stdin

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetCleanupOutput.java` - implement SkillOutput, add
  getOutput() method

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Add `implements SkillOutput`** to `GetCleanupOutput` class declaration
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetCleanupOutput.java`
2. **Add `getOutput(String[] args)` method** that delegates to `gatherAndFormatSurveyOutput()` using `--project-dir`
   arg or `scope.getClaudeProjectDir()` fallback
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetCleanupOutput.java`
3. **Add import** for `SkillOutput`
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetCleanupOutput.java`
4. **Run tests** to verify no regressions
5. **Build jlink bundle** and verify `/cat:cleanup` works

## Post-conditions
- [ ] `GetCleanupOutput` implements `SkillOutput`
- [ ] `getOutput(String[] args)` delegates to survey phase
- [ ] `main()` remains for plan/verify CLI phases
- [ ] All existing tests pass
- [ ] E2E: `/cat:cleanup` loads without error
