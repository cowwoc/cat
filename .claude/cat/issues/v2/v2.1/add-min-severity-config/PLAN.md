# Plan: add-min-severity-config

## Goal

Add a `minSeverity` config option to `cat-config.json` that sets the minimum stakeholder concern severity requiring
action. Concerns below this threshold are silently ignored — not fixed, not deferred, not tracked. This is distinct from
`reviewThreshold` (which controls auto-fix loops) and `patience` (which controls fix-vs-defer cost/benefit).

## Satisfies

None — user-requested enhancement

## Context: Three Concern Thresholds

| Config | Question it answers | Effect |
|--------|-------------------|--------|
| `minSeverity` (NEW) | "Which concerns matter at all?" | Below threshold → ignored entirely |
| `patience` (existing) | "Fix now or defer to a future issue?" | Cost/benefit analysis for concerns that pass minSeverity |
| `reviewThreshold` (existing) | "Auto-fix or show to user?" | Controls auto-fix loop vs user approval gate |

**Pipeline:** concern raised → minSeverity filter → patience fix/defer decision → reviewThreshold auto-fix loop

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Users may set threshold too high and miss important issues
- **Mitigation:** Default to `low` (all concerns matter); document clearly what each level ignores

## Proposed Config Schema

Add `minSeverity` to `cat-config.json`:

```json
{
  "minSeverity": "medium"
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

- `client/src/main/java/io/github/cowwoc/cat/hooks/Config.java` — Add `minSeverity` default and getter
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/ConcernSeverity.java` — New enum for CRITICAL/HIGH/MEDIUM/LOW
  severity levels (reusable by both minSeverity and future severity-based logic)
- `plugin/skills/stakeholder-review/first-use.md` — Filter out concerns below minSeverity before aggregation
- `plugin/skills/work-with-issue/first-use.md` — Redefine "untracked deferred concerns" (line 880, Step 6 Part B) in
  terms of minSeverity: concerns below minSeverity are ignored entirely (not presented in wizard), replacing the
  current ad-hoc "MEDIUM or LOW severity, or any concern not covered by the severity × patience matrix" language
- `plugin/agents/stakeholder-*.md` — Change JSON severity enum from `CRITICAL|HIGH|MEDIUM` to
  `CRITICAL|HIGH|MEDIUM|LOW` in all 10 agent files
- `docs/severity.md` — New file documenting severity levels, minSeverity config, and use-case guidance per threshold
- `docs/patience.md` — Add section explaining how minSeverity interacts with patience
- `.claude/cat/cat-config.json` — Add `minSeverity` default value

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Steps

1. **Create ConcernSeverity enum**
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/ConcernSeverity.java`
   - Enum with CRITICAL, HIGH, MEDIUM, LOW values
   - Include `fromString()` method (case-insensitive) and `isAtLeast(ConcernSeverity threshold)` comparison method
   - Add tests in `client/src/test/java/io/github/cowwoc/cat/hooks/test/ConcernSeverityTest.java`

2. **Add minSeverity to Config**
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/Config.java`
   - Add `"minSeverity"` to DEFAULTS with value `"low"`
   - Add `getMinSeverity()` method returning `ConcernSeverity`
   - Add tests for config loading with and without the new field

3. **Enable LOW severity in stakeholder instructions**
   - Files: `plugin/skills/stakeholder-review/first-use.md`, `plugin/agents/stakeholder-*.md` (all 10)
   - In `first-use.md` line 771: change JSON severity enum from `"CRITICAL|HIGH|MEDIUM"` to
     `"CRITICAL|HIGH|MEDIUM|LOW"`
   - In all 10 `plugin/agents/stakeholder-*.md` files: change JSON severity enum from `"CRITICAL|HIGH|MEDIUM"` to
     `"CRITICAL|HIGH|MEDIUM|LOW"`
   - The severity definitions table (lines 722-727) already includes LOW — no change needed there

4. **Update stakeholder review to filter by minSeverity**
   - Files: `plugin/skills/stakeholder-review/first-use.md`
   - After collecting concerns from all stakeholders, filter out concerns with severity below minSeverity
   - Add a note in the report step indicating how many concerns were filtered

5. **Redefine untracked concerns in work-with-issue**
   - Files: `plugin/skills/work-with-issue/first-use.md`
   - Line 880: Replace "Any deferred concern that does NOT have an issue automatically created above (e.g., MEDIUM or
     LOW severity, or any concern not covered by the severity × patience matrix)" with a minSeverity-based definition:
     concerns below minSeverity are ignored entirely (not deferred, not presented in wizard)
   - Step 6 Part B (line 919-949): Redefine "untracked deferred concerns" as concerns that (a) are at or above
     minSeverity AND (b) were deferred by the patience matrix but not auto-tracked as issues. Concerns below
     minSeverity never appear here — they are already filtered out upstream
   - Step 6 skip conditions (line 951-955): Add skip condition when all deferred concerns are below minSeverity

6. **Update cat-config.json default**
   - Files: `.claude/cat/cat-config.json`
   - Add `"minSeverity": "low"` to the default config

7. **Create docs/severity.md**
   - Files: `docs/severity.md`
   - Document the four severity levels (CRITICAL, HIGH, MEDIUM, LOW) with definitions and examples
   - Document `minSeverity` config option with use-case guidance for each threshold value:
     - `low` — Production systems, regulated environments, security-sensitive applications. All concerns are
       actionable. Ideal when quality, compliance, and correctness are non-negotiable.
     - `medium` — Standard development. Ignores minor stylistic suggestions (LOW) while preserving all substantive
       concerns. Appropriate for most production software.
     - `high` — MVPs, internal tools, time-boxed sprints. Ignores MEDIUM improvements (cyclomatic complexity, missing
       edge case tests) to focus on significant issues. Acceptable when shipping speed outweighs polish.
     - `critical` — Quick prototypes, throwaway spikes, proof-of-concept work. Only blocks on release-blocking
       issues (data loss, security breach, system crash). Allows unsanitized inputs, missing critical-path tests,
       method duplication. Not suitable for any code that will be deployed to users.
   - Clarify the distinction between minSeverity and patience near the top of the file:
     - `minSeverity` controls WHAT gets fixed — a hard floor that determines which concerns exist at all
     - `patience` controls WHEN it gets fixed — a cost/benefit analysis that determines whether an acknowledged concern
       is fixed now (inline) or deferred to a future issue
   - Include a concrete example illustrating the difference:
     > **Example:** A reviewer raises a MEDIUM concern: "High cyclomatic complexity in `PaymentService.process()`."
     >
     > - With `minSeverity: "high"` — the concern is **ignored**. It never appears in review results, is never
     >   tracked, and no one is asked about it. It ceases to exist.
     > - With `minSeverity: "low"` and `patience: "high"` — the concern is **acknowledged but deferred**. It appears
     >   in review results, a tracking issue is created (or the user is asked how to handle it), and it will be
     >   addressed in a future issue. The concern is real; the team just decided "not now."
     >
     > Both result in the concern not being fixed in the current issue. The difference: deferred concerns are tracked
     > and will eventually be addressed. Ignored concerns are permanently dropped.
   - Document the concern pipeline: minSeverity → patience → reviewThreshold

8. **Update patience documentation**
   - Files: `docs/patience.md`
   - Add cross-reference to `docs/severity.md` for minSeverity details
   - Add section explaining the concern pipeline: minSeverity → patience → reviewThreshold

## Post-conditions

- [ ] `minSeverity` is read from `cat-config.json` with default `"low"` when absent
- [ ] Setting `minSeverity` to `"high"` causes MEDIUM and LOW concerns to be silently dropped from review results
- [ ] Setting `minSeverity` to `"low"` preserves all concerns (backward compatible)
- [ ] `ConcernSeverity` enum exists with `fromString()` and `isAtLeast()` methods
- [ ] Config getter returns `ConcernSeverity` enum (not raw string)
- [ ] `docs/severity.md` exists with use-case guidance for each minSeverity threshold value
- [ ] Stakeholder agents and review skill accept LOW severity in their JSON format
- [ ] All tests pass (`mvn -f client/pom.xml verify`)
