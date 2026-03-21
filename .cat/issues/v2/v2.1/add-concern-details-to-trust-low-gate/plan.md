# Plan: add-concern-details-to-trust-low-gate

## Goal

Add `explanation` and `recommendation` fields inline to the trust=low Concern Decision Gate in
`plugin/skills/work-review-agent/first-use.md`, so users can judge each concern's validity and
decide whether to fix, defer, or skip it before the auto-fix loop runs.

## Parent Requirements

None

## Approaches

### A: Inline text expansion (chosen)
- **Risk:** LOW
- **Scope:** 1 file (minimal)
- **Description:** Extend the trust=low per-concern block in the Concern Decision Gate description
  to include `explanation` and `recommendation` fields, keeping trust=medium and trust=high
  unchanged. No code changes — this is a skill Markdown update only.

### B: Structured concern block (rejected)
- **Risk:** LOW
- **Scope:** 1 file (minimal)
- **Description:** Reformat the per-trust-level block as a table. Rejected because the existing
  format is text-based, not tabular, and a table would be harder to read at trust=low where maximum
  detail is wanted.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** trust=medium/high text might accidentally be modified
- **Mitigation:** Change is confined to the single trust=low bullet in the Concern Decision Gate
  section. trust=medium and trust=high bullets are untouched.

## Files to Modify

- `plugin/skills/work-review-agent/first-use.md` — extend the trust=low bullet in the Concern
  Decision Gate section to list `explanation` and `recommendation` among the displayed fields

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Read `plugin/skills/work-review-agent/first-use.md` lines around the Concern Decision Gate
  section (search for "trust=low" near "Concern Decision Gate").
  Find the bullet:
  ```
  - **trust=low**: Invoke AskUserQuestion tool with detailed FIX/DEFER summary (all fields: severity, stakeholder,
    location, decision, benefit, cost, threshold) and options:
  ```
  Change it to:
  ```
  - **trust=low**: Invoke AskUserQuestion tool with detailed FIX/DEFER summary (all fields: severity, stakeholder,
    location, explanation, recommendation, decision, benefit, cost, threshold) and options:
  ```
  - Files: `plugin/skills/work-review-agent/first-use.md`

- Verify no other occurrences of the trust=low field list exist in the file (search for
  "severity, stakeholder,
  location, decision" or similar) and update any duplicates found.
  - Files: `plugin/skills/work-review-agent/first-use.md`

- Run `mvn -f client/pom.xml test` to confirm no regressions.

- Update `index.json` to mark the issue closed with 100% progress. Write:
  ```json
  {
    "status": "closed",
    "progress": "100%"
  }
  ```
  - Files: `.cat/issues/v2/v2.1/add-concern-details-to-trust-low-gate/index.json`

## Post-conditions

- [ ] `plugin/skills/work-review-agent/first-use.md` trust=low Concern Decision Gate bullet lists
  `explanation` and `recommendation` among the displayed fields
- [ ] trust=medium and trust=high Concern Decision Gate bullets are unchanged
- [ ] All Maven tests pass (`mvn -f client/pom.xml test` exits 0)
- [ ] E2E: At trust=low, when a stakeholder review returns at least one concern, the AskUserQuestion
  presented to the user includes the concern's explanation and recommendation text inline
