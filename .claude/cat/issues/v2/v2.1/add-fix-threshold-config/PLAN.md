# Plan: add-fix-threshold-config

## Goal

Add a `fixThreshold` config option to `cat-config.json` that sets the minimum stakeholder concern severity requiring
action. Concerns below this threshold are silently ignored — not fixed, not deferred, not tracked. This is distinct from
`reviewThreshold` (which controls auto-fix loops) and `patience` (which controls fix-vs-defer cost/benefit).

## Satisfies

None — user-requested enhancement

## Context: Three Concern Thresholds

| Config | Question it answers | Effect |
|--------|-------------------|--------|
| `fixThreshold` (NEW) | "Which concerns matter at all?" | Below threshold → ignored entirely |
| `patience` (existing) | "Fix now or defer to a future issue?" | Cost/benefit analysis for concerns that pass fixThreshold |
| `reviewThreshold` (existing) | "Auto-fix or show to user?" | Controls auto-fix loop vs user approval gate |

**Pipeline:** concern raised → fixThreshold filter → patience fix/defer decision → reviewThreshold auto-fix loop

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Users may set threshold too high and miss important issues
- **Mitigation:** Default to `low` (all concerns matter); document clearly what each level ignores

## Proposed Config Schema

Add `fixThreshold` to `cat-config.json`:

```json
{
  "fixThreshold": "medium"
}
```

**Values and meaning:**

| Value | Concerns that require action | Concerns ignored |
|-------|------------------------------|-----------------|
| `low` (default) | CRITICAL, HIGH, MEDIUM, LOW | None |
| `medium` | CRITICAL, HIGH, MEDIUM | LOW |
| `high` | CRITICAL, HIGH | MEDIUM, LOW |
| `critical` | CRITICAL | HIGH, MEDIUM, LOW |

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/Config.java` — Add `fixThreshold` default and getter
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/ConcernSeverity.java` — New enum for CRITICAL/HIGH/MEDIUM/LOW
  severity levels (reusable by both fixThreshold and future severity-based logic)
- `plugin/skills/stakeholder-review/first-use.md` — Filter out concerns below fixThreshold before aggregation
- `plugin/skills/work-with-issue/first-use.md` — Redefine "untracked deferred concerns" (line 880, Step 6 Part B) in
  terms of fixThreshold: concerns below fixThreshold are ignored entirely (not presented in wizard), replacing the
  current ad-hoc "MEDIUM or LOW severity, or any concern not covered by the severity × patience matrix" language
- `plugin/agents/stakeholder-*.md` — Update severity table to note that LOW concerns may be ignored depending on config
- `docs/patience.md` — Add section explaining how fixThreshold interacts with patience
- `.claude/cat/cat-config.json` — Add `fixThreshold` default value

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Steps

1. **Create ConcernSeverity enum**
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/ConcernSeverity.java`
   - Enum with CRITICAL, HIGH, MEDIUM, LOW values
   - Include `fromString()` method (case-insensitive) and `isAtLeast(ConcernSeverity threshold)` comparison method
   - Add tests in `client/src/test/java/io/github/cowwoc/cat/hooks/test/ConcernSeverityTest.java`

2. **Add fixThreshold to Config**
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/Config.java`
   - Add `"fixThreshold"` to DEFAULTS with value `"low"`
   - Add `getFixThreshold()` method returning `ConcernSeverity`
   - Add tests for config loading with and without the new field

3. **Update stakeholder review to filter by fixThreshold**
   - Files: `plugin/skills/stakeholder-review/first-use.md`
   - After collecting concerns from all stakeholders, filter out concerns with severity below fixThreshold
   - Add a note in the report step indicating how many concerns were filtered

4. **Redefine untracked concerns in work-with-issue**
   - Files: `plugin/skills/work-with-issue/first-use.md`
   - Line 880: Replace "Any deferred concern that does NOT have an issue automatically created above (e.g., MEDIUM or
     LOW severity, or any concern not covered by the severity × patience matrix)" with a fixThreshold-based definition:
     concerns below fixThreshold are ignored entirely (not deferred, not presented in wizard)
   - Step 6 Part B (line 919-949): Redefine "untracked deferred concerns" as concerns that (a) are at or above
     fixThreshold AND (b) were deferred by the patience matrix but not auto-tracked as issues. Concerns below
     fixThreshold never appear here — they are already filtered out upstream
   - Step 6 skip conditions (line 951-955): Add skip condition when all deferred concerns are below fixThreshold

5. **Update cat-config.json default**
   - Files: `.claude/cat/cat-config.json`
   - Add `"fixThreshold": "low"` to the default config

6. **Update patience documentation**
   - Files: `docs/patience.md`
   - Add section explaining the concern pipeline: fixThreshold → patience → reviewThreshold

## Post-conditions

- [ ] `fixThreshold` is read from `cat-config.json` with default `"low"` when absent
- [ ] Setting `fixThreshold` to `"high"` causes MEDIUM and LOW concerns to be silently dropped from review results
- [ ] Setting `fixThreshold` to `"low"` preserves all concerns (backward compatible)
- [ ] `ConcernSeverity` enum exists with `fromString()` and `isAtLeast()` methods
- [ ] Config getter returns `ConcernSeverity` enum (not raw string)
- [ ] All tests pass (`mvn -f client/pom.xml verify`)
