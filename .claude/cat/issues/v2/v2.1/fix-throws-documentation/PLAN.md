# Plan: fix-throws-documentation

## Problem
Pre-existing `@throws` Javadoc violations in ProcessRunner.java and TokenCounter.java do not match the convention
in `.claude/cat/conventions/java.md` which requires all thrown exceptions to be documented.

### Specific Violations

**ProcessRunner.java - Result record:**
- Documents `@throws IllegalArgumentException` but `requireThat().isNotNull()` throws `NullPointerException`
- Should document `@throws NullPointerException` instead of (or in addition to) `IllegalArgumentException`

**TokenCounter.java - 2 methods:**
- Methods using `requireThat()` calls missing `@throws NullPointerException` documentation

## Satisfies
None (convention compliance fix)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None - purely documentation changes
- **Mitigation:** N/A

## Files to Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/ProcessRunner.java` - Fix @throws on Result record
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/TokenCounter.java` - Add @throws to 2 methods

## Acceptance Criteria
- [ ] All `requireThat().isNotNull()` calls have `@throws NullPointerException` documented
- [ ] All `requireThat().isNotBlank()` calls have both `@throws NullPointerException` and
      `@throws IllegalArgumentException` documented
- [ ] No incorrect `@throws` annotations (e.g., documenting wrong exception type)
- [ ] Tests pass

## Execution Steps
1. **Fix ProcessRunner.java Result record:** Update @throws to document NullPointerException for isNotNull() calls
2. **Fix TokenCounter.java:** Add @throws NullPointerException to the 2 methods with requireThat() calls
3. **Run tests:** Verify no regressions
