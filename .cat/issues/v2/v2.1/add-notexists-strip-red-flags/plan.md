# Plan

## Goal

Move the `Files.notExists()` vs `!Files.exists()` and `strip()` over `trim()` conventions from `.claude/rules/java.md` to
`plugin/lang/java.md` as universal Java red flags. These are not project-specific style choices but genuinely different
semantics that can cause real bugs, fitting the red flag pattern in `plugin/lang/java.md`.

## Pre-conditions

(none)

## Post-conditions

- [ ] `plugin/lang/java.md` contains a red flag entry for `!Files.exists()` recommending `Files.notExists()` instead
- [ ] `plugin/lang/java.md` contains a red flag entry for `String.trim()` recommending `String.strip()` instead
- [ ] `.claude/rules/java.md` no longer contains the `Files.notExists()` convention section
- [ ] `.claude/rules/java.md` no longer contains the `strip()` over `trim()` convention section
- [ ] Tests passing
- [ ] No regressions
- [ ] E2E verification: confirm `plugin/lang/java.md` contains both new red flag entries and `.claude/rules/java.md` no
  longer contains the moved content

## Research Findings

### Current State of `plugin/lang/java.md`

The file has sections: Performance, Security, Quality, Testing, Architecture. Each section uses a Markdown table.
Quality only has 2 columns (Pattern | Issue). Performance and Security have 3 columns (Pattern | Issue | Fix).

### Current State of `.claude/rules/java.md`

- `### Prefer strip() Over trim()` section (around line 904): explains Unicode semantics, with code examples
- `### Prefer Files.notExists() Over !Files.exists()` section (around line 938): explains semantic difference with code
  examples

### Red Flag Format

The new entries will be added to a new **Correctness** section in `plugin/lang/java.md` with 3 columns
(Pattern | Issue | Fix):

```markdown
## Correctness
| Pattern | Issue | Fix |
|---------|-------|-----|
| `!Files.exists(path)` | True for both "doesn't exist" and "can't determine" | `Files.notExists(path)` |
| `String.trim()` | Only handles ASCII whitespace (≤ U+0020) | `String.strip()` |
```

## Jobs

### Job 1

- In `plugin/lang/java.md`: append a new `## Correctness` section after the `## Architecture` section with two entries:
  - `!Files.exists(path)` | True for both "doesn't exist" and "can't determine" (permissions) | `Files.notExists(path)`
  - `String.trim()` | Only handles ASCII whitespace (≤ U+0020), misses Unicode whitespace | `String.strip()`
- In `.claude/rules/java.md`: remove the `### Prefer strip() Over trim()` section (lines 904–916) entirely
- In `.claude/rules/java.md`: remove the `### Prefer Files.notExists() Over !Files.exists()` section (lines 938–951)
  entirely
- Update `.cat/issues/v2/v2.1/add-notexists-strip-red-flags/index.json` in the same commit: set `status: closed`, `progress: 100%`
- Commit type: `refactor:` (moving conventions, no new behavior)
- Run `mvn -f client/pom.xml test` to verify no regressions
