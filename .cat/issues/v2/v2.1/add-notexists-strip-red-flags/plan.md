# Plan

## Goal

Move the `Files.notExists()` vs `!Files.exists()` and `strip()` over `trim()` conventions from `.claude/rules/java.md` to `plugin/lang/java.md` as universal Java red flags. These are not project-specific style choices but genuinely different semantics that can cause real bugs, fitting the red flag pattern in `plugin/lang/java.md`.

## Pre-conditions

(none)

## Post-conditions

- [ ] `plugin/lang/java.md` contains a red flag entry for `!Files.exists()` recommending `Files.notExists()` instead
- [ ] `plugin/lang/java.md` contains a red flag entry for `String.trim()` recommending `String.strip()` instead
- [ ] `.claude/rules/java.md` no longer contains the `Files.notExists()` convention section
- [ ] `.claude/rules/java.md` no longer contains the `strip()` over `trim()` convention section
- [ ] Tests passing
- [ ] No regressions
- [ ] E2E verification: confirm `plugin/lang/java.md` contains both new red flag entries and `.claude/rules/java.md` no longer contains the moved content
